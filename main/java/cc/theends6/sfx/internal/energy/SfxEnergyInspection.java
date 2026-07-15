package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.energy.runtime.*;

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
