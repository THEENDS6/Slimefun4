package cc.theends6.sfx.internal.machine;

import java.util.UUID;
import org.bukkit.Location;

public final class SfxMachineExecution implements AutoCloseable {
    private final SfxMachineRuntimeEngine engine;
    private final UUID instanceId;
    private final String machineId;
    private final Location location;
    private final SfxMachineTickContext context;
    private final long startedNanos = System.nanoTime();
    private SfxMachineStatus status = SfxMachineStatus.IDLE;
    private boolean closed;

    SfxMachineExecution(SfxMachineRuntimeEngine engine, UUID instanceId, String machineId, Location location, SfxMachineTickContext context) {
        this.engine = engine; this.instanceId = instanceId; this.machineId = machineId; this.location = location == null ? null : location.clone(); this.context = context;
    }
    public void status(SfxMachineStatus status) {
        if (status != null) {
            this.status = status;
            engine.runStatusPhase(machineId, instanceId, location, context, null, this.status);
        }
    }
    public SfxMachineStatus status() { return status; }
    @Override public void close() {
        if (closed) return; closed = true; engine.finishTick(instanceId, machineId, location, context, status, System.nanoTime() - startedNanos);
    }
}
