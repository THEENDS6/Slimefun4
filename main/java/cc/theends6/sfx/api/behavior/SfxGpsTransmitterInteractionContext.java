package cc.theends6.sfx.api.behavior;

import java.util.UUID;

public record SfxGpsTransmitterInteractionContext(
        String transmitterId,
        UUID ownerId,
        int storedEnergy,
        int requiredEnergy,
        int signalStrength,
        int networkComplexity,
        int ownedTransmitterCount,
        String transmitterName,
        String ownerName,
        String worldName,
        int blockX,
        int blockY,
        int blockZ
) {
}
