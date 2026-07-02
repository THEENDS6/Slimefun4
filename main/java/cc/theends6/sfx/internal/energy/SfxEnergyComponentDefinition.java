package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

public record SfxEnergyComponentDefinition(
        String id,
        SfxEnergyComponentType componentType,
        int capacity,
        int energyPerTick,
        int nightEnergyPerTick,
        int fuelBurnRateTenths,
        boolean usesVanillaCoalResolver,
        Material progressMaterial,
        List<FuelRule> fuelRules,
        SfxEnergyComponentUiDefinition ui
) {
    public SfxEnergyComponentDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(componentType, "componentType");
        Objects.requireNonNull(progressMaterial, "progressMaterial");
        Objects.requireNonNull(ui, "ui");
        fuelRules = fuelRules == null ? List.of() : List.copyOf(fuelRules);
        capacity = Math.max(0, capacity);
        energyPerTick = Math.max(0, energyPerTick);
        nightEnergyPerTick = Math.max(0, nightEnergyPerTick);
        fuelBurnRateTenths = Math.max(1, fuelBurnRateTenths);
    }

    public boolean expandsNetwork() {
        return componentType == SfxEnergyComponentType.REGULATOR
                || componentType == SfxEnergyComponentType.CONNECTOR
                || componentType == SfxEnergyComponentType.CAPACITOR;
    }

    public boolean isFueledGenerator() {
        return componentType == SfxEnergyComponentType.GENERATOR
                && (usesVanillaCoalResolver || !fuelRules.isEmpty());
    }

    public boolean isCharger() {
        return componentType == SfxEnergyComponentType.CHARGER;
    }

    public boolean isSolarGenerator() {
        return componentType == SfxEnergyComponentType.GENERATOR
                && !usesVanillaCoalResolver
                && fuelRules.isEmpty();
    }

    public record FuelRule(String key, SfxElectricStack input, SfxElectricStack output, int seconds) {
        public FuelRule {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(input, "input");
            seconds = Math.max(1, seconds);
        }
    }
}
