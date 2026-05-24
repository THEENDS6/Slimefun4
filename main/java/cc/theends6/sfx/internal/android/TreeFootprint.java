package cc.theends6.sfx.internal.android;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;

record TreeFootprint(Material sapling, List<Block> blocks) {
    TreeFootprint {
        blocks = List.copyOf(blocks);
    }

    boolean contains(Block block) {
        if (block == null) {
            return false;
        }
        for (Block candidate : blocks) {
            if (candidate.getWorld().equals(block.getWorld())
                    && candidate.getX() == block.getX()
                    && candidate.getY() == block.getY()
                    && candidate.getZ() == block.getZ()) {
                return true;
            }
        }
        return false;
    }
}
