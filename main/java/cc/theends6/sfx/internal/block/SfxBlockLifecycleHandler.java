package cc.theends6.sfx.internal.block;

import java.util.UUID;
import org.bukkit.block.Block;

/** Domain-specific lifecycle handler registered into the block framework router. */
public interface SfxBlockLifecycleHandler {
    boolean supports(String typeId);

    void destroy(Block block, UUID instanceId, String typeId, SfxBlockDestructionOptions options);
}
