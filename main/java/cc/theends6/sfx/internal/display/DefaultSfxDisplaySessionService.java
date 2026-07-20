package cc.theends6.sfx.internal.display;

import cc.theends6.sfx.api.display.SfxDisplayCategory;
import cc.theends6.sfx.api.display.SfxDisplayProjection;
import cc.theends6.sfx.api.display.SfxDisplaySessionService;
import cc.theends6.sfx.api.display.SfxDisplayType;
import cc.theends6.sfx.api.display.SfxDisplayUpdateStrategy;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;


public final class DefaultSfxDisplaySessionService implements SfxDisplaySessionService, AutoCloseable {
    private static final int ITEM_STACK_METADATA_INDEX = 23;
    private static final int ITEM_DISPLAY_TRANSFORM_INDEX = 24;
    private static final byte ITEM_DISPLAY_TRANSFORM_FIXED = 4;
    private static final int REFRESH_TICKS = 10;
    private static final byte ENTITY_FLAGS = 0;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final Function<String, Optional<SfxDisplayType>> types;
    private final Function<String, Optional<SfxDisplayCategory>> categories;
    private final Path preferencesFile;
    private final AtomicInteger entityIds = new AtomicInteger(6_000_000);
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<UUID, ProjectionState> projections = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Boolean>> preferences = new ConcurrentHashMap<>();

    public DefaultSfxDisplaySessionService(JavaPlugin plugin, SfxRuntime runtime,
                                           Function<String, Optional<SfxDisplayType>> types,
                                           Function<String, Optional<SfxDisplayCategory>> categories) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.types = types;
        this.categories = categories;
        this.preferencesFile = plugin.getDataFolder().toPath().resolve("data/display-preferences.properties");
        loadPreferences();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        scheduleRefresh();
    }

    @Override public void upsert(SfxDisplayProjection projection) {
        if (projection == null) throw new IllegalArgumentException("projection must not be null");
        SfxDisplayType type = types.apply(projection.typeId()).orElseThrow(
                () -> new IllegalArgumentException("Unknown display type: " + projection.typeId()));
        long tick = currentTick();
        projections.compute(projection.id(), (id, current) -> {
            if (current == null) return new ProjectionState(entityIds.incrementAndGet(), UUID.randomUUID(), projection, tick);
            if (current.projection.typeId().equals(projection.typeId())
                    && type.updateStrategy() == SfxDisplayUpdateStrategy.STATIC) return current;
            if (type.updateStrategy() == SfxDisplayUpdateStrategy.TICK_THROTTLED
                    && tick - current.lastUpdateTick < type.minimumUpdateTicks()) {
                current.pending = projection;
                return current;
            }
            current.projection = projection;
            current.lastUpdateTick = tick;
            current.dirty = true;
            return current;
        });
        refreshAllPlayers();
    }

    @Override public void remove(UUID projectionId) {
        ProjectionState state = projectionId == null ? null : projections.remove(projectionId);
        if (state != null) destroy(state);
    }

    @Override public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;
        for (ProjectionState state : new ArrayList<>(projections.values())) updateForPlayer(player, state);
    }

    @Override public void setCategoryEnabled(UUID playerId, String categoryId, boolean enabled) {
        if (playerId == null || categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("playerId and categoryId are required");
        }
        preferences.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(categoryId, enabled);
        savePreferences();
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) runtime.executeForPlayer(player, () -> refresh(player));
    }

    @Override public boolean categoryEnabled(UUID playerId, String categoryId) {
        Boolean configured = preferences.getOrDefault(playerId, Map.of()).get(categoryId);
        if (configured != null) return configured;
        return categories.apply(categoryId).map(SfxDisplayCategory::defaultEnabled).orElse(false);
    }

    private void scheduleRefresh() {
        runtime.executeGlobalLater(REFRESH_TICKS, () -> {
            if (!running.get()) return;
            promotePending();
            refreshAllPlayers();
            scheduleRefresh();
        });
    }

    private void promotePending() {
        long tick = currentTick();
        for (ProjectionState state : projections.values()) {
            SfxDisplayProjection pending = state.pending;
            if (pending == null) continue;
            int minimumTicks = types.apply(pending.typeId()).map(SfxDisplayType::minimumUpdateTicks).orElse(0);
            if (tick - state.lastUpdateTick < minimumTicks) continue;
            state.projection = pending;
            state.pending = null;
            state.lastUpdateTick = tick;
            state.dirty = true;
        }
    }

    private void refreshAllPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            runtime.executeForPlayer(player, () -> refresh(player));
        }
    }

    private void updateForPlayer(Player player, ProjectionState state) {
        SfxDisplayProjection projection = state.projection;
        SfxDisplayType type = types.apply(projection.typeId()).orElse(null);
        if (type == null) {
            if (projections.remove(projection.id(), state)) destroy(state);
            return;
        }
        Location location = projection.location();
        boolean visible = player.getWorld().equals(location.getWorld())
                && categoryEnabled(player.getUniqueId(), type.categoryId())
                && player.getLocation().distanceSquared(location) <= type.visibleDistance() * type.visibleDistance();
        if (!visible) {
            destroyForPlayer(player, state);
            return;
        }
        UUID playerId = player.getUniqueId();
        if (state.viewers.add(playerId)) {
            WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(state.entityId,
                    Optional.of(state.entityUuid), EntityTypes.ITEM_DISPLAY,
                    new Vector3d(location.getX(), location.getY(), location.getZ()),
                    0, 0, 0, 0, Optional.of(new Vector3d(0.0D, 0.0D, 0.0D)));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawn);
            sendMetadata(player, state);
        } else if (state.dirty) {
            
            destroyForPlayer(player, state);
            updateForPlayer(player, state);
        }
        if (state.viewers.size() >= plugin.getServer().getOnlinePlayers().size()) state.dirty = false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sendMetadata(Player player, ProjectionState state) {
        List<EntityData<?>> metadata = List.of(
                new EntityData(0, EntityDataTypes.BYTE, ENTITY_FLAGS),
                new EntityData(5, EntityDataTypes.BOOLEAN, true),
                new EntityData(ITEM_STACK_METADATA_INDEX, EntityDataTypes.ITEMSTACK,
                        toPacketItemStack(state.projection.item())),
                new EntityData(ITEM_DISPLAY_TRANSFORM_INDEX, EntityDataTypes.BYTE,
                        ITEM_DISPLAY_TRANSFORM_FIXED));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerEntityMetadata(state.entityId, metadata));
    }

    private Object toPacketItemStack(ItemStack item) {
        try {
            Class<?> converter = Class.forName("io.github.retrooper.packetevents.util.SpigotConversionUtil");
            Method method = converter.getMethod("fromBukkitItemStack", ItemStack.class);
            return method.invoke(null, item.clone());
        } catch (ReflectiveOperationException ignored) {
            return item.clone();
        }
    }

    private void destroyForPlayer(Player player, ProjectionState state) {
        if (!state.viewers.remove(player.getUniqueId())) return;
        if (player.isOnline()) PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerDestroyEntities(new int[] {state.entityId}));
    }

    private void destroy(ProjectionState state) {
        for (UUID viewerId : new HashSet<>(state.viewers)) {
            Player player = plugin.getServer().getPlayer(viewerId);
            if (player != null) runtime.executeForPlayer(player, () -> destroyForPlayer(player, state));
        }
    }

    public void clear() {
        for (ProjectionState state : new ArrayList<>(projections.values())) destroy(state);
        projections.clear();
    }

    private long currentTick() {
        try { return plugin.getServer().getCurrentTick(); }
        catch (UnsupportedOperationException ignored) { return 0L; }
    }

    private void loadPreferences() {
        if (!Files.isRegularFile(preferencesFile)) return;
        Properties stored = new Properties();
        try (InputStream input = Files.newInputStream(preferencesFile)) {
            stored.load(input);
            for (String key : stored.stringPropertyNames()) {
                int separator = key.indexOf('|');
                if (separator <= 0 || separator == key.length() - 1) continue;
                UUID playerId = UUID.fromString(key.substring(0, separator));
                preferences.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                        .put(key.substring(separator + 1), Boolean.parseBoolean(stored.getProperty(key)));
            }
        } catch (IllegalArgumentException | IOException exception) {
            plugin.getLogger().warning("Could not load addon display preferences: " + exception.getMessage());
        }
    }

    private synchronized void savePreferences() {
        Properties stored = new Properties();
        preferences.forEach((playerId, values) -> values.forEach(
                (category, enabled) -> stored.setProperty(playerId + "|" + category, enabled.toString())));
        try {
            Files.createDirectories(preferencesFile.getParent());
            Path temporary = preferencesFile.resolveSibling(preferencesFile.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                stored.store(output, "SlimeFunX addon display preferences");
            }
            Files.move(temporary, preferencesFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save addon display preferences: " + exception.getMessage());
        }
    }

    @Override public void close() {
        running.set(false);
        clear();
        savePreferences();
    }

    private static final class ProjectionState {
        private final int entityId;
        private final UUID entityUuid;
        private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
        private volatile SfxDisplayProjection projection;
        private volatile SfxDisplayProjection pending;
        private volatile long lastUpdateTick;
        private volatile boolean dirty;

        private ProjectionState(int entityId, UUID entityUuid, SfxDisplayProjection projection, long tick) {
            this.entityId = entityId;
            this.entityUuid = entityUuid;
            this.projection = projection;
            this.lastUpdateTick = tick;
        }
    }
}
