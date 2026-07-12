package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.UUID;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class SfxElectricMachineMenuRenderer {
    private final SfxItems items;
    private final JavaPlugin plugin;
    private final SfxLocalization localization;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxElectricMachineMenuRenderer(JavaPlugin plugin, SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.plugin = plugin;
        this.items = items;
        this.localization = localization;
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
        for (Map.Entry<Integer, ItemStack> entry : definition.recipeProvider().displayItems(plugin, items, definition, state).entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < inventory.getSize()) {
                inventory.setItem(entry.getKey(), entry.getValue());
            }
        }
    }

    private void fillInventoryFrame(SfxElectricMachineDefinition definition, Inventory inventory) {
        SfxElectricMachineUiPainter.fillConfiguredSlots(definition.ui(), inventory, localization);
    }
}
