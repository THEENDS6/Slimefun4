package cc.theends6.sfx.internal.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;

/** Ordered registry for domain lifecycle handlers. */
public final class SfxBlockLifecycleRegistry {
    private final List<SfxBlockLifecycleHandler> handlers = new ArrayList<>();

    public void register(SfxBlockLifecycleHandler handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
    }

    public Optional<SfxBlockLifecycleHandler> firstSupporting(String typeId) {
        if (typeId == null) {
            return Optional.empty();
        }
        return handlers.stream().filter(handler -> handler.supports(typeId)).findFirst();
    }

    public boolean destroyFirst(Block block, UUID instanceId, String typeId, SfxBlockDestructionOptions options) {
        Optional<SfxBlockLifecycleHandler> handler = firstSupporting(typeId);
        handler.ifPresent(value -> value.destroy(block, instanceId, typeId, options == null ? SfxBlockDestructionOptions.NONE : options));
        return handler.isPresent();
    }
}
