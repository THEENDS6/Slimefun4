package cc.theends6.sfx.internal.energy;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

record SfxEnergyComponentUiDefinition(
        int inventorySize,
        int statusSlot,
        List<SfxEnergyComponentUiFrame> frame,
        Map<Integer, SfxEnergyComponentUiSlot> slots
) {
    SfxEnergyComponentUiDefinition {
        inventorySize = Math.max(0, inventorySize);
        statusSlot = inventorySize == 0 ? -1 : statusSlot;
        frame = frame == null ? List.of() : List.copyOf(frame);
        slots = slots == null ? Map.of() : Map.copyOf(slots);
    }

    int[] inputSlots() {
        return roleSlots("input");
    }

    int[] outputSlots() {
        return roleSlots("output");
    }

    private int[] roleSlots(String role) {
        return slots.values().stream()
                .filter(slot -> slot.roleIs(role))
                .sorted(Comparator.comparingInt(slot -> slot.stateIndex() == null ? Integer.MAX_VALUE : slot.stateIndex()))
                .mapToInt(SfxEnergyComponentUiSlot::slot)
                .toArray();
    }

    boolean isRole(int rawSlot, String role) {
        SfxEnergyComponentUiSlot slot = slots.get(rawSlot);
        return slot != null && slot.roleIs(role);
    }

    @Override
    public String toString() {
        return "SfxEnergyComponentUiDefinition{inventorySize=" + inventorySize
                + ", statusSlot=" + statusSlot
                + ", inputSlots=" + Arrays.toString(inputSlots())
                + ", outputSlots=" + Arrays.toString(outputSlots()) + '}';
    }
}
