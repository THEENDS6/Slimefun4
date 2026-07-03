package cc.theends6.sfx.internal.configurable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record SfxConfigurableMachineUiPanel(
        int inventorySize,
        List<SfxConfigurableMachineUiFrame> frame,
        Map<Integer, SfxConfigurableMachineUiSlot> slots
) {
    SfxConfigurableMachineUiPanel {
        inventorySize = Math.max(0, inventorySize);
        frame = frame == null ? List.of() : List.copyOf(frame);
        slots = copySlots(slots, inventorySize);
    }

    int[] inputSlots() {
        return roleSlots("input");
    }

    int[] outputSlots() {
        return roleSlots("output");
    }

    int firstRoleSlot(String role) {
        int[] slots = roleSlots(role);
        return slots.length == 0 ? -1 : slots[0];
    }

    SfxConfigurableMachineUiSlot slot(int rawSlot) {
        return slots.get(rawSlot);
    }

    boolean isActionSlot(int rawSlot) {
        SfxConfigurableMachineUiSlot slot = slot(rawSlot);
        return slot != null && slot.action() != null;
    }

    int slotByItemSource(String itemSource) {
        for (SfxConfigurableMachineUiSlot slot : slots.values()) {
            if (slot.itemSourceIs(itemSource)) {
                return slot.slot();
            }
        }
        return -1;
    }

    int[] roleSlots(String role) {
        return slots.values().stream()
                .filter(slot -> slot.isRole(role))
                .sorted(Comparator.comparingInt(slot -> slot.stateIndex() == null ? Integer.MAX_VALUE : slot.stateIndex()))
                .mapToInt(SfxConfigurableMachineUiSlot::slot)
                .toArray();
    }

    private static Map<Integer, SfxConfigurableMachineUiSlot> copySlots(Map<Integer, SfxConfigurableMachineUiSlot> input, int inventorySize) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<Integer, SfxConfigurableMachineUiSlot> result = new LinkedHashMap<>();
        input.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Integer slot = entry.getKey();
                    SfxConfigurableMachineUiSlot definition = entry.getValue();
                    if (slot == null || definition == null) {
                        return;
                    }
                    if (slot < 0 || slot >= inventorySize) {
                        throw new IllegalArgumentException("UI slot " + slot + " is outside inventory size " + inventorySize + ".");
                    }
                    if (definition.slot() != slot) {
                        throw new IllegalArgumentException("UI slot key " + slot + " does not match slot payload " + definition.slot() + ".");
                    }
                    result.put(slot, definition);
                });
        return Map.copyOf(result);
    }

    @Override
    public String toString() {
        return "SfxConfigurableMachineUiPanel{inventorySize=" + inventorySize
                + ", inputSlots=" + Arrays.toString(inputSlots())
                + ", outputSlots=" + Arrays.toString(outputSlots()) + '}';
    }
}
