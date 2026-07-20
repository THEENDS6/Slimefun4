package cc.theends6.sfx.api.machine.continuous;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

public record SfxContinuousMachineState(UUID instanceId, Location location, Map<String, Double> variables,
                                        double progress, long lastTick, String lockedRecipeId) {
    public SfxContinuousMachineState {
        if (instanceId == null || location == null || location.getWorld() == null) throw new IllegalArgumentException("instance and world location are required");
        location = location.clone();
        variables = Map.copyOf(variables);
        if (!Double.isFinite(progress) || progress < 0.0D) throw new IllegalArgumentException("progress must be finite and non-negative");
    }
    @Override public Location location() { return location.clone(); }
}
