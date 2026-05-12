package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.util.SfxLocalization;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxEnergyDisplayService {
    private static final int VIEW_DISTANCE_SQUARED = 32 * 32;
    private static final byte ENTITY_FLAGS = 0x20;
    private static final byte BILLBOARD_CENTER = 3;
    private static final byte TEXT_OPACITY = (byte) 255;
    private static final byte TEXT_STYLE_FLAGS = 0x01;
    private static final int BACKGROUND_COLOR = 0x40000000;
    private static final int METADATA_BILLBOARD = 15;
    private static final int METADATA_VIEW_RANGE = 17;
    private static final int METADATA_TEXT = 23;
    private static final int METADATA_LINE_WIDTH = 24;
    private static final int METADATA_BACKGROUND = 25;
    private static final int METADATA_OPACITY = 26;
    private static final int METADATA_STYLE_FLAGS = 27;

    private final JavaPlugin plugin;
    private final SfxLocalization localization;
    private final AtomicInteger entityIds = new AtomicInteger(2_000_000);
    private final Map<SfxBlockAnchorKey, DisplayState> states = new ConcurrentHashMap<>();

    SfxEnergyDisplayService(JavaPlugin plugin, SfxLocalization localization) {
        this.plugin = plugin;
        this.localization = localization;
    }

    void update(SfxBlockAnchorKey anchorKey, DisplayText displayText) {
        World world = plugin.getServer().getWorld(anchorKey.worldId());
        if (world == null) {
            remove(anchorKey);
            return;
        }
        Location location = new Location(world, anchorKey.x() + 0.5, anchorKey.y() + 1.15, anchorKey.z() + 0.5);
        DisplayState state = states.computeIfAbsent(anchorKey, ignored -> new DisplayState(entityIds.incrementAndGet()));
        Component text = displayText.toComponent(localization);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || player.getWorld() != world || player.getLocation().distanceSquared(location) > VIEW_DISTANCE_SQUARED) {
                if (state.viewers.remove(player.getUniqueId()) != null) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(state.entityId));
                }
                continue;
            }
            if (state.viewers.put(player.getUniqueId(), Boolean.TRUE) == null) {
                spawn(player, state.entityId, location, text);
            } else if (!text.equals(state.lastText)) {
                updateMetadata(player, state.entityId, text);
            }
        }
        state.lastText = text;
    }

    void remove(SfxBlockAnchorKey anchorKey) {
        DisplayState state = states.remove(anchorKey);
        if (state == null) {
            return;
        }
        for (UUID viewerId : new ArrayList<>(state.viewers.keySet())) {
            Player player = plugin.getServer().getPlayer(viewerId);
            if (player != null && player.isOnline()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(state.entityId));
            }
        }
    }

    void shutdown() {
        for (SfxBlockAnchorKey key : List.copyOf(states.keySet())) {
            remove(key);
        }
        states.clear();
    }

    private void spawn(Player player, int entityId, Location location, Component text) {
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                0,
                0,
                0,
                0,
                Optional.empty());
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawn);
        updateMetadata(player, entityId, text);
    }

    private void updateMetadata(Player player, int entityId, Component text) {
        List<EntityData<?>> metadata = List.of(
                new EntityData<>(0, EntityDataTypes.BYTE, ENTITY_FLAGS),
                new EntityData<>(5, EntityDataTypes.BOOLEAN, true),
                new EntityData<>(METADATA_BILLBOARD, EntityDataTypes.BYTE, BILLBOARD_CENTER),
                new EntityData<>(METADATA_VIEW_RANGE, EntityDataTypes.FLOAT, 1.0f),
                new EntityData<>(METADATA_TEXT, EntityDataTypes.ADV_COMPONENT, text),
                new EntityData<>(METADATA_LINE_WIDTH, EntityDataTypes.INT, 200),
                new EntityData<>(METADATA_BACKGROUND, EntityDataTypes.INT, BACKGROUND_COLOR),
                new EntityData<>(METADATA_OPACITY, EntityDataTypes.BYTE, TEXT_OPACITY),
                new EntityData<>(METADATA_STYLE_FLAGS, EntityDataTypes.BYTE, TEXT_STYLE_FLAGS));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(entityId, metadata));
    }

    record DisplayText(String key, Map<String, ?> placeholders, String fallback) {
        Component toComponent(SfxLocalization localization) {
            return localization.component(key, fallback, placeholders);
        }
    }

    private static final class DisplayState {
        private final int entityId;
        private final Map<UUID, Boolean> viewers = new ConcurrentHashMap<>();
        private Component lastText = Component.empty();

        private DisplayState(int entityId) {
            this.entityId = entityId;
        }
    }
}
