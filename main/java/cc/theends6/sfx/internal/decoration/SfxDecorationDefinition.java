package cc.theends6.sfx.internal.decoration;

import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

public record SfxDecorationDefinition(
        String itemId,
        boolean animated,
        boolean structural,
        List<Material> cycle,
        Material readyMaterial,
        Material activeMaterial,
        Material errorMaterial
) {
    public SfxDecorationDefinition {
        Objects.requireNonNull(itemId, "itemId");
        cycle = cycle == null || cycle.isEmpty() ? List.of(Material.WHITE_WOOL) : List.copyOf(cycle);
        readyMaterial = readyMaterial == null ? cycle.get(0) : readyMaterial;
        activeMaterial = activeMaterial == null ? readyMaterial : activeMaterial;
        errorMaterial = errorMaterial == null ? readyMaterial : errorMaterial;
    }

    public Material materialFor(SfxDecorationState state, long phase) {
        if (animated && !cycle.isEmpty()) {
            int index = Math.floorMod((int) phase, cycle.size());
            return cycle.get(index);
        }
        return switch (state == null ? SfxDecorationState.DEFAULT : state) {
            case READY -> readyMaterial;
            case ACTIVE -> activeMaterial;
            case ERROR -> errorMaterial;
            case DEFAULT -> cycle.get(0);
        };
    }
}
