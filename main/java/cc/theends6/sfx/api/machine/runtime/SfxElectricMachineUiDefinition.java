package cc.theends6.sfx.api.machine.runtime;

import cc.theends6.sfx.api.machine.runtime.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SfxElectricMachineUiDefinition(
        int inventorySize,
        int statusSlot,
        List<SfxElectricMachineUiFrame> frame,
        Map<String, SfxElectricMachineUiItem> items,
        Map<String, SfxElectricMachineStatusUiTemplate> status,
        Map<Integer, SfxElectricMachineUiSlot> slots
) {
    static final SfxElectricMachineUiDefinition UNDEFINED = new SfxElectricMachineUiDefinition(0, -1, List.of());

    public SfxElectricMachineUiDefinition {
        inventorySize = Math.max(0, inventorySize);
        if (inventorySize == 0) {
            statusSlot = -1;
        }
        frame = frame == null ? List.of() : List.copyOf(frame);
        items = items == null ? Map.of() : Map.copyOf(items);
        status = status == null ? Map.of() : Map.copyOf(status);
        slots = copySlots(slots, inventorySize);
    }

    public SfxElectricMachineUiDefinition(int inventorySize, int statusSlot, List<SfxElectricMachineUiFrame> frame) {
        this(inventorySize, statusSlot, frame, Map.of(), Map.of());
    }

    public SfxElectricMachineUiDefinition(
            int inventorySize,
            int statusSlot,
            List<SfxElectricMachineUiFrame> frame,
            Map<String, SfxElectricMachineUiItem> items,
            Map<String, SfxElectricMachineStatusUiTemplate> status
    ) {
        this(inventorySize, statusSlot, frame, items, status, Map.of());
    }

    public SfxElectricMachineUiItem requiredItem(String key) {
        SfxElectricMachineUiItem item = items.get(key);
        if (item == null) {
            throw new IllegalStateException("Missing compiled UI item: " + key);
        }
        return item;
    }

    public SfxElectricMachineStatusUiTemplate statusTemplate(String key) {
        return status.get(key);
    }

    public SfxElectricMachineStatusUiTemplate requiredStatusTemplate(String key) {
        SfxElectricMachineStatusUiTemplate template = status.get(key);
        if (template == null) {
            throw new IllegalStateException("Missing compiled UI status template: " + key);
        }
        return template;
    }

    public SfxElectricMachineUiSlot slot(int rawSlot) {
        return slots.get(rawSlot);
    }

    public int[] stateSlots(String role) {
        List<SfxElectricMachineUiSlot> matching = new ArrayList<>();
        for (SfxElectricMachineUiSlot slot : slots.values()) {
            if (slot.isRole(role)) {
                matching.add(slot);
            }
        }
        boolean indexed = matching.stream().anyMatch(slot -> slot.stateIndex() != null);
        Comparator<SfxElectricMachineUiSlot> comparator = indexed
                ? Comparator.comparing((SfxElectricMachineUiSlot slot) -> slot.stateIndex() == null ? Integer.MAX_VALUE : slot.stateIndex()).thenComparingInt(SfxElectricMachineUiSlot::slot)
                : Comparator.comparingInt(SfxElectricMachineUiSlot::slot);
        return matching.stream().sorted(comparator).mapToInt(SfxElectricMachineUiSlot::slot).toArray();
    }

    public boolean isRole(int rawSlot, String role) {
        SfxElectricMachineUiSlot slot = slot(rawSlot);
        return slot != null && slot.isRole(role);
    }

    private static Map<Integer, SfxElectricMachineUiSlot> copySlots(Map<Integer, SfxElectricMachineUiSlot> input, int inventorySize) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<Integer, SfxElectricMachineUiSlot> result = new LinkedHashMap<>();
        input.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Integer slot = entry.getKey();
                    SfxElectricMachineUiSlot definition = entry.getValue();
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
}
