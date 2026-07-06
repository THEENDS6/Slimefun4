package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxAreaMachineRuleProvider {
    SfxAreaMachineRules apply(SfxAreaMachineRuleContext context, SfxAreaMachineRules currentRules);
}
