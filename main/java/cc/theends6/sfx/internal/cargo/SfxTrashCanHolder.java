package cc.theends6.sfx.internal.cargo;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class SfxTrashCanHolder implements InventoryHolder {
    private final UUID instanceId;
    private Inventory inventory;

    SfxTrashCanHolder(UUID instanceId) {
        this.instanceId = instanceId;
    }

    UUID instanceId() {
        return instanceId;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
