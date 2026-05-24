package cc.theends6.sfx.internal.cargo;

import java.util.UUID;
import org.bukkit.block.BlockFace;

record SfxCargoEndpointCacheKey(UUID nodeId, BlockFace face, boolean outputSide) {
}
