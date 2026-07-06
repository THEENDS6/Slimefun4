package cc.theends6.sfx.internal.behavior;

import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistry;
import cc.theends6.sfx.api.behavior.SfxAndroidWoodcutterPolicy;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRuleProvider;
import cc.theends6.sfx.api.behavior.SfxAutoBrewerBehaviorProvider;
import cc.theends6.sfx.api.behavior.SfxCargoInputTransferPolicy;
import cc.theends6.sfx.api.behavior.SfxElectricSpecialProviderKeyPolicy;
import cc.theends6.sfx.api.behavior.SfxEnhancedFurnaceFuelPolicy;
import cc.theends6.sfx.api.behavior.SfxEnergyBalanceRuleProvider;
import cc.theends6.sfx.api.behavior.SfxGpsTransmitterInteractionPolicy;
import cc.theends6.sfx.api.behavior.SfxGpsTransmitterStatusViewProvider;
import cc.theends6.sfx.api.behavior.SfxLocalizedListPostProcessor;
import cc.theends6.sfx.api.behavior.SfxRadiationRuleProvider;
import cc.theends6.sfx.api.behavior.SfxRechargeableItemProvider;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetBehaviorProvider;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetRuleProvider;
import cc.theends6.sfx.api.behavior.SfxUtilityRuleProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DefaultSfxBehaviorRegistry implements SfxBehaviorRegistry, SfxBehaviorRegistrar {
    private final List<SfxEnhancedFurnaceFuelPolicy> enhancedFurnaceFuelPolicies = new ArrayList<>();
    private final List<SfxAndroidWoodcutterPolicy> androidWoodcutterPolicies = new ArrayList<>();
    private final List<SfxRadiationRuleProvider> radiationRuleProviders = new ArrayList<>();
    private final List<SfxCargoInputTransferPolicy> cargoInputTransferPolicies = new ArrayList<>();
    private final List<SfxGpsTransmitterInteractionPolicy> gpsTransmitterInteractionPolicies = new ArrayList<>();
    private final List<SfxGpsTransmitterStatusViewProvider> gpsTransmitterStatusViewProviders = new ArrayList<>();
    private final List<SfxTechnicalGadgetRuleProvider> technicalGadgetRuleProviders = new ArrayList<>();
    private final List<SfxTechnicalGadgetBehaviorProvider> technicalGadgetBehaviorProviders = new ArrayList<>();
    private final List<SfxRechargeableItemProvider> rechargeableItemProviders = new ArrayList<>();
    private final List<SfxEnergyBalanceRuleProvider> energyBalanceRuleProviders = new ArrayList<>();
    private final List<SfxAreaMachineRuleProvider> areaMachineRuleProviders = new ArrayList<>();
    private final List<SfxUtilityRuleProvider> utilityRuleProviders = new ArrayList<>();
    private final List<SfxElectricSpecialProviderKeyPolicy> electricSpecialProviderKeyPolicies = new ArrayList<>();
    private final List<SfxAutoBrewerBehaviorProvider> autoBrewerBehaviorProviders = new ArrayList<>();
    private final List<SfxLocalizedListPostProcessor> localizedListPostProcessors = new ArrayList<>();

    public synchronized void clear() {
        enhancedFurnaceFuelPolicies.clear();
        androidWoodcutterPolicies.clear();
        radiationRuleProviders.clear();
        cargoInputTransferPolicies.clear();
        gpsTransmitterInteractionPolicies.clear();
        gpsTransmitterStatusViewProviders.clear();
        technicalGadgetRuleProviders.clear();
        technicalGadgetBehaviorProviders.clear();
        rechargeableItemProviders.clear();
        energyBalanceRuleProviders.clear();
        areaMachineRuleProviders.clear();
        utilityRuleProviders.clear();
        electricSpecialProviderKeyPolicies.clear();
        autoBrewerBehaviorProviders.clear();
        localizedListPostProcessors.clear();
    }

    @Override
    public synchronized void registerEnhancedFurnaceFuelPolicy(SfxEnhancedFurnaceFuelPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Enhanced furnace fuel policy must not be null.");
        }
        enhancedFurnaceFuelPolicies.add(policy);
    }

    @Override
    public synchronized List<SfxEnhancedFurnaceFuelPolicy> enhancedFurnaceFuelPolicies() {
        return Collections.unmodifiableList(new ArrayList<>(enhancedFurnaceFuelPolicies));
    }

    @Override
    public synchronized void registerAndroidWoodcutterPolicy(SfxAndroidWoodcutterPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Android woodcutter policy must not be null.");
        }
        androidWoodcutterPolicies.add(policy);
    }

    @Override
    public synchronized List<SfxAndroidWoodcutterPolicy> androidWoodcutterPolicies() {
        return Collections.unmodifiableList(new ArrayList<>(androidWoodcutterPolicies));
    }

    @Override
    public synchronized void registerRadiationRuleProvider(SfxRadiationRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Radiation rule provider must not be null.");
        }
        radiationRuleProviders.add(provider);
    }

    @Override
    public synchronized List<SfxRadiationRuleProvider> radiationRuleProviders() {
        return Collections.unmodifiableList(new ArrayList<>(radiationRuleProviders));
    }

    @Override
    public synchronized void registerCargoInputTransferPolicy(SfxCargoInputTransferPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Cargo input transfer policy must not be null.");
        }
        cargoInputTransferPolicies.add(policy);
    }

    @Override
    public synchronized List<SfxCargoInputTransferPolicy> cargoInputTransferPolicies() {
        return Collections.unmodifiableList(new ArrayList<>(cargoInputTransferPolicies));
    }

    @Override
    public synchronized void registerGpsTransmitterInteractionPolicy(SfxGpsTransmitterInteractionPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("GPS transmitter interaction policy must not be null.");
        }
        gpsTransmitterInteractionPolicies.add(policy);
    }

    @Override
    public synchronized List<SfxGpsTransmitterInteractionPolicy> gpsTransmitterInteractionPolicies() {
        return Collections.unmodifiableList(new ArrayList<>(gpsTransmitterInteractionPolicies));
    }

    @Override
    public synchronized void registerGpsTransmitterStatusViewProvider(SfxGpsTransmitterStatusViewProvider provider) {
        gpsTransmitterStatusViewProviders.add(Objects.requireNonNull(provider, "provider"));
    }

    @Override
    public synchronized List<SfxGpsTransmitterStatusViewProvider> gpsTransmitterStatusViewProviders() {
        return Collections.unmodifiableList(new ArrayList<>(gpsTransmitterStatusViewProviders));
    }

    @Override
    public synchronized void registerTechnicalGadgetRuleProvider(SfxTechnicalGadgetRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Technical gadget rule provider must not be null.");
        }
        technicalGadgetRuleProviders.add(provider);
    }

    @Override
    public synchronized List<SfxTechnicalGadgetRuleProvider> technicalGadgetRuleProviders() {
        return Collections.unmodifiableList(new ArrayList<>(technicalGadgetRuleProviders));
    }

    @Override
    public synchronized void registerTechnicalGadgetBehaviorProvider(SfxTechnicalGadgetBehaviorProvider provider) {
        technicalGadgetBehaviorProviders.add(Objects.requireNonNull(provider, "provider"));
    }

    @Override
    public synchronized List<SfxTechnicalGadgetBehaviorProvider> technicalGadgetBehaviorProviders() {
        return Collections.unmodifiableList(new ArrayList<>(technicalGadgetBehaviorProviders));
    }

    @Override
    public synchronized void registerRechargeableItemProvider(SfxRechargeableItemProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Rechargeable item provider must not be null.");
        }
        rechargeableItemProviders.add(provider);
    }

    @Override
    public synchronized List<SfxRechargeableItemProvider> rechargeableItemProviders() {
        return Collections.unmodifiableList(new ArrayList<>(rechargeableItemProviders));
    }

    @Override
    public synchronized void registerEnergyBalanceRuleProvider(SfxEnergyBalanceRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Energy balance rule provider must not be null.");
        }
        energyBalanceRuleProviders.add(provider);
    }

    @Override
    public synchronized List<SfxEnergyBalanceRuleProvider> energyBalanceRuleProviders() {
        return Collections.unmodifiableList(new ArrayList<>(energyBalanceRuleProviders));
    }

    @Override
    public synchronized void registerAreaMachineRuleProvider(SfxAreaMachineRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Area machine rule provider must not be null.");
        }
        areaMachineRuleProviders.add(provider);
    }

    @Override
    public synchronized List<SfxAreaMachineRuleProvider> areaMachineRuleProviders() {
        return Collections.unmodifiableList(new ArrayList<>(areaMachineRuleProviders));
    }

    @Override
    public synchronized void registerUtilityRuleProvider(SfxUtilityRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Utility rule provider must not be null.");
        }
        utilityRuleProviders.add(provider);
    }

    @Override
    public synchronized List<SfxUtilityRuleProvider> utilityRuleProviders() {
        return Collections.unmodifiableList(new ArrayList<>(utilityRuleProviders));
    }

    @Override
    public synchronized void registerElectricSpecialProviderKeyPolicy(SfxElectricSpecialProviderKeyPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Electric special provider key policy must not be null.");
        }
        electricSpecialProviderKeyPolicies.add(policy);
    }

    @Override
    public synchronized List<SfxElectricSpecialProviderKeyPolicy> electricSpecialProviderKeyPolicies() {
        return Collections.unmodifiableList(new ArrayList<>(electricSpecialProviderKeyPolicies));
    }

    @Override
    public synchronized void registerAutoBrewerBehaviorProvider(SfxAutoBrewerBehaviorProvider provider) {
        autoBrewerBehaviorProviders.add(Objects.requireNonNull(provider, "provider"));
    }

    @Override
    public synchronized List<SfxAutoBrewerBehaviorProvider> autoBrewerBehaviorProviders() {
        return Collections.unmodifiableList(new ArrayList<>(autoBrewerBehaviorProviders));
    }

    @Override
    public synchronized void registerLocalizedListPostProcessor(SfxLocalizedListPostProcessor processor) {
        if (processor == null) {
            throw new IllegalArgumentException("Localized list post processor must not be null.");
        }
        localizedListPostProcessors.add(processor);
    }

    @Override
    public synchronized List<SfxLocalizedListPostProcessor> localizedListPostProcessors() {
        return Collections.unmodifiableList(new ArrayList<>(localizedListPostProcessors));
    }
}
