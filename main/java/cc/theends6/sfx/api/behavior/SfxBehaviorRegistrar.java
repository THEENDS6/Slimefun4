package cc.theends6.sfx.api.behavior;

public interface SfxBehaviorRegistrar {
    void registerEnhancedFurnaceFuelPolicy(SfxEnhancedFurnaceFuelPolicy policy);

    void registerAndroidWoodcutterPolicy(SfxAndroidWoodcutterPolicy policy);

    void registerRadiationRuleProvider(SfxRadiationRuleProvider provider);

    void registerCargoInputTransferPolicy(SfxCargoInputTransferPolicy policy);

    void registerGpsTransmitterInteractionPolicy(SfxGpsTransmitterInteractionPolicy policy);

    void registerTechnicalGadgetRuleProvider(SfxTechnicalGadgetRuleProvider provider);

    void registerRechargeableItemProvider(SfxRechargeableItemProvider provider);

    void registerEnergyBalanceRuleProvider(SfxEnergyBalanceRuleProvider provider);

    void registerAreaMachineRuleProvider(SfxAreaMachineRuleProvider provider);

    void registerUtilityRuleProvider(SfxUtilityRuleProvider provider);

    void registerElectricSpecialProviderKeyPolicy(SfxElectricSpecialProviderKeyPolicy policy);

    void registerLocalizedListPostProcessor(SfxLocalizedListPostProcessor processor);
}
