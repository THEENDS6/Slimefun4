package cc.theends6.sfx.internal.electric;

import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

record SfxElectricMachineUiDefinition(int inventorySize, int statusSlot, List<SfxElectricMachineUiFrame> frame) {
    private static final int[] STANDARD_BACKGROUND = {0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 31, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] STANDARD_INPUT = {9, 10, 11, 12, 18, 21, 27, 28, 29, 30};
    private static final int[] STANDARD_OUTPUT = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};

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
    }

    static SfxElectricMachineUiDefinition forStyle(SfxElectricMachineMenuStyle style) {
        Objects.requireNonNull(style, "style");
        if (style == SfxElectricMachineMenuStyle.NONE) {
            return NONE;
        }
        if (style == SfxElectricMachineMenuStyle.STANDARD) {
            return STANDARD;
        }
        return new SfxElectricMachineUiDefinition(style.inventorySize(), 22, List.of());
    }
}
