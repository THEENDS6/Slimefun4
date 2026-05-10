package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class SfxArmorEffectListener implements Listener {
    private final SfxItems items;
    private final Map<UUID, Long> lastEffectTick = new HashMap<>();
    private final Map<UUID, Long> recentGlideUntil = new HashMap<>();

    public SfxArmorEffectListener(SfxItems items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            if (hasBootFlag(player, "armor-stomper")) {
                event.setCancelled(true);
                stomp(player, event.getDamage());
                return;
            }
            if (hasBootFlag(player, "armor-no-fall")) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, 0.8f, 1.0f);
                return;
            }
        }

        if ((cause == EntityDamageEvent.DamageCause.FALL || cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL)
                && hasHelmetFlag(player, "armor-elytra-impact")
                && wasRecentlyGliding(player)) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderPearlDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof EnderPearl && event.getEntity() instanceof Player player && hasBootFlag(player, "armor-ender-pearl-safe")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player && player.isGliding()) {
            recentGlideUntil.put(player.getUniqueId(), player.getWorld().getGameTime() + 2L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != org.bukkit.Material.FARMLAND) {
            return;
        }
        if (hasBootFlag(event.getPlayer(), "armor-farmland-safe")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        long now = player.getWorld().getGameTime();
        long last = lastEffectTick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 40L) {
            return;
        }
        lastEffectTick.put(player.getUniqueId(), now);
        if (hasEquippedFlag(player, "armor-night-vision")) {
            addPotion(player, "NIGHT_VISION", 20 * 12, 0);
        }
        if (hasEquippedFlag(player, "armor-speed")) {
            addPotion(player, "SPEED", 20 * 4, leggingsSpeedAmplifier(player));
        }
        if (hasEquippedFlag(player, "armor-jump")) {
            addPotion(player, "JUMP_BOOST", 20 * 4, bootsJumpAmplifier(player));
        }
        if (hasEquippedFlag(player, "armor-water-breathing")) {
            addPotion(player, "WATER_BREATHING", 20 * 4, 0);
        }
        if (hasEquippedFlag(player, "armor-fire-resistance")) {
            addPotion(player, "FIRE_RESISTANCE", 20 * 4, 0);
        }
        if (hasChestFlag(player, "armor-bee-wings") && shouldSlowFall(player, event)) {
            player.setFallDistance(0.0f);
            addPotion(player, "SLOW_FALLING", 20 * 3, 0);
        }
    }

    private void addPotion(Player player, String typeName, int durationTicks, int amplifier) {
        PotionEffectType type = resolvePotion(typeName);
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false, true));
        }
    }

    private PotionEffectType resolvePotion(String typeName) {
        PotionEffectType type = PotionEffectType.getByName(typeName);
        if (type != null) {
            return type;
        }
        return PotionEffectType.getByKey(NamespacedKey.minecraft(typeName.toLowerCase(java.util.Locale.ROOT)));
    }

    private boolean hasEquippedFlag(Player player, String flag) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (hasArmorFlag(armor, flag)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHelmetFlag(Player player, String flag) {
        return hasArmorFlag(player.getInventory().getHelmet(), flag);
    }

    private boolean hasChestFlag(Player player, String flag) {
        return hasArmorFlag(player.getInventory().getChestplate(), flag);
    }

    private boolean hasBootFlag(Player player, String flag) {
        return hasArmorFlag(player.getInventory().getBoots(), flag);
    }

    private boolean hasArmorFlag(ItemStack armor, String flag) {
        return items.readMarker(armor).map(SfxItemMarker::flags).map(flags -> flags.contains(flag)).orElse(false);
    }

    private boolean wasRecentlyGliding(Player player) {
        long now = player.getWorld().getGameTime();
        Long until = recentGlideUntil.get(player.getUniqueId());
        return player.isGliding() || (until != null && until >= now);
    }

    private boolean shouldSlowFall(Player player, PlayerMoveEvent event) {
        if (!player.isGliding() || event.getTo() == null || event.getTo().getY() >= event.getFrom().getY()) {
            return false;
        }

        Block block = player.getLocation().getBlock();
        int highestSurfaceDistance = block.getY() - player.getWorld().getHighestBlockYAt(player.getLocation());
        if (highestSurfaceDistance >= 0) {
            return highestSurfaceDistance <= 4;
        }

        for (int i = 1; i <= 6; i++) {
            if (block.getRelative(0, -i, 0).getType().isSolid()) {
                return i <= 4;
            }
        }
        return false;
    }

    private int leggingsSpeedAmplifier(Player player) {
        String itemId = armorItemId(player.getInventory().getLeggings());
        if ("sf:slime_leggings".equals(itemId) || "sf:slime_steel_leggings".equals(itemId)) {
            return 2;
        }
        return 0;
    }

    private int bootsJumpAmplifier(Player player) {
        String itemId = armorItemId(player.getInventory().getBoots());
        if ("sf:slime_boots".equals(itemId) || "sf:slime_steel_boots".equals(itemId)) {
            return 5;
        }
        if ("sf:bee_boots".equals(itemId)) {
            return 2;
        }
        return 0;
    }

    private String armorItemId(ItemStack armor) {
        return items.readMarker(armor).map(SfxItemMarker::itemId).orElse(null);
    }

    private void stomp(Player player, double fallDamage) {
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
        player.setVelocity(new Vector(0.0, 0.7, 0.0));

        for (Entity entity : player.getNearbyEntities(4.0, 4.0, 4.0)) {
            if (!(entity instanceof LivingEntity living) || entity.getUniqueId().equals(player.getUniqueId()) || !entity.isValid()) {
                continue;
            }
            Vector direction = entity.getLocation().toVector().subtract(player.getLocation().toVector());
            Vector knockback = direction.lengthSquared() < 0.05 ? new Vector(0.0, 1.0, 0.0) : direction.normalize().multiply(1.4);
            entity.setVelocity(knockback);
            if (!(entity instanceof Player) || player.getWorld().getPVP()) {
                living.damage(fallDamage / 2.0, player);
            }
        }

        Block floor = player.getLocation().getBlock().getRelative(0, -1, 0);
        if (floor.getType().isSolid()) {
            player.getWorld().spawnParticle(Particle.BLOCK, floor.getLocation().add(0.5, 0.5, 0.5), 20, 0.8, 0.2, 0.8, floor.getBlockData());
        }
    }
}
