package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

final class IndustrialMinerModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) {
        context.behaviors().registerIndustrialMinerTargetPolicy(
                (value, current) -> SfxBasicExpansionAddon.industrialMinerTarget(context, value, current));
    }
}
