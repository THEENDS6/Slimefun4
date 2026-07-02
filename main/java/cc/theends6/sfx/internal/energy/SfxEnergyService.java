package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxAnchoredInteraction;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.network.SfxNetworkDomain;
import cc.theends6.sfx.internal.network.SfxNetworkExecution;
import cc.theends6.sfx.internal.network.SfxNetworkReadiness;
import cc.theends6.sfx.internal.technical.SfxRechargeableItemService;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseResult;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import cc.theends6.sfx.internal.machine.SfxMachineLegacyHookBridge;
import cc.theends6.sfx.internal.topology.SfxTopologyComponent;
import cc.theends6.sfx.internal.topology.SfxTopologyService;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxEventGuards;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxEnergyService implements Listener {
    private static final int RANGE = 6;
    private static final long FLUSH_INTERVAL = 20L;

    final JavaPlugin plugin;
    final SfxRuntime runtime;
    final SfxItems items;
    final SfxLocalization localization;
    final SfxBlockDataService blockData;
    final SfxElectricMachineService electricMachines;
    final SfxConfigurableMachineService configurableMachines;
    private final SfxEnergyDisplayController displayController;
    private final SfxCapacitorAppearanceProjector capacitorProjector;
    private final SfxEnergyBackedElectricMenuService electricEnergyMenus;
    private final SfxRechargeableItemService rechargeableItems;
    final SfxMachineRuntimeEngine machineRuntime;
    private final SfxTopologyService topology;
    final Map<String, SfxEnergyComponentDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, SfxEnergyNodeState> nodeStates = new ConcurrentHashMap<>();
    final Map<UUID, SfxEnergyGridStatus> nodeGridStatuses = new ConcurrentHashMap<>();
    final Set<UUID> dirtyNodes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeNodes = ConcurrentHashMap.newKeySet();
    final Set<UUID> autoPausedGenerators = ConcurrentHashMap.newKeySet();
    private final Map<UUID, EnergyRuntimeGrid> runtimeGrids = new ConcurrentHashMap<>();
    private volatile long nodeGridStatusTopologyRevision = Long.MIN_VALUE;
    private volatile SfxFuelBurnTimeBridge fuelBurnTimeBridge;
    private volatile boolean running;

    public SfxEnergyService(
            JavaPlugin plugin,
            SfxRuntime runtime,
            SfxItems items,
            SfxLocalization localization,
            SfxBlockDataService blockData,
            SfxElectricMachineService electricMachines,
            SfxConfigurableMachineService configurableMachines,
            SfxFloatingTextDisplayService floatingTextDisplay,
            SfxRechargeableItemService rechargeableItems,
            SfxMachineRuntimeEngine machineRuntime
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.configurableMachines = Objects.requireNonNull(configurableMachines, "configurableMachines");
        this.rechargeableItems = Objects.requireNonNull(rechargeableItems, "rechargeableItems");
        this.machineRuntime = machineRuntime == null ? new SfxMachineRuntimeEngine() : machineRuntime;
        registerFrameworkEffects();
        this.displayController = new SfxEnergyDisplayController(plugin, localization, Objects.requireNonNull(floatingTextDisplay, "floatingTextDisplay"));
        this.capacitorProjector = new SfxCapacitorAppearanceProjector(runtime, blockData, definitions);
        this.definitions.putAll(SfxEnergyDefinitions.create(plugin));
        this.electricEnergyMenus = new SfxEnergyBackedElectricMenuService(this, rechargeableItems);
        this.topology = new SfxTopologyService(
                blockData,
                new SfxEnergyTopologyPolicy(definitions, electricMachines, configurableMachines),
                new SfxEnergyConnectivityPolicy(RANGE));
        bootstrapLoadedStates();
        topology.rebuild();
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        topology.rebuild();
        scheduleTick();
        scheduleFlush();
    }

    public Listener electricMenuListener() {
        return electricEnergyMenus;
    }

    private void registerFrameworkEffects() {
        for (String effectName : java.util.List.of(
                "energy:inspect-grid",
                "generator:check-world-condition",
                "generator:consume-fuel",
                "generator:emit-energy",
                "charge:write-item-energy"
        )) {
            machineRuntime.registerEffectHook(effectName, context -> frameworkEnergyEffect(effectName, context));
        }
    }

    public SfxMachinePhaseResult frameworkEffect(String effectName, cc.theends6.sfx.internal.machine.SfxMachinePhaseContext context) {
        return frameworkEnergyEffect(effectName, context);
    }

    private SfxMachinePhaseResult frameworkEnergyEffect(String effectName, cc.theends6.sfx.internal.machine.SfxMachinePhaseContext context) {
        if (context == null) return SfxMachinePhaseResult.cont();
        context.put("energy.framework.effect", effectName);
        context.put("energy.framework.effect.handled", Boolean.TRUE);
        EnergyRuntimeGrid grid = context.attachment("energy.grid", EnergyRuntimeGrid.class).orElse(null);
        SfxBlockInstanceRecord instance = context.attachment("energy.instance", SfxBlockInstanceRecord.class).orElse(null);
        SfxEnergyComponentDefinition definition = context.attachment("energy.definition", SfxEnergyComponentDefinition.class).orElse(null);
        SfxEnergyNodeState state = context.attachment("energy.state", SfxEnergyNodeState.class).orElse(null);
        if (grid != null) {
            context.put("energy.framework.grid.members", grid.members().size());
            context.put("energy.framework.grid.regulator", grid.regulatorId());
        }
        if (instance != null) {
            context.put("energy.framework.instance", instance.instanceId());
        }
        if (definition != null) {
            context.put("energy.framework.component-type", definition.componentType().name());
            context.put("energy.framework.capacity", definition.capacity());
        }
        if (state != null) {
            context.put("energy.framework.stored", state.storedEnergy());
        }
        return SfxMachinePhaseResult.cont();
    }

    Map<String, Object> energyFrameworkAttributes(EnergyRuntimeGrid grid, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("energy.grid", grid);
        attributes.put("energy.instance", instance);
        attributes.put("energy.definition", definition);
        attributes.put("energy.state", state);
        attributes.put("energy.service", this);
        attributes.put("framework.effect.dispatcher", (cc.theends6.sfx.internal.machine.SfxMachineEffectDispatcher) this::frameworkEnergyEffect);
        return attributes;
    }

    public boolean supportsType(String typeId) {
        return definitions.containsKey(typeId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            SfxEnergyComponentDefinition definition = definitions.get(marker.itemId());
            if (definition == null) {
                return;
            }
            int priorityDistance = definition.componentType() == SfxEnergyComponentType.CAPACITOR
                    ? capacitorPriorityDistanceForPlacement(event.getBlockPlaced().getLocation())
                    : SfxBlockInstanceRecord.DEFAULT_ENERGY_PRIORITY_DISTANCE;
            UUID instanceId = blockData.findAnchor(event.getBlockPlaced().getLocation())
                    .map(SfxAnchorRecord::instanceId)
                    .orElseGet(() -> blockData.registerSingleBlock(
                            marker.itemId(),
                            event.getBlockPlaced().getLocation(),
                            event.getBlockPlaced().getType(),
                            event.getPlayer().getUniqueId(),
                            priorityDistance));
            if (definition.componentType() == SfxEnergyComponentType.CAPACITOR) {
                blockData.updateEnergyPriorityDistance(instanceId, priorityDistance);
            }
            nodeStates.putIfAbsent(instanceId, SfxEnergyNodeState.empty());
            SfxMachineLegacyHookBridge.place(machineRuntime, marker.itemId(), instanceId, event.getBlockPlaced().getLocation(), "energy", "SfxEnergyService.onPlace");
            activeNodes.add(instanceId);
            dirtyNodes.add(instanceId);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        SfxAnchoredInteraction interaction = SfxAnchoredInteraction.resolve(event, blockData);
        if (interaction == null) {
            return;
        }
        SfxBlockInstanceRecord instance = interaction.instance();
        SfxMachineLegacyHookBridge.interact(machineRuntime, instance.typeId(), instance.instanceId(), interaction.block().getLocation(), "energy", "SfxEnergyService.onInteract");
        SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        if (definition.componentType() == SfxEnergyComponentType.CONNECTOR || definition.componentType() == SfxEnergyComponentType.CAPACITOR) {
            
            
            return;
        }
        if (SfxInteractionRules.prefersBlockPlacement(items, event)) {
            return;
        }
        SfxEventGuards.denyBlockAndItemUse(event);
        if (definition.isFueledGenerator() || definition.isCharger()) {
            runtime.executeForPlayer(event.getPlayer(), () -> electricEnergyMenus.open(event.getPlayer(), instance, definition));
            return;
        }
        if (definition.componentType() == SfxEnergyComponentType.REGULATOR) {
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text(
                    "energy.messages.regulator-status",
                    "<yellow>Energy regulator active.</yellow>")));
        }
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (block == null || instanceId == null || typeId == null || !definitions.containsKey(typeId)) {
            return;
        }
        electricEnergyMenus.closeAndSync(instanceId);

        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxEnergyNodeState state = nodeStates.get(instanceId);
        if (state == null && instance != null) {
            state = currentState(instanceId, instance);
        }
        if (state == null) {
            state = SfxEnergyNodeState.empty();
        }
        SfxEnergyComponentDefinition definition = definitions.get(typeId);
        for (int slot = 0; slot < inputSlotCount(definition); slot++) {
            dropStack(block, state.input(slot));
        }
        for (int slot = 0; slot < outputSlotCount(definition); slot++) {
            dropStack(block, state.output(slot));
        }
        if (definition != null && state.hasActiveFuel()) {
            SfxElectricStack interruptedOutput = interruptedFuelOutput(definition, state.activeFuelKey());
            if (interruptedOutput != null) {
                dropStack(block, interruptedOutput);
            }
        }
        dropStack(block, state.pendingOutput());
        dropPluginBlock(block, typeId);
        nodeStates.remove(instanceId);
        dirtyNodes.remove(instanceId);
        activeNodes.remove(instanceId);
        autoPausedGenerators.remove(instanceId);
        capacitorProjector.remove(instanceId);
        if (instance != null) {
            displayController.remove(instance.anchorKey());
        }
        blockData.unregisterAt(block.getLocation());
    }

    public void shutdown() {
        running = false;
        electricEnergyMenus.shutdown();
        flushDirty();
        displayController.shutdown();
        runtimeGrids.clear();
        nodeStates.clear();
        nodeGridStatuses.clear();
        dirtyNodes.clear();
        activeNodes.clear();
        autoPausedGenerators.clear();
    }

    private void bootstrapLoadedStates() {
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            if (!definitions.containsKey(instance.typeId())) {
                continue;
            }
            SfxEnergyNodeState state = SfxEnergyNodeState.decode(instance.stateBlob());
            nodeStates.put(instance.instanceId(), state);
            activeNodes.add(instance.instanceId());
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition != null && definition.componentType() == SfxEnergyComponentType.CAPACITOR) {
                scheduleCapacitorAppearanceUpdate(new SfxEnergyNodeRef(instance, definition, state));
            }
        }
    }

    private void scheduleTick() {
        runtime.executeGlobalLater(1L, () -> {
            if (!running) {
                return;
            }
            tickAllRegulators();
            scheduleTick();
        });
    }

    private void scheduleFlush() {
        runtime.executeGlobalLater(FLUSH_INTERVAL, () -> {
            if (!running) {
                return;
            }
            flushDirty();
            scheduleFlush();
        });
    }

    private void tickAllRegulators() {
        topology.rebuildIfStale();
        long topologyRevision = topology.revision();
        boolean topologyChanged = topologyRevision != nodeGridStatusTopologyRevision;
        Set<UUID> liveComponents = topologyChanged ? new LinkedHashSet<>() : Set.of();
        if (topologyChanged) {
            nodeGridStatuses.clear();
        }

        for (SfxTopologyComponent component : topology.components()) {
            if (topologyChanged) {
                liveComponents.add(component.componentId());
                cacheGridStatus(component);
            }
            SfxEnergyGridStatus status = gridStatusFor(component);
            if (status != SfxEnergyGridStatus.ONLINE) {
                if (topologyChanged) {
                    for (UUID controllerId : component.controllers()) {
                        SfxBlockInstanceRecord regulator = blockData.findInstance(controllerId).orElse(null);
                        if (regulator != null) {
                            displayStatus(regulator.anchorKey(), status, 0, 0, 0, 0, 0);
                        }
                    }
                    runtimeGrids.remove(component.componentId());
                }
                continue;
            }
            UUID regulatorId = component.controllers().stream().findFirst().orElse(null);
            if (regulatorId == null) {
                runtimeGrids.remove(component.componentId());
                continue;
            }
            SfxBlockInstanceRecord regulator = blockData.findInstance(regulatorId).orElse(null);
            if (regulator == null) {
                runtimeGrids.remove(component.componentId());
                continue;
            }
            Location regulatorLocation = toLocation(regulator.anchorKey());
            if (regulatorLocation == null) {
                runtimeGrids.remove(component.componentId());
                continue;
            }
            EnergyRuntimeGrid grid = runtimeGridFor(component, regulator);
            runtime.executeAt(regulatorLocation, () -> SfxNetworkExecution.tick(
                    SfxNetworkExecution.snapshot(component.componentId(), SfxNetworkDomain.ENERGY, component.members(), component.topologyRevision()),
                    SfxNetworkReadiness.READY,
                    () -> {
                        SfxMachineLegacyHookBridge.beforeNetworkTick(machineRuntime, "sf:energy_regulator", regulatorId, regulatorLocation, "energy", "SfxEnergyService.tickEnergy");
                        processGrid(grid);
                        SfxMachineLegacyHookBridge.afterNetworkTick(machineRuntime, "sf:energy_regulator", regulatorId, regulatorLocation, "energy", "SfxEnergyService.tickEnergy");
                    }));
        }

        if (topologyChanged) {
            for (UUID conflictedTerminal : topology.conflictedTerminals()) {
                nodeGridStatuses.put(conflictedTerminal, SfxEnergyGridStatus.SHARED_NODE_CONFLICT);
            }
            for (UUID detachedTerminal : topology.detachedTerminals()) {
                nodeGridStatuses.put(detachedTerminal, SfxEnergyGridStatus.NO_NETWORK);
            }
            runtimeGrids.keySet().removeIf(componentId -> !liveComponents.contains(componentId));
            nodeGridStatusTopologyRevision = topologyRevision;
        }
    }

    private void cacheGridStatus(SfxTopologyComponent component) {
        SfxEnergyGridStatus status = gridStatusFor(component);
        for (UUID memberId : component.members()) {
            nodeGridStatuses.put(memberId, status);
        }
    }

    private SfxEnergyGridStatus gridStatusFor(SfxTopologyComponent component) {
        SfxEnergyGridStatus status = switch (component.status()) {
            case ONLINE -> SfxEnergyGridStatus.ONLINE;
            case MULTIPLE_CONTROLLERS -> SfxEnergyGridStatus.MULTIPLE_REGULATORS;
            case INACTIVE -> SfxEnergyGridStatus.NO_NETWORK;
        };
        if (status == SfxEnergyGridStatus.ONLINE && component.backboneNodes().size() + component.terminals().size() <= 1) {
            return SfxEnergyGridStatus.NO_NETWORK;
        }
        return status;
    }

    private EnergyRuntimeGrid runtimeGridFor(SfxTopologyComponent component, SfxBlockInstanceRecord regulator) {
        EnergyRuntimeGrid cached = runtimeGrids.get(component.componentId());
        if (cached != null
                && cached.topologyRevision() == component.topologyRevision()
                && cached.regulatorId().equals(regulator.instanceId())) {
            return cached;
        }
        Set<UUID> members = new LinkedHashSet<>(component.members());
        Set<UUID> controllers = new LinkedHashSet<>(component.controllers());
        List<SfxBlockInstanceRecord> capacitors = new ArrayList<>();
        List<SfxBlockInstanceRecord> generators = new ArrayList<>();
        List<SfxBlockInstanceRecord> chargers = new ArrayList<>();
        List<SfxBlockInstanceRecord> electricConsumers = new ArrayList<>();
        List<SfxBlockInstanceRecord> configurableConsumers = new ArrayList<>();
        List<SfxBlockInstanceRecord> configurableProducers = new ArrayList<>();
        for (UUID memberId : members) {
            SfxBlockInstanceRecord instance = blockData.findInstance(memberId).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition != null) {
                switch (definition.componentType()) {
                    case CAPACITOR -> capacitors.add(instance);
                    case GENERATOR -> generators.add(instance);
                    case CHARGER -> chargers.add(instance);
                    case REGULATOR, CONNECTOR -> {
                    }
                }
            } else if (electricMachines.supportsType(instance.typeId())) {
                electricConsumers.add(instance);
            } else if (configurableMachines.isConsumer(instance.typeId())) {
                configurableConsumers.add(instance);
            } else if (configurableMachines.isProducer(instance.typeId())) {
                configurableProducers.add(instance);
            }
        }
        capacitors.sort((left, right) -> {
            int byDistance = Integer.compare(left.energyPriorityDistance(), right.energyPriorityDistance());
            if (byDistance != 0) {
                return byDistance;
            }
            return compareAnchorKeys(left.anchorKey(), right.anchorKey());
        });
        EnergyRuntimeGrid grid = new EnergyRuntimeGrid(
                component.componentId(),
                component.topologyRevision(),
                regulator.instanceId(),
                regulator.anchorKey(),
                Set.copyOf(members),
                Set.copyOf(controllers),
                List.copyOf(capacitors),
                List.copyOf(generators),
                List.copyOf(chargers),
                List.copyOf(electricConsumers),
                List.copyOf(configurableConsumers),
                List.copyOf(configurableProducers));
        runtimeGrids.put(component.componentId(), grid);
        return grid;
    }

    private void processGrid(EnergyRuntimeGrid grid) {
        SfxEnergyGridProcessor.processGrid(this, grid);
    }

    List<SfxBlockInstanceRecord> join(List<SfxBlockInstanceRecord> first, List<SfxBlockInstanceRecord> second) {
        List<SfxBlockInstanceRecord> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }


    int potentialGeneration(List<SfxEnergyNodeRef> generatorRefs, List<SfxBlockInstanceRecord> configurableProducers) {
        int total = 0;
        for (SfxEnergyNodeRef generator : generatorRefs) {
            total += generatorPotentialGeneration(generator.instance(), generator.definition(), generator.state());
        }
        for (SfxBlockInstanceRecord producer : configurableProducers) {
            total += configurableMachines.producerPotentialGeneration(producer.instanceId());
        }
        return total;
    }

    void applyGeneratorAutoPause(
            List<SfxEnergyNodeRef> generatorRefs,
            List<SfxBlockInstanceRecord> configurableProducers,
            int totalStored,
            int totalCapacity,
            int potentialSupply,
            int requestedConsumption
    ) {
        for (SfxEnergyNodeRef generator : generatorRefs) {
            if (generator.definition().isSolarGenerator()) {
                autoPausedGenerators.remove(generator.instance().instanceId());
            }
        }
        if (totalCapacity <= 0) {
            autoPausedGenerators.clear();
            for (SfxBlockInstanceRecord producer : configurableProducers) {
                configurableMachines.setProducerAutoPaused(producer.instanceId(), false);
            }
            return;
        }
        if (totalStored < totalCapacity) {
            autoPausedGenerators.clear();
            for (SfxBlockInstanceRecord producer : configurableProducers) {
                configurableMachines.setProducerAutoPaused(producer.instanceId(), false);
            }
            return;
        }
        int surplus = potentialSupply - requestedConsumption;
        if (surplus <= 0) {
            return;
        }
        List<AutoPauseCandidate> candidates = new ArrayList<>();
        for (SfxEnergyNodeRef generator : generatorRefs) {
            if (generator.definition().isSolarGenerator()) {
                autoPausedGenerators.remove(generator.instance().instanceId());
                continue;
            }
            int potential = generatorPotentialGeneration(generator.instance(), generator.definition(), generator.state());
            if (potential > 0) {
                candidates.add(new AutoPauseCandidate(generator.instance().instanceId(), potential, false));
            }
        }
        for (SfxBlockInstanceRecord producer : configurableProducers) {
            int potential = configurableMachines.producerPotentialGeneration(producer.instanceId());
            if (potential > 0 && configurableMachines.canAutoPauseProducer(producer.instanceId())) {
                candidates.add(new AutoPauseCandidate(producer.instanceId(), potential, true));
            }
        }
        Collections.shuffle(candidates);
        int closed = 0;
        for (AutoPauseCandidate candidate : candidates) {
            if (closed + candidate.generation() > surplus) {
                break;
            }
            if (candidate.configurable()) {
                configurableMachines.setProducerAutoPaused(candidate.instanceId(), true);
            } else {
                autoPausedGenerators.add(candidate.instanceId());
            }
            closed += candidate.generation();
        }
    }

    int generatorPotentialGeneration(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        Location generationLocation = instance == null ? null : toLocation(instance.anchorKey());
        if (generationLocation != null && !runtime.isOwnedByCurrentRegion(generationLocation)) {
            return runtime.supplyAt(generationLocation, () -> generatorPotentialGeneration(instance, definition, state));
        }
        if (definition.componentType() != SfxEnergyComponentType.GENERATOR) {
            return 0;
        }
        if (definition.isSolarGenerator()) {
            Location location = toLocation(instance.anchorKey());
            if (location == null) {
                return 0;
            }
            World world = location.getWorld();
            if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
                return definition.nightEnergyPerTick();
            }
            long time = world.getTime();
            boolean isDaytime = !world.hasStorm() && !world.isThundering() && (time < 12300 || time > 23850);
            return isDaytime ? definition.energyPerTick() : definition.nightEnergyPerTick();
        }
        if (state.hasPendingOutput() && findOutputSlot(definition, state, state.pendingOutput()) == null) {
            return 0;
        }
        if (state.hasActiveFuel()) {
            return definition.energyPerTick();
        }
        SfxEnergyFuelMatch fuel = findFuelMatch(definition, state);
        if (fuel == null) {
            return 0;
        }
        if (fuel.output() != null && findOutputSlot(definition, state, fuel.output()) == null) {
            return 0;
        }
        return definition.energyPerTick();
    }

    private void sortCapacitors(List<SfxEnergyNodeRef> capacitorRefs) {
        capacitorRefs.sort((left, right) -> {
            int byDistance = Integer.compare(left.instance().energyPriorityDistance(), right.instance().energyPriorityDistance());
            if (byDistance != 0) {
                return byDistance;
            }
            return compareAnchorKeys(left.instance().anchorKey(), right.instance().anchorKey());
        });
    }

    private int compareAnchorKeys(SfxBlockAnchorKey left, SfxBlockAnchorKey right) {
        int byWorld = left.worldId().compareTo(right.worldId());
        if (byWorld != 0) {
            return byWorld;
        }
        int byX = Integer.compare(left.x(), right.x());
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(left.y(), right.y());
        if (byY != 0) {
            return byY;
        }
        return Integer.compare(left.z(), right.z());
    }

    private int capacitorPriorityDistanceForPlacement(Location location) {
        if (location == null || location.getWorld() == null) {
            return SfxBlockInstanceRecord.DEFAULT_ENERGY_PRIORITY_DISTANCE;
        }
        SfxBlockAnchorKey placed = SfxBlockAnchorKey.fromLocation(location);
        int best = SfxBlockInstanceRecord.DEFAULT_ENERGY_PRIORITY_DISTANCE;
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            if (!anchor.key().worldId().equals(placed.worldId())) {
                continue;
            }
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition == null || definition.componentType() != SfxEnergyComponentType.REGULATOR) {
                continue;
            }
            int distance = manhattanDistance(placed, anchor.key());
            if (distance < best || (distance == best && compareAnchorKeys(anchor.key(), placed) < 0)) {
                best = distance;
            }
        }
        return best;
    }

    private int manhattanDistance(SfxBlockAnchorKey first, SfxBlockAnchorKey second) {
        if (!first.worldId().equals(second.worldId())) {
            return SfxBlockInstanceRecord.DEFAULT_ENERGY_PRIORITY_DISTANCE;
        }
        long distance = Math.abs((long) first.x() - second.x())
                + Math.abs((long) first.y() - second.y())
                + Math.abs((long) first.z() - second.z());
        return distance > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) distance;
    }

    private int storageBufferPercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("energy.storage.hidden-buffer-percent", 5)));
    }

    int hiddenBufferCapacity(int visibleCapacity) {
        if (visibleCapacity <= 0) {
            return 0;
        }
        return (int) Math.ceil(visibleCapacity * storageBufferPercent() / 100.0D);
    }

    int effectiveStorageCapacity(SfxEnergyComponentDefinition definition) {
        if (definition == null || definition.capacity() <= 0) {
            return 0;
        }
        if (definition.componentType() == SfxEnergyComponentType.CAPACITOR || definition.componentType() == SfxEnergyComponentType.GENERATOR) {
            return definition.capacity() + hiddenBufferCapacity(definition.capacity());
        }
        return definition.capacity();
    }

    int drainCapacitorsToElectricConsumer(List<SfxEnergyNodeRef> capacitorRefs, Set<UUID> dirtyNodes, SfxBlockInstanceRecord consumer, int remainingDemand, boolean hiddenOnly) {
        int remaining = Math.max(0, remainingDemand);
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            if (remaining <= 0) {
                break;
            }
            int available = capacitorDrainableEnergy(capacitor, hiddenOnly);
            if (available <= 0) {
                continue;
            }
            int offered = Math.min(available, remaining);
            int accepted = electricMachines.chargeConsumer(consumer.instanceId(), offered);
            if (accepted <= 0) {
                break;
            }
            capacitor.state().storedEnergy(capacitor.state().storedEnergy() - accepted);
            dirtyNodes.add(capacitor.instance().instanceId());
            remaining -= accepted;
        }
        return remaining;
    }

    int drainCapacitorsToConfigurableConsumer(List<SfxEnergyNodeRef> capacitorRefs, Set<UUID> dirtyNodes, SfxBlockInstanceRecord consumer, int remainingDemand, boolean hiddenOnly) {
        int remaining = Math.max(0, remainingDemand);
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            if (remaining <= 0) {
                break;
            }
            int available = capacitorDrainableEnergy(capacitor, hiddenOnly);
            if (available <= 0) {
                continue;
            }
            int offered = Math.min(available, remaining);
            int accepted = configurableMachines.chargeConsumer(consumer.instanceId(), offered);
            if (accepted <= 0) {
                break;
            }
            capacitor.state().storedEnergy(capacitor.state().storedEnergy() - accepted);
            dirtyNodes.add(capacitor.instance().instanceId());
            remaining -= accepted;
        }
        return remaining;
    }

    int drainCapacitorsToCharger(List<SfxEnergyNodeRef> capacitorRefs, Set<UUID> dirtyNodes, SfxEnergyNodeRef charger, int remainingDemand, boolean hiddenOnly) {
        int remaining = Math.max(0, remainingDemand);
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            if (remaining <= 0) {
                break;
            }
            int available = capacitorDrainableEnergy(capacitor, hiddenOnly);
            if (available <= 0) {
                continue;
            }
            int accepted = Math.min(available, remaining);
            charger.state().storedEnergy(charger.state().storedEnergy() + accepted);
            capacitor.state().storedEnergy(capacitor.state().storedEnergy() - accepted);
            dirtyNodes.add(charger.instance().instanceId());
            dirtyNodes.add(capacitor.instance().instanceId());
            remaining -= accepted;
        }
        return remaining;
    }

    private int capacitorDrainableEnergy(SfxEnergyNodeRef capacitor, boolean hiddenOnly) {
        int stored = Math.max(0, capacitor.state().storedEnergy());
        int visibleCapacity = Math.max(0, capacitor.definition().capacity());
        if (hiddenOnly) {
            return Math.max(0, stored - visibleCapacity);
        }
        return Math.min(stored, visibleCapacity);
    }


    int hiddenStorageBaseCapacity(List<SfxEnergyNodeRef> capacitorRefs, List<SfxEnergyNodeRef> generatorRefs) {
        int total = 0;
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            total += Math.max(0, capacitor.definition().capacity());
        }
        for (SfxEnergyNodeRef generator : generatorRefs) {
            total += Math.max(0, generator.definition().capacity());
        }
        return total;
    }

    int displayedEnergy(int stored, int capacity) {
        return Math.max(0, Math.min(stored, Math.max(0, capacity)));
    }

    int totalStoredEnergy(List<SfxEnergyNodeRef> capacitorRefs, List<SfxEnergyNodeRef> generatorRefs, List<SfxEnergyNodeRef> chargerRefs, List<SfxBlockInstanceRecord> consumers) {
        int total = 0;
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            total += capacitor.state().storedEnergy();
        }
        for (SfxEnergyNodeRef generator : generatorRefs) {
            total += generator.state().storedEnergy();
        }
        for (SfxEnergyNodeRef charger : chargerRefs) {
            total += charger.state().storedEnergy();
        }
        for (SfxBlockInstanceRecord consumer : consumers) {
            total += electricMachines.consumerStoredEnergy(consumer.instanceId());
        }
        return total;
    }

    int totalCapacity(List<SfxEnergyNodeRef> capacitorRefs, List<SfxEnergyNodeRef> generatorRefs, List<SfxEnergyNodeRef> chargerRefs, List<SfxBlockInstanceRecord> consumers) {
        int total = 0;
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            total += capacitor.definition().capacity();
        }
        for (SfxEnergyNodeRef generator : generatorRefs) {
            total += generator.definition().capacity();
        }
        for (SfxEnergyNodeRef charger : chargerRefs) {
            total += charger.definition().capacity();
        }
        for (SfxBlockInstanceRecord consumer : consumers) {
            total += electricMachines.consumerCapacity(consumer.typeId());
        }
        return total;
    }

    int requestedChargerEnergy(List<SfxEnergyNodeRef> chargerRefs) {
        int total = 0;
        for (SfxEnergyNodeRef charger : chargerRefs) {
            if (!canChargeAnyInput(charger.definition(), charger.state())) {
                continue;
            }
            int demand = Math.max(0, charger.definition().capacity() - charger.state().storedEnergy());
            total += Math.min(charger.definition().energyPerTick(), demand);
        }
        return total;
    }

    boolean canChargeAnyInput(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        for (int slot = 0; slot < inputSlotCount(definition); slot++) {
            SfxElectricStack input = state.input(slot);
            if (input == null || input.amount() != 1) {
                continue;
            }
            ItemStack item = input.toItemStack(items);
            if (!rechargeableItems.isRechargeable(item)) {
                continue;
            }
            if (rechargeableItems.charge(item) < rechargeableItems.capacity(item)) {
                return true;
            }
            if (findOutputSlot(definition, state, SfxElectricStack.fromItemStack(items, item)) != null) {
                return true;
            }
        }
        return false;
    }

    void tickChargingBench(SfxEnergyNodeRef charger) {
        SfxEnergyNodeState state = charger.state();
        for (int slot = 0; slot < inputSlotCount(charger.definition()); slot++) {
            SfxElectricStack input = state.input(slot);
            if (input == null || input.amount() != 1) {
                continue;
            }
            ItemStack item = input.toItemStack(items);
            if (!rechargeableItems.isRechargeable(item)) {
                traceChargingBench(charger.definition(), "tick non-rechargeable input slot=" + slot + " stack=" + describe(item) + " sfx=" + items.isSfxItem(item));
                if (items.isSfxItem(item)) {
                    moveChargingBenchInputToOutput(charger, slot, input);
                }
                return;
            }
            double currentCharge = rechargeableItems.charge(item);
            double capacity = rechargeableItems.capacity(item);
            if (currentCharge >= capacity) {
                traceChargingBench(charger.definition(), "tick full input slot=" + slot + " stack=" + describe(item));
                moveChargingBenchInputToOutput(charger, slot, SfxElectricStack.fromItemStack(items, item));
                renderStorageSlots(charger.instance().instanceId(), state);
                return;
            }
            double efficiency = chargingBenchEfficiency();
            if (efficiency <= 0.0D) {
                return;
            }
            double missing = capacity - currentCharge;
            int spendLimit = Math.max(1, charger.definition().energyPerTick());
            int storedBefore = state.storedEnergy();
            int actualSpend = Math.min(spendLimit, storedBefore);
            actualSpend = Math.min(actualSpend, (int) Math.max(1, Math.ceil(missing / efficiency)));
            if (actualSpend <= 0) {
                traceChargingBench(charger.definition(), "tick no-spend slot=" + slot
                        + " stored=" + storedBefore
                        + " energyPerTick=" + charger.definition().energyPerTick()
                        + " efficiency=" + efficiency
                        + " charge=" + currentCharge + "/" + capacity
                        + " missing=" + missing
                        + " stack=" + describe(item));
                return;
            }
            rechargeableItems.addCharge(item, actualSpend * efficiency);
            double chargeAfter = rechargeableItems.charge(item);
            state.storedEnergy(state.storedEnergy() - actualSpend);
            state.input(slot, SfxElectricStack.fromItemStack(items, item));
            dirtyNodes.add(charger.instance().instanceId());
            renderStorageSlots(charger.instance().instanceId(), state);
            traceChargingBench(charger.definition(), "tick charged slot=" + slot
                    + " spend=" + actualSpend
                    + " delivered=" + (actualSpend * efficiency)
                    + " storedBefore=" + storedBefore
                    + " storedAfter=" + state.storedEnergy()
                    + " energyPerTick=" + charger.definition().energyPerTick()
                    + " efficiency=" + efficiency
                    + " chargeBefore=" + currentCharge
                    + " chargeAfter=" + chargeAfter
                    + " capacity=" + capacity
                    + " stack=" + describe(item));
            return;
        }
    }

    private double chargingBenchEfficiency() {
        double loss = plugin.getConfig().getBoolean("technical-gadgets.sfx-balance.enabled", true)
                ? plugin.getConfig().getDouble("technical-gadgets.sfx-balance.charging-bench.energy-loss", 0.80D)
                : plugin.getConfig().getDouble("technical-gadgets.classic.charging-bench.energy-loss", 0.50D);
        return Math.max(0.0D, Math.min(1.0D, 1.0D - loss));
    }

    private void moveChargingBenchInputToOutput(SfxEnergyNodeRef charger, int inputSlot, SfxElectricStack stack) {
        Integer outputSlot = findOutputSlot(charger.definition(), charger.state(), stack);
        if (outputSlot == null) {
            return;
        }
        charger.state().input(inputSlot, null);
        pushOutput(charger.state(), outputSlot, stack);
        dirtyNodes.add(charger.instance().instanceId());
        renderStorageSlots(charger.instance().instanceId(), charger.state());
    }

    int generate(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        Location generationLocation = instance == null ? null : toLocation(instance.anchorKey());
        if (generationLocation != null && !runtime.isOwnedByCurrentRegion(generationLocation)) {
            return runtime.supplyAt(generationLocation, () -> generate(instance, definition, state));
        }
        if (definition.isSolarGenerator()) {
            Location location = toLocation(instance.anchorKey());
            if (location == null) {
                return 0;
            }
            World world = location.getWorld();
            if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
                return definition.nightEnergyPerTick();
            }
            long time = world.getTime();
            boolean isDaytime = !world.hasStorm() && !world.isThundering() && (time < 12300 || time > 23850);
            return isDaytime ? definition.energyPerTick() : definition.nightEnergyPerTick();
        }

        if (state.hasPendingOutput()) {
            Integer outputSlot = findOutputSlot(definition, state, state.pendingOutput());
            if (outputSlot != null) {
                pushOutput(state, outputSlot, state.pendingOutput());
                state.pendingOutput(null);
                dirtyNodes.add(instance.instanceId());
            } else {
                return 0;
            }
        }

        if (!state.hasActiveFuel()) {
            SfxEnergyFuelMatch fuel = findFuelMatch(definition, state);
            if (fuel == null) {
                return 0;
            }
            if (fuel.output() != null && findOutputSlot(definition, state, fuel.output()) == null) {
                return 0;
            }
            consumeInput(state, fuel.inputSlot(), fuel.input().amount());
            state.activeFuelKey(fuel.key());
            state.fuelProgressTenths(0);
            state.fuelTotalTenths(fuel.totalTenths());
            if (shouldReturnFuelOutputImmediately(definition, fuel.output())) {
                Integer outputSlot = findOutputSlot(definition, state, fuel.output());
                if (outputSlot != null) {
                    pushOutput(state, outputSlot, fuel.output());
                }
            }
            dirtyNodes.add(instance.instanceId());
        }

        if (definition.capacity() > 0 && state.storedEnergy() + definition.energyPerTick() > effectiveStorageCapacity(definition)) {
            return 0;
        }

        state.fuelProgressTenths(state.fuelProgressTenths() + definition.fuelBurnRateTenths());
        dirtyNodes.add(instance.instanceId());
        if (state.fuelProgressTenths() >= state.fuelTotalTenths()) {
            String completedFuelKey = state.activeFuelKey();
            state.clearFuelOperation();
            SfxElectricStack completedOutput = fuelOutput(definition, completedFuelKey);
            if (completedOutput != null && !shouldReturnFuelOutputImmediately(definition, completedOutput)) {
                Integer outputSlot = findOutputSlot(definition, state, completedOutput);
                if (outputSlot != null) {
                    pushOutput(state, outputSlot, completedOutput);
                } else {
                    state.pendingOutput(completedOutput);
                }
            }
            dirtyNodes.add(instance.instanceId());
        }
        return definition.energyPerTick();
    }

    SfxEnergyFuelMatch findFuelMatch(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        for (int slot = 0; slot < inputSlotCount(definition); slot++) {
            SfxElectricStack input = state.input(slot);
            if (input == null) {
                continue;
            }
            if (definition.usesVanillaCoalResolver()) {
                int totalTenths = resolveCoalFuelTenths(input, definition);
                if (totalTenths > 0) {
                    return new SfxEnergyFuelMatch(slot, input.copyWithAmount(1), null, stackKey(input), totalTenths);
                }
                continue;
            }
            for (SfxEnergyComponentDefinition.FuelRule rule : definition.fuelRules()) {
                if (input.sameKind(rule.input()) && input.amount() >= rule.input().amount()) {
                    return new SfxEnergyFuelMatch(slot, rule.input(), rule.output(), rule.key(), rule.seconds() * 20 * 10);
                }
            }
        }
        return null;
    }

    private int resolveCoalFuelTenths(SfxElectricStack input, SfxEnergyComponentDefinition definition) {
        if (input.isSfxItem()) {
            return 0;
        }
        Material material = input.material();
        if (material == Material.LAVA_BUCKET || isCarpetFuel(material)) {
            return 0;
        }
        int burnTicks = vanillaFuelTicks(input.toItemStack(items));
        if (burnTicks <= 0) {
            return 0;
        }
        int multiplier = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true) ? 2 : 1;
        return burnTicks * multiplier;
    }

    private boolean isCarpetFuel(Material material) {
        return material != null && material.name().endsWith("_CARPET");
    }

    private boolean shouldReturnFuelOutputImmediately(SfxEnergyComponentDefinition definition, SfxElectricStack output) {
        if (output == null || output.isSfxItem() || output.material() != Material.BUCKET) {
            return false;
        }
        return plugin.getConfig().getBoolean("energy.bucket-fuels.instant-empty-bucket-return", true);
    }

    private SfxElectricStack interruptedFuelOutput(SfxEnergyComponentDefinition definition, String fuelKey) {
        SfxElectricStack output = fuelOutput(definition, fuelKey);
        if (output == null) {
            return null;
        }
        return shouldReturnFuelOutputImmediately(definition, output) ? null : output;
    }

    private SfxElectricStack fuelOutput(SfxEnergyComponentDefinition definition, String fuelKey) {
        if (fuelKey == null || definition.usesVanillaCoalResolver()) {
            return null;
        }
        for (SfxEnergyComponentDefinition.FuelRule rule : definition.fuelRules()) {
            if (fuelKey.equals(rule.key())) {
                return rule.output();
            }
        }
        return null;
    }

    private int vanillaFuelTicks(ItemStack stack) {
        SfxFuelBurnTimeBridge bridge = fuelBurnTimeBridge;
        if (bridge == null) {
            bridge = SfxFuelBurnTimeBridge.create();
            fuelBurnTimeBridge = bridge;
        }
        return bridge.burnTicks(stack);
    }

    private String stackKey(SfxElectricStack stack) {
        return stack.isSfxItem() ? stack.itemId() : stack.material().key().toString();
    }

    private void flushDirty() {
        for (UUID instanceId : List.copyOf(dirtyNodes)) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            SfxEnergyNodeState state = nodeStates.get(instanceId);
            if (instance == null || state == null) {
                dirtyNodes.remove(instanceId);
                continue;
            }
            SfxBlockLifecycleState lifecycle = state.hasActiveFuel() || state.storedEnergy() > 0 ? SfxBlockLifecycleState.ACTIVE : SfxBlockLifecycleState.IDLE;
            blockData.updateInstanceState(instanceId, state.encode(), lifecycle);
            dirtyNodes.remove(instanceId);
        }
    }

    void scheduleCapacitorAppearanceUpdate(SfxEnergyNodeRef capacitor) {
        capacitorProjector.scheduleUpdate(
                capacitor.instance().instanceId(),
                toLocation(capacitor.instance().anchorKey()),
                displayedEnergy(capacitor.state().storedEnergy(), capacitor.definition().capacity()),
                capacitor.definition().capacity());
    }

    void displayStatus(SfxBlockAnchorKey regulatorKey, SfxEnergyGridStatus status, int supply, int consumption, int net, int totalStored, int totalCapacity) {
        displayController.displayStatus(regulatorKey, status, supply, consumption, net, totalStored, totalCapacity);
    }

    void refreshOpenSfxEnergyGeneratorSessions() {
        electricEnergyMenus.refreshOpenSessions();
    }

    private void renderStorageSlots(UUID instanceId, SfxEnergyNodeState state) {
        electricEnergyMenus.renderStorageSlots(instanceId, state);
    }

    SfxEnergyNodeState currentState(UUID instanceId, SfxBlockInstanceRecord instance) {
        return nodeStates.computeIfAbsent(instanceId, ignored -> SfxEnergyNodeState.decode(instance.stateBlob()));
    }

    private SfxElectricStack consumeInput(SfxEnergyNodeState state, int slot, int amount) {
        SfxElectricStack input = state.input(slot);
        if (input == null) {
            return null;
        }
        SfxElectricStack consumed = input.copyWithAmount(amount);
        int remaining = input.amount() - amount;
        state.input(slot, remaining <= 0 ? null : input.copyWithAmount(remaining));
        return consumed;
    }

    Integer findOutputSlot(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxElectricStack output) {
        for (int slot = 0; slot < outputSlotCount(definition); slot++) {
            SfxElectricStack current = state.output(slot);
            if (current != null && output.canMerge(current, items)) {
                return slot;
            }
        }
        for (int slot = 0; slot < outputSlotCount(definition); slot++) {
            if (state.output(slot) == null) {
                return slot;
            }
        }
        return null;
    }

    private int inputSlotCount(SfxEnergyComponentDefinition definition) {
        return definition == null ? 0 : definition.ui().inputSlots().length;
    }

    private int outputSlotCount(SfxEnergyComponentDefinition definition) {
        return definition == null ? 0 : definition.ui().outputSlots().length;
    }

    private void pushOutput(SfxEnergyNodeState state, int slot, SfxElectricStack output) {
        SfxElectricStack current = state.output(slot);
        if (current == null) {
            state.output(slot, output);
            return;
        }
        state.output(slot, current.copyWithAmount(current.amount() + output.amount()));
    }

    SfxEnergyComponentDefinition definitionFor(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        return instance == null ? null : definitions.get(instance.typeId());
    }

    void markActive(UUID instanceId) {
        activeNodes.add(instanceId);
    }

    void markDirty(UUID instanceId) {
        dirtyNodes.add(instanceId);
    }

    void traceChargingBench(SfxEnergyComponentDefinition definition, String message) {
        if (definition != null && definition.isCharger()) {
            SfxValidationDiagnostics.log(plugin, "charging-bench", message);
        }
    }

    private String describe(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "empty";
        }
        return stack.getType().name() + "*" + stack.getAmount() + items.readMarker(stack).map(marker -> "[" + marker.itemId() + "]").orElse("");
    }

    private String describe(SfxElectricStack stack) {
        if (stack == null) {
            return "empty";
        }
        return stack.toItemStack(items).getType().name() + "*" + stack.amount() + (stack.isSfxItem() ? "[" + stack.itemId() + "]" : "");
    }


    boolean isInstanceChunkLoaded(SfxBlockInstanceRecord instance) {
        if (instance == null) {
            return false;
        }
        Location location = toLocation(instance.anchorKey());
        if (location == null) {
            return false;
        }
        World world = location.getWorld();
        return world != null && world.isChunkLoaded(instance.anchorKey().x() >> 4, instance.anchorKey().z() >> 4);
    }

    Location toLocation(SfxBlockAnchorKey key) {
        World world = plugin.getServer().getWorld(key.worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, key.x(), key.y(), key.z());
    }

    private void dropPluginBlock(Block block, String typeId) {
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
    }

    private void dropStack(Block block, SfxElectricStack stack) {
        SfxBlockDrops.dropStack(block, items, stack);
    }


    public boolean isConnectionStatusNode(String typeId) {
        SfxEnergyComponentDefinition definition = definitions.get(typeId);
        return definition != null && (definition.componentType() == SfxEnergyComponentType.CAPACITOR
                || definition.componentType() == SfxEnergyComponentType.CONNECTOR);
    }

    public boolean isConnectedToOnlineGrid(UUID instanceId) {
        return nodeGridStatuses.get(instanceId) == SfxEnergyGridStatus.ONLINE;
    }

    public SfxEnergyInspection inspectEnergyComponent(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return null;
        }
        SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            return null;
        }
        SfxEnergyNodeState state = currentState(instanceId, instance);
        int generation = generatorPotentialGeneration(instance, definition, state);
        return new SfxEnergyInspection(
                instance.typeId(),
                definition.componentType(),
                displayedEnergy(state.storedEnergy(), definition.capacity()),
                definition.capacity(),
                generation,
                isConnectedToOnlineGrid(instanceId),
                autoPausedGenerators.contains(instanceId));
    }

    public SfxEnergyGridInspection inspectGridForMember(UUID memberId) {
        if (memberId == null) {
            return null;
        }
        topology.rebuildIfStale();
        SfxTopologyComponent component = topology.componentForMember(memberId).orElse(null);
        if (component == null) {
            return null;
        }
        UUID regulatorId = component.controllers().stream().findFirst().orElse(null);
        if (regulatorId == null) {
            return null;
        }
        SfxBlockInstanceRecord regulator = blockData.findInstance(regulatorId).orElse(null);
        if (regulator == null) {
            return null;
        }
        SfxEnergyGridStatus status = switch (component.status()) {
            case ONLINE -> SfxEnergyGridStatus.ONLINE;
            case MULTIPLE_CONTROLLERS -> SfxEnergyGridStatus.MULTIPLE_REGULATORS;
            case INACTIVE -> SfxEnergyGridStatus.NO_NETWORK;
        };
        if (status == SfxEnergyGridStatus.ONLINE && component.members().size() <= 1) {
            status = SfxEnergyGridStatus.NO_NETWORK;
        }
        if (topology.isConflictedTerminal(memberId)) {
            status = SfxEnergyGridStatus.SHARED_NODE_CONFLICT;
        }
        if (status == SfxEnergyGridStatus.ONLINE && !isInstanceChunkLoaded(regulator)) {
            status = SfxEnergyGridStatus.NO_NETWORK;
        }
        return inspectGrid(new SfxEnergyGridResult(regulatorId, regulator.anchorKey(), component.members(), status));
    }

    private SfxEnergyGridInspection inspectGrid(SfxEnergyGridResult grid) {
        int generators = 0;
        int capacitors = 0;
        int connectors = 0;
        int consumers = 0;
        int reactors = 0;
        int stored = 0;
        int capacity = 0;
        List<SfxEnergyNodeRef> generatorRefs = new ArrayList<>();
        List<SfxBlockInstanceRecord> electricConsumers = new ArrayList<>();
        List<SfxBlockInstanceRecord> configurableConsumers = new ArrayList<>();
        List<SfxBlockInstanceRecord> configurableProducers = new ArrayList<>();
        List<UUID> electricConsumerIds = new ArrayList<>();
        List<UUID> configurableConsumerIds = new ArrayList<>();

        for (UUID memberId : grid.members()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(memberId).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition != null) {
                SfxEnergyNodeState state = currentState(memberId, instance);
                stored += state.storedEnergy();
                capacity += definition.capacity();
                switch (definition.componentType()) {
                    case GENERATOR -> {
                        generators++;
                        generatorRefs.add(new SfxEnergyNodeRef(instance, definition, state));
                    }
                    case CAPACITOR -> capacitors++;
                    case CHARGER -> consumers++;
                    case CONNECTOR -> connectors++;
                    case REGULATOR -> connectors++;
                }
                continue;
            }
            if (electricMachines.supportsType(instance.typeId())) {
                consumers++;
                electricConsumers.add(instance);
                electricConsumerIds.add(instance.instanceId());
                stored += electricMachines.consumerStoredEnergy(instance.instanceId());
                capacity += electricMachines.consumerCapacity(instance.typeId());
                continue;
            }
            if (configurableMachines.isConsumer(instance.typeId())) {
                consumers++;
                configurableConsumers.add(instance);
                configurableConsumerIds.add(instance.instanceId());
                stored += configurableMachines.consumerStoredEnergy(instance.instanceId());
                capacity += configurableMachines.consumerCapacity(instance.typeId());
                continue;
            }
            if (configurableMachines.isProducer(instance.typeId())) {
                reactors++;
                configurableProducers.add(instance);
                stored += configurableMachines.producerStoredEnergy(instance.instanceId());
                capacity += configurableMachines.producerCapacity(instance.typeId());
            }
        }
        int generation = potentialGeneration(generatorRefs, configurableProducers);
        int consumption = electricMachines.requestedEnergyConsumption(electricConsumerIds)
                + configurableMachines.requestedEnergyConsumption(configurableConsumerIds);
        int autoPaused = 0;
        for (SfxEnergyNodeRef generator : generatorRefs) {
            if (autoPausedGenerators.contains(generator.instance().instanceId())) {
                autoPaused++;
            }
        }
        for (SfxBlockInstanceRecord producer : configurableProducers) {
            if (configurableMachines.isProducerAutoPaused(producer.instanceId())) {
                autoPaused++;
            }
        }
        return new SfxEnergyGridInspection(grid.regulatorKey(), grid.status(), grid.members().size(), generators, reactors, capacitors, connectors, consumers, displayedEnergy(stored, capacity), capacity, generation, consumption, autoPaused);
    }






}
