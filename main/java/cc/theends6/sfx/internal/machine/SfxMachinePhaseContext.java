package cc.theends6.sfx.internal.machine;

import java.util.UUID;
import org.bukkit.Location;

/** Immutable execution context exposed to phase hooks. */
public record SfxMachinePhaseContext(
        SfxMachineDefinition definition,
        SfxMachinePhase phase,
        UUID instanceId,
        Location location,
        SfxMachineTickContext tickContext,
        SfxMachineState state,
        SfxMachineStatus currentStatus
) {
    public long currentTick() { return tickContext == null ? 0L : tickContext.currentTick(); }
}
