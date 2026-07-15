package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.machine.runtime.*;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

public final class SfxMachineExecution implements AutoCloseable {
    private final SfxMachineRuntimeEngine engine;
    private final UUID instanceId;
    private final String machineId;
    private final Location location;
    private final SfxMachineTickContext context;
    private final SfxMachineState state;
    private final Map<String, Object> attributes;
    private final long startedNanos = System.nanoTime();
    private SfxMachineStatus status = SfxMachineStatus.IDLE;
    private boolean closed;

    SfxMachineExecution(SfxMachineRuntimeEngine engine, UUID instanceId, String machineId, Location location, SfxMachineTickContext context) {
        this(engine, instanceId, machineId, location, context, null, null);
    }

    SfxMachineExecution(SfxMachineRuntimeEngine engine, UUID instanceId, String machineId, Location location, SfxMachineTickContext context, SfxMachineState state, Map<String, Object> attributes) {
        this.engine = engine;
        this.instanceId = instanceId;
        this.machineId = machineId;
        this.location = location == null ? null : location.clone();
        this.context = context;
        this.state = state;
        this.attributes = attributes;
    }

    public void status(SfxMachineStatus status) {
        if (status != null) {
            this.status = status;
            engine.runStatusPhase(machineId, instanceId, location, context, state, this.status, attributes);
        }
    }

    public Map<String, Object> attributes() { return attributes; }
    public SfxMachineStatus status() { return status; }
    @Override public void close() {
        if (closed) return;
        closed = true;
        engine.finishTick(instanceId, machineId, location, context, state, status, System.nanoTime() - startedNanos, attributes);
    }
}
