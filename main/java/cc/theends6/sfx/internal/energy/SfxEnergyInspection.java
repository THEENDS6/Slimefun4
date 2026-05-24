package cc.theends6.sfx.internal.energy;

public record SfxEnergyInspection(
        String typeId,
        SfxEnergyComponentType componentType,
        int storedEnergy,
        int capacity,
        int generationPerTick,
        boolean connected,
        boolean autoPaused
) {
}
