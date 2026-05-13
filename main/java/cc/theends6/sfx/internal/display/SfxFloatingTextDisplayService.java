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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reusable PacketEvents-backed floating text projector.
 *
 * <p>This service owns only client-side virtual Text Display entities. It does not create, store,
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

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final AtomicInteger entityIds = new AtomicInteger(2_000_000);
    private final Map<SfxFloatingTextKey, SfxFloatingTextDisplayState> states = new ConcurrentHashMap<>();

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
                if (player.isOnline()) {
                    runtime.executeForPlayer(player, () -> PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(state.entityId())));
                }
            }
        }
    }

    public void refreshViewer(Player player) {
        if (player == null) {
            return;
        }
        for (SfxFloatingTextDisplayState state : states.values()) {
            SfxFloatingTextProjection projection = state.projection();
            if (projection != null) {
                runtime.executeForPlayer(player, () -> updateForPlayer(player, state, projection));
            }
        }
    }

    public void shutdown() {
        for (SfxFloatingTextKey key : List.copyOf(states.keySet())) {
            remove(key);
        }
        states.clear();
    }

    private void updateForPlayer(Player player, SfxFloatingTextDisplayState state, SfxFloatingTextProjection projection) {
        if (!player.isOnline()) {
            state.viewers().remove(player.getUniqueId());
            state.viewerText().remove(player.getUniqueId());
            return;
        }
        World world = player.getWorld();
        boolean sameWorld = world.getUID().equals(projection.key().worldId());
        Location location = sameWorld ? new Location(world, projection.x(), projection.y(), projection.z()) : null;
        if (!sameWorld || player.getLocation().distanceSquared(location) > projection.viewDistanceSquared()) {
            if (state.viewers().remove(player.getUniqueId()) != null) {
                state.viewerText().remove(player.getUniqueId());
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(state.entityId()));
            }
            return;
        }
        Component previousText = state.viewerText().put(player.getUniqueId(), projection.text());
        if (state.viewers().put(player.getUniqueId(), Boolean.TRUE) == null) {
            spawn(player, state.entityId(), location, projection);
        } else if (!projection.text().equals(previousText)) {
            updateMetadata(player, state.entityId(), projection);
        }
    }

    private void destroyForPlayer(Player player, SfxFloatingTextDisplayState state) {
        state.viewers().remove(player.getUniqueId());
        state.viewerText().remove(player.getUniqueId());
        if (player.isOnline()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(state.entityId()));
        }
    }

    private void spawn(Player player, int entityId, Location location, SfxFloatingTextProjection projection) {
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
        updateMetadata(player, entityId, projection);
    }

    private void updateMetadata(Player player, int entityId, SfxFloatingTextProjection projection) {
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
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(entityId, metadata));
    }

    private byte styleFlags(SfxFloatingTextProjection projection) {
        return (byte) (TEXT_STYLE_SHADOW | (projection.seeThrough() ? TEXT_STYLE_SEE_THROUGH : 0));
    }


}
