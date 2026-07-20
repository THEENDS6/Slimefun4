package cc.theends6.sfx.api.machine.continuous;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

public interface SfxContinuousMachineContext {
    UUID instanceId();
    Location location();
    long elapsedTicks();
    Map<String, Double> variables();
    default String lockedRecipeId() { return null; }
}
