package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxUtilityRuleProvider {
    SfxUtilityRules apply(SfxUtilityRuleContext context, SfxUtilityRules currentRules);
}
