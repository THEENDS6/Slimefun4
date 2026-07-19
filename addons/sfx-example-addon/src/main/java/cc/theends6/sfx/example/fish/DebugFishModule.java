package cc.theends6.sfx.example.fish;

import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.example.ExampleIds;
import cc.theends6.sfx.example.ExamplePermissions;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Cod;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
    private static final long PARTICLE_DELAY_TICKS = 2L;
    private static final double TRAIL_SAMPLE_SPACING = 0.24;
    private static final double TRAIL_BACK_EXTENSION = 0.65;

    private final SfxAddonContext context;
    private final Map<UUID, Flight> flights = new ConcurrentHashMap<>();
    private final double damage;
    private final int lifetimeTicks;
    private final double maxDistanceSquared;
    private final double speed;
    private final double airDrag;
    private final double gravityPerTick;
    private final double bounceRetention;
    private final double surfaceRetention;
    private final double slideDrag;
    private final double groundBounceTangentRetention;
    private final double slideNormalSpeed;
    private volatile boolean closed;

    public DebugFishModule(SfxAddonContext context) {
        this.context = context;
        damage = clamp(context.configDouble("debug-fish.damage", 6.0), 0.0, 2048.0);
        lifetimeTicks = clamp(context.configInt("debug-fish.lifetime-ticks", 100), 1, 20 * 60);
        double maxDistance = clamp(context.configDouble("debug-fish.max-distance", 64.0), 1.0, 512.0);
        maxDistanceSquared = maxDistance * maxDistance;
        speed = clamp(context.configDouble("debug-fish.speed", 1.6), 0.1, 8.0);
        airDrag = clamp(context.configDouble("debug-fish.air-drag", 0.995), 0.8, 1.0);
        gravityPerTick = clamp(context.configDouble("debug-fish.gravity-per-tick", 0.15), 0.0, 1.0);
        bounceRetention = clamp(context.configDouble("debug-fish.bounce-retention", 0.72), 0.0, 1.25);
        surfaceRetention = clamp(context.configDouble("debug-fish.surface-retention", 0.90), 0.0, 1.0);
        slideDrag = clamp(context.configDouble("debug-fish.slide-drag", 0.0), 0.0, 1.0);
        groundBounceTangentRetention = clamp(
                context.configDouble("debug-fish.ground-bounce-tangent-retention", 0.94), 0.0, 1.0);
        slideNormalSpeed = clamp(context.configDouble("debug-fish.slide-normal-speed", 0.45), 0.0, 2.0);
        context.resources().registerListener(this);
        cleanupLoadedProjectiles();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack == null) {
            stack = event.getPlayer().getInventory().getItemInMainHand();
        }
        if (stack == null || stack.getType().isAir()) {
            return;
        }
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
        ItemStack usedStack = stack;
        context.api().items().definition(marker.itemId()).ifPresent(definition -> {
            int ticks = definition.cooldownSeconds() == null ? 20 : Math.max(1, Math.round(definition.cooldownSeconds() * 20.0f));
            player.setCooldown(usedStack, ticks);
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
        Vector velocity = shooter.getEyeLocation().getDirection().normalize().multiply(speed)
                .add(shooter.getVelocity());
        Cod fish = origin.getWorld().spawn(origin, Cod.class, spawned -> {
            spawned.setAI(false);
            spawned.setGravity(false);
            spawned.setCollidable(false);
            spawned.setPersistent(false);
            spawned.setSilent(true);
            spawned.getPersistentDataContainer().set(PROJECTILE_KEY, PersistentDataType.BYTE, (byte) 1);
            spawned.setVelocity(velocity);
        });
        Flight flight = new Flight(fish, shooter.getUniqueId(), origin.clone(), velocity.clone());
        flights.put(fish.getUniqueId(), flight);
        spawnParticlesLater(origin, location ->
                location.getWorld().spawnParticle(Particle.SPLASH, location, 12, 0.18, 0.18, 0.18, 0.08));
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
        flight.clearSeparatedEntityContacts(current);
        if (flight.sliding() && traceSolidBlock(current, new Vector(0.0, -1.0, 0.0), 0.20) == null) {
            flight.setSliding(false);
        }
        Vector movement = flight.velocity().clone();
        if (flight.sliding()) {
            movement.setY(0.0).multiply(1.0 - slideDrag);
        } else {
            movement.multiply(airDrag).add(new Vector(0.0, -gravityPerTick, 0.0));
        }
        flight.velocity().copy(movement);
        Location next = current.clone().add(movement);
        if (current.getWorld() != flight.origin().getWorld()
                || next.distanceSquared(flight.origin()) > maxDistanceSquared) {
            finish(flight);
            return;
        }

        Vector segment = movement.clone();
        double distance = segment.length();
        boolean bounced = false;
        if (distance > 0.001) {
            Vector direction = segment.clone().normalize();
            RayTraceResult blockHit = traceSolidBlock(current, direction, distance);
            double blockDistanceSquared = blockHit == null
                    ? Double.POSITIVE_INFINITY
                    : blockHit.getHitPosition().distanceSquared(current.toVector());
            Set<UUID> tracedEntityIds = new java.util.HashSet<>();
            while (true) {
                RayTraceResult entityHit = current.getWorld().rayTraceEntities(
                        current, direction, distance, 0.35,
                        entity -> validTarget(flight, entity) && !tracedEntityIds.contains(entity.getUniqueId()));
                if (entityHit == null || entityHit.getHitEntity() == null
                        || entityHit.getHitPosition().distanceSquared(current.toVector()) > blockDistanceSquared) {
                    break;
                }
                LivingEntity target = (LivingEntity) entityHit.getHitEntity();
                tracedEntityIds.add(target.getUniqueId());
                if (flight.beginEntityContact(target.getUniqueId())) {
                    damageOnContact(flight, target);
                }
            }
            if (blockHit != null) {
                BlockFace face = blockHit.getHitBlockFace();
                Vector normal = face == null
                        ? movement.clone().multiply(-1.0).normalize()
                        : new Vector(face.getModX(), face.getModY(), face.getModZ());
                normal = collisionNormal(normal, movement);
                double incomingNormalSpeed = Math.abs(movement.dot(normal));
                if (!flight.sliding() && normal.getY() > 0.5 && incomingNormalSpeed <= slideNormalSpeed) {
                    Vector slidingVelocity = movement.clone()
                            .subtract(normal.clone().multiply(movement.dot(normal)))
                            .multiply(groundBounceTangentRetention);
                    slidingVelocity.setY(0.0);
                    flight.velocity().copy(slidingVelocity);
                    flight.setSliding(true);
                    next = collisionDestination(current, blockHit, normal, flight.velocity());
                } else {
                    double tangentRetention = normal.getY() > 0.5
                            ? groundBounceTangentRetention
                            : surfaceRetention;
                    Vector bouncedVelocity = bounceVelocity(movement, normal, bounceRetention, tangentRetention);
                    if (flight.sliding()) bouncedVelocity.setY(0.0);
                    flight.velocity().copy(bouncedVelocity);
                    next = collisionDestination(current, blockHit, normal, flight.velocity());
                    bounced = true;
                }
            }
        }
        
        
        
        Location destination = next;
        boolean bounceEffect = bounced;
        fish.teleportAsync(destination).thenAccept(moved -> {
            Location owner = moved ? destination : current;
            context.api().runtime().executeAt(owner, () -> {
                if (!moved || closed || !fish.isValid()) {
                    finish(flight);
                    return;
                }
                if (bounceEffect) {
                    playBounceEffect(destination);
                }
                spawnTrail(current, destination);
                flight.advance();
                schedule(flight);
            });
        });
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

    private void damageOnContact(Flight flight, LivingEntity target) {
        Location targetLocation = target.getLocation();
        context.api().runtime().executeAt(targetLocation, () -> {
            if (!closed && target.isValid() && !target.isDead()) {
                Player shooter = Bukkit.getPlayer(flight.shooterId());
                target.setNoDamageTicks(0);
                target.damage(damage, shooter == null ? flight.fish() : shooter);
            }
        });
    }

    private void playBounceEffect(Location location) {
        spawnParticlesLater(location, delayed -> {
            delayed.getWorld().spawnParticle(Particle.BUBBLE_POP, delayed, 10, 0.16, 0.16, 0.16, 0.03);
            delayed.getWorld().spawnParticle(Particle.SPLASH, delayed, 8, 0.20, 0.12, 0.20, 0.08);
        });
        location.getWorld().playSound(location, Sound.ENTITY_SLIME_SQUISH, 0.45f, 1.35f);
    }

    private void spawnTrail(Location from, Location to) {
        if (from.getWorld() != to.getWorld()) {
            return;
        }
        Vector segment = to.toVector().subtract(from.toVector());
        double distance = segment.length();
        if (distance < 1.0E-4) {
            spawnParticlesLater(to, delayed ->
                    delayed.getWorld().spawnParticle(Particle.BUBBLE_POP, delayed, 2, 0.05, 0.05, 0.05, 0.005));
            return;
        }
        Vector direction = segment.clone().multiply(1.0 / distance);
        Location trailStart = from.clone().subtract(direction.clone().multiply(TRAIL_BACK_EXTENSION));
        double trailLength = distance + TRAIL_BACK_EXTENSION;
        int samples = Math.max(2, (int) Math.ceil(trailLength / TRAIL_SAMPLE_SPACING));
        spawnParticlesLater(to, ignored -> {
            for (int index = 0; index <= samples; index++) {
                double offset = trailLength * index / samples;
                Location point = trailStart.clone().add(direction.clone().multiply(offset));
                point.getWorld().spawnParticle(Particle.BUBBLE_POP, point, 1, 0.035, 0.035, 0.035, 0.004);
                if ((index & 1) == 0) {
                    point.getWorld().spawnParticle(Particle.SPLASH, point, 1, 0.045, 0.025, 0.045, 0.008);
                }
            }
        });
    }

    private void spawnParticlesLater(Location location, java.util.function.Consumer<Location> particles) {
        Location snapshot = location.clone();
        context.api().runtime().executeAtLater(snapshot, PARTICLE_DELAY_TICKS, () -> {
            if (!closed && snapshot.getWorld() != null) {
                particles.accept(snapshot);
            }
        });
    }

    private static Location collisionDestination(
            Location current, RayTraceResult hit, Vector normal, Vector outgoingVelocity) {
        Location destination = hit.getHitPosition().toLocation(current.getWorld()).add(normal.clone().multiply(0.08));
        if (outgoingVelocity.lengthSquared() > 1.0E-6) {
            destination.setDirection(outgoingVelocity);
        }
        return destination;
    }

    private static RayTraceResult traceSolidBlock(Location start, Vector direction, double distance) {
        return start.getWorld().rayTraceBlocks(
                start, direction, distance, FluidCollisionMode.NEVER, true);
    }

    private static Vector collisionNormal(Vector candidate, Vector incoming) {
        Vector normal = candidate.lengthSquared() < 1.0E-6
                ? incoming.clone().multiply(-1.0)
                : candidate.clone();
        if (normal.lengthSquared() < 1.0E-6) {
            return new Vector(0.0, 1.0, 0.0);
        }
        normal.normalize();
        if (incoming.dot(normal) >= 0.0) {
            normal.multiply(-1.0);
        }
        return normal;
    }

    private static Vector bounceVelocity(
            Vector incoming, Vector normal, double normalRetention, double tangentRetention) {
        double normalSpeed = incoming.dot(normal);
        Vector normalPart = normal.clone().multiply(-normalSpeed * normalRetention);
        Vector tangentPart = incoming.clone().subtract(normal.clone().multiply(normalSpeed)).multiply(tangentRetention);
        return tangentPart.add(normalPart);
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
        private final Vector velocity;
        private final Set<UUID> activeEntityContacts = ConcurrentHashMap.newKeySet();
        private int ageTicks;
        private boolean sliding;

        private Flight(Cod fish, UUID shooterId, Location origin, Vector velocity) {
            this.fish = fish;
            this.shooterId = shooterId;
            this.origin = origin;
            this.velocity = velocity;
        }

        Cod fish() { return fish; }
        UUID shooterId() { return shooterId; }
        Location origin() { return origin; }
        Vector velocity() { return velocity; }
        int ageTicks() { return ageTicks; }
        boolean sliding() { return sliding; }
        void setSliding(boolean sliding) { this.sliding = sliding; }

        void clearSeparatedEntityContacts(Location current) {
            Vector point = current.toVector();
            activeEntityContacts.removeIf(entityId -> {
                Entity entity = Bukkit.getEntity(entityId);
                return entity == null || !entity.isValid()
                        || !entity.getBoundingBox().clone().expand(0.35).contains(point);
            });
        }

        boolean beginEntityContact(UUID entityId) {
            return activeEntityContacts.add(entityId);
        }

        void advance() {
            ageTicks++;
        }
    }
}
