package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

final class BasicExpansionBehaviorModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) {
        context.behaviors().registerEnhancedFurnaceFuelPolicy((value, current) -> SfxBasicExpansionAddon.speedScaledEnhancedFurnaceFuel(context, value, current));
        context.behaviors().registerAndroidWoodcutterPolicy((value, current) -> SfxBasicExpansionAddon.batchReplantBottomLayer(context, value, current));
        context.behaviors().registerEntityDropChancePolicy((value, current) -> SfxBasicExpansionAddon.basicCircuitBoardDropChance(context, value, current));
        context.behaviors().registerIndustrialMinerTargetPolicy((value, current) -> SfxBasicExpansionAddon.industrialMinerTarget(context, value, current));
        context.behaviors().registerRadiationRuleProvider((value, current) -> SfxBasicExpansionAddon.sfxRadiationRules(context, value, current));
        context.behaviors().registerRadiationSymptomHandler(value -> SfxBasicExpansionAddon.radiationSymptoms(context, value));
        context.behaviors().registerCargoInputTransferPolicy((value, current) -> SfxBasicExpansionAddon.advancedInputTransfer(context, value, current));
        context.behaviors().registerGpsTransmitterInteractionPolicy((value, current) -> SfxBasicExpansionAddon.gpsTransmitterInteraction(context, current));
        context.behaviors().registerGpsTransmitterStatusViewProvider((value, current) -> SfxBasicExpansionAddon.gpsTransmitterStatusView(context, value, current));
        context.behaviors().registerTechnicalGadgetRuleProvider((value, current) -> SfxBasicExpansionAddon.technicalGadgetRules(context, value, current));
        context.behaviors().registerTechnicalGadgetBehaviorProvider(new SfxBasicExpansionAddon.BasicTechnicalGadgetBehavior());
        context.behaviors().registerRechargeableItemProvider(() -> SfxBasicExpansionAddon.rechargeableItems(context));
        context.behaviors().registerEnergyBalanceRuleProvider((value, current) -> SfxBasicExpansionAddon.energyBalanceRules(context, value, current));
        context.behaviors().registerAreaMachineRuleProvider((value, current) -> SfxBasicExpansionAddon.areaMachineRules(context, value, current));
        context.behaviors().registerUtilityRuleProvider((value, current) -> SfxBasicExpansionAddon.utilityRules(context, value, current));
        context.behaviors().registerElectricMachineProviderKeyPolicy((value, current) -> SfxBasicExpansionAddon.electricMachineProviderKey(context, value.providerKey(), current));
        context.behaviors().registerAutoBrewerBehaviorProvider(new SfxBasicExpansionAddon.BasicAutoBrewerBehavior());
        context.behaviors().registerLocalizedListPostProcessor((value, current) -> SfxBasicExpansionAddon.localizedList(context, value, current));
    }
}
