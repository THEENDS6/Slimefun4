package cc.theends6.sfx.api.behavior;

import java.util.List;

public interface SfxBehaviorRegistry {
    List<SfxEnhancedFurnaceFuelPolicy> enhancedFurnaceFuelPolicies();

    List<SfxAndroidWoodcutterPolicy> androidWoodcutterPolicies();

    List<SfxRadiationRuleProvider> radiationRuleProviders();

    List<SfxCargoInputTransferPolicy> cargoInputTransferPolicies();

    List<SfxGpsTransmitterInteractionPolicy> gpsTransmitterInteractionPolicies();

    List<SfxGpsTransmitterStatusViewProvider> gpsTransmitterStatusViewProviders();

    List<SfxTechnicalGadgetRuleProvider> technicalGadgetRuleProviders();

    List<SfxTechnicalGadgetBehaviorProvider> technicalGadgetBehaviorProviders();

    List<SfxRechargeableItemProvider> rechargeableItemProviders();

    List<SfxEnergyBalanceRuleProvider> energyBalanceRuleProviders();

    List<SfxAreaMachineRuleProvider> areaMachineRuleProviders();

    List<SfxUtilityRuleProvider> utilityRuleProviders();

    List<SfxElectricSpecialProviderKeyPolicy> electricSpecialProviderKeyPolicies();

    List<SfxLocalizedListPostProcessor> localizedListPostProcessors();
}
