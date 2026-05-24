package cc.theends6.sfx.internal.machine;

import java.util.List;

/** Declarative input source. Concrete services may still provide the backing inventory. */
public record SfxMachineInputProvider(Kind kind, List<Integer> slots, String description) {
    public enum Kind { NONE, GUI_SLOTS, EXTERNAL_CONTAINER, VANILLA_BLOCK_INVENTORY, HAND, PEDESTALS, WORLD, CUSTOM }
    public SfxMachineInputProvider {
        kind = kind == null ? Kind.NONE : kind;
        slots = slots == null ? List.of() : List.copyOf(slots);
        description = description == null ? "" : description;
    }
    public static SfxMachineInputProvider none() { return new SfxMachineInputProvider(Kind.NONE, List.of(), "none"); }
    public static SfxMachineInputProvider guiSlots(List<Integer> slots) { return new SfxMachineInputProvider(Kind.GUI_SLOTS, slots, "gui-slots"); }
    public static SfxMachineInputProvider externalContainer(String description) { return new SfxMachineInputProvider(Kind.EXTERNAL_CONTAINER, List.of(), description); }
    public static SfxMachineInputProvider vanillaBlockInventory(String description) { return new SfxMachineInputProvider(Kind.VANILLA_BLOCK_INVENTORY, List.of(), description); }
    public static SfxMachineInputProvider hand(String description) { return new SfxMachineInputProvider(Kind.HAND, List.of(), description); }
    public static SfxMachineInputProvider pedestals(String description) { return new SfxMachineInputProvider(Kind.PEDESTALS, List.of(), description); }
    public static SfxMachineInputProvider custom(String description) { return new SfxMachineInputProvider(Kind.CUSTOM, List.of(), description); }
}
