package cc.theends6.sfx.internal.behavior;

import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistry;
import cc.theends6.sfx.api.block.SfxCyclingBlockDefinition;
import cc.theends6.sfx.api.cargo.SfxCargoNodeDefinition;
import cc.theends6.sfx.api.behavior.SfxAndroidWoodcutterPolicy;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRuleProvider;
import cc.theends6.sfx.api.behavior.SfxAutoBrewerBehaviorProvider;
import cc.theends6.sfx.api.behavior.SfxCargoInputTransferPolicy;
import cc.theends6.sfx.api.behavior.SfxCargoFilterRuleProvider;
import cc.theends6.sfx.api.behavior.SfxElectricMachineProviderKeyPolicy;
import cc.theends6.sfx.api.behavior.SfxElectricMachineProviderFactory;
import cc.theends6.sfx.api.behavior.SfxElectricMachineProviderRegistration;
import cc.theends6.sfx.api.behavior.SfxEnhancedFurnaceFuelPolicy;
import cc.theends6.sfx.api.behavior.SfxEnergyBalanceRuleProvider;
import cc.theends6.sfx.api.behavior.SfxEnergyGeneratorProviderFactory;
import cc.theends6.sfx.api.behavior.SfxEnergyGeneratorProviderRegistration;
import cc.theends6.sfx.api.behavior.SfxEntityDropChancePolicy;
import cc.theends6.sfx.api.behavior.SfxGpsTransmitterInteractionPolicy;
import cc.theends6.sfx.api.behavior.SfxGpsTransmitterStatusViewProvider;
import cc.theends6.sfx.api.behavior.SfxIndustrialMinerTargetPolicy;
import cc.theends6.sfx.api.behavior.SfxLocalizedListPostProcessor;
import cc.theends6.sfx.api.behavior.SfxRadiationRuleProvider;
import cc.theends6.sfx.api.behavior.SfxRadiationSymptomHandler;
import cc.theends6.sfx.api.behavior.SfxRechargeableItemProvider;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetBehaviorProvider;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetRuleProvider;
import cc.theends6.sfx.api.behavior.SfxUtilityRuleProvider;
import cc.theends6.sfx.internal.core.SfxOwnedEntries;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;

public final class DefaultSfxBehaviorRegistry implements SfxBehaviorRegistry, SfxBehaviorRegistrar {
    private final ThreadLocal<String> registrationOwner = ThreadLocal.withInitial(() -> SfxOwnedEntries.CORE_OWNER);
    private final SfxOwnedEntries<SfxEnhancedFurnaceFuelPolicy> enhancedFurnaceFuelPolicies = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxAndroidWoodcutterPolicy> androidWoodcutterPolicies = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxEntityDropChancePolicy> entityDropChancePolicies = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxIndustrialMinerTargetPolicy> industrialMinerTargetPolicies = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxRadiationRuleProvider> radiationRuleProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxCargoInputTransferPolicy> cargoInputTransferPolicies = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxCargoFilterRuleProvider> cargoFilterRuleProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxGpsTransmitterInteractionPolicy> gpsTransmitterInteractionPolicies = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxGpsTransmitterStatusViewProvider> gpsTransmitterStatusViewProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxRadiationSymptomHandler> radiationSymptomHandlers = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxTechnicalGadgetRuleProvider> technicalGadgetRuleProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxTechnicalGadgetBehaviorProvider> technicalGadgetBehaviorProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxRechargeableItemProvider> rechargeableItemProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxEnergyBalanceRuleProvider> energyBalanceRuleProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxAreaMachineRuleProvider> areaMachineRuleProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxUtilityRuleProvider> utilityRuleProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxElectricMachineProviderKeyPolicy> electricMachineProviderKeyPolicies = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxElectricMachineProviderRegistration> electricMachineProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxEnergyGeneratorProviderRegistration> energyGeneratorProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxAutoBrewerBehaviorProvider> autoBrewerBehaviorProviders = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxLocalizedListPostProcessor> localizedListPostProcessors = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxCyclingBlockDefinition> cyclingBlocks = new SfxOwnedEntries<>();
    private final SfxOwnedEntries<SfxCargoNodeDefinition> cargoNodes = new SfxOwnedEntries<>();

    public synchronized void clear() {
        enhancedFurnaceFuelPolicies.clear();
        androidWoodcutterPolicies.clear();
        entityDropChancePolicies.clear();
        industrialMinerTargetPolicies.clear();
        radiationRuleProviders.clear();
        cargoInputTransferPolicies.clear();
        cargoFilterRuleProviders.clear();
        gpsTransmitterInteractionPolicies.clear();
        gpsTransmitterStatusViewProviders.clear();
        radiationSymptomHandlers.clear();
        technicalGadgetRuleProviders.clear();
        technicalGadgetBehaviorProviders.clear();
        rechargeableItemProviders.clear();
        energyBalanceRuleProviders.clear();
        areaMachineRuleProviders.clear();
        utilityRuleProviders.clear();
        electricMachineProviderKeyPolicies.clear();
        electricMachineProviders.clear();
        energyGeneratorProviders.clear();
        autoBrewerBehaviorProviders.clear();
        localizedListPostProcessors.clear();
        cyclingBlocks.clear();
        cargoNodes.clear();
    }

    public synchronized void removeOwner(String owner) {
        enhancedFurnaceFuelPolicies.removeOwner(owner);
        androidWoodcutterPolicies.removeOwner(owner);
        entityDropChancePolicies.removeOwner(owner);
        industrialMinerTargetPolicies.removeOwner(owner);
        radiationRuleProviders.removeOwner(owner);
        cargoInputTransferPolicies.removeOwner(owner);
        cargoFilterRuleProviders.removeOwner(owner);
        gpsTransmitterInteractionPolicies.removeOwner(owner);
        gpsTransmitterStatusViewProviders.removeOwner(owner);
        radiationSymptomHandlers.removeOwner(owner);
        technicalGadgetRuleProviders.removeOwner(owner);
        technicalGadgetBehaviorProviders.removeOwner(owner);
        rechargeableItemProviders.removeOwner(owner);
        energyBalanceRuleProviders.removeOwner(owner);
        areaMachineRuleProviders.removeOwner(owner);
        utilityRuleProviders.removeOwner(owner);
        electricMachineProviderKeyPolicies.removeOwner(owner);
        electricMachineProviders.removeOwner(owner);
        energyGeneratorProviders.removeOwner(owner);
        autoBrewerBehaviorProviders.removeOwner(owner);
        localizedListPostProcessors.removeOwner(owner);
        cyclingBlocks.removeOwner(owner);
        cargoNodes.removeOwner(owner);
    }

    public SfxBehaviorRegistrar registrarFor(String owner) {
        Objects.requireNonNull(owner, "owner");
        return (SfxBehaviorRegistrar) Proxy.newProxyInstance(
                SfxBehaviorRegistrar.class.getClassLoader(),
                new Class<?>[] {SfxBehaviorRegistrar.class},
                (proxy, method, args) -> {
                    String previous = registrationOwner.get();
                    registrationOwner.set(owner);
                    try {
                        return method.invoke(this, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    } finally {
                        registrationOwner.set(previous);
                    }
                });
    }

    private String currentOwner() {
        return registrationOwner.get();
    }

    @Override
    public synchronized void registerEnhancedFurnaceFuelPolicy(SfxEnhancedFurnaceFuelPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Enhanced furnace fuel policy must not be null.");
        }
        enhancedFurnaceFuelPolicies.add(currentOwner(), policy);
    }

    @Override
    public synchronized List<SfxEnhancedFurnaceFuelPolicy> enhancedFurnaceFuelPolicies() {
        return enhancedFurnaceFuelPolicies.values();
    }

    @Override
    public synchronized void registerAndroidWoodcutterPolicy(SfxAndroidWoodcutterPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Android woodcutter policy must not be null.");
        }
        androidWoodcutterPolicies.add(currentOwner(), policy);
    }

    @Override
    public synchronized List<SfxAndroidWoodcutterPolicy> androidWoodcutterPolicies() {
        return androidWoodcutterPolicies.values();
    }

    @Override
    public synchronized void registerEntityDropChancePolicy(SfxEntityDropChancePolicy policy) {
        entityDropChancePolicies.add(currentOwner(), Objects.requireNonNull(policy, "policy"));
    }

    @Override
    public synchronized List<SfxEntityDropChancePolicy> entityDropChancePolicies() {
        return entityDropChancePolicies.values();
    }

    @Override
    public synchronized void registerIndustrialMinerTargetPolicy(SfxIndustrialMinerTargetPolicy policy) {
        industrialMinerTargetPolicies.add(currentOwner(), Objects.requireNonNull(policy, "policy"));
    }

    @Override
    public synchronized List<SfxIndustrialMinerTargetPolicy> industrialMinerTargetPolicies() {
        return industrialMinerTargetPolicies.values();
    }

    @Override
    public synchronized void registerRadiationRuleProvider(SfxRadiationRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Radiation rule provider must not be null.");
        }
        radiationRuleProviders.add(currentOwner(), provider);
    }

    @Override
    public synchronized List<SfxRadiationRuleProvider> radiationRuleProviders() {
        return radiationRuleProviders.values();
    }

    @Override
    public synchronized void registerRadiationSymptomHandler(SfxRadiationSymptomHandler handler) {
        radiationSymptomHandlers.add(currentOwner(), Objects.requireNonNull(handler, "handler"));
    }

    @Override
    public synchronized List<SfxRadiationSymptomHandler> radiationSymptomHandlers() {
        return radiationSymptomHandlers.values();
    }

    @Override
    public synchronized void registerCargoInputTransferPolicy(SfxCargoInputTransferPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Cargo input transfer policy must not be null.");
        }
        cargoInputTransferPolicies.add(currentOwner(), policy);
    }

    @Override
    public synchronized List<SfxCargoInputTransferPolicy> cargoInputTransferPolicies() {
        return cargoInputTransferPolicies.values();
    }

    @Override
    public synchronized void registerCargoFilterRuleProvider(SfxCargoFilterRuleProvider provider) {
        cargoFilterRuleProviders.add(currentOwner(), Objects.requireNonNull(provider, "provider"));
    }

    @Override
    public synchronized List<SfxCargoFilterRuleProvider> cargoFilterRuleProviders() {
        return cargoFilterRuleProviders.values();
    }

    @Override
    public synchronized void registerGpsTransmitterInteractionPolicy(SfxGpsTransmitterInteractionPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("GPS transmitter interaction policy must not be null.");
        }
        gpsTransmitterInteractionPolicies.add(currentOwner(), policy);
    }

    @Override
    public synchronized List<SfxGpsTransmitterInteractionPolicy> gpsTransmitterInteractionPolicies() {
        return gpsTransmitterInteractionPolicies.values();
    }

    @Override
    public synchronized void registerGpsTransmitterStatusViewProvider(SfxGpsTransmitterStatusViewProvider provider) {
        gpsTransmitterStatusViewProviders.add(currentOwner(), Objects.requireNonNull(provider, "provider"));
    }

    @Override
    public synchronized List<SfxGpsTransmitterStatusViewProvider> gpsTransmitterStatusViewProviders() {
        return gpsTransmitterStatusViewProviders.values();
    }

    @Override
    public synchronized void registerTechnicalGadgetRuleProvider(SfxTechnicalGadgetRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Technical gadget rule provider must not be null.");
        }
        technicalGadgetRuleProviders.add(currentOwner(), provider);
    }

    @Override
    public synchronized List<SfxTechnicalGadgetRuleProvider> technicalGadgetRuleProviders() {
        return technicalGadgetRuleProviders.values();
    }

    @Override
    public synchronized void registerTechnicalGadgetBehaviorProvider(SfxTechnicalGadgetBehaviorProvider provider) {
        technicalGadgetBehaviorProviders.add(currentOwner(), Objects.requireNonNull(provider, "provider"));
    }

    @Override
    public synchronized List<SfxTechnicalGadgetBehaviorProvider> technicalGadgetBehaviorProviders() {
        return technicalGadgetBehaviorProviders.values();
    }

    @Override
    public synchronized void registerRechargeableItemProvider(SfxRechargeableItemProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Rechargeable item provider must not be null.");
        }
        rechargeableItemProviders.add(currentOwner(), provider);
    }

    @Override
    public synchronized List<SfxRechargeableItemProvider> rechargeableItemProviders() {
        return rechargeableItemProviders.values();
    }

    @Override
    public synchronized void registerEnergyBalanceRuleProvider(SfxEnergyBalanceRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Energy balance rule provider must not be null.");
        }
        energyBalanceRuleProviders.add(currentOwner(), provider);
    }

    @Override
    public synchronized List<SfxEnergyBalanceRuleProvider> energyBalanceRuleProviders() {
        return energyBalanceRuleProviders.values();
    }

    @Override
    public synchronized void registerAreaMachineRuleProvider(SfxAreaMachineRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Area machine rule provider must not be null.");
        }
        areaMachineRuleProviders.add(currentOwner(), provider);
    }

    @Override
    public synchronized List<SfxAreaMachineRuleProvider> areaMachineRuleProviders() {
        return areaMachineRuleProviders.values();
    }

    @Override
    public synchronized void registerUtilityRuleProvider(SfxUtilityRuleProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Utility rule provider must not be null.");
        }
        utilityRuleProviders.add(currentOwner(), provider);
    }

    @Override
    public synchronized List<SfxUtilityRuleProvider> utilityRuleProviders() {
        return utilityRuleProviders.values();
    }

    @Override
    public synchronized void registerElectricMachineProviderKeyPolicy(SfxElectricMachineProviderKeyPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Electric machine provider key policy must not be null.");
        }
        electricMachineProviderKeyPolicies.add(currentOwner(), policy);
    }

    @Override
    public synchronized List<SfxElectricMachineProviderKeyPolicy> electricMachineProviderKeyPolicies() {
        return electricMachineProviderKeyPolicies.values();
    }

    @Override
    public synchronized void registerElectricMachineProvider(String key, SfxElectricMachineProviderFactory factory) {
        electricMachineProviders.add(currentOwner(), new SfxElectricMachineProviderRegistration(key, factory));
    }

    @Override
    public synchronized List<SfxElectricMachineProviderRegistration> electricMachineProviders() {
        return electricMachineProviders.values();
    }

    @Override
    public synchronized void registerEnergyGeneratorProvider(String key, SfxEnergyGeneratorProviderFactory factory) {
        energyGeneratorProviders.add(currentOwner(), new SfxEnergyGeneratorProviderRegistration(key, factory));
    }

    @Override
    public synchronized List<SfxEnergyGeneratorProviderRegistration> energyGeneratorProviders() {
        return energyGeneratorProviders.values();
    }

    @Override
    public synchronized void registerAutoBrewerBehaviorProvider(SfxAutoBrewerBehaviorProvider provider) {
        autoBrewerBehaviorProviders.add(currentOwner(), Objects.requireNonNull(provider, "provider"));
    }

    @Override
    public synchronized List<SfxAutoBrewerBehaviorProvider> autoBrewerBehaviorProviders() {
        return autoBrewerBehaviorProviders.values();
    }

    @Override
    public synchronized void registerLocalizedListPostProcessor(SfxLocalizedListPostProcessor processor) {
        if (processor == null) {
            throw new IllegalArgumentException("Localized list post processor must not be null.");
        }
        localizedListPostProcessors.add(currentOwner(), processor);
    }

    @Override
    public synchronized List<SfxLocalizedListPostProcessor> localizedListPostProcessors() {
        return localizedListPostProcessors.values();
    }

    @Override
    public synchronized void registerCyclingBlock(SfxCyclingBlockDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (cyclingBlocks.anyMatch(existing -> existing.itemId().equals(definition.itemId()))) {
            throw new IllegalArgumentException("Cycling block already registered: " + definition.itemId());
        }
        cyclingBlocks.add(currentOwner(), definition);
    }

    @Override
    public synchronized List<SfxCyclingBlockDefinition> cyclingBlocks() {
        return cyclingBlocks.values();
    }

    @Override
    public synchronized void registerCargoNode(SfxCargoNodeDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (cargoNodes.anyMatch(existing -> existing.itemId().equals(definition.itemId()))) {
            throw new IllegalArgumentException("Cargo node already registered: " + definition.itemId());
        }
        cargoNodes.add(currentOwner(), definition);
    }

    @Override
    public synchronized List<SfxCargoNodeDefinition> cargoNodes() {
        return cargoNodes.values();
    }
}
