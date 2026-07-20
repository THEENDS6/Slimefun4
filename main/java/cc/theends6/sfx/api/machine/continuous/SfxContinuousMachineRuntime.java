package cc.theends6.sfx.api.machine.continuous;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

public interface SfxContinuousMachineRuntime {
    Optional<SfxContinuousMachineState> create(String machineId, UUID instanceId, Location location, long currentTick);
    Optional<SfxContinuousMachineState> applyInput(String machineId, SfxContinuousMachineState state,
                                                   double input, long currentTick);
    Optional<SfxContinuousMachineState> applyInput(String machineId, SfxContinuousMachineState state,
                                                   UUID playerId, double input, long currentTick);
    Optional<SfxContinuousMachineState> tick(String machineId, SfxContinuousMachineState state, long currentTick);

    default SfxContinuousMachineState lockRecipe(SfxContinuousMachineState state, String recipeId) {
        if (recipeId == null || recipeId.isBlank()) throw new IllegalArgumentException("recipeId must not be blank");
        return new SfxContinuousMachineState(state.instanceId(), state.location(), state.variables(),
                state.progress(), state.lastTick(), recipeId);
    }

    default SfxContinuousMachineState clearRecipe(SfxContinuousMachineState state) {
        return new SfxContinuousMachineState(state.instanceId(), state.location(), state.variables(),
                0.0D, state.lastTick(), null);
    }
}
