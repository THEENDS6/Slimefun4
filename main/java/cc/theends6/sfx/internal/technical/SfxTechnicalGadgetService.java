package cc.theends6.sfx.internal.technical;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class SfxTechnicalGadgetService {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxRechargeableItemService rechargeableItems;
    private boolean running;
    private final int jetpackIntervalTicks;
    private final int jetBootsIntervalTicks;
    private final Map<UUID, Long> nextJetpackUseTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextJetBootsUseTick = new ConcurrentHashMap<>();
    private long tickCounter;

    public SfxTechnicalGadgetService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.rechargeableItems = new SfxRechargeableItemService(plugin, Objects.requireNonNull(items, "items"));
        int legacyInterval = Math.max(1, plugin.getConfig().getInt("technical-gadgets.classic.interval-ticks", 10));
        this.jetpackIntervalTicks = Math.max(1, plugin.getConfig().getInt("technical-gadgets.classic.jetpack-interval-ticks", legacyInterval == 10 ? 3 : legacyInterval));
        this.jetBootsIntervalTicks = Math.max(1, plugin.getConfig().getInt("technical-gadgets.classic.jetboots-interval-ticks", legacyInterval == 10 ? 2 : legacyInterval));
        this.tickCounter = 0L;
        this.running = true;
        scheduleTick();
    }

    public SfxRechargeableItemService rechargeableItems() {
        return rechargeableItems;
    }

    public void shutdown() {
        running = false;
    }

    private void scheduleTick() {
        runtime.executeGlobalLater(1, () -> {
            if (!running) {
                return;
            }
            tickCounter++;
            tickOnlinePlayers();
            scheduleTick();
        });
    }

    private void tickOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!player.isSneaking() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                nextJetpackUseTick.remove(id);
                nextJetBootsUseTick.remove(id);
                continue;
            }
            if (tickCounter >= nextJetpackUseTick.getOrDefault(id, 0L) && tryUseJetpack(player)) {
                nextJetpackUseTick.put(id, tickCounter + jetpackIntervalTicks);
            }
            if (tickCounter >= nextJetBootsUseTick.getOrDefault(id, 0L) && tryUseJetBoots(player)) {
                nextJetBootsUseTick.put(id, tickCounter + jetBootsIntervalTicks);
            }
        }
    }

    private boolean tryUseJetpack(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        SfxRechargeableItemService.Definition definition = rechargeableItems.definition(chestplate).orElse(null);
        if (definition == null || definition.kind() != SfxRechargeableItemService.RechargeableKind.JETPACK) {
            return false;
        }
        if (!rechargeableItems.removeCharge(chestplate, definition.useCost())) {
            return false;
        }
        player.getInventory().setChestplate(chestplate);
        player.setFallDistance(0.0F);
        Vector vector = new Vector(0.0D, 1.0D, 0.0D);
        vector.multiply(definition.movementValue());
        vector.add(player.getEyeLocation().getDirection().multiply(0.2D));
        player.setVelocity(vector);
        playThrustEffects(player, true);
        return true;
    }

    private boolean tryUseJetBoots(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        SfxRechargeableItemService.Definition definition = rechargeableItems.definition(boots).orElse(null);
        if (definition == null || definition.kind() != SfxRechargeableItemService.RechargeableKind.JETBOOTS) {
            return false;
        }
        if (!rechargeableItems.removeCharge(boots, definition.useCost())) {
            return false;
        }
        player.getInventory().setBoots(boots);
        player.setFallDistance(0.0F);
        double accuracy = Math.max(0.0D, definition.movementValue() - 0.7D);
        double offset = ThreadLocalRandom.current().nextBoolean() ? accuracy : -accuracy;
        Vector direction = player.getEyeLocation().getDirection();
        Vector vector = new Vector(direction.getX() * definition.movementValue() + offset, 0.04D, direction.getZ() * definition.movementValue() - offset);
        player.setVelocity(vector);
        playThrustEffects(player, false);
        return true;
    }

    private void playThrustEffects(Player player, boolean jetpack) {
        player.getWorld().playEffect(player.getLocation(), Effect.SMOKE, 1, 1);
        player.getWorld().playSound(player.getLocation(), jetpack ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.25F, 1.0F);
    }
}
