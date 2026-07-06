package cc.theends6.sfx.api.behavior;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import java.util.Objects;

public record SfxRechargeableItemDefinition(
        String itemId,
        SfxRechargeableItemKind kind,
        int level,
        double capacity,
        double movementValue,
        double useCost,
        int heightLimit,
        boolean hoverSupported,
        double fuelCapacity
) {
    public SfxRechargeableItemDefinition {
        itemId = SfxItemDefinition.normalizeId(itemId);
        kind = Objects.requireNonNull(kind, "kind");
        level = Math.max(0, level);
        capacity = Math.max(0.0D, capacity);
        movementValue = Math.max(0.0D, movementValue);
        useCost = Math.max(0.0D, useCost);
        fuelCapacity = Math.max(0.0D, fuelCapacity);
    }

    public static SfxRechargeableItemDefinition electric(String itemId, SfxRechargeableItemKind kind, int level, double capacity, double movementValue, double useCost, int heightLimit, boolean hoverSupported) {
        if (kind == SfxRechargeableItemKind.FUEL_JETPACK) {
            throw new IllegalArgumentException("Fuel jetpacks must use fuelJetpack().");
        }
        return new SfxRechargeableItemDefinition(itemId, kind, level, capacity, movementValue, useCost, heightLimit, hoverSupported, 0.0D);
    }

    public static SfxRechargeableItemDefinition fuelJetpack(String itemId, int level, double thrust, double fuelUseCost, int heightLimit, boolean hoverSupported, double fuelCapacity) {
        return new SfxRechargeableItemDefinition(itemId, SfxRechargeableItemKind.FUEL_JETPACK, level, 0.0D, thrust, fuelUseCost, heightLimit, hoverSupported, fuelCapacity);
    }
}
