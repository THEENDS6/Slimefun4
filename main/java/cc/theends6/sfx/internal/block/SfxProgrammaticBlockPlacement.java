package cc.theends6.sfx.internal.block;

import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

public interface SfxProgrammaticBlockPlacement {
    boolean canPlaceFromBlockPlacer(String itemId, ItemStack stack, Block target, UUID ownerId);

    boolean placeFromBlockPlacer(String itemId, ItemStack stack, Block target, UUID ownerId);
}
