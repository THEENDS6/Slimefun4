package cc.theends6.sfx.internal.radiation;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxRadiationRuleContext;
import cc.theends6.sfx.api.behavior.SfxRadiationRuleProvider;
import cc.theends6.sfx.api.behavior.SfxRadiationRules;
import cc.theends6.sfx.api.behavior.SfxRadiationSymptomProfile;
import cc.theends6.sfx.api.behavior.SfxRadiationSymptomContext;
import cc.theends6.sfx.api.behavior.SfxRadiationSymptomHandler;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SfxRadiationService implements Listener {
    private static final int DEFAULT_SCAN_INTERVAL = 10;
    private static final int DEFAULT_RECOVERY_PER_SCAN = 10;
    private static final int DEFAULT_MAX_EXPOSURE = 3000;
    private static final double DEFAULT_REDUCTION_PER_HAZMAT_PIECE = 0.25D;
    private static final int EFFECT_DURATION_TICKS = 40;
    private static final int DEFAULT_RESPAWN_IMMUNITY_TICKS = 200;
    private static final long RADIATION_DEATH_WINDOW_MILLIS = 3_000L;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxItemRegistry registry;
    private final SfxLocalization localization;
    private final SfxPlayerDataService playerData;
    private final Map<String, SfxRadiationLevel> radioactiveItems = new HashMap<>();
    private final Map<UUID, Integer> cachedExposure = new ConcurrentHashMap<>();
    private final Map<UUID, SfxRadiationStage> lastAnnouncedStage = new ConcurrentHashMap<>();
    private final Set<UUID> currentlyAffectedByRadiation = ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingRespawn = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> respawnImmuneUntilMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRadiationDamageMillis = new ConcurrentHashMap<>();
    private boolean running;

    public SfxRadiationService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxItemRegistry registry, SfxLocalization localization, SfxPlayerDataService playerData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        rebuildRadioactiveItemIndex();
    }

    public void start() {
        if (!enabled()) {
            return;
        }
        this.running = true;
        scheduleScan();
    }

    public void shutdown() {
        this.running = false;
        for (Map.Entry<UUID, Integer> entry : cachedExposure.entrySet()) {
            playerData.find(entry.getKey()).ifPresent(profile -> profile.setRadiationExposure(entry.getValue()));
        }
        cachedExposure.clear();
    }

    public void clearExposure(Player player) {
        if (player == null) {
            return;
        }
        setExposure(player, 0);
        lastAnnouncedStage.remove(player.getUniqueId());
        currentlyAffectedByRadiation.remove(player.getUniqueId());
        respawnImmuneUntilMillis.remove(player.getUniqueId());
        lastRadiationDamageMillis.remove(player.getUniqueId());
        removeRadiationEffects(player);
        playerData.find(player.getUniqueId()).ifPresent(playerData::saveAsync);
    }

    public int exposure(Player player) {
        if (player == null) {
            return 0;
        }
        return exposure(player.getUniqueId());
    }

    public int exposure(UUID playerId) {
        Integer cached = cachedExposure.get(playerId);
        if (cached != null) {
            return cached;
        }
        return playerData.find(playerId).map(profile -> {
            int value = profile.radiationExposure();
            cachedExposure.put(playerId, value);
            return value;
        }).orElse(0);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        if (isRadiationDeath(player)) {
            event.setDeathMessage(localization.text("radiation.messages.death", Map.of("player", player.getName())));
        }
        awaitingRespawn.add(playerId);
        clearExposure(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        awaitingRespawn.remove(playerId);
        respawnImmuneUntilMillis.put(playerId, System.currentTimeMillis() + respawnImmunityTicks() * 50L);
        currentlyAffectedByRadiation.remove(playerId);
        lastAnnouncedStage.put(playerId, SfxRadiationStage.NONE);
        removeRadiationEffects(event.getPlayer());
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        GameMode newMode = event.getNewGameMode();
        if (newMode == GameMode.CREATIVE || newMode == GameMode.SPECTATOR) {
            clearExposure(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastAnnouncedStage.remove(playerId);
        currentlyAffectedByRadiation.remove(playerId);
        awaitingRespawn.remove(playerId);
        respawnImmuneUntilMillis.remove(playerId);
        lastRadiationDamageMillis.remove(playerId);
        cachedExposure.remove(playerId);
    }

    private void scheduleScan() {
        runtime.executeGlobalLater(rules().scanIntervalTicks(), () -> {
            if (!running || !enabled()) {
                return;
            }
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                runtime.executeForPlayer(player, () -> tickPlayer(player));
            }
            scheduleScan();
        });
    }

    private void tickPlayer(Player player) {
        if (!player.isOnline() || player.isDead() || awaitingRespawn.contains(player.getUniqueId())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            if (exposure(player) > 0) {
                clearExposure(player);
            }
            currentlyAffectedByRadiation.remove(player.getUniqueId());
            return;
        }

        int current = exposure(player);
        int radioactiveGain = radioactiveExposureInInventory(player);
        SfxRadiationRules rules = rules();
        int adjustedGain = 0;
        if (radioactiveGain > 0) {
            int hazmatPieces = hazmatPieces(player);
            if (rules.partialHazmatProtection()) {
                double multiplier = Math.max(0.0D, 1.0D - hazmatPieces * rules.hazmatReductionPerPiece());
                adjustedGain = (int) Math.floor(radioactiveGain * multiplier);
            } else if (hazmatPieces < 4) {
                adjustedGain = radioactiveGain;
            }
        }

        if (adjustedGain > 0) {
            warnRadiationExposureTransition(player);
        } else {
            currentlyAffectedByRadiation.remove(player.getUniqueId());
        }

        if (isRespawnImmune(player)) {
            removeRadiationEffects(player);
            return;
        }

        int next;
        if (radioactiveGain <= 0 || (!rules.partialHazmatProtection() && adjustedGain <= 0)) {
            next = Math.max(0, current - rules.recoveryPerScan());
        } else {
            next = Math.min(rules.maxExposure(), current + adjustedGain);
        }
        if (next != current) {
            setExposure(player, next);
        }
        if (rules.symptomProfile() == SfxRadiationSymptomProfile.SFX_REWORK) {
            SfxRadiationStage stage = SfxRadiationStage.fromExposure(next);
            announceStageChange(player, stage);
            applyAddonSymptoms(player, next, stage);
        } else {
            lastAnnouncedStage.remove(player.getUniqueId());
            applyClassicSymptoms(player, next, rules.scanIntervalTicks());
        }
    }

    private boolean isRespawnImmune(Player player) {
        Long until = respawnImmuneUntilMillis.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            respawnImmuneUntilMillis.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private boolean isRadiationDeath(Player player) {
        long lastDamage = lastRadiationDamageMillis.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastDamage <= RADIATION_DEATH_WINDOW_MILLIS) {
            return true;
        }
        EntityDamageEvent damage = player.getLastDamageCause();
        if (damage == null || exposure(player) <= 0) {
            return false;
        }
        EntityDamageEvent.DamageCause cause = damage.getCause();
        return cause == EntityDamageEvent.DamageCause.MAGIC
                || cause == EntityDamageEvent.DamageCause.WITHER
                || cause == EntityDamageEvent.DamageCause.POISON;
    }

    private void setExposure(Player player, int exposure) {
        int normalized = Math.max(0, Math.min(rules().maxExposure(), exposure));
        cachedExposure.put(player.getUniqueId(), normalized);
        playerData.find(player.getUniqueId()).ifPresent(profile -> profile.setRadiationExposure(normalized));
    }

    private int radioactiveExposureInInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        int total = 0;
        total += radioactiveExposure(inventory.getStorageContents());
        total += radioactiveExposure(inventory.getArmorContents());
        total += radioactiveExposure(inventory.getExtraContents());
        return total;
    }

    private int radioactiveExposure(ItemStack[] contents) {
        int total = 0;
        if (contents == null) {
            return 0;
        }
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            SfxRadiationLevel level = items.readMarker(item)
                    .map(SfxItemMarker::itemId)
                    .map(radioactiveItems::get)
                    .orElse(null);
            if (level == null) {
                continue;
            }
            total += item.getAmount() * level.exposureModifier();
        }
        return total;
    }

    private int hazmatPieces(Player player) {
        int pieces = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) {
                continue;
            }
            if (items.readMarker(armor).map(SfxItemMarker::flags).map(flags -> flags.contains("armor-hazmat")).orElse(false)) {
                pieces++;
            }
        }
        return Math.min(4, pieces);
    }

    private void warnRadiationExposureTransition(Player player) {
        if (!currentlyAffectedByRadiation.add(player.getUniqueId())) {
            return;
        }
        player.sendMessage(localization.component("radiation.messages.exposure-warning"));
    }

    private void announceStageChange(Player player, SfxRadiationStage stage) {
        UUID playerId = player.getUniqueId();
        SfxRadiationStage previous = lastAnnouncedStage.get(playerId);
        if (previous == stage) {
            return;
        }
        if (stage == SfxRadiationStage.NONE) {
            if (previous != null && previous != SfxRadiationStage.NONE) {
                player.sendMessage(localization.component("radiation.messages.recovered"));
            }
            lastAnnouncedStage.put(playerId, SfxRadiationStage.NONE);
            return;
        }
        lastAnnouncedStage.put(playerId, stage);
        player.sendMessage(stageMessage(stage));
    }

    private Component stageMessage(SfxRadiationStage stage) {
        return Component.text(localization.text("radiation.messages.stage-prefix"), NamedTextColor.RED)
                .append(Component.text(stageNumeral(stage), stageNumeralColor(stage)))
                .append(Component.text(" " + stageDescription(stage), NamedTextColor.GRAY));
    }

    private String stageNumeral(SfxRadiationStage stage) {
        return switch (stage) {
            case I -> "I";
            case II -> "II";
            case III -> "III";
            case IV -> "IV";
            case V -> "V";
            case NONE -> "";
        };
    }

    private String stageDescription(SfxRadiationStage stage) {
        return switch (stage) {
            case I -> localization.text("radiation.stages.i.description");
            case II -> localization.text("radiation.stages.ii.description");
            case III -> localization.text("radiation.stages.iii.description");
            case IV -> localization.text("radiation.stages.iv.description");
            case V -> localization.text("radiation.stages.v.description");
            case NONE -> "";
        };
    }

    private NamedTextColor stageNumeralColor(SfxRadiationStage stage) {
        return switch (stage) {
            case I -> NamedTextColor.WHITE;
            case II -> NamedTextColor.YELLOW;
            case III -> NamedTextColor.RED;
            case IV, V -> NamedTextColor.DARK_RED;
            case NONE -> NamedTextColor.GRAY;
        };
    }

    private void applyAddonSymptoms(Player player, int exposure, SfxRadiationStage stage) {
        if (stage == SfxRadiationStage.NONE || !(plugin instanceof SlimeFunXPlugin sfx) || sfx.api() == null) {
            return;
        }
        SfxRadiationSymptomContext context = new SfxRadiationSymptomContext(
                player,
                exposure,
                stage.level(),
                EFFECT_DURATION_TICKS,
                () -> lastRadiationDamageMillis.put(player.getUniqueId(), System.currentTimeMillis()));
        for (SfxRadiationSymptomHandler handler : sfx.api().behaviors().radiationSymptomHandlers()) {
            if (handler.apply(context)) {
                return;
            }
        }
    }

    private void applyClassicSymptoms(Player player, int exposure, int scanIntervalTicks) {
        if (exposure <= 0) {
            return;
        }
        int duration = Math.max(1, scanIntervalTicks + 20);
        if (exposure >= 10) {
            addEffect(player, "SLOW", duration, 3);
        }
        if (exposure >= 25) {
            addEffect(player, "WITHER", duration, 0);
        }
        if (exposure >= 50) {
            addEffect(player, "BLINDNESS", duration, 4);
        }
        if (exposure >= 75) {
            addEffect(player, "WITHER", duration, 3);
        }
        if (exposure >= 100) {
            lastRadiationDamageMillis.put(player.getUniqueId(), System.currentTimeMillis());
            addEffect(player, "HARM", duration, 49);
        }
    }

    private void addEffect(Player player, String rawName, int durationTicks, int amplifier) {
        PotionEffectType type = resolvePotion(rawName);
        if (type == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, Math.max(1, durationTicks), amplifier, true, true, true), true);
    }

    private void removeRadiationEffects(Player player) {
        for (String effect : new String[] {"WEAKNESS", "HUNGER", "SLOW", "POISON", "SLOW_DIGGING", "CONFUSION", "WITHER", "BLINDNESS"}) {
            PotionEffectType type = resolvePotion(effect);
            if (type != null) {
                player.removePotionEffect(type);
            }
        }
    }

    private PotionEffectType resolvePotion(String rawName) {
        PotionEffectType type = PotionEffectType.getByName(rawName);
        if (type != null) {
            return type;
        }
        String key = switch (rawName.toUpperCase(Locale.ROOT)) {
            case "SLOW" -> "slowness";
            case "SLOW_DIGGING" -> "mining_fatigue";
            case "CONFUSION" -> "nausea";
            case "HARM" -> "instant_damage";
            default -> rawName.toLowerCase(Locale.ROOT);
        };
        return PotionEffectType.getByKey(NamespacedKey.minecraft(key));
    }

    private void rebuildRadioactiveItemIndex() {
        radioactiveItems.clear();
        PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();
        for (SfxItemDefinition definition : registry.items()) {
            SfxRadiationLevel level = radiationLevel(definition, plainText);
            if (level != null) {
                radioactiveItems.put(definition.id(), level);
            }
        }
        
        radioactiveItems.putIfAbsent("sf:tiny_uranium", SfxRadiationLevel.LOW);
        radioactiveItems.putIfAbsent("sf:small_uranium", SfxRadiationLevel.MODERATE);
        radioactiveItems.putIfAbsent("sf:nether_ice", SfxRadiationLevel.MODERATE);
        radioactiveItems.putIfAbsent("sf:uranium", SfxRadiationLevel.HIGH);
        radioactiveItems.putIfAbsent("sf:neptunium", SfxRadiationLevel.HIGH);
        radioactiveItems.putIfAbsent("sf:blistering_ingot", SfxRadiationLevel.HIGH);
        radioactiveItems.putIfAbsent("sf:plutonium", SfxRadiationLevel.VERY_HIGH);
        radioactiveItems.putIfAbsent("sf:boosted_uranium", SfxRadiationLevel.VERY_HIGH);
        radioactiveItems.putIfAbsent("sf:blistering_ingot_2", SfxRadiationLevel.VERY_HIGH);
        radioactiveItems.putIfAbsent("sf:blistering_ingot_3", SfxRadiationLevel.VERY_HIGH);
        radioactiveItems.putIfAbsent("sf:enriched_nether_ice", SfxRadiationLevel.VERY_HIGH);
    }

    private SfxRadiationLevel radiationLevel(SfxItemDefinition definition, PlainTextComponentSerializer plainText) {
        for (var line : definition.lore()) {
            String text = plainText.serialize(line).toLowerCase(Locale.ROOT).replace('_', ' ');
            if (!text.contains("radiation level") && !text.contains("辐射等级")) {
                continue;
            }
            if (text.contains("very deadly") || text.contains("致命")) {
                return SfxRadiationLevel.VERY_DEADLY;
            }
            if (text.contains("very high") || text.contains("极高")) {
                return SfxRadiationLevel.VERY_HIGH;
            }
            if (text.contains("moderate") || text.contains("中等")) {
                return SfxRadiationLevel.MODERATE;
            }
            if (text.contains("high") || text.contains("高")) {
                return SfxRadiationLevel.HIGH;
            }
            if (text.contains("low") || text.contains("低")) {
                return SfxRadiationLevel.LOW;
            }
        }
        return null;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("radiation.enabled", true);
    }

    private int scanIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt("radiation.scan-interval-ticks", DEFAULT_SCAN_INTERVAL));
    }

    private int recoveryPerScan() {
        return Math.max(0, plugin.getConfig().getInt("radiation.recovery-per-scan", DEFAULT_RECOVERY_PER_SCAN));
    }

    private int maxExposure() {
        return Math.max(SfxRadiationStage.V.threshold(), plugin.getConfig().getInt("radiation.max-exposure", DEFAULT_MAX_EXPOSURE));
    }

    private double hazmatReductionPerPiece() {
        return Math.max(0.0D, Math.min(1.0D, plugin.getConfig().getDouble("radiation.protection.reduction-per-piece", DEFAULT_REDUCTION_PER_HAZMAT_PIECE)));
    }

    private int respawnImmunityTicks() {
        return rules().respawnImmunityTicks();
    }

    private SfxRadiationRules rules() {
        SfxRadiationRuleContext context = new SfxRadiationRuleContext(
                scanIntervalTicks(),
                recoveryPerScan(),
                maxExposure(),
                hazmatReductionPerPiece(),
                Math.max(0, plugin.getConfig().getInt("radiation.respawn-immunity-ticks", DEFAULT_RESPAWN_IMMUNITY_TICKS))
        );
        SfxRadiationRules rules = SfxRadiationRules.classicDefaults(context.configuredRespawnImmunityTicks());
        SfxApi api = sfxApi();
        if (api == null) {
            return rules;
        }
        for (SfxRadiationRuleProvider provider : api.behaviors().radiationRuleProviders()) {
            SfxRadiationRules provided = provider.apply(context, rules);
            if (provided != null) {
                rules = provided;
            }
        }
        return rules;
    }

    private SfxApi sfxApi() {
        return plugin instanceof SlimeFunXPlugin sfx ? sfx.api() : null;
    }
}
