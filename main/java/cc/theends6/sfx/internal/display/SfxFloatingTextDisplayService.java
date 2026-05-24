package cc.theends6.sfx.internal.display;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reusable PacketEvents-backed floating text projector.
 *
 * <p>This service owns only client-side virtual floating text entities. It does not create, store,
 * or depend on real world entities. Business systems should feed it already-rendered projection
 * models and keep authoritative state elsewhere.</p>
 */
public final class SfxFloatingTextDisplayService {
    private static final byte ENTITY_FLAGS = 0x20;
    private static final byte BILLBOARD_CENTER = 3;
    private static final byte TEXT_OPACITY = (byte) 255;
    private static final byte TEXT_STYLE_SHADOW = 0x01;
    private static final byte TEXT_STYLE_SEE_THROUGH = 0x02;
    private static final int BACKGROUND_COLOR = 0x40000000;
    private static final int METADATA_BILLBOARD = 15;
    private static final int METADATA_VIEW_RANGE = 17;
    private static final int METADATA_TEXT = 23;
    private static final int METADATA_LINE_WIDTH = 24;
    private static final int METADATA_BACKGROUND = 25;
    private static final int METADATA_OPACITY = 26;
    private static final int METADATA_STYLE_FLAGS = 27;
    private static final int ENTITY_METADATA_CUSTOM_NAME = 2;
    private static final int ENTITY_METADATA_CUSTOM_NAME_VISIBLE = 3;
    private static final int ENTITY_METADATA_NO_GRAVITY = 5;
    private static final int ARMOR_STAND_METADATA_FLAGS = 15;
    private static final byte ARMOR_STAND_SMALL = 0x01;
    private static final byte ARMOR_STAND_MARKER = 0x10;
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final AtomicInteger entityIds = new AtomicInteger(2_000_000);
    private final Map<SfxFloatingTextKey, SfxFloatingTextDisplayState> states = new ConcurrentHashMap<>();
    private volatile boolean running;

    public SfxFloatingTextDisplayService(JavaPlugin plugin, SfxRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    public void update(SfxFloatingTextProjection projection) {
        SfxFloatingTextDisplayState state = states.computeIfAbsent(projection.key(), ignored -> new SfxFloatingTextDisplayState(entityIds.incrementAndGet()));
        state.projection(projection);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            runtime.executeForPlayer(player, () -> updateForPlayer(player, state, projection));
        }
    }

    public void remove(SfxFloatingTextKey key) {
        SfxFloatingTextDisplayState state = states.remove(key);
        if (state == null) {
            return;
        }
        for (UUID viewerId : new ArrayList<>(state.viewers().keySet())) {
            Player player = plugin.getServer().getPlayer(viewerId);
            if (player != null && player.isOnline()) {
                runtime.executeForPlayer(player, () -> destroyForPlayer(player, state));
            }
        }
    }


    public void clearViewer(Player player) {
        if (player == null) {
            return;
        }
        UUID viewerId = player.getUniqueId();
        for (SfxFloatingTextDisplayState state : states.values()) {
            if (state.viewers().remove(viewerId) != null) {
                state.viewerText().remove(viewerId);
                state.viewerPositions().remove(viewerId);
                if (player.isOnline()) {
                    runtime.executeForPlayer(player, () -> destroyForPlayer(player, state));
                }
            }
        }
    }

    public void refreshViewer(Player player) {
        if (player == null) {
            return;
        }
        clearViewer(player);
        for (SfxFloatingTextDisplayState state : states.values()) {
            SfxFloatingTextProjection projection = state.projection();
            if (projection != null) {
                runtime.executeForPlayer(player, () -> updateForPlayer(player, state, projection));
            }
        }
    }

    public void refreshViewerLater(Player player, long delayTicks) {
        if (player == null) {
            return;
        }
        runtime.executeForPlayerLater(player, Math.max(1L, delayTicks), () -> refreshViewer(player));
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        scheduleResync();
    }

    public void shutdown() {
        running = false;
        for (SfxFloatingTextKey key : List.copyOf(states.keySet())) {
            remove(key);
        }
        states.clear();
    }

    private void scheduleResync() {
        long interval = Math.max(1L, plugin.getConfig().getLong("floating-text.resync-interval-ticks", 20L));
        runtime.executeGlobalLater(interval, () -> {
            if (!running) {
                return;
            }
            refreshVisibleViewers();
            scheduleResync();
        });
    }

    private void refreshVisibleViewers() {
        if (states.isEmpty()) {
            return;
        }
        for (SfxFloatingTextDisplayState state : states.values()) {
            SfxFloatingTextProjection projection = state.projection();
            if (projection == null) {
                continue;
            }
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                runtime.executeForPlayer(player, () -> updateForPlayer(player, state, projection));
            }
        }
    }

    private void updateForPlayer(Player player, SfxFloatingTextDisplayState state, SfxFloatingTextProjection projection) {
        if (!player.isOnline()) {
            state.viewers().remove(player.getUniqueId());
            state.viewerText().remove(player.getUniqueId());
            state.viewerPositions().remove(player.getUniqueId());
            return;
        }
        World world = player.getWorld();
        boolean sameWorld = world.getUID().equals(projection.key().worldId());
        Location location = sameWorld ? new Location(world, projection.x(), projection.y(), projection.z()) : null;
        if (!sameWorld || player.getLocation().distanceSquared(location) > projection.viewDistanceSquared()) {
            if (state.viewers().remove(player.getUniqueId()) != null) {
                state.viewerText().remove(player.getUniqueId());
                state.viewerPositions().remove(player.getUniqueId());
                destroyForPlayer(player, state);
            }
            return;
        }

        SfxFloatingTextDisplayMode mode = displayMode(projection);
        List<Component> lines = mode == SfxFloatingTextDisplayMode.ARMOR_STAND ? armorStandLines(projection.text()) : List.of(projection.text());
        int desiredEntities = Math.max(1, lines.size());
        boolean modeChanged = state.displayMode() != null && state.displayMode() != mode;
        if (modeChanged || state.entityIds().size() != desiredEntities) {
            int[] oldEntityIds = state.entityIds().stream().mapToInt(Integer::intValue).toArray();
            for (UUID viewerId : new ArrayList<>(state.viewers().keySet())) {
                Player viewer = plugin.getServer().getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(oldEntityIds));
                }
            }
            state.viewers().clear();
            state.viewerText().clear();
            state.viewerPositions().clear();
            state.ensureEntityCount(desiredEntities, entityIds);
        }
        state.displayMode(mode);

        UUID viewerId = player.getUniqueId();
        String currentPosition = positionKey(location, mode, lines.size());
        boolean knownViewer = state.viewers().containsKey(viewerId);
        Component previousText = state.viewerText().get(viewerId);
        String previousPosition = state.viewerPositions().get(viewerId);
        if (!knownViewer) {
            state.viewers().put(viewerId, Boolean.TRUE);
            state.viewerText().put(viewerId, projection.text());
            state.viewerPositions().put(viewerId, currentPosition);
            spawn(player, state, location, projection, mode, lines);
        } else if (previousPosition == null || !previousPosition.equals(currentPosition)) {
            destroyForPlayer(player, state);
            state.viewers().put(viewerId, Boolean.TRUE);
            state.viewerText().put(viewerId, projection.text());
            state.viewerPositions().put(viewerId, currentPosition);
            spawn(player, state, location, projection, mode, lines);
        } else if (!projection.text().equals(previousText)) {
            state.viewerText().put(viewerId, projection.text());
            updateMetadata(player, state, projection, mode, lines);
        }
    }

    private String positionKey(Location location, SfxFloatingTextDisplayMode mode, int lines) {
        double y = location.getY() + (mode == SfxFloatingTextDisplayMode.TEXT_DISPLAY
                ? plugin.getConfig().getDouble("floating-text.text-display.y-offset", 0.0D)
                : plugin.getConfig().getDouble("floating-text.armor-stand.y-offset", -0.25D));
        return String.format(Locale.ROOT, "%s:%d:%.4f:%.4f:%.4f", mode.name(), Math.max(1, lines), location.getX(), y, location.getZ());
    }

    private void destroyForPlayer(Player player, SfxFloatingTextDisplayState state) {
        state.viewers().remove(player.getUniqueId());
        state.viewerText().remove(player.getUniqueId());
        state.viewerPositions().remove(player.getUniqueId());
        if (player.isOnline()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(state.entityIds().stream().mapToInt(Integer::intValue).toArray()));
        }
    }

    private void spawn(Player player, SfxFloatingTextDisplayState state, Location location, SfxFloatingTextProjection projection, SfxFloatingTextDisplayMode mode, List<Component> lines) {
        if (mode == SfxFloatingTextDisplayMode.ARMOR_STAND) {
            double yOffset = plugin.getConfig().getDouble("floating-text.armor-stand.y-offset", -0.25D);
            double lineSpacing = plugin.getConfig().getDouble("floating-text.armor-stand.line-spacing", 0.25D);
            for (int line = 0; line < lines.size(); line++) {
                int entityId = state.entityIds().get(line);
                WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                        entityId,
                        Optional.of(UUID.randomUUID()),
                        EntityTypes.ARMOR_STAND,
                        new Vector3d(location.getX(), location.getY() + yOffset - (line * lineSpacing), location.getZ()),
                        0,
                        0,
                        0,
                        0,
                        Optional.empty());
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawn);
            }
        } else {
            WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                    state.entityId(),
                    Optional.of(UUID.randomUUID()),
                    EntityTypes.TEXT_DISPLAY,
                    new Vector3d(location.getX(), location.getY() + plugin.getConfig().getDouble("floating-text.text-display.y-offset", 0.0D), location.getZ()),
                    0,
                    0,
                    0,
                    0,
                    Optional.empty());
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawn);
        }
        updateMetadata(player, state, projection, mode, lines);
    }

    private void updateMetadata(Player player, SfxFloatingTextDisplayState state, SfxFloatingTextProjection projection, SfxFloatingTextDisplayMode mode, List<Component> lines) {
        if (mode == SfxFloatingTextDisplayMode.ARMOR_STAND) {
            for (int line = 0; line < lines.size(); line++) {
                int entityId = state.entityIds().get(line);
                List<EntityData<?>> metadata = List.of(
                        new EntityData<>(0, EntityDataTypes.BYTE, ENTITY_FLAGS),
                        new EntityData<>(ENTITY_METADATA_CUSTOM_NAME, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(lines.get(line))),
                        new EntityData<>(ENTITY_METADATA_CUSTOM_NAME_VISIBLE, EntityDataTypes.BOOLEAN, true),
                        new EntityData<>(ENTITY_METADATA_NO_GRAVITY, EntityDataTypes.BOOLEAN, true),
                        new EntityData<>(ARMOR_STAND_METADATA_FLAGS, EntityDataTypes.BYTE, (byte) (ARMOR_STAND_SMALL | ARMOR_STAND_MARKER)));
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(entityId, metadata));
            }
            return;
        }

        List<EntityData<?>> metadata = List.of(
                new EntityData<>(0, EntityDataTypes.BYTE, ENTITY_FLAGS),
                new EntityData<>(5, EntityDataTypes.BOOLEAN, true),
                new EntityData<>(METADATA_BILLBOARD, EntityDataTypes.BYTE, BILLBOARD_CENTER),
                new EntityData<>(METADATA_VIEW_RANGE, EntityDataTypes.FLOAT, 1.0f),
                new EntityData<>(METADATA_TEXT, EntityDataTypes.ADV_COMPONENT, projection.text()),
                new EntityData<>(METADATA_LINE_WIDTH, EntityDataTypes.INT, 200),
                new EntityData<>(METADATA_BACKGROUND, EntityDataTypes.INT, BACKGROUND_COLOR),
                new EntityData<>(METADATA_OPACITY, EntityDataTypes.BYTE, TEXT_OPACITY),
                new EntityData<>(METADATA_STYLE_FLAGS, EntityDataTypes.BYTE, styleFlags(projection)));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(state.entityId(), metadata));
    }

    private List<Component> armorStandLines(Component text) {
        String serialized = LEGACY_SECTION.serialize(text);
        String[] rawLines = serialized.split("\\n", -1);
        List<Component> lines = new ArrayList<>();
        for (String rawLine : rawLines) {
            if (rawLine == null || rawLine.isBlank()) {
                continue;
            }
            lines.add(LEGACY_SECTION.deserialize(rawLine));
        }
        if (lines.isEmpty()) {
            lines.add(Component.empty());
        }
        return lines;
    }

    private SfxFloatingTextDisplayMode displayMode(SfxFloatingTextProjection projection) {
        if (projection.displayMode() != null) {
            return projection.displayMode();
        }
        String raw = plugin.getConfig().getString("floating-text.mode", "armor_stand");
        if (raw == null) {
            return SfxFloatingTextDisplayMode.ARMOR_STAND;
        }
        String normalized = raw.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.equals("text") || normalized.equals("text_display") || normalized.equals("display")) {
            return SfxFloatingTextDisplayMode.TEXT_DISPLAY;
        }
        return SfxFloatingTextDisplayMode.ARMOR_STAND;
    }

    private byte styleFlags(SfxFloatingTextProjection projection) {
        return (byte) (TEXT_STYLE_SHADOW | (projection.seeThrough() ? TEXT_STYLE_SEE_THROUGH : 0));
    }


}
