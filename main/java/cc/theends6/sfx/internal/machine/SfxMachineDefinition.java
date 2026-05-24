package cc.theends6.sfx.internal.machine;

import java.util.List;

public record SfxMachineDefinition(String id, String displayName, SfxMachineCategory category, List<Integer> inputSlots, List<Integer> outputSlots, int statusSlot, int tickInterval) {
    public SfxMachineDefinition(String id, String displayName, List<Integer> inputSlots, List<Integer> outputSlots, int statusSlot, int tickInterval) {
        this(id, displayName, SfxMachineCategory.SPECIAL, inputSlots, outputSlots, statusSlot, tickInterval);
    }
    public SfxMachineDefinition {
        inputSlots = inputSlots == null ? List.of() : List.copyOf(inputSlots);
        outputSlots = outputSlots == null ? List.of() : List.copyOf(outputSlots);
        category = category == null ? SfxMachineCategory.SPECIAL : category;
        tickInterval = Math.max(1, tickInterval);
    }
}
