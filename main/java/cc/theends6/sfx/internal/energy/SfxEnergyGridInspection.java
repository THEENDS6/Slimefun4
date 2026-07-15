package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;

public record SfxEnergyGridInspection(
        SfxBlockAnchorKey regulatorKey,
        SfxEnergyGridStatus status,
        int members,
        int generators,
        int reactors,
        int capacitors,
        int connectors,
        int consumers,
        int storedEnergy,
        int capacity,
        int generationPerTick,
        int consumptionPerTick,
        int autoPaused
) {
}
