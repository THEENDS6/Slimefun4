package cc.theends6.sfx.api.machine.continuous;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

public record SfxContinuousManualMachine(
        String id,
        Map<String, Double> initialVariables,
        BiConsumer<SfxContinuousMachineContext, Double> playerInput,
        BiConsumer<SfxContinuousMachineContext, Long> decay,
        ToDoubleFunction<SfxContinuousMachineContext> progressPerTick,
        java.util.function.Predicate<SfxContinuousMachineContext> canProgress) {
    public SfxContinuousManualMachine {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Continuous machine id must not be blank");
        initialVariables = Map.copyOf(Objects.requireNonNull(initialVariables, "initialVariables"));
        Objects.requireNonNull(playerInput, "playerInput");
        Objects.requireNonNull(decay, "decay");
        Objects.requireNonNull(progressPerTick, "progressPerTick");
        Objects.requireNonNull(canProgress, "canProgress");
    }
}
