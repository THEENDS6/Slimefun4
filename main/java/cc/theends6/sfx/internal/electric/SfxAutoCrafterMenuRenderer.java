package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.ui.SfxUiItems;
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
        for (SfxElectricMachineUiFrame frame : definition.ui().frame()) {
            for (int slot : frame.slots()) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, frame.item().toItemStack(localization, Map.of()));
                }
            }
        }
    }

    private void drawRecipePreview(SfxElectricMachineDefinition definition, Inventory inventory, SfxAutoCrafterRecipeChoice choice) {
        if (choice == null) {
            SfxElectricMachineUiItem item = definition == null ? null : definition.ui().item("auto-crafter.no-recipe", null);
            inventory.setItem(OUTPUT_SLOT, item == null
                    ? SfxUiItems.named(org.bukkit.Material.PAPER, localization.component("electric-ui.auto-crafter.recipe.none-title", "<yellow>No Recipe Selected</yellow>"), List.of(
                    localization.component("electric-ui.auto-crafter.recipe.configure", "<gray>Sneak-right-click this machine while holding the target item to configure.</gray>")
            ))
                    : item.toItemStack(localization, Map.of()));
            return;
        }
        ItemStack[] inputs = choice.inputPreview();
        for (int i = 0; i < Math.min(INPUT_GRID.length, inputs.length); i++) {
            inventory.setItem(INPUT_GRID[i], cloneOrNull(inputs[i]));
        }
        inventory.setItem(OUTPUT_SLOT, cloneOrNull(choice.outputPreview()));
    }

    private ItemStack enabledItem(SfxElectricMachineDefinition definition, boolean enabled, String recipeKey) {
        String recipe = recipeKey == null || recipeKey.isBlank() ? localization.text("electric-ui.auto-crafter.recipe.none", "None") : recipeKey;
        SfxElectricMachineUiItem item = definition.ui().item(enabled ? "auto-crafter.enabled" : "auto-crafter.disabled", null);
        if (item != null) {
            return item.toItemStack(localization, Map.of("recipe", recipe));
        }
        org.bukkit.Material material = enabled ? org.bukkit.Material.BARRIER : org.bukkit.Material.REDSTONE_TORCH;
        String key = enabled ? "electric-ui.auto-crafter.enabled.name" : "electric-ui.auto-crafter.disabled.name";
        return SfxUiItems.named(material, Text.renderFlexible(localization.requiredText(key)), List.of(
                Text.renderFlexible(localization.requiredText("electric-ui.auto-crafter.enabled.lore")),
                Text.renderFlexible(localization.requiredText("electric-ui.auto-crafter.recipe.clear")),
                Text.renderFlexible(applyPlaceholders(localization.requiredText("electric-ui.auto-crafter.recipe.current"), Map.of("recipe", recipe)))
        ));
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
        return definition.ui().item("auto-crafter.select", new SfxElectricMachineUiItem(org.bukkit.Material.CRAFTING_TABLE, "<green>Select Recipe</green>", List.of("<yellow>Click to select this recipe.</yellow>")))
                .toItemStack(localization, Map.of());
    }

    private ItemStack pageButton(SfxElectricMachineDefinition definition, String key, int page, int total) {
        return definition.ui().item(key, new SfxElectricMachineUiItem(org.bukkit.Material.ARROW, "<yellow>Recipe</yellow>", List.of("<gray>Recipe {page} / {total}</gray>")))
                .toItemStack(localization, Map.of("page", page, "total", Math.max(1, total)));
    }

    private ItemStack chestItem(SfxElectricMachineDefinition definition, SfxElectricMachineRenderStatus status) {
        SfxElectricMachineUiItem configured = definition.ui().item(status == SfxElectricMachineRenderStatus.NO_TARGET ? "auto-crafter.container.missing" : "auto-crafter.container.ok", null);
        if (configured != null) {
            return configured.toItemStack(localization, Map.of());
        }
        org.bukkit.Material material = status == SfxElectricMachineRenderStatus.NO_TARGET ? org.bukkit.Material.BARRIER : org.bukkit.Material.CHEST;
        return SfxUiItems.named(material, Text.renderFlexible(localization.requiredText("electric-ui.auto-crafter.container.name")), List.of(
                Text.renderFlexible(localization.requiredText("electric-ui.auto-crafter.container.lore"))
        ));
    }

    private String applyPlaceholders(String text, Map<String, ?> placeholders) {
        String result = text;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}
