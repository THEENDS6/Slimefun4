package cc.theends6.sfx.internal.machine;

import java.util.UUID;
import org.bukkit.Location;

public record SfxMachineRuntimeContext(UUID instanceId, Location location, long tick, long nowMillis) {
}
