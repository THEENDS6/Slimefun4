package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.ui.SfxInventoryPainter;
import cc.theends6.sfx.internal.ui.SfxUiItems;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class SfxAutoCrafterMenuRenderer {
    static final int CHEST_SLOT = 45;
    static final int PREVIOUS_SLOT = 46;
    static final int ENABLE_SLOT = 49;
    static final int SELECT_SLOT = 49;
    static final int NEXT_SLOT = 52;
    static final int STATUS_SLOT = 53;
    private static final int[] BACKGROUND = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 14, 15, 16, 17,
            18, 19, 23, 25, 26,
            27, 28, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] VIEW_BOTTOM_BACKGROUND = {46, 47, 48, 50, 51, 52};
    private static final int[] SELECT_BOTTOM_BACKGROUND = {45, 47, 48, 50, 51, 53};
    private static final int[] INPUT_GRID = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private static final int OUTPUT_SLOT = 24;

    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxElectricMachineStatusIconRenderer statusIcons;

    SfxAutoCrafterMenuRenderer(SfxItems items, SfxLocalization localization, SfxPlayerDataService profiles) {
        this.items = items;
        this.localization = localization;
        this.statusIcons = new SfxElectricMachineStatusIconRenderer(items, localization, profiles);
    }

    void render(UUID viewerId, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricMachineRenderStatus status, SfxAutoCrafterRecipeChoice choice) {
        inventory.clear();
        drawClassicBackground(inventory, VIEW_BOTTOM_BACKGROUND);
        drawRecipePreview(inventory, choice);
        inventory.setItem(CHEST_SLOT, chestItem(status));
        inventory.setItem(ENABLE_SLOT, enabledItem(state.enabled(), state.activeRecipeKey()));
        inventory.setItem(STATUS_SLOT, statusIcons.render(viewerId, definition, state, null, status));
    }

    void renderSelection(SfxElectricMachineDefinition definition, Inventory inventory, List<SfxAutoCrafterRecipeChoice> choices, int index) {
        inventory.clear();
        drawClassicBackground(inventory, SELECT_BOTTOM_BACKGROUND);
        SfxAutoCrafterRecipeChoice choice = choices.isEmpty() ? null : choices.get(Math.max(0, Math.min(index, choices.size() - 1)));
        drawRecipePreview(inventory, choice);
        inventory.setItem(PREVIOUS_SLOT, pageButton(Material.ARROW, "electric-ui.auto-crafter.previous.name", "<yellow>Previous Recipe</yellow>", index + 1, choices.size()));
        inventory.setItem(SELECT_SLOT, selectItem(choice));
        inventory.setItem(NEXT_SLOT, pageButton(Material.ARROW, "electric-ui.auto-crafter.next.name", "<yellow>Next Recipe</yellow>", index + 1, choices.size()));
    }

    private void drawClassicBackground(Inventory inventory, int[] bottom) {
        ItemStack background = SfxUiItems.named(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        SfxInventoryPainter.setSlots(inventory, background, BACKGROUND);
        SfxInventoryPainter.setSlots(inventory, background, bottom);
    }

    private void drawRecipePreview(Inventory inventory, SfxAutoCrafterRecipeChoice choice) {
        if (choice == null) {
            inventory.setItem(OUTPUT_SLOT, SfxUiItems.named(Material.PAPER, localization.component("electric-ui.auto-crafter.recipe.none-title", "<yellow>No Recipe Selected</yellow>"), List.of(
                    localization.component("electric-ui.auto-crafter.recipe.configure", "<gray>Sneak-right-click this machine while holding the target item to configure.</gray>")
            )));
            return;
        }
        ItemStack[] inputs = choice.inputPreview();
        for (int i = 0; i < Math.min(INPUT_GRID.length, inputs.length); i++) {
            inventory.setItem(INPUT_GRID[i], cloneOrNull(inputs[i]));
        }
        inventory.setItem(OUTPUT_SLOT, cloneOrNull(choice.outputPreview()));
    }

    private ItemStack enabledItem(boolean enabled, String recipeKey) {
        Material material = enabled ? Material.BARRIER : Material.REDSTONE_TORCH;
        String key = enabled ? "electric-ui.auto-crafter.enabled.name" : "electric-ui.auto-crafter.disabled.name";
        String fallback = enabled ? "<green>Recipe Enabled</green>" : "<red>Recipe Disabled</red>";
        return SfxUiItems.named(material, localization.component(key, fallback), List.of(
                localization.component("electric-ui.auto-crafter.enabled.lore", "<yellow>Left-click: toggle recipe.</yellow>"),
                localization.component("electric-ui.auto-crafter.recipe.clear", "<yellow>Right-click: clear recipe</yellow>"),
                localization.component("electric-ui.auto-crafter.recipe.current", "<gray>Recipe: </gray><white>{recipe}</white>", Map.of("recipe", recipeKey == null || recipeKey.isBlank() ? localization.text("electric-ui.auto-crafter.recipe.none", "None") : recipeKey))
        ));
    }

    private ItemStack selectItem(SfxAutoCrafterRecipeChoice choice) {
        ItemStack icon = choice == null ? new ItemStack(Material.CRAFTING_TABLE) : cloneOrNull(choice.outputPreview());
        if (icon == null || icon.getType().isAir()) {
            icon = new ItemStack(Material.CRAFTING_TABLE);
        }
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.displayName(localization.component("electric-ui.auto-crafter.select.name", "<green>Select Recipe</green>"));
            meta.lore(List.of(localization.component("electric-ui.auto-crafter.select.lore", "<yellow>Click to select this recipe.</yellow>")));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack pageButton(Material material, String key, String fallback, int page, int total) {
        return SfxUiItems.named(material, localization.component(key, fallback), List.of(
                localization.component("electric-ui.auto-crafter.page", "<gray>Recipe {page} / {total}</gray>", Map.of("page", page, "total", Math.max(1, total)))
        ));
    }

    private ItemStack chestItem(SfxElectricMachineRenderStatus status) {
        Material material = status == SfxElectricMachineRenderStatus.NO_TARGET ? Material.BARRIER : Material.CHEST;
        return SfxUiItems.named(material, localization.component("electric-ui.auto-crafter.container.name", "<gold>Attached Container</gold>"), List.of(
                localization.component("electric-ui.auto-crafter.container.lore", "<gray>This machine uses the container directly below it.</gray>")
        ));
    }

    private ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}
