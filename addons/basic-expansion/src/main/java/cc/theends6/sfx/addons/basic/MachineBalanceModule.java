package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

final class MachineBalanceModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) {
        context.behaviors().registerEnhancedFurnaceFuelPolicy(
                (value, current) -> SfxBasicExpansionAddon.speedScaledEnhancedFurnaceFuel(context, value, current));
        context.behaviors().registerAndroidWoodcutterPolicy(
                (value, current) -> SfxBasicExpansionAddon.batchReplantBottomLayer(context, value, current));
        context.behaviors().registerAreaMachineRuleProvider(
                (value, current) -> SfxBasicExpansionAddon.areaMachineRules(context, value, current));
        context.behaviors().registerElectricMachineProviderKeyPolicy(
                (value, current) -> SfxBasicExpansionAddon.electricMachineProviderKey(context, value.providerKey(), current));
        context.behaviors().registerAutoBrewerBehaviorProvider(new SfxBasicExpansionAddon.BasicAutoBrewerBehavior());
        context.behaviors().registerLocalizedListPostProcessor(
                (value, current) -> SfxBasicExpansionAddon.localizedList(context, value, current));
    }
}
