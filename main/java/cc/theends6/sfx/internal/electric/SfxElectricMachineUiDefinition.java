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
                    new SfxElectricMachineUiFrame(STANDARD_BACKGROUND, new SfxElectricMachineUiItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())),
                    new SfxElectricMachineUiFrame(STANDARD_INPUT, new SfxElectricMachineUiItem(Material.CYAN_STAINED_GLASS_PANE, "<aqua>Input</aqua>", List.of("<gray>Place items here.</gray>"))),
                    new SfxElectricMachineUiFrame(STANDARD_OUTPUT, new SfxElectricMachineUiItem(Material.ORANGE_STAINED_GLASS_PANE, "<gold>Output</gold>", List.of("<gray>Take finished items here.</gray>")))
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
                    List.of(new SfxElectricMachineUiFrame(SIMPLE_IO_BACKGROUND, new SfxElectricMachineUiItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()))));
            case GEO_MINER -> new SfxElectricMachineUiDefinition(54, 4, List.of(
                    new SfxElectricMachineUiFrame(GEO_BORDER, new SfxElectricMachineUiItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())),
                    new SfxElectricMachineUiFrame(GEO_OUTPUT_BORDER, new SfxElectricMachineUiItem(Material.ORANGE_STAINED_GLASS_PANE, "&6输出", List.of("&7从这里取走成品。")))));
            case AUTO_BREWER -> new SfxElectricMachineUiDefinition(54, 13, List.of(
                    new SfxElectricMachineUiFrame(AUTO_BREWER_BLAZE, new SfxElectricMachineUiItem(Material.ORANGE_STAINED_GLASS_PANE, "&6烈焰粉", List.of())),
                    new SfxElectricMachineUiFrame(AUTO_BREWER_GRAY, new SfxElectricMachineUiItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())),
                    new SfxElectricMachineUiFrame(AUTO_BREWER_INGREDIENT, new SfxElectricMachineUiItem(Material.BLUE_STAINED_GLASS_PANE, "&9酿造原料", List.of())),
                    new SfxElectricMachineUiFrame(AUTO_BREWER_POTION, new SfxElectricMachineUiItem(Material.LIME_STAINED_GLASS_PANE, "&a药水瓶", List.of()))),
                    Map.of("auto-brewer.fuel.empty", new SfxElectricMachineUiItem(Material.STONE, "&6烈焰粉", List.of("&7储存烈焰粉：&e{fuel} &7/ &e{capacity} &7tick(s)", "&7添加烈焰粉后才会开始酿造。")),
                            "auto-brewer.fuel.stored", new SfxElectricMachineUiItem(Material.MAGMA_BLOCK, "&6烈焰粉", List.of("&7储存烈焰粉：&e{fuel} &7/ &e{capacity} &7tick(s)", "&7本配方消耗：&e{recipe_fuel} &7tick(s)，最多 &e{powders} &7个烈焰粉"))),
                    Map.of());
            case AUTO_CRAFTER -> new SfxElectricMachineUiDefinition(54, 53,
                    List.of(new SfxElectricMachineUiFrame(AUTO_CRAFTER_BACKGROUND, new SfxElectricMachineUiItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()))),
                    Map.of(
                            "auto-crafter.container.ok", new SfxElectricMachineUiItem(Material.CHEST, "&6附加容器", List.of("&7该机器直接使用下方容器。")),
                            "auto-crafter.container.missing", new SfxElectricMachineUiItem(Material.BARRIER, "&6附加容器", List.of("&7该机器直接使用下方容器。")),
                            "auto-crafter.enabled", new SfxElectricMachineUiItem(Material.BARRIER, "&a配方已启用", List.of("&e左键：切换配方", "&e右键：清除配方", "&7配方：&f{recipe}")),
                            "auto-crafter.disabled", new SfxElectricMachineUiItem(Material.REDSTONE_TORCH, "&c配方已禁用", List.of("&e左键：切换配方", "&e右键：清除配方", "&7配方：&f{recipe}")),
                            "auto-crafter.no-recipe", new SfxElectricMachineUiItem(Material.PAPER, "&e未选择配方", List.of("&7手持目标物品潜行右键机器来配置。")),
                            "auto-crafter.previous", new SfxElectricMachineUiItem(Material.ARROW, "&e上一个配方", List.of("&7配方 {page} / {total}")),
                            "auto-crafter.next", new SfxElectricMachineUiItem(Material.ARROW, "&e下一个配方", List.of("&7配方 {page} / {total}")),
                            "auto-crafter.select", new SfxElectricMachineUiItem(Material.CRAFTING_TABLE, "&a选择配方", List.of("&e点击选择这个配方。"))),
                    Map.of());
            case ASSEMBLER -> new SfxElectricMachineUiDefinition(54, 22,
                    List.of(
                            new SfxElectricMachineUiFrame(ASSEMBLER_BACKGROUND, new SfxElectricMachineUiItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of())),
                            new SfxElectricMachineUiFrame(ASSEMBLER_HEAD_FRAME, new SfxElectricMachineUiItem(Material.ORANGE_STAINED_GLASS_PANE, "&e头部材料", List.of())),
                            new SfxElectricMachineUiFrame(ASSEMBLER_BODY_FRAME, new SfxElectricMachineUiItem(Material.WHITE_STAINED_GLASS_PANE, "&6身体材料", List.of()))
                    ),
                    Map.of(
                            "assembler.enabled", new SfxElectricMachineUiItem(Material.LIME_STAINED_GLASS_PANE, "&a已启用", List.of("&7点击切换装配机状态。")),
                            "assembler.disabled", new SfxElectricMachineUiItem(Material.RED_STAINED_GLASS_PANE, "&c已禁用", List.of("&7此装配机不会开始工作。", "&7点击切换装配机状态。")),
                            "assembler.offset", new SfxElectricMachineUiItem(Material.COMPASS, "&b生成偏移", List.of("&7当前偏移：&e{offset} &7格", "&e左键：&7+0.1", "&e右键：&7-0.1", "&7范围：-10.0 到 10.0")),
                            "assembler.head.display", new SfxElectricMachineUiItem(Material.CARVED_PUMPKIN, "&e头部材料", List.of("&7需求数量：&e{amount}")),
                            "assembler.body.display", new SfxElectricMachineUiItem(Material.IRON_BLOCK, "&6身体材料", List.of("&7需求数量：&e{amount}"))
                    ),
                    Map.of());
            default -> new SfxElectricMachineUiDefinition(style.inventorySize(), 22, List.of());
        };
    }

    SfxElectricMachineUiItem item(String key, SfxElectricMachineUiItem fallback) {
        return items.getOrDefault(key, fallback);
    }

    SfxElectricMachineStatusUiTemplate statusTemplate(String key) {
        return status.get(key);
    }
}
