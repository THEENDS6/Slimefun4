package cc.theends6.sfx.api.behavior;

import java.util.List;

public interface SfxBehaviorRegistry {
    List<SfxEnhancedFurnaceFuelPolicy> enhancedFurnaceFuelPolicies();

    List<SfxAndroidWoodcutterPolicy> androidWoodcutterPolicies();

    List<SfxRadiationRuleProvider> radiationRuleProviders();

    List<SfxRadiationSymptomHandler> radiationSymptomHandlers();

    List<SfxCargoInputTransferPolicy> cargoInputTransferPolicies();

    List<SfxGpsTransmitterInteractionPolicy> gpsTransmitterInteractionPolicies();

    List<SfxGpsTransmitterStatusViewProvider> gpsTransmitterStatusViewProviders();

    List<SfxTechnicalGadgetRuleProvider> technicalGadgetRuleProviders();

    List<SfxTechnicalGadgetBehaviorProvider> technicalGadgetBehaviorProviders();

    List<SfxRechargeableItemProvider> rechargeableItemProviders();

    List<SfxEnergyBalanceRuleProvider> energyBalanceRuleProviders();

    List<SfxAreaMachineRuleProvider> areaMachineRuleProviders();

    List<SfxUtilityRuleProvider> utilityRuleProviders();

    List<SfxElectricMachineProviderKeyPolicy> electricMachineProviderKeyPolicies();

    List<SfxElectricMachineProviderRegistration> electricMachineProviders();

    List<SfxEnergyGeneratorProviderRegistration> energyGeneratorProviders();

    List<SfxAutoBrewerBehaviorProvider> autoBrewerBehaviorProviders();

    List<SfxLocalizedListPostProcessor> localizedListPostProcessors();
}
