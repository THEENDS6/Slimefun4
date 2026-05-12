package cc.theends6.sfx.internal.electric;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

record SfxElectricMachineHolder(UUID instanceId) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
