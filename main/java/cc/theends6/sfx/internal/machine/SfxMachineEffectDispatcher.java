package cc.theends6.sfx.internal.machine;








@FunctionalInterface
public interface SfxMachineEffectDispatcher {
    SfxMachinePhaseResult apply(String effectName, SfxMachinePhaseContext context);
}
