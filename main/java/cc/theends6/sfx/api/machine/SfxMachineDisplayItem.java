package cc.theends6.sfx.api.machine;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record SfxMachineDisplayItem(
        Material material,
        String nameKey,
        List<String> loreKeys,
        Map<String, Object> placeholders,
        boolean glint,
        int progress,
        int capacity,
        String headTextureHash
) {
    public SfxMachineDisplayItem(Material material, String nameKey, List<String> loreKeys,
                                 Map<String, Object> placeholders, boolean glint, int progress, int capacity) {
        this(material, nameKey, loreKeys, placeholders, glint, progress, capacity, null);
    }

    public SfxMachineDisplayItem {
        loreKeys = loreKeys == null ? List.of() : List.copyOf(loreKeys);
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        progress = Math.max(0, progress);
        capacity = Math.max(0, capacity);
        headTextureHash = headTextureHash == null || headTextureHash.isBlank() ? null : headTextureHash.trim();
    }
}
