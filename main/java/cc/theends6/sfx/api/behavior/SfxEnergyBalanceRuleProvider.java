package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxEnergyBalanceRuleProvider {
    SfxEnergyBalanceRules apply(SfxEnergyBalanceRuleContext context, SfxEnergyBalanceRules currentRules);
}
