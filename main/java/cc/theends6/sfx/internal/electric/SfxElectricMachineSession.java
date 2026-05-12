package cc.theends6.sfx.internal.electric;

import java.util.UUID;
import org.bukkit.inventory.Inventory;

final class SfxElectricMachineSession {
    private final UUID viewerId;
    private final UUID instanceId;
    private final Inventory inventory;
    private long lastRenderedTick = Long.MIN_VALUE;
    private SfxElectricMachineRenderStatus lastRenderedStatus;

    SfxElectricMachineSession(UUID viewerId, UUID instanceId, Inventory inventory) {
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

    long lastRenderedTick() {
        return lastRenderedTick;
    }

    SfxElectricMachineRenderStatus lastRenderedStatus() {
        return lastRenderedStatus;
    }

    void markRendered(long tick, SfxElectricMachineRenderStatus status) {
        this.lastRenderedTick = tick;
        this.lastRenderedStatus = status;
    }
}
