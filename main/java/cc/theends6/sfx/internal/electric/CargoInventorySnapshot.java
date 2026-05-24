package cc.theends6.sfx.internal.electric;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

record CargoInventorySnapshot(UUID instanceId, SfxElectricMachineState state, boolean inputInventory, ItemStack[] contents) {
}
