package cc.theends6.sfx.example.fish;

import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.example.ExampleIds;
import cc.theends6.sfx.example.ExamplePermissions;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Cod;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;








public final class DebugFishModule implements Listener, AutoCloseable {
    private static final NamespacedKey PROJECTILE_KEY = new NamespacedKey("sfx_example", "debug_fish_projectile");

    private final SfxAddonContext context;
    private final Map<UUID, Flight> flights = new ConcurrentHashMap<>();
    private final double damage;
    private final int lifetimeTicks;
    private final double maxDistanceSquared;
    private final double speed;
    private volatile boolean closed;

    public DebugFishModule(SfxAddonContext context) {
        this.context = context;
        damage = clamp(context.configDouble("debug-fish.damage", 6.0), 0.0, 2048.0);
        lifetimeTicks = clamp(context.configInt("debug-fish.lifetime-ticks", 100), 1, 20 * 60);
        double maxDistance = clamp(context.configDouble("debug-fish.max-distance", 64.0), 1.0, 512.0);
        maxDistanceSquared = maxDistance * maxDistance;
        speed = clamp(context.configDouble("debug-fish.speed", 1.6), 0.1, 8.0);
        Bukkit.getPluginManager().registerEvents(this, context.api().runtime().plugin());
        cleanupLoadedProjectiles();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack stack = event.getItem();
        SfxItemMarker marker = context.api().items().readMarker(stack).orElse(null);
        if (marker == null || !ExampleIds.DEBUG_FISH.equals(marker.itemId())) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (!player.hasPermission(ExamplePermissions.DEBUG) || !context.api().items().canUse(player, marker.itemId())) {
            return;
        }
        if (player.hasCooldown(stack)) {
            return;
        }
        context.api().items().definition(marker.itemId()).ifPresent(definition -> {
            int ticks = definition.cooldownSeconds() == null ? 20 : Math.max(1, Math.round(definition.cooldownSeconds() * 20.0f));
            player.setCooldown(stack, ticks);
        });
        launch(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        SfxItemMarker marker = context.api().items().readMarker(event.getItem()).orElse(null);
        if (marker != null && ExampleIds.DEBUG_FISH.equals(marker.itemId())) {
            event.setCancelled(true);
        }
    }

    private void launch(Player shooter) {
        Location origin = shooter.getEyeLocation().add(shooter.getEyeLocation().getDirection().multiply(0.8));
        Cod fish = origin.getWorld().spawn(origin, Cod.class, spawned -> {
            spawned.setAI(false);
            spawned.setGravity(false);
            spawned.setCollidable(false);
            spawned.setPersistent(false);
            spawned.setSilent(true);
            spawned.getPersistentDataContainer().set(PROJECTILE_KEY, PersistentDataType.BYTE, (byte) 1);
            spawned.setVelocity(shooter.getEyeLocation().getDirection().normalize().multiply(speed));
        });
        Flight flight = new Flight(fish, shooter.getUniqueId(), origin.clone(), origin.clone(), 0);
        flights.put(fish.getUniqueId(), flight);
        schedule(flight);
    }

    private void schedule(Flight flight) {
        if (closed || !flight.fish().isValid()) {
            finish(flight);
            return;
        }
        context.api().runtime().executeAtLater(flight.fish().getLocation(), 1L, () -> tick(flight));
    }

    private void tick(Flight flight) {
        Cod fish = flight.fish();
        if (closed || !fish.isValid() || fish.isDead() || flight.ageTicks() >= lifetimeTicks) {
            finish(flight);
            return;
        }
        Location current = fish.getLocation();
        if (current.getWorld() != flight.origin().getWorld()
                || current.distanceSquared(flight.origin()) > maxDistanceSquared) {
            finish(flight);
            return;
        }

        Location previous = flight.previous();
        Vector segment = current.toVector().subtract(previous.toVector());
        double distance = segment.length();
        if (distance > 0.001) {
            Vector direction = segment.clone().normalize();
            RayTraceResult blockHit = current.getWorld().rayTraceBlocks(previous, direction, distance);
            RayTraceResult entityHit = current.getWorld().rayTraceEntities(previous, direction, distance, 0.35,
                    entity -> validTarget(flight, entity));
            if (entityHit != null && (blockHit == null
                    || entityHit.getHitPosition().distanceSquared(previous.toVector())
                    <= blockHit.getHitPosition().distanceSquared(previous.toVector()))) {
                LivingEntity target = (LivingEntity) entityHit.getHitEntity();
                Player shooter = Bukkit.getPlayer(flight.shooterId());
                target.damage(damage, shooter == null ? fish : shooter);
                finish(flight);
                return;
            }
            if (blockHit != null) {
                finish(flight);
                return;
            }
        }
        current.getWorld().spawnParticle(Particle.BUBBLE_POP, current, 1, 0.05, 0.05, 0.05, 0.0);
        flight.advance(current);
        schedule(flight);
    }

    private boolean validTarget(Flight flight, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity.getUniqueId().equals(flight.fish().getUniqueId())
                || entity.getUniqueId().equals(flight.shooterId()) || entity.isDead()) {
            return false;
        }
        Player shooter = Bukkit.getPlayer(flight.shooterId());
        if (shooter != null && living instanceof Player target
                && shooter.getScoreboard().getEntryTeam(shooter.getName()) != null
                && shooter.getScoreboard().getEntryTeam(shooter.getName())
                == shooter.getScoreboard().getEntryTeam(target.getName())) {
            return false;
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEntityEvent event) {
        if (isProjectile(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!isProjectile(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        flights.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isProjectile(entity)) {
                flights.remove(entity.getUniqueId());
                entity.remove();
            }
        }
    }

    private boolean isProjectile(Entity entity) {
        return entity.getPersistentDataContainer().has(PROJECTILE_KEY, PersistentDataType.BYTE);
    }

    private void finish(Flight flight) {
        flights.remove(flight.fish().getUniqueId());
        if (flight.fish().isValid()) {
            flight.fish().remove();
        }
    }

    private void cleanupLoadedProjectiles() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                Location owner = new Location(world, (chunk.getX() << 4) + 8, world.getMinHeight(), (chunk.getZ() << 4) + 8);
                context.api().runtime().executeAt(owner, () -> {
                    if (!chunk.isLoaded()) {
                        return;
                    }
                    for (Entity entity : chunk.getEntities()) {
                        if (isProjectile(entity)) {
                            entity.remove();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        HandlerList.unregisterAll(this);
        for (Flight flight : flights.values()) {
            context.api().runtime().executeAt(flight.fish().getLocation(), flight.fish()::remove);
        }
        flights.clear();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

    private static final class Flight {
        private final Cod fish;
        private final UUID shooterId;
        private final Location origin;
        private Location previous;
        private int ageTicks;

        private Flight(Cod fish, UUID shooterId, Location origin, Location previous, int ageTicks) {
            this.fish = fish;
            this.shooterId = shooterId;
            this.origin = origin;
            this.previous = previous;
            this.ageTicks = ageTicks;
        }

        Cod fish() { return fish; }
        UUID shooterId() { return shooterId; }
        Location origin() { return origin; }
        Location previous() { return previous; }
        int ageTicks() { return ageTicks; }

        void advance(Location current) {
            previous = current.clone();
            ageTicks++;
        }
    }
}
