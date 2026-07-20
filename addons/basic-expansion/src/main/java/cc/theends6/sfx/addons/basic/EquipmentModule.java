package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

final class EquipmentModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) {
        context.behaviors().registerTechnicalGadgetRuleProvider(
                (value, current) -> SfxBasicExpansionAddon.technicalGadgetRules(context, value, current));
        context.behaviors().registerTechnicalGadgetBehaviorProvider(
                new SfxBasicExpansionAddon.BasicTechnicalGadgetBehavior());
        context.behaviors().registerRechargeableItemProvider(() -> SfxBasicExpansionAddon.rechargeableItems(context));
        context.behaviors().registerUtilityRuleProvider(
                (value, current) -> SfxBasicExpansionAddon.utilityRules(context, value, current));
    }
}
