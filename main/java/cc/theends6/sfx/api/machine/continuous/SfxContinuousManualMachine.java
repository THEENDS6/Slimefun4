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
        java.util.function.Predicate<SfxContinuousMachineContext> canProgress,
        double maxInputPerTick,
        double maxInputPerPlayerPerTick) {
    public SfxContinuousManualMachine(String id, Map<String, Double> initialVariables,
                                      BiConsumer<SfxContinuousMachineContext, Double> playerInput,
                                      BiConsumer<SfxContinuousMachineContext, Long> decay,
                                      ToDoubleFunction<SfxContinuousMachineContext> progressPerTick,
                                      java.util.function.Predicate<SfxContinuousMachineContext> canProgress) {
        this(id, initialVariables, playerInput, decay, progressPerTick, canProgress,
                Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public SfxContinuousManualMachine {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Continuous machine id must not be blank");
        initialVariables = Map.copyOf(Objects.requireNonNull(initialVariables, "initialVariables"));
        Objects.requireNonNull(playerInput, "playerInput");
        Objects.requireNonNull(decay, "decay");
        Objects.requireNonNull(progressPerTick, "progressPerTick");
        Objects.requireNonNull(canProgress, "canProgress");
        if (!Double.isFinite(maxInputPerTick) || maxInputPerTick <= 0.0D
                || !Double.isFinite(maxInputPerPlayerPerTick) || maxInputPerPlayerPerTick <= 0.0D) {
            throw new IllegalArgumentException("Continuous machine input limits must be finite and positive");
        }
    }
}
