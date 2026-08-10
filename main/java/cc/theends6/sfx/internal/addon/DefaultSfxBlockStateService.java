package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.api.block.SfxBlockLifecycleState;
import cc.theends6.sfx.api.block.SfxBlockStateService;
import cc.theends6.sfx.api.block.SfxBlockStateView;
import cc.theends6.sfx.api.block.SfxBlockType;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

final class DefaultSfxBlockStateService implements SfxBlockStateService {
    private final SfxBlockDataService blockData;
    private final Function<String, Optional<SfxBlockType<?>>> types;

    DefaultSfxBlockStateService(SfxBlockDataService blockData,
                                Function<String, Optional<SfxBlockType<?>>> types) {
        this.blockData = blockData;
        this.types = types;
    }

    @Override public Optional<SfxBlockStateView<?>> find(Location location) {
        SfxBlockInstanceRecord record = record(location).orElse(null);
        if (record == null || record.lifecycleState() == SfxBlockLifecycleState.INVALID) return Optional.empty();
        SfxBlockType<?> type = types.apply(record.typeId()).orElse(null);
        return type == null ? Optional.empty() : decoded(record, type).map(value -> value);
    }

    @Override public <S> Optional<SfxBlockStateView<S>> find(Location location, SfxBlockType<S> type) {
        if (type == null) return Optional.empty();
        SfxBlockInstanceRecord record = record(location).orElse(null);
        if (record == null || !type.id().equals(record.typeId())
                || record.lifecycleState() == SfxBlockLifecycleState.INVALID) return Optional.empty();
        return decoded(record, type);
    }

    @Override public <S> Optional<SfxBlockStateView<S>> update(Location location, SfxBlockType<S> type,
                                                               UnaryOperator<S> update) {
        if (type == null || update == null) return Optional.empty();
        SfxBlockInstanceRecord initial = record(location).orElse(null);
        if (initial == null || !type.id().equals(initial.typeId())
                || initial.lifecycleState() == SfxBlockLifecycleState.INVALID) return Optional.empty();
        try {
            Optional<SfxBlockInstanceRecord> changed = blockData.updateInstanceAtomic(initial.instanceId(), current -> {
                if (!type.id().equals(current.typeId()) || current.lifecycleState() == SfxBlockLifecycleState.INVALID) {
                    return current;
                }
                S decoded;
                try { decoded = decode(type, current); }
                catch (RuntimeException failure) { throw new StateCodecFailure(failure); }
                S next = update.apply(decoded);
                byte[] payload;
                try { payload = type.stateSchema().codec().encode(next); }
                catch (RuntimeException failure) { throw new StateCodecFailure(failure); }
                return current.withState(payload, current.lifecycleState(), type.stateSchema().version(),
                        Instant.now().toEpochMilli());
            });
            SfxBlockInstanceRecord record = changed.orElse(null);
            return record == null || !type.id().equals(record.typeId())
                    || record.lifecycleState() == SfxBlockLifecycleState.INVALID
                    ? Optional.empty() : decoded(record, type);
        } catch (StateCodecFailure failure) {
            blockData.updateInstanceState(initial.instanceId(), initial.stateBlob(),
                    SfxBlockLifecycleState.INVALID, initial.version());
            return Optional.empty();
        }
    }

    @Override public List<SfxBlockStateView<?>> findLoaded(Chunk chunk, String blockTypeId) {
        if (chunk == null || blockTypeId == null || blockTypeId.isBlank()) return List.of();
        List<SfxBlockStateView<?>> result = new ArrayList<>();
        for (SfxAnchorRecord anchor : blockData.anchorsInChunk(
                chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
            SfxBlockInstanceRecord record = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (record == null || !blockTypeId.equals(record.typeId())) continue;
            find(location(anchor.key())).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    @Override public boolean exists(Location location, String blockTypeId) {
        return record(location).filter(record -> blockTypeId != null && blockTypeId.equals(record.typeId())
                && record.lifecycleState() != SfxBlockLifecycleState.INVALID).isPresent();
    }

    private Optional<SfxBlockInstanceRecord> record(Location location) {
        if (location == null || location.getWorld() == null) return Optional.empty();
        SfxAnchorRecord anchor = blockData.findAnchorFast(location).orElse(null);
        return anchor == null ? Optional.empty() : blockData.findInstance(anchor.instanceId());
    }

    private <S> Optional<SfxBlockStateView<S>> decoded(SfxBlockInstanceRecord record, SfxBlockType<S> type) {
        try {
            return Optional.of(new SfxBlockStateView<>(record.instanceId(), record.typeId(), location(record.anchorKey()),
                    record.version(), record.lifecycleState(), decode(type, record)));
        } catch (RuntimeException failure) {
            blockData.updateInstanceState(record.instanceId(), record.stateBlob(),
                    SfxBlockLifecycleState.INVALID, record.version());
            return Optional.empty();
        }
    }

    private static <S> S decode(SfxBlockType<S> type, SfxBlockInstanceRecord record) {
        return !record.hasState() ? type.initialState().get()
                : type.stateSchema().decode(record.version(), record.stateBlob());
    }

    private static Location location(SfxBlockAnchorKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) throw new IllegalStateException("World is not loaded: " + key.worldId());
        return new Location(world, key.x(), key.y(), key.z());
    }

    private static final class StateCodecFailure extends RuntimeException {
        private StateCodecFailure(RuntimeException cause) { super(cause); }
    }
}
