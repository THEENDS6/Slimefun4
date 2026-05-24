package cc.theends6.sfx.internal.machine;

/** A named effect or policy hook registered on a machine phase. */
@FunctionalInterface
public interface SfxMachineHook {
    SfxMachinePhaseResult apply(SfxMachinePhaseContext context);
}
