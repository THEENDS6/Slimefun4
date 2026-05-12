package cc.theends6.sfx.internal.energy;

import java.util.UUID;
import org.bukkit.inventory.Inventory;

final class SfxEnergyGeneratorSession {
    private final UUID viewerId;
    private final UUID instanceId;
    private final Inventory inventory;

    SfxEnergyGeneratorSession(UUID viewerId, UUID instanceId, Inventory inventory) {
        this.viewerId = viewerId;
        this.instanceId = instanceId;
        this.inventory = inventory;
    }

    UUID viewerId() {
        return viewerId;
    }

    UUID instanceId() {
        return instanceId;
    }

    Inventory inventory() {
        return inventory;
    }
}
