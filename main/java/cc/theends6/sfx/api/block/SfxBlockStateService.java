package cc.theends6.sfx.api.block;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.bukkit.Chunk;
import org.bukkit.Location;


public interface SfxBlockStateService {
    Optional<SfxBlockStateView<?>> find(Location location);
    <S> Optional<SfxBlockStateView<S>> find(Location location, SfxBlockType<S> type);
    <S> Optional<SfxBlockStateView<S>> update(Location location, SfxBlockType<S> type,
                                               UnaryOperator<S> update);
    List<SfxBlockStateView<?>> findLoaded(Chunk chunk, String blockTypeId);
    boolean exists(Location location, String blockTypeId);
}
