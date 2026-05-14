package cc.theends6.sfx.internal.electric;

import java.util.Set;
import org.bukkit.Material;

record SfxElectricAssemblerSpec(
        Material headMaterial,
        int headAmount,
        Set<Material> bodyMaterials,
        int bodyAmount
) {
    SfxElectricAssemblerSpec {
        if (headMaterial == null) {
            throw new IllegalArgumentException("Head material cannot be null.");
        }
        if (bodyMaterials == null || bodyMaterials.isEmpty()) {
            throw new IllegalArgumentException("Body materials cannot be empty.");
        }
        headAmount = Math.max(1, headAmount);
        bodyAmount = Math.max(1, bodyAmount);
        bodyMaterials = Set.copyOf(bodyMaterials);
    }

    Material primaryBodyMaterial() {
        return bodyMaterials.contains(Material.SOUL_SAND) ? Material.SOUL_SAND : bodyMaterials.iterator().next();
    }
}
