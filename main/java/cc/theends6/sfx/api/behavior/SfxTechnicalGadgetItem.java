package cc.theends6.sfx.api.behavior;

public record SfxTechnicalGadgetItem(
        String itemId,
        SfxTechnicalGadgetItemKind kind,
        int level,
        double capacity,
        double movementValue,
        double useCost,
        int heightLimit,
        boolean hoverSupported,
        double fuelCapacity
) {
}
