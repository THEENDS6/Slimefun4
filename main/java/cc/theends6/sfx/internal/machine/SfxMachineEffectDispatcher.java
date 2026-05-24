package cc.theends6.sfx.internal.machine;

/**
 * Domain-level dispatcher for declarative machine effects.
 *
 * <p>Special machines should no longer put ad-hoc side effects directly in tick methods. A service
 * exposes its old behavior to the framework by attaching or registering one dispatcher, and the
 * shared runtime executes the declared effect by name inside the fixed phase pipeline.</p>
 */
@FunctionalInterface
public interface SfxMachineEffectDispatcher {
    SfxMachinePhaseResult apply(String effectName, SfxMachinePhaseContext context);
}
