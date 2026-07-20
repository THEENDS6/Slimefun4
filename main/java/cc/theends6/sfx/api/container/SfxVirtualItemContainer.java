package cc.theends6.sfx.api.container;

import org.bukkit.inventory.ItemStack;

public interface SfxVirtualItemContainer {
    int slots();
    ItemStack peek(int slot);
    ItemStack extract(int slot, int amount, SfxTransactionMode mode);
    ItemStack insert(int slot, ItemStack item, SfxTransactionMode mode);
    default java.util.Optional<SfxTransactionReservation> prepareInsert(int slot, ItemStack item) {
        return java.util.Optional.empty();
    }
    default java.util.Optional<SfxTransactionReservation> prepareExtract(int slot, int amount) {
        return java.util.Optional.empty();
    }
}
