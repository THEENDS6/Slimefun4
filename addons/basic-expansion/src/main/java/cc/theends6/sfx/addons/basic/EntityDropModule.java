package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

final class EntityDropModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) {
        context.behaviors().registerEntityDropChancePolicy(
                (value, current) -> SfxBasicExpansionAddon.basicCircuitBoardDropChance(context, value, current));
    }
}
