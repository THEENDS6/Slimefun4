package cc.theends6.sfx.internal.listener;

import org.bukkit.entity.Arrow;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

record WeightedDrop(int weight, ItemStack item) {
}

record GrappleState(Arrow arrow, boolean consumed) {
}

final class DustbinHolder implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
