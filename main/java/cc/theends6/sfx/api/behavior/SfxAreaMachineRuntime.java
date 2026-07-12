package cc.theends6.sfx.api.behavior;

import org.bukkit.Location;

public interface SfxAreaMachineRuntime {
    boolean hasOverlappingMachine(Location location, String machineId, int horizontalRadius, int verticalRadius);
}
