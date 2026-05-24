package cc.theends6.sfx.internal.machine;

import java.util.Objects;

/** Declarative wrapper for a phase hook. */
public record SfxMachineEffect(String name, SfxMachinePhase phase, SfxMachineHook hook) {
    public SfxMachineEffect {
        name = name == null || name.isBlank() ? "unnamed-effect" : name;
        phase = phase == null ? SfxMachinePhase.AFTER_TICK : phase;
        hook = Objects.requireNonNull(hook, "hook");
    }

    public static SfxMachineEffect marker(String name, SfxMachinePhase phase) {
        return new SfxMachineEffect(name, phase, context -> SfxMachinePhaseResult.cont());
    }
}
