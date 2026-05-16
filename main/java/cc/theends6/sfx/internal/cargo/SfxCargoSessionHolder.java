package cc.theends6.sfx.internal.cargo;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class SfxCargoSessionHolder implements InventoryHolder {
    private final UUID instanceId;
    private final SfxCargoComponentType type;
    private Inventory inventory;

    SfxCargoSessionHolder(UUID instanceId, SfxCargoComponentType type) {
        this.instanceId = instanceId;
        this.type = type;
    }

    UUID instanceId() {
        return instanceId;
    }

    SfxCargoComponentType type() {
        return type;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
