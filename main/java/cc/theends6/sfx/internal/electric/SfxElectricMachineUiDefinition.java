package cc.theends6.sfx.internal.electric;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;

record SfxElectricMachineUiDefinition(
        int inventorySize,
        int statusSlot,
        List<SfxElectricMachineUiFrame> frame,
        Map<String, SfxElectricMachineUiItem> items,
        Map<String, SfxElectricMachineStatusUiTemplate> status
) {
    private static final int[] STANDARD_BACKGROUND = {0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 31, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] STANDARD_INPUT = {9, 10, 11, 12, 18, 21, 27, 28, 29, 30};
    private static final int[] STANDARD_OUTPUT = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};
    private static final int[] SIMPLE_IO_BACKGROUND = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int[] GEO_BORDER = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 26, 27, 35, 36, 44, 45, 53};
    private static final int[] GEO_OUTPUT_BORDER = {19, 20, 21, 22, 23, 24, 25, 28, 34, 37, 43, 46, 47, 48, 49, 50, 51, 52};
    private static final int[] AUTO_BREWER_BLAZE = {0, 1, 2, 9, 11, 18, 19, 20};
    private static final int[] AUTO_BREWER_GRAY = {3, 4, 5, 12, 14, 21, 23};
    private static final int[] AUTO_BREWER_INGREDIENT = {6, 7, 8, 15, 17, 24, 25, 26};
    private static final int[] AUTO_BREWER_POTION = {27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 38, 40, 42, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
    private static final int[] AUTO_CRAFTER_BACKGROUND = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 14, 15, 16, 17, 18, 19, 23, 25, 26, 27, 28, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 50, 51, 52};
    private static final int[] ASSEMBLER_BACKGROUND = {0, 2, 3, 4, 5, 6, 8, 12, 14, 21, 23, 30, 32, 39, 40, 41, 45, 46, 47, 48, 49, 50, 51, 52, 53};
    private static final int[] ASSEMBLER_HEAD_FRAME = {9, 10, 11, 18, 20, 27, 29, 36, 37, 38};
    private static final int[] ASSEMBLER_BODY_FRAME = {15, 16, 17, 24, 26, 33, 35, 42, 43, 44};

    static final SfxElectricMachineUiDefinition NONE = new SfxElectricMachineUiDefinition(0, -1, List.of());
    static final SfxElectricMachineUiDefinition STANDARD = new SfxElectricMachineUiDefinition(
            45,
            22,
            List.of(
                    new SfxElectricMachineUiFrame(STANDARD_BACKGROUND, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())),
                    new SfxElectricMachineUiFrame(STANDARD_INPUT, item(Material.CYAN_STAINED_GLASS_PANE, "<aqua>Input</aqua>", List.of("<gray>Place items here.</gray>"))),
                    new SfxElectricMachineUiFrame(STANDARD_OUTPUT, item(Material.ORANGE_STAINED_GLASS_PANE, "<gold>Output</gold>", List.of("<gray>Take finished items here.</gray>")))
            ));

    SfxElectricMachineUiDefinition {
        inventorySize = Math.max(0, inventorySize);
        if (inventorySize == 0) {
            statusSlot = -1;
        }
        frame = frame == null ? List.of() : List.copyOf(frame);
        items = items == null ? Map.of() : Map.copyOf(items);
        status = status == null ? Map.of() : Map.copyOf(status);
    }

    SfxElectricMachineUiDefinition(int inventorySize, int statusSlot, List<SfxElectricMachineUiFrame> frame) {
        this(inventorySize, statusSlot, frame, Map.of(), Map.of());
    }

    static SfxElectricMachineUiDefinition forStyle(SfxElectricMachineMenuStyle style) {
        Objects.requireNonNull(style, "style");
        if (style == SfxElectricMachineMenuStyle.NONE) {
            return NONE;
        }
        if (style == SfxElectricMachineMenuStyle.STANDARD) {
            return STANDARD;
        }
        return switch (style) {
            case SIMPLE_IO -> new SfxElectricMachineUiDefinition(27, 4,
                    List.of(new SfxElectricMachineUiFrame(SIMPLE_IO_BACKGROUND, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()))));
            case GEO_MINER -> new SfxElectricMachineUiDefinition(54, 4, List.of(
                    new SfxElectricMachineUiFrame(GEO_BORDER, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())),
                    new SfxElectricMachineUiFrame(GEO_OUTPUT_BORDER, item(Material.ORANGE_STAINED_GLASS_PANE, "<gold>Output</gold>", List.of("<gray>Take finished items here.</gray>")))));
            case AUTO_BREWER -> new SfxElectricMachineUiDefinition(54, 13, List.of(
                    new SfxElectricMachineUiFrame(AUTO_BREWER_BLAZE, item(Material.ORANGE_STAINED_GLASS_PANE, "<gold>Blaze Powder</gold>", List.of())),
                    new SfxElectricMachineUiFrame(AUTO_BREWER_GRAY, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())),
                    new SfxElectricMachineUiFrame(AUTO_BREWER_INGREDIENT, item(Material.BLUE_STAINED_GLASS_PANE, "<blue>Brewing Ingredient</blue>", List.of())),
                    new SfxElectricMachineUiFrame(AUTO_BREWER_POTION, item(Material.LIME_STAINED_GLASS_PANE, "<green>Potion Bottles</green>", List.of()))),
                    Map.of(
                            "auto-brewer.fuel.empty", item(Material.STONE, "<gold>Blaze Powder</gold>", List.of("<gray>Stored blaze powder: <yellow>{fuel}</yellow><gray> / </gray><yellow>{capacity}</yellow><gray> tick(s)</gray>", "<gray>Add blaze powder before brewing.</gray>")),
                            "auto-brewer.fuel.stored", item(Material.MAGMA_BLOCK, "<gold>Blaze Powder</gold>", List.of("<gray>Stored blaze powder: <yellow>{fuel}</yellow><gray> / </gray><yellow>{capacity}</yellow><gray> tick(s)</gray>", "<gray>Recipe cost: <yellow>{recipe_fuel}</yellow><gray> tick(s), up to </gray><yellow>{powders}</yellow><gray> blaze powder</gray>"))
                    ),
                    Map.of());
            case AUTO_CRAFTER -> new SfxElectricMachineUiDefinition(54, 53,
                    List.of(new SfxElectricMachineUiFrame(AUTO_CRAFTER_BACKGROUND, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()))),
                    Map.of(
                            "auto-crafter.container.ok", item(Material.CHEST, "<gold>Attached Container</gold>", List.of("<gray>This machine uses the container directly below it.</gray>")),
                            "auto-crafter.container.missing", item(Material.BARRIER, "<gold>Attached Container</gold>", List.of("<gray>This machine uses the container directly below it.</gray>")),
                            "auto-crafter.enabled", item(Material.BARRIER, "<green>Recipe Enabled</green>", List.of("<yellow>Left-click: toggle recipe</yellow>", "<yellow>Right-click: clear recipe</yellow>", "<gray>Recipe: </gray><white>{recipe}</white>")),
                            "auto-crafter.disabled", item(Material.REDSTONE_TORCH, "<red>Recipe Disabled</red>", List.of("<yellow>Left-click: toggle recipe</yellow>", "<yellow>Right-click: clear recipe</yellow>", "<gray>Recipe: </gray><white>{recipe}</white>")),
                            "auto-crafter.no-recipe", item(Material.PAPER, "<yellow>No Recipe Selected</yellow>", List.of("<gray>Sneak-right-click this machine while holding the target item to configure.</gray>")),
                            "auto-crafter.previous", item(Material.ARROW, "<yellow>Previous Recipe</yellow>", List.of("<gray>Recipe {page} / {total}</gray>")),
                            "auto-crafter.next", item(Material.ARROW, "<yellow>Next Recipe</yellow>", List.of("<gray>Recipe {page} / {total}</gray>")),
                            "auto-crafter.select", item(Material.CRAFTING_TABLE, "<green>Select Recipe</green>", List.of("<yellow>Click to select this recipe.</yellow>"))
                    ),
                    Map.of());
            case ASSEMBLER -> new SfxElectricMachineUiDefinition(54, 22,
                    List.of(
                            new SfxElectricMachineUiFrame(ASSEMBLER_BACKGROUND, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())),
                            new SfxElectricMachineUiFrame(ASSEMBLER_HEAD_FRAME, item(Material.ORANGE_STAINED_GLASS_PANE, "<yellow>Head Material</yellow>", List.of())),
                            new SfxElectricMachineUiFrame(ASSEMBLER_BODY_FRAME, item(Material.WHITE_STAINED_GLASS_PANE, "<gold>Body Material</gold>", List.of()))
                    ),
                    Map.of(
                            "assembler.enabled", item(Material.LIME_STAINED_GLASS_PANE, "<green>Enabled</green>", List.of("<gray>Click to toggle this assembler.</gray>")),
                            "assembler.disabled", item(Material.RED_STAINED_GLASS_PANE, "<red>Disabled</red>", List.of("<gray>This assembler will not start.</gray>", "<gray>Click to toggle this assembler.</gray>")),
                            "assembler.offset", item(Material.COMPASS, "<aqua>Spawn Offset</aqua>", List.of("<gray>Current offset: <yellow>{offset}</yellow><gray> block(s)</gray>", "<yellow>Left-click: </yellow><gray>+0.1</gray>", "<yellow>Right-click: </yellow><gray>-0.1</gray>", "<gray>Range: -10.0 to 10.0</gray>")),
                            "assembler.head.display", item(Material.CARVED_PUMPKIN, "<yellow>Head Material</yellow>", List.of("<gray>Required amount: <yellow>{amount}</yellow></gray>")),
                            "assembler.body.display", item(Material.IRON_BLOCK, "<gold>Body Material</gold>", List.of("<gray>Required amount: <yellow>{amount}</yellow></gray>"))
                    ),
                    Map.of());
            default -> new SfxElectricMachineUiDefinition(style.inventorySize(), 22, List.of());
        };
    }

    SfxElectricMachineUiItem item(String key, SfxElectricMachineUiItem defaultItem) {
        return items.getOrDefault(key, defaultItem);
    }

    SfxElectricMachineStatusUiTemplate statusTemplate(String key) {
        return status.get(key);
    }

    private static SfxElectricMachineUiItem item(Material material, String name, List<String> lore) {
        return new SfxElectricMachineUiItem(material, name, lore);
    }
}
