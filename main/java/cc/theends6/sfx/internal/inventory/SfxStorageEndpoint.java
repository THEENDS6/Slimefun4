package cc.theends6.sfx.internal.inventory;

import org.bukkit.inventory.ItemStack;

public interface SfxStorageEndpoint {
    SfxStorageKey storageKey();

    SfxInventoryAccessState accessState();

    int simulateInsert(ItemStack stack, boolean smartFill);

    ItemStack insert(ItemStack stack, boolean smartFill);

    default boolean ready() {
        return accessState().ready();
    }
}
