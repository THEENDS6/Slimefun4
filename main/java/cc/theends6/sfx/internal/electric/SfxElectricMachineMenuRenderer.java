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

final class SfxElectricMachineMenuRenderer {
    private static final int DISPLAY_SLOT = 22;
    private static final int[] BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 31, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] BORDER_IN = {9, 10, 11, 12, 18, 21, 27, 28, 29, 30};
    private static final int[] BORDER_OUT = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};

    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxElectricMachineMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.localization = localization;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        fillInventoryFrame(inventory);
        inventory.setItem(DISPLAY_SLOT, statusIcons.render(viewerId, definition, state, recipe, status));
        int[] inputSlots = definition.inputSlots();
        for (int slot = 0; slot < inputSlots.length; slot++) {
            inventory.setItem(inputSlots[slot], state.input(slot) == null ? null : state.input(slot).toItemStack(items));
        }
        int[] outputSlots = definition.outputSlots();
        for (int slot = 0; slot < outputSlots.length; slot++) {
            inventory.setItem(outputSlots[slot], state.output(slot) == null ? null : state.output(slot).toItemStack(items));
        }
    }

    private void fillInventoryFrame(Inventory inventory) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        ItemStack inputBorder = namedItem(
                Material.CYAN_STAINED_GLASS_PANE,
                localization.component("electric-ui.input.name", "<aqua>Input</aqua>"),
                List.of(localization.component("electric-ui.input.lore", "<gray>Place items here.</gray>")));
        ItemStack outputBorder = namedItem(
                Material.ORANGE_STAINED_GLASS_PANE,
                localization.component("electric-ui.output.name", "<gold>Output</gold>"),
                List.of(localization.component("electric-ui.output.lore", "<gray>Take finished items here.</gray>")));
        SfxInventoryPainter.setSlots(inventory, filler, BORDER);
        SfxInventoryPainter.setSlots(inventory, inputBorder, BORDER_IN);
        SfxInventoryPainter.setSlots(inventory, outputBorder, BORDER_OUT);
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        return SfxUiItems.named(material, name, lore);
    }
}
