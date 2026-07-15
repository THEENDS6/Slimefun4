package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.internal.util.SfxLocalization;
import org.bukkit.inventory.Inventory;

final class SfxEnergyComponentUiPainter {
    private SfxEnergyComponentUiPainter() {
    }

    static void fillConfiguredSlots(SfxEnergyComponentUiDefinition ui, Inventory inventory, SfxLocalization localization) {
        for (SfxEnergyComponentUiSlot slot : ui.slots().values()) {
            if (slot.item() == null || slot.slot() < 0 || slot.slot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot.slot(), slot.item().toItemStack(localization));
        }
    }
}
