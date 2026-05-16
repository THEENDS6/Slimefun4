package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.ui.SfxInventoryPainter;
import cc.theends6.sfx.internal.ui.SfxUiItems;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class SfxSimpleIoMachineMenuRenderer {
    private static final int STATUS_SLOT = 4;
    private static final int[] IO_SLOTS = SfxElectricMachineDefinition.SIMPLE_IO_SLOTS;

    private final SfxItems items;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxSimpleIoMachineMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        fillBackground(inventory);
        inventory.setItem(STATUS_SLOT, statusIcons.render(viewerId, definition, state, null, status));
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

    private void fillBackground(Inventory inventory) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        SfxInventoryPainter.fill(inventory, filler);
        SfxInventoryPainter.clearSlots(inventory, IO_SLOTS);
        inventory.setItem(STATUS_SLOT, null);
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        return SfxUiItems.named(material, name, lore);
    }
}
