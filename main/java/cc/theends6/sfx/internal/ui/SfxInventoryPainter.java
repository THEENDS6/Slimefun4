package cc.theends6.sfx.internal.ui;

import cc.theends6.sfx.api.ui.SfxUiItems;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class SfxInventoryPainter {
    private SfxInventoryPainter() {
    }

    public static void setSlots(Inventory inventory, ItemStack item, int... slots) {
        if (inventory == null || slots == null) {
            return;
        }
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item == null ? null : item.clone());
            }
        }
    }

    public static void setSlots(Inventory inventory, Material material, int... slots) {
        setSlots(inventory, SfxUiItems.blankPane(material), slots);
    }

    public static void fill(Inventory inventory, ItemStack item) {
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, item == null ? null : item.clone());
        }
    }

    public static void clearSlots(Inventory inventory, int... slots) {
        setSlots(inventory, (ItemStack) null, slots);
    }
}
