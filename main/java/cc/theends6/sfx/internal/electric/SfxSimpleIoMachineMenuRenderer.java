package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.UUID;
import org.bukkit.inventory.Inventory;

final class SfxSimpleIoMachineMenuRenderer {
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxSimpleIoMachineMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.localization = localization;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        fillBackground(definition, inventory);
        inventory.setItem(definition.ui().statusSlot(), statusIcons.render(viewerId, definition, state, null, status));
        if (definition.inputSlots().length > 0 && definition.outputSlots().length == 0) {
            for (int index = 0; index < definition.inputSlots().length; index++) {
                int slot = definition.inputSlots()[index];
                inventory.setItem(slot, state.input(index) == null ? null : state.input(index).toItemStack(items));
            }
        } else if (definition.outputSlots().length > 0 && definition.inputSlots().length == 0) {
            for (int index = 0; index < definition.outputSlots().length; index++) {
                int slot = definition.outputSlots()[index];
                inventory.setItem(slot, state.output(index) == null ? null : state.output(index).toItemStack(items));
            }
        }
    }

    private void fillBackground(SfxElectricMachineDefinition definition, Inventory inventory) {
        inventory.clear();
        SfxElectricMachineUiPainter.fillConfiguredSlots(definition.ui(), inventory, localization);
    }
}
