package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxRadiationRuleProvider {
    SfxRadiationRules apply(SfxRadiationRuleContext context, SfxRadiationRules currentRules);
}
