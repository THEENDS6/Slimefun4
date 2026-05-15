package cc.theends6.sfx.internal.technical;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class SfxTechnicalGadgetService {
    private static final double JETPACK_COST = 0.08D;
    private static final double JETBOOTS_COST = 0.075D;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxRechargeableItemService rechargeableItems;
    private boolean running;

    public SfxTechnicalGadgetService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.rechargeableItems = new SfxRechargeableItemService(plugin, Objects.requireNonNull(items, "items"));
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
        runtime.executeGlobalLater(1L, () -> {
            if (!running) {
                return;
            }
            tickOnlinePlayers();
            scheduleTick();
        });
    }

    private void tickOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isSneaking() || player.isDead()) {
                continue;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (tryUseJetpack(player)) {
                continue;
            }
            tryUseJetBoots(player);
        }
    }

    private boolean tryUseJetpack(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        SfxRechargeableItemService.Definition definition = rechargeableItems.definition(chestplate).orElse(null);
        if (definition == null || definition.kind() != SfxRechargeableItemService.RechargeableKind.JETPACK) {
            return false;
        }
        if (!rechargeableItems.removeCharge(chestplate, JETPACK_COST)) {
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
        if (!rechargeableItems.removeCharge(boots, JETBOOTS_COST)) {
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
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0.0D, 0.2D, 0.0D), 4, 0.15D, 0.15D, 0.15D, 0.01D);
        player.getWorld().playSound(player.getLocation(), jetpack ? Sound.ENTITY_FIREWORK_ROCKET_LAUNCH : Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.25F, jetpack ? 1.25F : 1.6F);
    }
}
