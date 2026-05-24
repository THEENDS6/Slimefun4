package cc.theends6.sfx.internal.machine;


@FunctionalInterface
public interface SfxMachineHook {
    SfxMachinePhaseResult apply(SfxMachinePhaseContext context);
}
