package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachinePipelineGuard;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs one resolved energy grid tick outside the service/listener facade.
 */
final class SfxEnergyGridProcessor {
    private SfxEnergyGridProcessor() {
    }

    static void processGrid(SfxEnergyService service, EnergyRuntimeGrid grid) {
        if (grid == null) {
            return;
        }
        SfxBlockInstanceRecord regulator = service.blockData.findInstance(grid.regulatorId()).orElse(null);
        if (regulator == null || !service.isInstanceChunkLoaded(regulator)) {
            for (UUID memberId : grid.members()) {
                service.nodeGridStatuses.put(memberId, SfxEnergyGridStatus.NO_NETWORK);
            }
            return;
        }
        Map<String, Object> frameworkAttributes = service.energyFrameworkAttributes(grid, regulator, service.definitions.get(regulator.typeId()), service.currentState(regulator.instanceId(), regulator));
        SfxMachineTickContext energyTick = new SfxMachineTickContext(0L, 1L, false);
        if (!SfxMachinePipelineGuard.proceed(service.machineRuntime.runPhase(regulator.typeId(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, grid.regulatorId(), service.toLocation(regulator.anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, frameworkAttributes), frameworkAttributes, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
            service.displayStatus(grid.regulatorKey(), SfxEnergyGridStatus.NO_NETWORK, 0, 0, 0, 0, 0);
            return;
        }
        int available = 0;
        int supply = 0;
        List<SfxEnergyNodeRef> capacitorRefs = new ArrayList<>(grid.capacitors().size());
        List<SfxEnergyNodeRef> generatorRefs = new ArrayList<>(grid.generators().size());
        List<SfxEnergyNodeRef> chargerRefs = new ArrayList<>(grid.chargers().size());
        List<SfxBlockInstanceRecord> electricConsumers = new ArrayList<>(grid.electricConsumers().size());
        List<SfxBlockInstanceRecord> configurableConsumers = new ArrayList<>(grid.configurableConsumers().size());
        List<SfxBlockInstanceRecord> configurableProducers = new ArrayList<>(grid.configurableProducers().size());
        List<UUID> electricConsumerIds = new ArrayList<>(grid.electricConsumers().size());
        List<UUID> configurableConsumerIds = new ArrayList<>(grid.configurableConsumers().size());
        Set<UUID> loadedRuntimeMembers = new LinkedHashSet<>();

        for (SfxBlockInstanceRecord instance : grid.capacitors()) {
            if (!service.isInstanceChunkLoaded(instance)) {
                continue;
            }
            SfxEnergyComponentDefinition definition = service.definitions.get(instance.typeId());
            if (definition == null) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            capacitorRefs.add(new SfxEnergyNodeRef(instance, definition, service.currentState(instance.instanceId(), instance)));
        }
        for (SfxBlockInstanceRecord instance : grid.generators()) {
            if (!service.isInstanceChunkLoaded(instance)) {
                continue;
            }
            SfxEnergyComponentDefinition definition = service.definitions.get(instance.typeId());
            if (definition == null) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            generatorRefs.add(new SfxEnergyNodeRef(instance, definition, service.currentState(instance.instanceId(), instance)));
        }
        for (SfxBlockInstanceRecord instance : grid.chargers()) {
            if (!service.isInstanceChunkLoaded(instance)) {
                continue;
            }
            SfxEnergyComponentDefinition definition = service.definitions.get(instance.typeId());
            if (definition == null) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            chargerRefs.add(new SfxEnergyNodeRef(instance, definition, service.currentState(instance.instanceId(), instance)));
        }
        for (SfxBlockInstanceRecord instance : grid.electricConsumers()) {
            if (!service.isInstanceChunkLoaded(instance)) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            electricConsumers.add(instance);
            electricConsumerIds.add(instance.instanceId());
        }
        for (SfxBlockInstanceRecord instance : grid.configurableConsumers()) {
            if (!service.isInstanceChunkLoaded(instance)) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            configurableConsumers.add(instance);
            configurableConsumerIds.add(instance.instanceId());
        }
        for (SfxBlockInstanceRecord instance : grid.configurableProducers()) {
            if (!service.isInstanceChunkLoaded(instance)) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            configurableProducers.add(instance);
        }

        List<SfxBlockInstanceRecord> configurableRuntimeMachines = service.join(configurableConsumers, configurableProducers);
        int requestedConsumption = service.electricMachines.requestedEnergyConsumption(electricConsumerIds)
                + service.configurableMachines.requestedEnergyConsumption(configurableConsumerIds)
                + service.requestedChargerEnergy(chargerRefs);
        int totalStoredBefore = service.totalStoredEnergy(capacitorRefs, generatorRefs, chargerRefs, electricConsumers)
                + service.configurableMachines.totalStoredEnergy(configurableRuntimeMachines);
        int totalCapacityBefore = service.totalCapacity(capacitorRefs, generatorRefs, chargerRefs, electricConsumers)
                + service.configurableMachines.totalCapacity(configurableRuntimeMachines);
        int totalEffectiveCapacityBefore = totalCapacityBefore + service.hiddenBufferCapacity(service.hiddenStorageBaseCapacity(capacitorRefs, generatorRefs));
        boolean autoPauseEnabled = service.plugin.getConfig().getBoolean("energy.generator-balance.pause-generators-when-grid-full", true);
        int potentialSupply = service.potentialGeneration(generatorRefs, configurableProducers);
        if (autoPauseEnabled) {
            service.applyGeneratorAutoPause(generatorRefs, configurableProducers, totalStoredBefore, totalEffectiveCapacityBefore, potentialSupply, requestedConsumption);
        } else {
            service.autoPausedGenerators.clear();
            for (SfxBlockInstanceRecord producer : configurableProducers) {
                service.configurableMachines.setProducerAutoPaused(producer.instanceId(), false);
            }
        }

        for (SfxEnergyNodeRef generator : generatorRefs) {
            Map<String, Object> generatorFramework = service.energyFrameworkAttributes(grid, generator.instance(), generator.definition(), generator.state());
            if (!SfxMachinePipelineGuard.proceed(service.machineRuntime.runPhase(generator.instance().typeId(), SfxMachinePhase.BEFORE_INPUT, generator.instance().instanceId(), service.toLocation(generator.instance().anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, generatorFramework), generatorFramework, SfxMachinePhase.BEFORE_INPUT.name())) {
                continue;
            }
            if (!SfxMachinePipelineGuard.proceed(service.machineRuntime.runPhase(generator.instance().typeId(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, generator.instance().instanceId(), service.toLocation(generator.instance().anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, generatorFramework), generatorFramework, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
                continue;
            }
            if (generator.state().storedEnergy() > 0) {
                available += generator.state().storedEnergy();
                generator.state().storedEnergy(0);
                service.dirtyNodes.add(generator.instance().instanceId());
            }
            int produced = service.autoPausedGenerators.contains(generator.instance().instanceId()) ? 0 : service.generate(generator.instance(), generator.definition(), generator.state());
            generatorFramework.put("energy.generated", produced);
            SfxMachinePipelineGuard.proceed(service.machineRuntime.runPhase(generator.instance().typeId(), SfxMachinePhase.AFTER_PROGRESS, generator.instance().instanceId(), service.toLocation(generator.instance().anchorKey()), energyTick, null, produced > 0 ? cc.theends6.sfx.internal.machine.SfxMachineStatus.RUNNING : cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, generatorFramework), generatorFramework, SfxMachinePhase.AFTER_PROGRESS.name());
            available += produced;
            supply += produced;
        }

        for (SfxBlockInstanceRecord producer : configurableProducers) {
            int produced = service.configurableMachines.generateProducerEnergy(producer.instanceId());
            if (produced > 0) {
                supply += produced;
            }
            int cached = service.configurableMachines.drainProducerEnergy(producer.instanceId());
            if (cached > 0) {
                available += cached;
            }
        }

        for (SfxBlockInstanceRecord consumer : electricConsumers) {
            if (available <= 0) {
                break;
            }
            int accepted = service.electricMachines.chargeConsumer(consumer.instanceId(), available);
            if (accepted > 0) {
                available -= accepted;
            }
        }
        for (SfxBlockInstanceRecord consumer : configurableConsumers) {
            if (available <= 0) {
                break;
            }
            int accepted = service.configurableMachines.chargeConsumer(consumer.instanceId(), available);
            if (accepted > 0) {
                available -= accepted;
            }
        }

        for (SfxEnergyNodeRef charger : chargerRefs) {
            if (available <= 0) {
                break;
            }
            if (!service.canChargeAnyInput(charger.state())) {
                continue;
            }
            int accepted = Math.max(0, Math.min(available, charger.definition().capacity() - charger.state().storedEnergy()));
            if (accepted > 0) {
                int availableBefore = available;
                int storedBefore = charger.state().storedEnergy();
                charger.state().storedEnergy(charger.state().storedEnergy() + accepted);
                service.dirtyNodes.add(charger.instance().instanceId());
                available -= accepted;
                service.traceChargingBench(charger.definition(), "grid accepted"
                        + " accepted=" + accepted
                        + " availableBefore=" + availableBefore
                        + " availableAfter=" + available
                        + " storedBefore=" + storedBefore
                        + " storedAfter=" + charger.state().storedEnergy()
                        + " capacity=" + charger.definition().capacity()
                        + " energyPerTick=" + charger.definition().energyPerTick());
            }
        }

        for (SfxBlockInstanceRecord consumer : electricConsumers) {
            int remainingDemand = Math.max(0, service.electricMachines.consumerCapacity(consumer.typeId()) - service.electricMachines.consumerStoredEnergy(consumer.instanceId()));
            if (remainingDemand <= 0) {
                continue;
            }
            remainingDemand = service.drainCapacitorsToElectricConsumer(capacitorRefs, service.dirtyNodes, consumer, remainingDemand, true);
            if (remainingDemand > 0) {
                service.drainCapacitorsToElectricConsumer(capacitorRefs, service.dirtyNodes, consumer, remainingDemand, false);
            }
        }
        for (SfxBlockInstanceRecord consumer : configurableConsumers) {
            int remainingDemand = Math.max(0, service.configurableMachines.consumerCapacity(consumer.typeId()) - service.configurableMachines.consumerStoredEnergy(consumer.instanceId()));
            if (remainingDemand <= 0) {
                continue;
            }
            remainingDemand = service.drainCapacitorsToConfigurableConsumer(capacitorRefs, service.dirtyNodes, consumer, remainingDemand, true);
            if (remainingDemand > 0) {
                service.drainCapacitorsToConfigurableConsumer(capacitorRefs, service.dirtyNodes, consumer, remainingDemand, false);
            }
        }

        for (SfxEnergyNodeRef charger : chargerRefs) {
            int remainingDemand = Math.max(0, charger.definition().capacity() - charger.state().storedEnergy());
            if (remainingDemand <= 0 || !service.canChargeAnyInput(charger.state())) {
                continue;
            }
            remainingDemand = service.drainCapacitorsToCharger(capacitorRefs, service.dirtyNodes, charger, remainingDemand, true);
            if (remainingDemand > 0) {
                service.drainCapacitorsToCharger(capacitorRefs, service.dirtyNodes, charger, remainingDemand, false);
            }
        }

        for (SfxEnergyNodeRef charger : chargerRefs) {
            Map<String, Object> chargerFramework = service.energyFrameworkAttributes(grid, charger.instance(), charger.definition(), charger.state());
            int storedBeforeCharge = charger.state().storedEnergy();
            service.tickChargingBench(charger);
            chargerFramework.put("energy.charger.delta", Math.max(0, charger.state().storedEnergy() - storedBeforeCharge));
            if (SfxMachinePipelineGuard.proceed(service.machineRuntime.runPhase(charger.instance().typeId(), SfxMachinePhase.AFTER_PROGRESS, charger.instance().instanceId(), service.toLocation(charger.instance().anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, chargerFramework), chargerFramework, SfxMachinePhase.AFTER_PROGRESS.name())) {
                SfxMachinePipelineGuard.proceed(service.machineRuntime.runPhase(charger.instance().typeId(), SfxMachinePhase.AFTER_OUTPUT, charger.instance().instanceId(), service.toLocation(charger.instance().anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, chargerFramework), chargerFramework, SfxMachinePhase.AFTER_OUTPUT.name());
            }
        }

        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            if (available <= 0) {
                break;
            }
            int stored = capacitor.state().storedEnergy();
            int accepted = Math.max(0, Math.min(available, service.effectiveStorageCapacity(capacitor.definition()) - stored));
            if (accepted > 0) {
                capacitor.state().storedEnergy(stored + accepted);
                service.dirtyNodes.add(capacitor.instance().instanceId());
                available -= accepted;
            }
        }

        for (SfxEnergyNodeRef generator : generatorRefs) {
            if (available <= 0 || generator.definition().capacity() <= 0) {
                continue;
            }
            int stored = generator.state().storedEnergy();
            int accepted = Math.max(0, Math.min(available, service.effectiveStorageCapacity(generator.definition()) - stored));
            if (accepted > 0) {
                generator.state().storedEnergy(stored + accepted);
                service.dirtyNodes.add(generator.instance().instanceId());
                available -= accepted;
            }
        }

        for (SfxBlockInstanceRecord producer : configurableProducers) {
            if (available <= 0) {
                break;
            }
            int accepted = service.configurableMachines.chargeProducer(producer.instanceId(), available);
            if (accepted > 0) {
                available -= accepted;
            }
        }

        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            service.scheduleCapacitorAppearanceUpdate(capacitor);
        }

        service.electricMachines.drainRecentEnergyConsumption(loadedRuntimeMembers);
        service.configurableMachines.drainRecentEnergyConsumption(new ArrayList<>(loadedRuntimeMembers));
        int totalStored = service.totalStoredEnergy(capacitorRefs, generatorRefs, chargerRefs, electricConsumers)
                + service.configurableMachines.totalStoredEnergy(configurableRuntimeMachines);
        int totalCapacity = service.totalCapacity(capacitorRefs, generatorRefs, chargerRefs, electricConsumers)
                + service.configurableMachines.totalCapacity(configurableRuntimeMachines);
        int displayStored = service.displayedEnergy(totalStored, totalCapacity);
        int displaySupply = autoPauseEnabled ? potentialSupply : supply;
        int net = displaySupply - requestedConsumption;
        service.displayStatus(grid.regulatorKey(), SfxEnergyGridStatus.ONLINE, displaySupply, requestedConsumption, net, displayStored, totalCapacity);
        service.refreshOpenSfxEnergyGeneratorSessions();
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            service.machineRuntime.runPhase(capacitor.instance().typeId(), SfxMachinePhase.AFTER_TICK, capacitor.instance().instanceId(), service.toLocation(capacitor.instance().anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, service.energyFrameworkAttributes(grid, capacitor.instance(), capacitor.definition(), capacitor.state()));
        }
        for (SfxEnergyNodeRef generator : generatorRefs) {
            service.machineRuntime.runPhase(generator.instance().typeId(), SfxMachinePhase.AFTER_TICK, generator.instance().instanceId(), service.toLocation(generator.instance().anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, service.energyFrameworkAttributes(grid, generator.instance(), generator.definition(), generator.state()));
        }
        for (SfxEnergyNodeRef charger : chargerRefs) {
            service.machineRuntime.runPhase(charger.instance().typeId(), SfxMachinePhase.AFTER_TICK, charger.instance().instanceId(), service.toLocation(charger.instance().anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, service.energyFrameworkAttributes(grid, charger.instance(), charger.definition(), charger.state()));
        }
        service.machineRuntime.runPhase(regulator.typeId(), SfxMachinePhase.AFTER_TICK, grid.regulatorId(), service.toLocation(regulator.anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, frameworkAttributes);
        service.machineRuntime.runPhase(regulator.typeId(), SfxMachinePhase.ON_COMPLETE, grid.regulatorId(), service.toLocation(regulator.anchorKey()), energyTick, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, frameworkAttributes);
    
    }
}
