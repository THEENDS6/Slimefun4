package cc.theends6.sfx.internal.machine;

import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public interface SfxOutputPolicy {
    boolean canFitAll(Inventory inventory, List<Integer> outputSlots, List<ItemStack> outputs);
}
