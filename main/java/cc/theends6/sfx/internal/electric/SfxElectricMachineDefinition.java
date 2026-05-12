package cc.theends6.sfx.internal.electric;

import java.util.Objects;
import org.bukkit.Material;

public record SfxElectricMachineDefinition(
        String id,
        String title,
        int speed,
        int energyCapacity,
        int energyConsumptionPerTick,
        Material progressMaterial,
        SfxElectricRecipeProvider recipeProvider
) {
    public SfxElectricMachineDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(progressMaterial, "progressMaterial");
        Objects.requireNonNull(recipeProvider, "recipeProvider");
        speed = Math.max(1, speed);
        energyCapacity = Math.max(0, energyCapacity);
        energyConsumptionPerTick = Math.max(0, energyConsumptionPerTick);
    }
}
