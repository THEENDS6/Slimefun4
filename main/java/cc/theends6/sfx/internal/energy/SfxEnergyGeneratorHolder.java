package cc.theends6.sfx.internal.energy;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

record SfxEnergyGeneratorHolder(UUID instanceId) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
