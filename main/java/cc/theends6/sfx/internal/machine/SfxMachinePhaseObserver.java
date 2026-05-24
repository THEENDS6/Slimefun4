package cc.theends6.sfx.internal.machine;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;






@FunctionalInterface
public interface SfxMachinePhaseObserver {
    void observe(String machineId, SfxMachinePhase phase, UUID instanceId, Location location, SfxMachineStatus status, Map<String, Object> attributes);
}
