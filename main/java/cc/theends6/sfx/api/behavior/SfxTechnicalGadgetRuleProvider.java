package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxTechnicalGadgetRuleProvider {
    SfxTechnicalGadgetRules apply(SfxTechnicalGadgetRuleContext context, SfxTechnicalGadgetRules currentRules);
}
