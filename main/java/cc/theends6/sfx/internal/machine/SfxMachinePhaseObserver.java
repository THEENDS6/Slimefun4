package cc.theends6.sfx.internal.machine;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Observes every machine-framework phase invocation, including legacy phases for definitions that
 * do not yet have a concrete processor. This is intentionally independent from effect hooks: hooks
 * can stop or mutate a pipeline, while observers only audit and route framework activity.
 */
@FunctionalInterface
public interface SfxMachinePhaseObserver {
    void observe(String machineId, SfxMachinePhase phase, UUID instanceId, Location location, SfxMachineStatus status, Map<String, Object> attributes);
}
