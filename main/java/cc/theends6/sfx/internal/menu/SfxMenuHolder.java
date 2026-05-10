package cc.theends6.sfx.internal.menu;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class SfxMenuHolder implements InventoryHolder {
    private final UUID viewerId;
    private Inventory inventory;

    SfxMenuHolder(UUID viewerId) {
        this.viewerId = viewerId;
    }

    UUID viewerId() {
        return viewerId;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
