package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxCargoFilterRuleProvider {
    SfxCargoFilterRules apply(SfxCargoFilterRuleContext context, SfxCargoFilterRules currentRules);
}
