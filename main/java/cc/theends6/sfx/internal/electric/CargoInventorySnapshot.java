package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

record CargoInventorySnapshot(UUID instanceId, SfxElectricMachineState state, boolean inputInventory, ItemStack[] contents) {
}
