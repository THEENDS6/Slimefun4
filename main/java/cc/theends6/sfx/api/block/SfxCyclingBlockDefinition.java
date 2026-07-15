package cc.theends6.sfx.api.block;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;







public record SfxCyclingBlockDefinition(String itemId, List<Material> materials, int intervalTicks) {
    public SfxCyclingBlockDefinition {
        itemId = Objects.requireNonNull(itemId, "itemId").trim().toLowerCase(Locale.ROOT);
        if (itemId.isEmpty() || !itemId.contains(":")) {
            throw new IllegalArgumentException("Cycling block item id must be namespaced.");
        }
        materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
        if (materials.size() < 2 || materials.stream().anyMatch(material -> material == null || !material.isBlock())) {
            throw new IllegalArgumentException("Cycling blocks require at least two block materials.");
        }
        intervalTicks = Math.max(1, intervalTicks);
    }
}
