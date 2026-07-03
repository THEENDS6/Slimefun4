package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        drawConfiguredBackground(definition, inventory);
        drawRecipePreview(definition, inventory, choice);
        inventory.setItem(CHEST_SLOT, chestItem(definition, status));
        inventory.setItem(ENABLE_SLOT, enabledItem(definition, state.enabled(), state.activeRecipeKey()));
        inventory.setItem(definition.ui().statusSlot(), statusIcons.render(viewerId, definition, state, null, status));
    }

    void renderSelection(SfxElectricMachineDefinition definition, Inventory inventory, List<SfxAutoCrafterRecipeChoice> choices, int index) {
        inventory.clear();
        drawConfiguredBackground(definition, inventory);
        SfxAutoCrafterRecipeChoice choice = choices.isEmpty() ? null : choices.get(Math.max(0, Math.min(index, choices.size() - 1)));
        drawRecipePreview(definition, inventory, choice);
        inventory.setItem(PREVIOUS_SLOT, pageButton(definition, "auto-crafter.previous", index + 1, choices.size()));
        inventory.setItem(SELECT_SLOT, selectItem(definition, choice));
        inventory.setItem(NEXT_SLOT, pageButton(definition, "auto-crafter.next", index + 1, choices.size()));
    }

    private void drawConfiguredBackground(SfxElectricMachineDefinition definition, Inventory inventory) {
        SfxElectricMachineUiPainter.fillConfiguredSlots(definition.ui(), inventory, localization);
    }

    private void drawRecipePreview(SfxElectricMachineDefinition definition, Inventory inventory, SfxAutoCrafterRecipeChoice choice) {
        if (choice == null) {
            inventory.setItem(OUTPUT_SLOT, definition.ui().requiredItem("auto-crafter.no-recipe").toItemStack(localization, Map.of()));
            return;
        }
        ItemStack[] inputs = choice.inputPreview();
        for (int i = 0; i < Math.min(INPUT_GRID.length, inputs.length); i++) {
            inventory.setItem(INPUT_GRID[i], cloneOrNull(inputs[i]));
        }
        inventory.setItem(OUTPUT_SLOT, cloneOrNull(choice.outputPreview()));
    }

    private ItemStack enabledItem(SfxElectricMachineDefinition definition, boolean enabled, String recipeKey) {
        String recipe = recipeKey == null || recipeKey.isBlank() ? localization.text("electric-ui.auto-crafter.recipe.none") : recipeKey;
        return definition.ui().requiredItem(enabled ? "auto-crafter.enabled" : "auto-crafter.disabled").toItemStack(localization, Map.of("recipe", recipe));
    }

    private ItemStack selectItem(SfxElectricMachineDefinition definition, SfxAutoCrafterRecipeChoice choice) {
        ItemStack icon = choice == null
                ? configuredSelectItem(definition)
                : cloneOrNull(choice.outputPreview());
        if (icon == null || icon.getType().isAir()) {
            icon = configuredSelectItem(definition);
        }
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.renderFlexible(localization.requiredText("electric-ui.auto-crafter.select.name")));
            meta.lore(List.of(Text.renderFlexible(localization.requiredText("electric-ui.auto-crafter.select.lore"))));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack configuredSelectItem(SfxElectricMachineDefinition definition) {
        return definition.ui().requiredItem("auto-crafter.select").toItemStack(localization, Map.of());
    }

    private ItemStack pageButton(SfxElectricMachineDefinition definition, String key, int page, int total) {
        return definition.ui().requiredItem(key).toItemStack(localization, Map.of("page", page, "total", Math.max(1, total)));
    }

    private ItemStack chestItem(SfxElectricMachineDefinition definition, SfxElectricMachineRenderStatus status) {
        return definition.ui().requiredItem(status == SfxElectricMachineRenderStatus.NO_TARGET ? "auto-crafter.container.missing" : "auto-crafter.container.ok")
                .toItemStack(localization, Map.of());
    }

    private ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}
