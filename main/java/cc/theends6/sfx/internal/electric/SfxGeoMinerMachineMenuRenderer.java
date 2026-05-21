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

final class SfxGeoMinerMachineMenuRenderer {
    private static final int STATUS_SLOT = 4;
    private static final int[] BORDER = {
            0, 1, 2, 3, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 26, 27, 35, 36, 44, 45, 53
    };
    private static final int[] OUTPUT_BORDER = {
            19, 20, 21, 22, 23, 24, 25,
            28, 34, 37, 43,
            46, 47, 48, 49, 50, 51, 52
    };

    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxGeoMinerMachineMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.localization = localization;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        fillFrame(inventory);
        inventory.setItem(STATUS_SLOT, statusIcons.render(viewerId, definition, state, null, status));
        int[] outputSlots = definition.outputSlots();
        for (int slot = 0; slot < outputSlots.length; slot++) {
            inventory.setItem(outputSlots[slot], state.output(slot) == null ? null : state.output(slot).toItemStack(items));
        }
    }

    private void fillFrame(Inventory inventory) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        ItemStack outputBorder = namedItem(
                Material.ORANGE_STAINED_GLASS_PANE,
                localization.component("electric-ui.output.name", "<gold>Output</gold>"),
                List.of(localization.component("electric-ui.output.lore", "<gray>Take finished items here.</gray>")));
        SfxInventoryPainter.setSlots(inventory, filler, BORDER);
        SfxInventoryPainter.setSlots(inventory, outputBorder, OUTPUT_BORDER);
        SfxInventoryPainter.clearSlots(inventory, SfxElectricMachineDefinition.GEO_MINER_OUTPUT_SLOTS);
        inventory.setItem(STATUS_SLOT, null);
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        return SfxUiItems.named(material, name, lore);
    }
}
