package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.UUID;
import org.bukkit.inventory.Inventory;

final class SfxElectricMachineMenuRenderer {
    private final SfxItems items;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxElectricMachineMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        fillInventoryFrame(definition, inventory);
        if (definition.ui().statusSlot() >= 0) {
            inventory.setItem(definition.ui().statusSlot(), statusIcons.render(viewerId, definition, state, recipe, status));
        }
        int[] inputSlots = definition.inputSlots();
        for (int slot = 0; slot < inputSlots.length; slot++) {
            inventory.setItem(inputSlots[slot], state.input(slot) == null ? null : state.input(slot).toItemStack(items));
        }
        int[] outputSlots = definition.outputSlots();
        for (int slot = 0; slot < outputSlots.length; slot++) {
            inventory.setItem(outputSlots[slot], state.output(slot) == null ? null : state.output(slot).toItemStack(items));
        }
    }

    private void fillInventoryFrame(SfxElectricMachineDefinition definition, Inventory inventory) {
        for (SfxElectricMachineUiFrame frame : definition.ui().frame()) {
            for (int slot : frame.slots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, frame.item().toItemStack());
                }
            }
        }
    }
}
