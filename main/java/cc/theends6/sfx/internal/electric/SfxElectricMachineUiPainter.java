package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.Map;
import org.bukkit.inventory.Inventory;

final class SfxElectricMachineUiPainter {
    private SfxElectricMachineUiPainter() {
    }

    static void fillConfiguredSlots(SfxElectricMachineUiDefinition ui, Inventory inventory, SfxLocalization localization) {
        for (SfxElectricMachineUiSlot slot : ui.slots().values()) {
            if (slot.item() == null || slot.slot() < 0 || slot.slot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot.slot(), slot.item().toItemStack(localization, Map.of()));
        }
    }
}
