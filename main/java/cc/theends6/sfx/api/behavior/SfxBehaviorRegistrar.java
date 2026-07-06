package cc.theends6.sfx.api.behavior;

public interface SfxBehaviorRegistrar {
    void registerEnhancedFurnaceFuelPolicy(SfxEnhancedFurnaceFuelPolicy policy);

    void registerAndroidWoodcutterPolicy(SfxAndroidWoodcutterPolicy policy);

    void registerRadiationRuleProvider(SfxRadiationRuleProvider provider);

    void registerCargoInputTransferPolicy(SfxCargoInputTransferPolicy policy);

    void registerGpsTransmitterInteractionPolicy(SfxGpsTransmitterInteractionPolicy policy);

    void registerGpsTransmitterStatusViewProvider(SfxGpsTransmitterStatusViewProvider provider);

    void registerTechnicalGadgetRuleProvider(SfxTechnicalGadgetRuleProvider provider);

    void registerTechnicalGadgetBehaviorProvider(SfxTechnicalGadgetBehaviorProvider provider);

    void registerRechargeableItemProvider(SfxRechargeableItemProvider provider);

    void registerEnergyBalanceRuleProvider(SfxEnergyBalanceRuleProvider provider);

    void registerAreaMachineRuleProvider(SfxAreaMachineRuleProvider provider);

    void registerUtilityRuleProvider(SfxUtilityRuleProvider provider);

    void registerElectricSpecialProviderKeyPolicy(SfxElectricSpecialProviderKeyPolicy policy);

    void registerAutoBrewerBehaviorProvider(SfxAutoBrewerBehaviorProvider provider);

    void registerLocalizedListPostProcessor(SfxLocalizedListPostProcessor processor);
}
