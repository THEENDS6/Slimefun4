package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import java.util.Set;
import java.util.UUID;

record SfxEnergyGridResult(UUID regulatorId, SfxBlockAnchorKey regulatorKey, Set<UUID> members, SfxEnergyGridStatus status) {
}
