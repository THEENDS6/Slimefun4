package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

final class RadiationModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) {
        context.behaviors().registerRadiationRuleProvider(
                (value, current) -> SfxBasicExpansionAddon.sfxRadiationRules(context, value, current));
        context.behaviors().registerRadiationSymptomHandler(
                value -> SfxBasicExpansionAddon.radiationSymptoms(context, value));
    }
}
