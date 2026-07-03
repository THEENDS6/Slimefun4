package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.ui.SfxItemProgressBar;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxAutoBrewerMenuRenderer {
    static final int BLAZE_SLOT = 10;
    static final int PROGRESS_SLOT = 13;
    static final int INGREDIENT_SLOT = 16;
    static final int FUEL_DISPLAY_SLOT = 22;
    static final int[] POTION_SLOTS = {37, 39, 41, 43};

    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxAutoBrewerMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.localization = localization;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        fillFrame(definition, inventory);
        inventory.setItem(BLAZE_SLOT, state.input(0) == null ? null : state.input(0).toItemStack(items));
        inventory.setItem(definition.ui().statusSlot(), statusIcons.render(viewerId, definition, state, null, status));
        inventory.setItem(INGREDIENT_SLOT, state.input(1) == null ? null : state.input(1).toItemStack(items));
        inventory.setItem(FUEL_DISPLAY_SLOT, fuelDisplay(definition, state));
        for (int index = 0; index < POTION_SLOTS.length; index++) {
            inventory.setItem(POTION_SLOTS[index], state.input(2 + index) == null ? null : state.input(2 + index).toItemStack(items));
        }
    }

    private void fillFrame(SfxElectricMachineDefinition definition, Inventory inventory) {
        inventory.clear();
        SfxElectricMachineUiPainter.fillConfiguredSlots(definition.ui(), inventory, localization);
    }

    private ItemStack fuelDisplay(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        int stored = Math.max(0, Math.min(SfxAdvancedAutoBrewerRecipeProvider.MAX_BLAZE_FUEL_TICKS, state.specialData()));
        int recipeFuel = state.hasProgress() && state.activeBaseTicks() > 0 ? Math.max(1, state.activeBaseTicks()) : 0;
        int powders = recipeFuel <= 0 ? 0 : (recipeFuel + SfxAdvancedAutoBrewerRecipeProvider.BLAZE_FUEL_TICKS - 1) / SfxAdvancedAutoBrewerRecipeProvider.BLAZE_FUEL_TICKS;
        ItemStack stack = definition.ui().requiredItem(stored <= 0 ? "auto-brewer.fuel.empty" : "auto-brewer.fuel.stored")
                .toItemStack(localization, Map.of(
                "fuel", stored,
                "capacity", SfxAdvancedAutoBrewerRecipeProvider.MAX_BLAZE_FUEL_TICKS,
                "recipe_fuel", recipeFuel,
                "powders", powders));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (stored > 0 && stored >= SfxAdvancedAutoBrewerRecipeProvider.MAX_BLAZE_FUEL_TICKS) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
            stack.setItemMeta(meta);
            if (stored > 0) {
                SfxItemProgressBar.apply(stack, stored, SfxAdvancedAutoBrewerRecipeProvider.MAX_BLAZE_FUEL_TICKS);
            }
        }
        return stack;
    }


}
