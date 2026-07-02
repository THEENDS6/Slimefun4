package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.UUID;
import org.bukkit.inventory.Inventory;

final class SfxGeoMinerMachineMenuRenderer {
    private final SfxItems items;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxGeoMinerMachineMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        fillFrame(definition, inventory);
        inventory.setItem(definition.ui().statusSlot(), statusIcons.render(viewerId, definition, state, null, status));
        int[] outputSlots = definition.outputSlots();
        for (int slot = 0; slot < outputSlots.length; slot++) {
            inventory.setItem(outputSlots[slot], state.output(slot) == null ? null : state.output(slot).toItemStack(items));
        }
    }

    private void fillFrame(SfxElectricMachineDefinition definition, Inventory inventory) {
        inventory.clear();
        for (SfxElectricMachineUiFrame frame : definition.ui().frame()) {
            for (int slot : frame.slots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, frame.item().toItemStack());
                }
            }
        }
    }
}
