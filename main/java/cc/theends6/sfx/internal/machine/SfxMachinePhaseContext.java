package cc.theends6.sfx.internal.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Execution context exposed to phase hooks.
 *
 * <p>The context intentionally carries a small mutable attachment map. Domain services use it to
 * expose their legacy state/session objects to framework phase effects while the framework remains
 * the only place that decides hook ordering and pipeline control. New framework-native processors
 * should gradually replace these attachments with typed state records.</p>
 */
public final class SfxMachinePhaseContext {
    private final SfxMachineDefinition definition;
    private final SfxMachinePhase phase;
    private final UUID instanceId;
    private final Location location;
    private final SfxMachineTickContext tickContext;
    private final SfxMachineState state;
    private final SfxMachineStatus currentStatus;
    private final Map<String, Object> attachments;

    public SfxMachinePhaseContext(
            SfxMachineDefinition definition,
            SfxMachinePhase phase,
            UUID instanceId,
            Location location,
            SfxMachineTickContext tickContext,
            SfxMachineState state,
            SfxMachineStatus currentStatus
    ) {
        this(definition, phase, instanceId, location, tickContext, state, currentStatus, null);
    }

    public SfxMachinePhaseContext(
            SfxMachineDefinition definition,
            SfxMachinePhase phase,
            UUID instanceId,
            Location location,
            SfxMachineTickContext tickContext,
            SfxMachineState state,
            SfxMachineStatus currentStatus,
            Map<String, Object> attachments
    ) {
        this.definition = definition;
        this.phase = phase;
        this.instanceId = instanceId;
        this.location = location == null ? null : location.clone();
        this.tickContext = tickContext;
        this.state = state;
        this.currentStatus = currentStatus;
        this.attachments = attachments == null ? new HashMap<>() : attachments;
    }

    public SfxMachineDefinition definition() { return definition; }
    public SfxMachinePhase phase() { return phase; }
    public UUID instanceId() { return instanceId; }
    public Location location() { return location == null ? null : location.clone(); }
    public SfxMachineTickContext tickContext() { return tickContext; }
    public SfxMachineState state() { return state; }
    public SfxMachineStatus currentStatus() { return currentStatus; }
    public long currentTick() { return tickContext == null ? 0L : tickContext.currentTick(); }

    public Map<String, Object> attachments() { return attachments; }

    public void put(String key, Object value) {
        if (key == null || key.isBlank()) return;
        if (value == null) attachments.remove(key); else attachments.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> attachment(String key, Class<T> type) {
        if (key == null || type == null) return Optional.empty();
        Object value = attachments.get(key);
        return type.isInstance(value) ? Optional.of((T) value) : Optional.empty();
    }

    public Object attachment(String key) {
        return key == null ? null : attachments.get(key);
    }
}
