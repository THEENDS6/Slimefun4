package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.ui.SfxInventoryPainter;
import cc.theends6.sfx.internal.ui.SfxItemProgressBar;
import cc.theends6.sfx.internal.ui.SfxUiItems;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxAutoBrewerMenuRenderer {
    static final int BLAZE_SLOT = 10;
    static final int PROGRESS_SLOT = 13;
    static final int INGREDIENT_SLOT = 16;
    static final int FUEL_DISPLAY_SLOT = 22;
    static final int[] POTION_SLOTS = {37, 39, 41, 43};
    private static final int[] ORANGE_SLOTS = {0, 1, 2, 9, 11, 18, 19, 20};
    private static final int[] GRAY_SLOTS = {3, 4, 5, 12, 14, 21, 23};
    private static final int[] BLUE_SLOTS = {6, 7, 8, 15, 17, 24, 25, 26};
    private static final int[] GREEN_SLOTS = {27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 38, 40, 42, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};

    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxAutoBrewerMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.localization = localization;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        fillFrame(inventory);
        inventory.setItem(BLAZE_SLOT, state.input(0) == null ? null : state.input(0).toItemStack(items));
        inventory.setItem(PROGRESS_SLOT, statusIcons.render(viewerId, definition, state, null, status));
        inventory.setItem(INGREDIENT_SLOT, state.input(1) == null ? null : state.input(1).toItemStack(items));
        inventory.setItem(FUEL_DISPLAY_SLOT, fuelDisplay(state));
        for (int index = 0; index < POTION_SLOTS.length; index++) {
            inventory.setItem(POTION_SLOTS[index], state.input(2 + index) == null ? null : state.input(2 + index).toItemStack(items));
        }
    }

    private void fillFrame(Inventory inventory) {
        inventory.clear();
        SfxInventoryPainter.setSlots(inventory, namedItem(Material.ORANGE_STAINED_GLASS_PANE, localization.component("electric-ui.auto-brewer.blaze.frame", "<gold>Blaze Powder</gold>"), List.of()), ORANGE_SLOTS);
        SfxInventoryPainter.setSlots(inventory, namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of()), GRAY_SLOTS);
        SfxInventoryPainter.setSlots(inventory, namedItem(Material.BLUE_STAINED_GLASS_PANE, localization.component("electric-ui.auto-brewer.ingredient.frame", "<blue>Brewing Ingredient</blue>"), List.of()), BLUE_SLOTS);
        SfxInventoryPainter.setSlots(inventory, namedItem(Material.LIME_STAINED_GLASS_PANE, localization.component("electric-ui.auto-brewer.potion.frame", "<green>Potion Bottles</green>"), List.of()), GREEN_SLOTS);
    }

    private ItemStack fuelDisplay(SfxElectricMachineState state) {
        int stored = Math.max(0, Math.min(SfxAdvancedAutoBrewerRecipeProvider.MAX_BLAZE_FUEL_TICKS, state.specialData()));
        Material material = stored <= 0 ? Material.STONE : Material.MAGMA_BLOCK;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(localization.component("electric-ui.auto-brewer.blaze.name", "<gold>Blaze Powder</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(localization.component(
                    "electric-ui.auto-brewer.blaze.fuel-left",
                    "<gray>Stored blaze powder: </gray><yellow>{fuel}</yellow><gray> / </gray><yellow>{capacity}</yellow><gray> tick(s)</gray>",
                    Map.of("fuel", stored, "capacity", SfxAdvancedAutoBrewerRecipeProvider.MAX_BLAZE_FUEL_TICKS)));
            if (state.hasProgress() && state.activeBaseTicks() > 0) {
                int recipeFuel = Math.max(1, state.activeBaseTicks());
                int powders = (recipeFuel + SfxAdvancedAutoBrewerRecipeProvider.BLAZE_FUEL_TICKS - 1) / SfxAdvancedAutoBrewerRecipeProvider.BLAZE_FUEL_TICKS;
                lore.add(localization.component(
                        "electric-ui.auto-brewer.blaze.recipe-cost",
                        "<gray>This recipe consumes: </gray><yellow>{fuel}</yellow><gray> tick(s), up to </gray><yellow>{powders}</yellow><gray> blaze powder</gray>",
                        Map.of("fuel", recipeFuel, "powders", powders)));
            }
            if (stored <= 0) {
                lore.add(localization.component("electric-ui.auto-brewer.blaze.missing-lore", "<gray>Add blaze powder before brewing.</gray>"));
            }
            meta.lore(lore);
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

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        return SfxUiItems.named(material, name, lore);
    }


}
