package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.config.SfxLegacyItemBehaviorConfig;
import cc.theends6.sfx.internal.radiation.SfxRadiationService;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.playerdata.SfxPlayerProfile;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.util.SfxEventGuards;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class SfxLegacyUtilityListener implements Listener {
    private static final Set<String> CURABLE_EFFECTS = Set.of(
            "POISON", "WITHER", "SLOW", "SLOW_DIGGING", "WEAKNESS", "CONFUSION", "BLINDNESS", "BAD_OMEN"
    );
    private static final Set<Material> GOLD_PAN_INPUTS = Set.of(Material.GRAVEL);
    private static final Set<Material> NETHER_GOLD_PAN_INPUTS = Set.of(Material.SOUL_SAND, Material.SOUL_SOIL);

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxLegacyItemBehaviorConfig behaviorConfig;
    private final SfxBlockDataService blockData;
    private final SfxRadiationService radiationService;
    private final SfxPlayerDataService playerData;
    private final SfxResearchService researches;
    private final NamespacedKey tapeAnchorKey;
    private final NamespacedKey stormStaffUsageKey;
    private final NamespacedKey knowledgeTomeOwnerKey;
    private final NamespacedKey knowledgeTomeOwnerNameKey;
    private final DecimalFormat distanceFormat = new DecimalFormat("##.###");
    private final Map<UUID, Long> magnetTick = new HashMap<>();
    private final Map<UUID, GrappleState> grapples = new HashMap<>();
    private final Map<UUID, Long> grapplingNoFallUntil = new HashMap<>();

    public SfxLegacyUtilityListener(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization, SfxLegacyItemBehaviorConfig behaviorConfig, SfxBlockDataService blockData, SfxRadiationService radiationService, SfxPlayerDataService playerData, SfxResearchService researches) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.behaviorConfig = Objects.requireNonNull(behaviorConfig, "behaviorConfig");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.radiationService = Objects.requireNonNull(radiationService, "radiationService");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        this.researches = Objects.requireNonNull(researches, "researches");
        this.tapeAnchorKey = new NamespacedKey(plugin, "tape_anchor");
        this.stormStaffUsageKey = new NamespacedKey(plugin, "stormstaff_usage");
        this.knowledgeTomeOwnerKey = new NamespacedKey(plugin, "knowledge_tome_owner");
        this.knowledgeTomeOwnerNameKey = new NamespacedKey(plugin, "knowledge_tome_owner_name");
    }

    boolean handleItemUse(PlayerInteractEvent event, String itemId) {
        switch (itemId) {
            case "sf:portable_crafter" -> openPortableCrafter(event);
            case "sf:portable_dustbin" -> openPortableDustbin(event);
            case "sf:ender_backpack" -> openEnderBackpack(event);
            case "sf:magic_eye_of_ender" -> useMagicEye(event);
            case "sf:infernal_bonemeal" -> useInfernalBonemeal(event);
            case "sf:tape_measure" -> useTapeMeasure(event);
            case "sf:gold_pan" -> useGoldPan(event, GOLD_PAN_INPUTS, List.of(
                    drop(40, new ItemStack(Material.FLINT)),
                    drop(20, new ItemStack(Material.CLAY_BALL)),
                    drop(35, items.create("sf:sifted_ore")),
                    drop(5, new ItemStack(Material.IRON_NUGGET))
            ));
            case "sf:nether_gold_pan" -> useGoldPan(event, NETHER_GOLD_PAN_INPUTS, List.of(
                    drop(50, new ItemStack(Material.QUARTZ)),
                    drop(25, new ItemStack(Material.GOLD_NUGGET)),
                    drop(10, new ItemStack(Material.NETHER_WART)),
                    drop(8, new ItemStack(Material.BLAZE_POWDER)),
                    drop(5, new ItemStack(Material.GLOWSTONE_DUST)),
                    drop(2, new ItemStack(Material.GHAST_TEAR))
            ));
            case "sf:grappling_hook" -> useGrapplingHook(event);
            case "sf:rag" -> useBandageLike(event, 4.0, true, Sound.BLOCK_WOOL_BREAK);
            case "sf:bandage" -> useBandageLike(event, 8.0, true, Sound.BLOCK_WOOL_BREAK);
            case "sf:splint" -> useBandageLike(event, 4.0, false, Sound.ITEM_ARMOR_EQUIP_GENERIC);
            case "sf:vitamins" -> useMedicalSupply(event, 8.0);
            case "sf:medicine" -> useMedicalSupply(event, 8.0);
            case "sf:flask_of_knowledge" -> useKnowledgeFlask(event);
            case "sf:filled_flask_of_knowledge" -> useFilledKnowledgeFlask(event);
            case "sf:tome_of_knowledge_sharing" -> useKnowledgeTome(event);
            case "sf:scroll_of_dimensional_teleposition" -> useTelepositionScroll(event);
            case "sf:staff_elemental_wind" -> useWindStaff(event);
            case "sf:staff_elemental_water" -> useWaterStaff(event);
            case "sf:staff_elemental_storm" -> useStormStaff(event);
            default -> {
                return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!"sf:magical_zombie_pills".equals(itemId(item))) {
            return;
        }

        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        if (entity instanceof ZombieVillager zombieVillager) {
            event.setCancelled(true);
            consumeOne(item, player);
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 1.0f);
            zombieVillager.setConversionTime(1);
            zombieVillager.setConversionPlayer(player);
        } else if (entity instanceof PigZombie piglin) {
            event.setCancelled(true);
            consumeOne(item, player);
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 1.0f);
            var location = piglin.getLocation();
            piglin.remove();
            location.getWorld().spawn(location, org.bukkit.entity.Piglin.class);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow && arrow.getShooter() instanceof Player player) {
            GrappleState state = grapples.get(player.getUniqueId());
            if (state != null && state.arrow().getUniqueId().equals(arrow.getUniqueId())) {
                runtime.executeForPlayer(player, () -> resolveGrapple(player, state, arrow.getLocation().toVector()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHitEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player player) {
            GrappleState state = grapples.get(player.getUniqueId());
            if (state != null && state.arrow().getUniqueId().equals(arrow.getUniqueId())) {
                runtime.executeForPlayer(player, () -> resolveGrapple(player, state, arrow.getLocation().toVector()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        Long until = grapplingNoFallUntil.get(player.getUniqueId());
        if (until != null && until >= player.getWorld().getGameTime()) {
            grapplingNoFallUntil.remove(player.getUniqueId());
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        long tick = player.getWorld().getGameTime();
        if (tick % 5L == 0) {
            handleInfusedMagnet(player, tick);
        }
        handleParachute(player, event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof DustbinHolder) {
            event.getInventory().clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        GrappleState state = grapples.remove(uuid);
        if (state != null && state.arrow().isValid()) {
            state.arrow().remove();
        }
        grapplingNoFallUntil.remove(uuid);
        magnetTick.remove(uuid);
    }

    private void openPortableCrafter(PlayerInteractEvent event) {
        denyItemUse(event);
        Player player = event.getPlayer();
        runtime.executeForPlayer(player, () -> {
            player.openWorkbench(player.getLocation(), true);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
        });
    }

    private void openPortableDustbin(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        runtime.executeForPlayer(player, () -> {
            Inventory inventory = plugin.getServer().createInventory(new DustbinHolder(), 27, Component.text("Delete Items"));
            player.openInventory(inventory);
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8f, 1.0f);
        });
    }

    private void openEnderBackpack(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        runtime.executeForPlayer(player, () -> {
            player.openInventory(player.getEnderChest());
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.0f);
        });
    }

    private void useMagicEye(PlayerInteractEvent event) {
        denyItemUse(event);
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        if (matchesId(inventory.getHelmet(), "sf:ender_helmet")
                && matchesId(inventory.getChestplate(), "sf:ender_chestplate")
                && matchesId(inventory.getLeggings(), "sf:ender_leggings")
                && matchesId(inventory.getBoots(), "sf:ender_boots")) {
            player.launchProjectile(EnderPearl.class);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 0.9f, 1.0f);
        }
    }

    private void useInfernalBonemeal(PlayerInteractEvent event) {
        denyItemUse(event);
        Optional<Block> clicked = Optional.ofNullable(event.getClickedBlock());
        if (clicked.isEmpty()) {
            return;
        }
        Block block = clicked.get();
        if (block.getType() != Material.NETHER_WART || !(block.getBlockData() instanceof Ageable ageable)) {
            return;
        }
        if (ageable.getAge() >= ageable.getMaximumAge()) {
            return;
        }

        ageable.setAge(ageable.getMaximumAge());
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(null, "sf:legacy_tool", block, ageable, true, "legacy-utility", "growth-accelerator");
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, Material.REDSTONE_BLOCK);
        consumeOne(event.getItem(), event.getPlayer());
    }

    private void useTapeMeasure(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        denyItemUse(event);
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        if (player.isSneaking()) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(
                    tapeAnchorKey,
                    PersistentDataType.STRING,
                    block.getWorld().getUID() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ()
            );
            item.setItemMeta(meta);
            send(player, "messages.tape-measure.anchor-set", "&aSuccessfully set the anchor:&e {anchor}",
                    Map.of("anchor", block.getX() + " | " + block.getY() + " | " + block.getZ()));
            player.playSound(block.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.5f);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(tapeAnchorKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            send(player, "messages.tape-measure.no-anchor", "&cYou need to set an anchor before you can start to measure!");
            return;
        }

        String[] parts = raw.split(";");
        if (parts.length != 4) {
            send(player, "messages.tape-measure.no-anchor", "&cYou need to set an anchor before you can start to measure!");
            return;
        }
        UUID worldId = UUID.fromString(parts[0]);
        if (!block.getWorld().getUID().equals(worldId)) {
            send(player, "messages.tape-measure.wrong-world", "&cYour anchor seems to be in a different world!");
            return;
        }

        double dx = block.getX() - Integer.parseInt(parts[1]);
        double dy = block.getY() - Integer.parseInt(parts[2]);
        double dz = block.getZ() - Integer.parseInt(parts[3]);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
        send(player, "messages.tape-measure.distance", "&7Measurement taken. &eDistance: {distance}",
                Map.of("distance", distanceFormat.format(distance)));
    }

    private void useGoldPan(PlayerInteractEvent event, Set<Material> inputs, List<WeightedDrop> drops) {
        denyItemUse(event);
        Block block = event.getClickedBlock();
        if (block == null || !inputs.contains(block.getType()) || isSfxAnchored(block)) {
            return;
        }

        Player player = event.getPlayer();
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        plugin.getServer().getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) {
            return;
        }

        Material brokenType = block.getType();
        ItemStack output = rollDrop(drops);
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, brokenType);
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, "sf:legacy_tool", block, Material.AIR, true, "legacy-utility", "gold-pan");
        if (output != null && output.getType() != Material.AIR) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), output);
        }
    }

    private boolean isSfxAnchored(Block block) {
        return block != null && blockData.findAnchor(block.getLocation()).isPresent();
    }

    private void useGrapplingHook(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getType().isInteractable()) {
            return;
        }

        denyItemUse(event);
        Player player = event.getPlayer();
        GrappleState existing = grapples.get(player.getUniqueId());
        if (existing != null && existing.arrow().isValid() && !existing.arrow().isDead()) {
            return;
        }
        if (player.getInventory().getItemInOffHand().getType() == Material.BOW) {
            return;
        }

        ItemStack item = event.getItem();
        boolean consumed = item != null && behaviorConfig.grapplingHookConsumeOnUse() && player.getGameMode() != GameMode.CREATIVE;
        if (consumed) {
            consumeOne(item, player);
        }

        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setVelocity(player.getEyeLocation().getDirection().multiply(2.0));
        arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        grapples.put(player.getUniqueId(), new GrappleState(arrow, consumed));
    }

    private void useBandageLike(PlayerInteractEvent event, double healAmount, boolean extinguish, Sound sound) {
        Player player = event.getPlayer();
        double maxHealth = maxHealth(player);
        if (player.getFireTicks() <= 0 && player.getHealth() >= maxHealth) {
            return;
        }

        denyItemUse(event);
        consumeOne(event.getItem(), player);
        player.playSound(player.getLocation(), sound, 0.8f, 1.0f);
        heal(player, healAmount);
        if (extinguish) {
            player.setFireTicks(0);
        }
    }

    private void useMedicalSupply(PlayerInteractEvent event, double healAmount) {
        denyItemUse(event);
        Player player = event.getPlayer();
        consumeOne(event.getItem(), player);
        player.setFireTicks(0);
        clearNegativeEffects(player);
        radiationService.clearExposure(player);
        heal(player, healAmount);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.7f, 1.2f);
    }

    private void useKnowledgeFlask(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getType().isInteractable()) {
            return;
        }

        denyItemUse(event);
        Player player = event.getPlayer();
        if (player.getLevel() < 1) {
            return;
        }

        player.setLevel(player.getLevel() - 1);
        items.give(player, items.create("sf:filled_flask_of_knowledge"));
        consumeOne(event.getItem(), player);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 0.8f);
    }

    private void useFilledKnowledgeFlask(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getType().isInteractable()) {
            return;
        }

        denyItemUse(event);
        Player player = event.getPlayer();
        ThrownExpBottle bottle = player.launchProjectile(ThrownExpBottle.class);
        bottle.setVelocity(player.getEyeLocation().getDirection().multiply(0.7));
        consumeOne(event.getItem(), player);
    }

    private void useTelepositionScroll(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getType().isInteractable()) {
            return;
        }

        denyItemUse(event);
        Player player = event.getPlayer();
        int radius = behaviorConfig.telepositionScrollRadius();
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || nearby instanceof ArmorStand || nearby.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            Location location = living.getLocation();
            float yaw = location.getYaw() + 180.0F;
            if (yaw > 360.0F) {
                yaw -= 360.0F;
            }
            living.teleport(new Location(location.getWorld(), location.getX(), location.getY(), location.getZ(), yaw, location.getPitch()));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.7f);
    }

    private void handleInfusedMagnet(Player player, long tick) {
        if (!player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR || !inventoryContains(player, "sf:infused_magnet")) {
            return;
        }
        Long last = magnetTick.get(player.getUniqueId());
        if (last != null && tick - last < 5L) {
            return;
        }
        magnetTick.put(player.getUniqueId(), tick);

        boolean found = false;
        for (Entity entity : player.getNearbyEntities(6.0, 6.0, 6.0)) {
            if (entity instanceof Item item && item.getPickupDelay() <= 0 && player.getLocation().distanceSquared(item.getLocation()) > 0.3) {
                item.teleport(player.getLocation());
                found = true;
            }
        }
        if (found) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.25f, 1.8f);
        }
    }

    private void handleParachute(Player player, PlayerMoveEvent event) {
        if (!player.isSneaking() || player.isOnGround() || !matchesId(player.getInventory().getChestplate(), "sf:parachute")) {
            return;
        }
        if (event.getTo() == null || event.getTo().getY() >= event.getFrom().getY()) {
            return;
        }
        player.setVelocity(new Vector(0.0, -0.1, 0.0));
        player.setFallDistance(0.0f);
    }

    private void resolveGrapple(Player player, GrappleState state, Vector target) {
        GrappleState current = grapples.get(player.getUniqueId());
        if (current == null || !current.arrow().getUniqueId().equals(state.arrow().getUniqueId())) {
            return;
        }

        grapples.remove(player.getUniqueId());
        Arrow arrow = state.arrow();
        Location arrowLocation = arrow.getLocation();
        if (arrow.isValid()) {
            if (runtime.isOwnedByCurrentRegion(arrowLocation)) {
                arrow.remove();
            } else {
                runtime.executeAt(arrowLocation, () -> {
                    if (arrow.isValid()) {
                        arrow.remove();
                    }
                });
            }
        }

        Vector velocity;
        if (player.getLocation().distanceSquared(target.toLocation(player.getWorld())) < 9.0 && target.getY() <= player.getLocation().getY()) {
            velocity = target.clone().subtract(player.getLocation().toVector());
        } else {
            var location = player.getLocation().clone();
            location.setY(location.getY() + 0.5);
            player.teleport(location);
            double gravity = -0.08;
            double distance = target.distance(location.toVector());
            double time = Math.max(1.0, distance);
            double vX = (1.0 + 0.08 * time) * (target.getX() - location.getX()) / time;
            double vY = (1.0 + 0.04 * time) * (target.getY() - location.getY()) / time - 0.5D * gravity * time;
            double vZ = (1.0 + 0.08 * time) * (target.getZ() - location.getZ()) / time;
            velocity = new Vector(vX, vY, vZ);
        }

        player.setVelocity(velocity);
        grapplingNoFallUntil.put(player.getUniqueId(), player.getWorld().getGameTime() + behaviorConfig.grapplingHookNoFallTicks());
        if (state.consumed()) {
            ItemStack hook = items.create("sf:grappling_hook");
            Location dropLocation = target.toLocation(player.getWorld());
            runtime.executeAt(dropLocation, () -> {
                Item dropped = player.getWorld().dropItemNaturally(dropLocation, hook);
                dropped.setPickupDelay(16);
            });
        }
    }


    private void useKnowledgeTome(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        denyItemUse(event);
        Player player = event.getPlayer();
        ItemStack tome = itemInHand(event);
        if (tome == null || tome.getType().isAir()) {
            return;
        }
        ItemMeta meta = tome.getItemMeta();
        if (meta == null) {
            return;
        }
        String ownerRaw = meta.getPersistentDataContainer().get(knowledgeTomeOwnerKey, PersistentDataType.STRING);
        String ownerName = meta.getPersistentDataContainer().get(knowledgeTomeOwnerNameKey, PersistentDataType.STRING);
        if (ownerRaw == null || ownerRaw.isBlank()) {
            bindKnowledgeTome(player, tome, meta);
            return;
        }
        UUID ownerId;
        try {
            ownerId = UUID.fromString(ownerRaw);
        } catch (IllegalArgumentException exception) {
            bindKnowledgeTome(player, tome, meta);
            return;
        }
        if (ownerId.equals(player.getUniqueId())) {
            send(player, "messages.no-tome-yourself", "<red>You cannot learn from your own Tome of Knowledge.</red>");
            return;
        }
        String safeOwnerName = ownerName == null || ownerName.isBlank() ? ownerId.toString() : ownerName;
        playerData.request(ownerId, safeOwnerName, ownerProfile -> playerData.request(player, targetProfile -> shareKnowledgeTome(player, ownerProfile, targetProfile, safeOwnerName)));
    }

    private void bindKnowledgeTome(Player player, ItemStack tome, ItemMeta meta) {
        meta.getPersistentDataContainer().set(knowledgeTomeOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        meta.getPersistentDataContainer().set(knowledgeTomeOwnerNameKey, PersistentDataType.STRING, player.getName());
        List<Component> lore = new ArrayList<>();
        lore.add(Text.renderFlexible(localization.text("items.tome-of-knowledge-sharing.owner-line", "&7Owner: &b{name}", Map.of("name", player.getName()))));
        lore.add(Component.empty());
        lore.add(Text.renderFlexible(localization.text("items.tome-of-knowledge-sharing.bound-line-1", "&eRight Click&7 to obtain all Researches by")));
        lore.add(Text.renderFlexible(localization.text("items.tome-of-knowledge-sharing.bound-line-2", "&7the previously assigned Owner")));
        meta.lore(lore);
        tome.setItemMeta(meta);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7F, 1.6F);
        send(player, "messages.tome-of-knowledge-sharing.bound", "<green>This Tome of Knowledge has been bound to you.</green>");
    }

    private void shareKnowledgeTome(Player player, SfxPlayerProfile ownerProfile, SfxPlayerProfile targetProfile, String ownerName) {
        if (ownerProfile == null || targetProfile == null || !player.isOnline()) {
            return;
        }
        int unlocked = 0;
        for (String researchId : ownerProfile.unlockedResearchesCopy()) {
            if (researchId == null || targetProfile.hasUnlocked(researchId)) {
                continue;
            }
            researches.researchById(researchId).ifPresent(research -> {
                targetProfile.unlock(research.id());
            });
            if (targetProfile.hasUnlocked(researchId)) {
                unlocked++;
            }
        }
        if (unlocked > 0) {
            playerData.saveAsync(targetProfile);
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if ("sf:tome_of_knowledge_sharing".equals(itemId(hand))) {
            consumeOne(hand, player);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 1.0F);
        send(player, "messages.tome-of-knowledge-sharing.shared", "<green>You learned {count} researches from {owner}.</green>", Map.of("count", unlocked, "owner", ownerName));
    }


    private void useWindStaff(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        denyItemUse(event);
        Player player = event.getPlayer();
        if (!consumeFood(player, 2)) {
            send(player, "messages.hungry", "<red>You are too hungry to use this.</red>");
            return;
        }
        Vector velocity = player.getEyeLocation().getDirection().normalize().multiply(4.0D);
        player.setVelocity(velocity);
        player.setFallDistance(0.0F);
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0F, 1.0F);
        player.getWorld().playEffect(player.getLocation(), Effect.SMOKE, 1);
    }

    private void useWaterStaff(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        denyItemUse(event);
        Player player = event.getPlayer();
        player.setFireTicks(0);
        send(player, "messages.fire-extinguish", "<aqua>You have been extinguished.</aqua>");
    }

    private void useStormStaff(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        denyItemUse(event);
        Player player = event.getPlayer();
        Block target = player.getTargetBlockExact(30);
        if (target == null || target.getWorld() == null) {
            return;
        }
        if (!target.getWorld().getPVP()) {
            send(player, "messages.no-pvp", "<red>PVP is disabled in this world.</red>");
            return;
        }
        if (!consumeFood(player, 4)) {
            send(player, "messages.hungry", "<red>You are too hungry to use this.</red>");
            return;
        }
        Location strike = target.getLocation();
        target.getWorld().strikeLightning(strike);
        damageStormStaff(player, itemInHand(event));
    }

    private void damageStormStaff(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || item.getType() == Material.SHEARS || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        int uses = meta.getPersistentDataContainer().getOrDefault(stormStaffUsageKey, PersistentDataType.INTEGER, 0) + 1;
        if (uses >= 8) {
            int next = item.getAmount() - 1;
            item.setAmount(Math.max(0, next));
            return;
        }
        meta.getPersistentDataContainer().set(stormStaffUsageKey, PersistentDataType.INTEGER, uses);
        List<Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
        lore.removeIf(line -> {
            String legacy = Text.toLegacy(line);
            return legacy.contains("Uses left") || legacy.contains("剩余使用次数");
        });
        lore.add(Text.renderFlexible(localization.text("items.staff.storm.uses-left", "&7Uses left: &e{uses}", Map.of("uses", 8 - uses))));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private boolean consumeFood(Player player, int amount) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        if (player.getFoodLevel() < amount) {
            return false;
        }
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(player, Math.max(0, player.getFoodLevel() - amount), null);
        plugin.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            player.setFoodLevel(event.getFoodLevel());
        }
        return true;
    }

    private WeightedDrop drop(int weight, ItemStack item) {
        return new WeightedDrop(weight, item);
    }

    private ItemStack rollDrop(List<WeightedDrop> drops) {
        int totalWeight = 0;
        for (WeightedDrop drop : drops) {
            totalWeight += Math.max(0, drop.weight());
        }
        if (totalWeight <= 0) {
            return null;
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (WeightedDrop drop : drops) {
            cursor += Math.max(0, drop.weight());
            if (roll < cursor) {
                return drop.item().clone();
            }
        }
        return null;
    }

    private void clearNegativeEffects(Player player) {
        for (String effectName : CURABLE_EFFECTS) {
            PotionEffectType effect = PotionEffectType.getByName(effectName);
            if (effect != null && player.hasPotionEffect(effect)) {
                player.removePotionEffect(effect);
            }
        }
    }

    private void heal(Player player, double amount) {
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    private double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private boolean inventoryContains(Player player, String itemId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (matchesId(item, itemId)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesId(ItemStack item, String itemId) {
        return itemId.equals(itemId(item));
    }

    private String itemId(ItemStack item) {
        return items.readMarker(item).map(SfxItemMarker::itemId).orElse(null);
    }

    private void consumeOne(ItemStack item, Player player) {
        if (item == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        int amount = item.getAmount() - 1;
        if (amount <= 0) {
            item.setAmount(0);
        } else {
            item.setAmount(amount);
        }
    }

    private void send(Player player, String key, String fallback) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key, fallback)));
    }

    private void send(Player player, String key, String fallback, Map<String, ?> placeholders) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key, fallback, placeholders)));
    }

    private ItemStack itemInHand(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null) {
            return item;
        }
        return event.getPlayer().getInventory().getItemInMainHand();
    }

    private void denyItemUse(PlayerInteractEvent event) {
        SfxEventGuards.denyBlockAndItemUse(event);
    }


}
