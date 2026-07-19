package cc.theends6.sfx.api.behavior;

import cc.theends6.sfx.api.block.SfxCyclingBlockDefinition;
import cc.theends6.sfx.api.cargo.SfxCargoNodeDefinition;

public interface SfxBehaviorRegistrar {
    void registerEnhancedFurnaceFuelPolicy(SfxEnhancedFurnaceFuelPolicy policy);

    void registerAndroidWoodcutterPolicy(SfxAndroidWoodcutterPolicy policy);

    void registerEntityDropChancePolicy(SfxEntityDropChancePolicy policy);

    void registerRadiationRuleProvider(SfxRadiationRuleProvider provider);

    void registerRadiationSymptomHandler(SfxRadiationSymptomHandler handler);

    void registerCargoInputTransferPolicy(SfxCargoInputTransferPolicy policy);

    void registerGpsTransmitterInteractionPolicy(SfxGpsTransmitterInteractionPolicy policy);

    void registerGpsTransmitterStatusViewProvider(SfxGpsTransmitterStatusViewProvider provider);

    void registerTechnicalGadgetRuleProvider(SfxTechnicalGadgetRuleProvider provider);

    void registerTechnicalGadgetBehaviorProvider(SfxTechnicalGadgetBehaviorProvider provider);

    void registerRechargeableItemProvider(SfxRechargeableItemProvider provider);

    void registerEnergyBalanceRuleProvider(SfxEnergyBalanceRuleProvider provider);

    void registerAreaMachineRuleProvider(SfxAreaMachineRuleProvider provider);

    void registerUtilityRuleProvider(SfxUtilityRuleProvider provider);

    void registerElectricMachineProviderKeyPolicy(SfxElectricMachineProviderKeyPolicy policy);

    void registerElectricMachineProvider(String key, SfxElectricMachineProviderFactory factory);

    void registerEnergyGeneratorProvider(String key, SfxEnergyGeneratorProviderFactory factory);

    void registerAutoBrewerBehaviorProvider(SfxAutoBrewerBehaviorProvider provider);

    void registerLocalizedListPostProcessor(SfxLocalizedListPostProcessor processor);

    void registerCyclingBlock(SfxCyclingBlockDefinition definition);

    void registerCargoNode(SfxCargoNodeDefinition definition);
}
