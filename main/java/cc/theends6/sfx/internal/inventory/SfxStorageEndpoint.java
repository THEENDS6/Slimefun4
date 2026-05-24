package cc.theends6.sfx.internal.inventory;

import org.bukkit.inventory.ItemStack;

public interface SfxStorageEndpoint {
    SfxStorageKey storageKey();

    SfxInventoryAccessState accessState();

    int simulateInsert(ItemStack stack, boolean smartFill);

    default int simulateInsertSingleSlot(ItemStack stack, boolean smartFill) {
        return simulateInsert(stack, smartFill);
    }

    ItemStack insert(ItemStack stack, boolean smartFill);

    default ItemStack insertSingleSlot(ItemStack stack, boolean smartFill) {
        return insert(stack, smartFill);
    }

    default boolean ready() {
        return accessState().ready();
    }
}
