package cc.theends6.sfx.internal.machine;

import java.util.UUID;
import org.bukkit.Location;

public record SfxMachineRuntimeSnapshot(UUID instanceId, String machineId, SfxMachineStatus status, long lastTick, long lastDurationNanos, Location lastLocation) {}
