package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

final class GeneratorModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) {
        context.behaviors().registerEnergyBalanceRuleProvider(
                (value, current) -> SfxBasicExpansionAddon.energyBalanceRules(context, value, current));
    }
}
