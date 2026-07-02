package cc.theends6.sfx.internal.electric;

import java.util.List;
import java.util.Map;

record SfxElectricMachineUiDefinition(
        int inventorySize,
        int statusSlot,
        List<SfxElectricMachineUiFrame> frame,
        Map<String, SfxElectricMachineUiItem> items,
        Map<String, SfxElectricMachineStatusUiTemplate> status
) {
    static final SfxElectricMachineUiDefinition UNDEFINED = new SfxElectricMachineUiDefinition(0, -1, List.of());

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

    SfxElectricMachineUiItem item(String key, SfxElectricMachineUiItem defaultItem) {
        return items.getOrDefault(key, defaultItem);
    }

    SfxElectricMachineStatusUiTemplate statusTemplate(String key) {
        return status.get(key);
    }
}
