package cc.theends6.sfx.internal.machine;

import java.util.List;


public record SfxMachineOutputProvider(Kind kind, List<Integer> slots, String description) {
    public enum Kind { NONE, GUI_SLOTS, EXTERNAL_CONTAINER, VANILLA_BLOCK_INVENTORY, WORLD_DROP, PEDESTALS, ENERGY_NETWORK, CUSTOM }
    public SfxMachineOutputProvider {
        kind = kind == null ? Kind.NONE : kind;
        slots = slots == null ? List.of() : List.copyOf(slots);
        description = description == null ? "" : description;
    }
    public static SfxMachineOutputProvider none() { return new SfxMachineOutputProvider(Kind.NONE, List.of(), "none"); }
    public static SfxMachineOutputProvider guiSlots(List<Integer> slots) { return new SfxMachineOutputProvider(Kind.GUI_SLOTS, slots, "gui-slots"); }
    public static SfxMachineOutputProvider externalContainer(String description) { return new SfxMachineOutputProvider(Kind.EXTERNAL_CONTAINER, List.of(), description); }
    public static SfxMachineOutputProvider vanillaBlockInventory(String description) { return new SfxMachineOutputProvider(Kind.VANILLA_BLOCK_INVENTORY, List.of(), description); }
    public static SfxMachineOutputProvider worldDrop(String description) { return new SfxMachineOutputProvider(Kind.WORLD_DROP, List.of(), description); }
    public static SfxMachineOutputProvider energyNetwork(String description) { return new SfxMachineOutputProvider(Kind.ENERGY_NETWORK, List.of(), description); }
    public static SfxMachineOutputProvider custom(String description) { return new SfxMachineOutputProvider(Kind.CUSTOM, List.of(), description); }
}
