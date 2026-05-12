package cc.theends6.sfx.internal.listener;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

record BackpackBinding(UUID ownerId, int backpackId) {
}

record OpenBackpackSession(String uniqueKey, UUID ownerId, int backpackId, String itemId, Inventory inventory) {
}

record BackpackHolder(UUID ownerId, int backpackId) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
