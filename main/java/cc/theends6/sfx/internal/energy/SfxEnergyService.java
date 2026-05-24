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
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.network.SfxNetworkDomain;
import cc.theends6.sfx.internal.network.SfxNetworkExecution;
import cc.theends6.sfx.internal.network.SfxNetworkReadiness;
import cc.theends6.sfx.internal.technical.SfxRechargeableItemService;
import cc.theends6.sfx.internal.ui.SfxMachineStatusKey;
import cc.theends6.sfx.internal.topology.SfxTopologyComponent;
import cc.theends6.sfx.internal.topology.SfxTopologyService;
import cc.theends6.sfx.internal.ui.SfxInventoryPolicy;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxEventGuards;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxInventorySlots;
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
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxEnergyService implements Listener {
    private static final int RANGE = 6;
    private static final int INVENTORY_SIZE = 45;
    private static final int DISPLAY_SLOT = 22;
    private static final int[] INPUT_SLOTS = {19, 20};
    private static final int[] OUTPUT_SLOTS = {24, 25};
    private static final int[] BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 31, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] BORDER_IN = {9, 10, 11, 12, 18, 21, 27, 28, 29, 30};
    private static final int[] BORDER_OUT = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};
    private static final long FLUSH_INTERVAL = 20L;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxElectricMachineService electricMachines;
    private final SfxConfigurableMachineService configurableMachines;
    private final SfxEnergyDisplayController displayController;
    private final SfxCapacitorAppearanceProjector capacitorProjector;
    private final SfxEnergyGeneratorMenuRenderer generatorMenuRenderer;
    private final SfxRechargeableItemService rechargeableItems;
    private final SfxTopologyService topology;
    private final Map<String, SfxEnergyComponentDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, SfxEnergyNodeState> nodeStates = new ConcurrentHashMap<>();
    private final Map<UUID, SfxEnergyGridStatus> nodeGridStatuses = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyNodes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeNodes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> autoPausedGenerators = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SfxEnergyGeneratorSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, SfxEnergyGeneratorSession> sessionsByInstance = new ConcurrentHashMap<>();
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
            SfxRechargeableItemService rechargeableItems
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.configurableMachines = Objects.requireNonNull(configurableMachines, "configurableMachines");
        this.rechargeableItems = Objects.requireNonNull(rechargeableItems, "rechargeableItems");
        this.displayController = new SfxEnergyDisplayController(plugin, localization, Objects.requireNonNull(floatingTextDisplay, "floatingTextDisplay"));
        this.capacitorProjector = new SfxCapacitorAppearanceProjector(runtime, blockData, definitions);
        this.generatorMenuRenderer = new SfxEnergyGeneratorMenuRenderer(plugin, items, localization, rechargeableItems);
        this.definitions.putAll(SfxEnergyDefinitions.create(plugin));
        this.topology = new SfxTopologyService(
                blockData,
                new SfxEnergyTopologyPolicy(definitions, electricMachines, configurableMachines),
                new SfxEnergyConnectivityPolicy(RANGE));
        bootstrapLoadedStates();
        topology.rebuild();
        running = true;
        scheduleTick();
        scheduleFlush();
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
        SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        if (definition.componentType() == SfxEnergyComponentType.CONNECTOR || definition.componentType() == SfxEnergyComponentType.CAPACITOR) {
            // Connection status for connectors/capacitors is handled by SfxMultimeterListener.
            // Do not cancel here: holding a placeable block must remain a normal placement action.
            return;
        }
        if (SfxInteractionRules.prefersBlockPlacement(items, event)) {
            return;
        }
        SfxEventGuards.denyBlockAndItemUse(event);
        if (definition.isFueledGenerator() || definition.isCharger()) {
            runtime.executeForPlayer(event.getPlayer(), () -> openGenerator(event.getPlayer(), instance, definition));
            return;
        }
        if (definition.componentType() == SfxEnergyComponentType.REGULATOR) {
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text(
                    "energy.messages.regulator-status",
                    "<yellow>Energy regulator active.</yellow>")));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxEnergyGeneratorHolder holder)) {
            return;
        }
        if (SfxInventoryPolicy.cancelDangerousClick(event)) {
            return;
        }
        SfxEnergyComponentDefinition clickDefinition = definitionFor(holder.instanceId());
        if (clickDefinition == null) {
            event.setCancelled(true);
            return;
        }
        boolean topSlot = event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (event.isShiftClick() && !topSlot) {
            if (moveShiftClickedStackToInputs(event.getView().getTopInventory(), event.getCurrentItem(), clickDefinition)) {
                if (event.getCurrentItem() != null && event.getCurrentItem().getAmount() <= 0) {
                    event.setCurrentItem(null);
                }
                event.setCancelled(true);
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            } else {
                event.setCancelled(true);
            }
            return;
        }
        if (topSlot && contains(OUTPUT_SLOTS, event.getRawSlot())) {
            if (!isTakingFromOutput(event)) {
                event.setCancelled(true);
                return;
            }
            runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            return;
        }
        if (topSlot && !contains(INPUT_SLOTS, event.getRawSlot())) {
            event.setCancelled(true);
            return;
        }
        if (topSlot && clickDefinition.isCharger()) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isValidChargingBenchInput(cursor)) {
                event.setCancelled(true);
                return;
            }
        }
        runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxEnergyGeneratorHolder holder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        SfxEnergyComponentDefinition dragDefinition = definitionFor(holder.instanceId());
        if (dragDefinition == null) {
            event.setCancelled(true);
            return;
        }
        boolean onlyEditable = event.getRawSlots().stream()
                .filter(slot -> slot < topSize)
                .allMatch(slot -> contains(INPUT_SLOTS, slot));
        if (!onlyEditable) {
            event.setCancelled(true);
            return;
        }
        if (dragDefinition.isCharger()) {
            boolean valid = event.getNewItems().entrySet().stream()
                    .filter(entry -> entry.getKey() < topSize)
                    .allMatch(entry -> contains(INPUT_SLOTS, entry.getKey()) && isValidChargingBenchInput(entry.getValue()));
            if (!valid) {
                event.setCancelled(true);
                return;
            }
        }
        runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SfxEnergyGeneratorHolder holder)) {
            return;
        }
        SfxEnergyGeneratorSession session = sessionsByInstance.remove(holder.instanceId());
        if (session == null) {
            return;
        }
        sessionsByViewer.remove(session.viewerId());
        syncSessionState(session);
        activeNodes.add(holder.instanceId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        SfxEnergyGeneratorSession session = sessionsByViewer.remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        sessionsByInstance.remove(session.instanceId());
        syncSessionState(session);
        activeNodes.add(session.instanceId());
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (block == null || instanceId == null || typeId == null || !definitions.containsKey(typeId)) {
            return;
        }
        SfxEnergyGeneratorSession session = sessionsByInstance.remove(instanceId);
        if (session != null) {
            sessionsByViewer.remove(session.viewerId());
            syncSessionState(session);
            Player viewer = plugin.getServer().getPlayer(session.viewerId());
            if (viewer != null) {
                runtime.executeForPlayer(viewer, viewer::closeInventory);
            }
        }

        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxEnergyNodeState state = nodeStates.get(instanceId);
        if (state == null && instance != null) {
            state = currentState(instanceId, instance);
        }
        if (state == null) {
            state = SfxEnergyNodeState.empty();
        }
        SfxEnergyComponentDefinition definition = definitions.get(typeId);
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            dropStack(block, state.input(slot));
        }
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
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
        for (SfxEnergyGeneratorSession session : List.copyOf(sessionsByViewer.values())) {
            syncSessionState(session);
            Player player = plugin.getServer().getPlayer(session.viewerId());
            if (player != null) {
                runtime.executeForPlayer(player, player::closeInventory);
            }
        }
        flushDirty();
        displayController.shutdown();
        sessionsByViewer.clear();
        sessionsByInstance.clear();
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
        syncOpenSfxEnergyGeneratorSessionsToState();
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
                    () -> processGrid(grid)));
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
        if (grid == null) {
            return;
        }
        SfxBlockInstanceRecord regulator = blockData.findInstance(grid.regulatorId()).orElse(null);
        if (regulator == null || !isInstanceChunkLoaded(regulator)) {
            for (UUID memberId : grid.members()) {
                nodeGridStatuses.put(memberId, SfxEnergyGridStatus.NO_NETWORK);
            }
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
            if (!isInstanceChunkLoaded(instance)) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition == null) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            capacitorRefs.add(new SfxEnergyNodeRef(instance, definition, currentState(instance.instanceId(), instance)));
        }
        for (SfxBlockInstanceRecord instance : grid.generators()) {
            if (!isInstanceChunkLoaded(instance)) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition == null) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            generatorRefs.add(new SfxEnergyNodeRef(instance, definition, currentState(instance.instanceId(), instance)));
        }
        for (SfxBlockInstanceRecord instance : grid.chargers()) {
            if (!isInstanceChunkLoaded(instance)) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition == null) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            chargerRefs.add(new SfxEnergyNodeRef(instance, definition, currentState(instance.instanceId(), instance)));
        }
        for (SfxBlockInstanceRecord instance : grid.electricConsumers()) {
            if (!isInstanceChunkLoaded(instance)) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            electricConsumers.add(instance);
            electricConsumerIds.add(instance.instanceId());
        }
        for (SfxBlockInstanceRecord instance : grid.configurableConsumers()) {
            if (!isInstanceChunkLoaded(instance)) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            configurableConsumers.add(instance);
            configurableConsumerIds.add(instance.instanceId());
        }
        for (SfxBlockInstanceRecord instance : grid.configurableProducers()) {
            if (!isInstanceChunkLoaded(instance)) {
                continue;
            }
            loadedRuntimeMembers.add(instance.instanceId());
            configurableProducers.add(instance);
        }

        List<SfxBlockInstanceRecord> configurableRuntimeMachines = join(configurableConsumers, configurableProducers);
        int requestedConsumption = electricMachines.requestedEnergyConsumption(electricConsumerIds)
                + configurableMachines.requestedEnergyConsumption(configurableConsumerIds)
                + requestedChargerEnergy(chargerRefs);
        int totalStoredBefore = totalStoredEnergy(capacitorRefs, generatorRefs, chargerRefs, electricConsumers)
                + configurableMachines.totalStoredEnergy(configurableRuntimeMachines);
        int totalCapacityBefore = totalCapacity(capacitorRefs, generatorRefs, chargerRefs, electricConsumers)
                + configurableMachines.totalCapacity(configurableRuntimeMachines);
        int totalEffectiveCapacityBefore = totalCapacityBefore + hiddenBufferCapacity(hiddenStorageBaseCapacity(capacitorRefs, generatorRefs));
        boolean autoPauseEnabled = plugin.getConfig().getBoolean("energy.generator-balance.pause-generators-when-grid-full", true);
        int potentialSupply = potentialGeneration(generatorRefs, configurableProducers);
        if (autoPauseEnabled) {
            applyGeneratorAutoPause(generatorRefs, configurableProducers, totalStoredBefore, totalEffectiveCapacityBefore, potentialSupply, requestedConsumption);
        } else {
            autoPausedGenerators.clear();
            for (SfxBlockInstanceRecord producer : configurableProducers) {
                configurableMachines.setProducerAutoPaused(producer.instanceId(), false);
            }
        }

        for (SfxEnergyNodeRef generator : generatorRefs) {
            if (generator.state().storedEnergy() > 0) {
                available += generator.state().storedEnergy();
                generator.state().storedEnergy(0);
                dirtyNodes.add(generator.instance().instanceId());
            }
            int produced = autoPausedGenerators.contains(generator.instance().instanceId()) ? 0 : generate(generator.instance(), generator.definition(), generator.state());
            available += produced;
            supply += produced;
        }

        for (SfxBlockInstanceRecord producer : configurableProducers) {
            int produced = configurableMachines.generateProducerEnergy(producer.instanceId());
            if (produced > 0) {
                supply += produced;
            }
            int cached = configurableMachines.drainProducerEnergy(producer.instanceId());
            if (cached > 0) {
                available += cached;
            }
        }

        for (SfxBlockInstanceRecord consumer : electricConsumers) {
            if (available <= 0) {
                break;
            }
            int accepted = electricMachines.chargeConsumer(consumer.instanceId(), available);
            if (accepted > 0) {
                available -= accepted;
            }
        }
        for (SfxBlockInstanceRecord consumer : configurableConsumers) {
            if (available <= 0) {
                break;
            }
            int accepted = configurableMachines.chargeConsumer(consumer.instanceId(), available);
            if (accepted > 0) {
                available -= accepted;
            }
        }

        for (SfxEnergyNodeRef charger : chargerRefs) {
            if (available <= 0) {
                break;
            }
            if (!canChargeAnyInput(charger.state())) {
                continue;
            }
            int accepted = Math.max(0, Math.min(available, charger.definition().capacity() - charger.state().storedEnergy()));
            if (accepted > 0) {
                charger.state().storedEnergy(charger.state().storedEnergy() + accepted);
                dirtyNodes.add(charger.instance().instanceId());
                available -= accepted;
            }
        }

        for (SfxBlockInstanceRecord consumer : electricConsumers) {
            int remainingDemand = Math.max(0, electricMachines.consumerCapacity(consumer.typeId()) - electricMachines.consumerStoredEnergy(consumer.instanceId()));
            if (remainingDemand <= 0) {
                continue;
            }
            remainingDemand = drainCapacitorsToElectricConsumer(capacitorRefs, dirtyNodes, consumer, remainingDemand, true);
            if (remainingDemand > 0) {
                drainCapacitorsToElectricConsumer(capacitorRefs, dirtyNodes, consumer, remainingDemand, false);
            }
        }
        for (SfxBlockInstanceRecord consumer : configurableConsumers) {
            int remainingDemand = Math.max(0, configurableMachines.consumerCapacity(consumer.typeId()) - configurableMachines.consumerStoredEnergy(consumer.instanceId()));
            if (remainingDemand <= 0) {
                continue;
            }
            remainingDemand = drainCapacitorsToConfigurableConsumer(capacitorRefs, dirtyNodes, consumer, remainingDemand, true);
            if (remainingDemand > 0) {
                drainCapacitorsToConfigurableConsumer(capacitorRefs, dirtyNodes, consumer, remainingDemand, false);
            }
        }

        for (SfxEnergyNodeRef charger : chargerRefs) {
            int remainingDemand = Math.max(0, charger.definition().capacity() - charger.state().storedEnergy());
            if (remainingDemand <= 0 || !canChargeAnyInput(charger.state())) {
                continue;
            }
            remainingDemand = drainCapacitorsToCharger(capacitorRefs, dirtyNodes, charger, remainingDemand, true);
            if (remainingDemand > 0) {
                drainCapacitorsToCharger(capacitorRefs, dirtyNodes, charger, remainingDemand, false);
            }
        }

        for (SfxEnergyNodeRef charger : chargerRefs) {
            tickChargingBench(charger);
        }

        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            if (available <= 0) {
                break;
            }
            int stored = capacitor.state().storedEnergy();
            int accepted = Math.max(0, Math.min(available, effectiveStorageCapacity(capacitor.definition()) - stored));
            if (accepted > 0) {
                capacitor.state().storedEnergy(stored + accepted);
                dirtyNodes.add(capacitor.instance().instanceId());
                available -= accepted;
            }
        }

        for (SfxEnergyNodeRef generator : generatorRefs) {
            if (available <= 0 || generator.definition().capacity() <= 0) {
                continue;
            }
            int stored = generator.state().storedEnergy();
            int accepted = Math.max(0, Math.min(available, effectiveStorageCapacity(generator.definition()) - stored));
            if (accepted > 0) {
                generator.state().storedEnergy(stored + accepted);
                dirtyNodes.add(generator.instance().instanceId());
                available -= accepted;
            }
        }

        for (SfxBlockInstanceRecord producer : configurableProducers) {
            if (available <= 0) {
                break;
            }
            int accepted = configurableMachines.chargeProducer(producer.instanceId(), available);
            if (accepted > 0) {
                available -= accepted;
            }
        }

        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            scheduleCapacitorAppearanceUpdate(capacitor);
        }

        electricMachines.drainRecentEnergyConsumption(loadedRuntimeMembers);
        configurableMachines.drainRecentEnergyConsumption(new ArrayList<>(loadedRuntimeMembers));
        int totalStored = totalStoredEnergy(capacitorRefs, generatorRefs, chargerRefs, electricConsumers)
                + configurableMachines.totalStoredEnergy(configurableRuntimeMachines);
        int totalCapacity = totalCapacity(capacitorRefs, generatorRefs, chargerRefs, electricConsumers)
                + configurableMachines.totalCapacity(configurableRuntimeMachines);
        int displayStored = displayedEnergy(totalStored, totalCapacity);
        int displaySupply = autoPauseEnabled ? potentialSupply : supply;
        int net = displaySupply - requestedConsumption;
        displayStatus(grid.regulatorKey(), SfxEnergyGridStatus.ONLINE, displaySupply, requestedConsumption, net, displayStored, totalCapacity);
        refreshOpenSfxEnergyGeneratorSessions();
    }

    private List<SfxBlockInstanceRecord> join(List<SfxBlockInstanceRecord> first, List<SfxBlockInstanceRecord> second) {
        List<SfxBlockInstanceRecord> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }


    private int potentialGeneration(List<SfxEnergyNodeRef> generatorRefs, List<SfxBlockInstanceRecord> configurableProducers) {
        int total = 0;
        for (SfxEnergyNodeRef generator : generatorRefs) {
            total += generatorPotentialGeneration(generator.instance(), generator.definition(), generator.state());
        }
        for (SfxBlockInstanceRecord producer : configurableProducers) {
            total += configurableMachines.producerPotentialGeneration(producer.instanceId());
        }
        return total;
    }

    private void applyGeneratorAutoPause(
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

    private int generatorPotentialGeneration(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
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
        if (state.hasPendingOutput() && findOutputSlot(state, state.pendingOutput()) == null) {
            return 0;
        }
        if (state.hasActiveFuel()) {
            return definition.energyPerTick();
        }
        SfxEnergyFuelMatch fuel = findFuelMatch(definition, state);
        if (fuel == null) {
            return 0;
        }
        if (fuel.output() != null && findOutputSlot(state, fuel.output()) == null) {
            return 0;
        }
        return definition.energyPerTick();
    }

    private record EnergyRuntimeGrid(
            UUID componentId,
            long topologyRevision,
            UUID regulatorId,
            SfxBlockAnchorKey regulatorKey,
            Set<UUID> members,
            Set<UUID> controllers,
            List<SfxBlockInstanceRecord> capacitors,
            List<SfxBlockInstanceRecord> generators,
            List<SfxBlockInstanceRecord> chargers,
            List<SfxBlockInstanceRecord> electricConsumers,
            List<SfxBlockInstanceRecord> configurableConsumers,
            List<SfxBlockInstanceRecord> configurableProducers
    ) {
    }

    private record AutoPauseCandidate(UUID instanceId, int generation, boolean configurable) {
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

    private int hiddenBufferCapacity(int visibleCapacity) {
        if (visibleCapacity <= 0) {
            return 0;
        }
        return (int) Math.ceil(visibleCapacity * storageBufferPercent() / 100.0D);
    }

    private int effectiveStorageCapacity(SfxEnergyComponentDefinition definition) {
        if (definition == null || definition.capacity() <= 0) {
            return 0;
        }
        if (definition.componentType() == SfxEnergyComponentType.CAPACITOR || definition.componentType() == SfxEnergyComponentType.GENERATOR) {
            return definition.capacity() + hiddenBufferCapacity(definition.capacity());
        }
        return definition.capacity();
    }

    private int drainCapacitorsToElectricConsumer(List<SfxEnergyNodeRef> capacitorRefs, Set<UUID> dirtyNodes, SfxBlockInstanceRecord consumer, int remainingDemand, boolean hiddenOnly) {
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

    private int drainCapacitorsToConfigurableConsumer(List<SfxEnergyNodeRef> capacitorRefs, Set<UUID> dirtyNodes, SfxBlockInstanceRecord consumer, int remainingDemand, boolean hiddenOnly) {
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

    private int drainCapacitorsToCharger(List<SfxEnergyNodeRef> capacitorRefs, Set<UUID> dirtyNodes, SfxEnergyNodeRef charger, int remainingDemand, boolean hiddenOnly) {
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


    private int hiddenStorageBaseCapacity(List<SfxEnergyNodeRef> capacitorRefs, List<SfxEnergyNodeRef> generatorRefs) {
        int total = 0;
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            total += Math.max(0, capacitor.definition().capacity());
        }
        for (SfxEnergyNodeRef generator : generatorRefs) {
            total += Math.max(0, generator.definition().capacity());
        }
        return total;
    }

    private int displayedEnergy(int stored, int capacity) {
        return Math.max(0, Math.min(stored, Math.max(0, capacity)));
    }

    private int totalStoredEnergy(List<SfxEnergyNodeRef> capacitorRefs, List<SfxEnergyNodeRef> generatorRefs, List<SfxEnergyNodeRef> chargerRefs, List<SfxBlockInstanceRecord> consumers) {
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

    private int totalCapacity(List<SfxEnergyNodeRef> capacitorRefs, List<SfxEnergyNodeRef> generatorRefs, List<SfxEnergyNodeRef> chargerRefs, List<SfxBlockInstanceRecord> consumers) {
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

    private int requestedChargerEnergy(List<SfxEnergyNodeRef> chargerRefs) {
        int total = 0;
        for (SfxEnergyNodeRef charger : chargerRefs) {
            if (!canChargeAnyInput(charger.state())) {
                continue;
            }
            int demand = Math.max(0, charger.definition().capacity() - charger.state().storedEnergy());
            total += Math.min(charger.definition().energyPerTick(), demand);
        }
        return total;
    }

    private boolean canChargeAnyInput(SfxEnergyNodeState state) {
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
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
            if (findOutputSlot(state, SfxElectricStack.fromItemStack(items, item)) != null) {
                return true;
            }
        }
        return false;
    }

    private void tickChargingBench(SfxEnergyNodeRef charger) {
        SfxEnergyNodeState state = charger.state();
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            SfxElectricStack input = state.input(slot);
            if (input == null || input.amount() != 1) {
                continue;
            }
            ItemStack item = input.toItemStack(items);
            if (!rechargeableItems.isRechargeable(item)) {
                if (items.isSfxItem(item)) {
                    moveChargingBenchInputToOutput(charger, slot, input);
                }
                return;
            }
            double currentCharge = rechargeableItems.charge(item);
            double capacity = rechargeableItems.capacity(item);
            if (currentCharge >= capacity) {
                moveChargingBenchInputToOutput(charger, slot, SfxElectricStack.fromItemStack(items, item));
                return;
            }
            double efficiency = chargingBenchEfficiency();
            if (efficiency <= 0.0D) {
                return;
            }
            double missing = capacity - currentCharge;
            int spendLimit = Math.max(1, charger.definition().energyPerTick());
            int actualSpend = Math.min(spendLimit, state.storedEnergy());
            actualSpend = Math.min(actualSpend, (int) Math.max(1, Math.ceil(missing / efficiency)));
            if (actualSpend <= 0) {
                return;
            }
            rechargeableItems.addCharge(item, actualSpend * efficiency);
            state.storedEnergy(state.storedEnergy() - actualSpend);
            state.input(slot, SfxElectricStack.fromItemStack(items, item));
            dirtyNodes.add(charger.instance().instanceId());
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
        Integer outputSlot = findOutputSlot(charger.state(), stack);
        if (outputSlot == null) {
            return;
        }
        charger.state().input(inputSlot, null);
        pushOutput(charger.state(), outputSlot, stack);
        dirtyNodes.add(charger.instance().instanceId());
    }

    private int generate(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
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
            Integer outputSlot = findOutputSlot(state, state.pendingOutput());
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
            if (fuel.output() != null && findOutputSlot(state, fuel.output()) == null) {
                return 0;
            }
            consumeInput(state, fuel.inputSlot(), fuel.input().amount());
            state.activeFuelKey(fuel.key());
            state.fuelProgressTenths(0);
            state.fuelTotalTenths(fuel.totalTenths());
            if (shouldReturnFuelOutputImmediately(definition, fuel.output())) {
                Integer outputSlot = findOutputSlot(state, fuel.output());
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
                Integer outputSlot = findOutputSlot(state, completedOutput);
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

    private SfxEnergyFuelMatch findFuelMatch(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
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

    private void scheduleCapacitorAppearanceUpdate(SfxEnergyNodeRef capacitor) {
        capacitorProjector.scheduleUpdate(
                capacitor.instance().instanceId(),
                toLocation(capacitor.instance().anchorKey()),
                displayedEnergy(capacitor.state().storedEnergy(), capacitor.definition().capacity()),
                capacitor.definition().capacity());
    }

    private void displayStatus(SfxBlockAnchorKey regulatorKey, SfxEnergyGridStatus status, int supply, int consumption, int net, int totalStored, int totalCapacity) {
        displayController.displayStatus(regulatorKey, status, supply, consumption, net, totalStored, totalCapacity);
    }

    private void openGenerator(Player player, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition) {
        SfxEnergyGeneratorSession existing = sessionsByInstance.get(instance.instanceId());
        if (existing != null && !existing.viewerId().equals(player.getUniqueId())) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.busy", "<red>This machine is already open.</red>")));
            return;
        }
        SfxEnergyGeneratorSession previous = sessionsByViewer.remove(player.getUniqueId());
        if (previous != null) {
            sessionsByInstance.remove(previous.instanceId());
            syncSessionState(previous);
        }

        SfxEnergyNodeState state = currentState(instance.instanceId(), instance);
        Component title = localization.itemName(definition.id(), Component.text(definition.id()));
        Inventory inventory = plugin.getServer().createInventory(new SfxEnergyGeneratorHolder(instance.instanceId()), INVENTORY_SIZE, title);
        SfxEnergyGeneratorSession session = new SfxEnergyGeneratorSession(player.getUniqueId(), instance.instanceId(), inventory);
        sessionsByViewer.put(player.getUniqueId(), session);
        sessionsByInstance.put(instance.instanceId(), session);
        activeNodes.add(instance.instanceId());
        render(session, instance, definition, inventory, state);
        player.openInventory(inventory);
    }

    private void refreshSession(UUID instanceId) {
        SfxEnergyGeneratorSession session = sessionsByInstance.get(instanceId);
        if (session == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        SfxEnergyNodeState state = currentState(instanceId, instance);
        syncInventoryToState(session.inventory(), state);
        dirtyNodes.add(instanceId);
        activeNodes.add(instanceId);
        render(session, instance, definition, session.inventory(), state);
    }

    private void refreshOpenSfxEnergyGeneratorSessions() {
        for (SfxEnergyGeneratorSession session : List.copyOf(sessionsByInstance.values())) {
            SfxBlockInstanceRecord instance = blockData.findInstance(session.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition == null) {
                continue;
            }
            SfxEnergyNodeState state = currentState(instance.instanceId(), instance);
            render(session, instance, definition, session.inventory(), state);
        }
    }

    private void syncOpenSfxEnergyGeneratorSessionsToState() {
        for (SfxEnergyGeneratorSession session : List.copyOf(sessionsByInstance.values())) {
            SfxBlockInstanceRecord instance = blockData.findInstance(session.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyNodeState state = currentState(session.instanceId(), instance);
            syncInventoryToState(session.inventory(), state);
            dirtyNodes.add(session.instanceId());
        }
    }

    private void syncSessionState(SfxEnergyGeneratorSession session) {
        SfxBlockInstanceRecord instance = blockData.findInstance(session.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyNodeState state = currentState(session.instanceId(), instance);
        syncInventoryToState(session.inventory(), state);
        dirtyNodes.add(session.instanceId());
    }

    private SfxMachineStatusKey generatorRenderStatus(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        SfxEnergyGridStatus gridStatus = nodeGridStatuses.get(instance.instanceId());
        if (gridStatus == SfxEnergyGridStatus.SHARED_NODE_CONFLICT || gridStatus == SfxEnergyGridStatus.MULTIPLE_REGULATORS) {
            return SfxMachineStatusKey.NETWORK_CONFLICT;
        }
        boolean connected = gridStatus == SfxEnergyGridStatus.ONLINE;
        SfxEnergyFuelMatch fuelMatch = definition.isSolarGenerator() ? null : findFuelMatch(definition, state);
        boolean hasFuelLoaded = definition.isSolarGenerator() || state.hasActiveFuel() || fuelMatch != null;
        if (!connected && hasFuelLoaded) {
            return SfxMachineStatusKey.NO_NETWORK;
        }
        if (state.hasPendingOutput() && findOutputSlot(state, state.pendingOutput()) == null) {
            return SfxMachineStatusKey.OUTPUT_FULL;
        }
        if (!state.hasActiveFuel() && fuelMatch != null && fuelMatch.output() != null && findOutputSlot(state, fuelMatch.output()) == null) {
            return SfxMachineStatusKey.OUTPUT_FULL;
        }
        if (!state.hasActiveFuel()) {
            return SfxMachineStatusKey.IDLE;
        }
        return SfxMachineStatusKey.WORKING;
    }

    private void render(SfxEnergyGeneratorSession session, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state) {
        generatorMenuRenderer.render(definition, inventory, state, generatorRenderStatus(instance, definition, state));
    }

    private void syncInventoryToState(Inventory inventory, SfxEnergyNodeState state) {
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            state.input(i, SfxElectricStack.fromItemStack(items, inventory.getItem(INPUT_SLOTS[i])));
        }
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            state.output(i, SfxElectricStack.fromItemStack(items, inventory.getItem(OUTPUT_SLOTS[i])));
        }
    }

    private SfxEnergyNodeState currentState(UUID instanceId, SfxBlockInstanceRecord instance) {
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

    private Integer findOutputSlot(SfxEnergyNodeState state, SfxElectricStack output) {
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            SfxElectricStack current = state.output(slot);
            if (current != null && output.canMerge(current, items)) {
                return slot;
            }
        }
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            if (state.output(slot) == null) {
                return slot;
            }
        }
        return null;
    }

    private void pushOutput(SfxEnergyNodeState state, int slot, SfxElectricStack output) {
        SfxElectricStack current = state.output(slot);
        if (current == null) {
            state.output(slot, output);
            return;
        }
        state.output(slot, current.copyWithAmount(current.amount() + output.amount()));
    }

    private boolean moveShiftClickedStackToInputs(Inventory topInventory, ItemStack current, SfxEnergyComponentDefinition definition) {
        return SfxInventorySlots.moveStackToSlots(topInventory, INPUT_SLOTS, current, (slot, stack) -> !definition.isCharger() || isValidChargingBenchInput(stack));
    }

    private boolean isValidChargingBenchInput(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        return !rechargeableItems.isRechargeable(item) || item.getAmount() == 1;
    }

    private boolean isTakingFromOutput(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentItem = current != null && !current.getType().isAir();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        return currentItem && (cursorEmpty || event.isShiftClick());
    }

    private SfxEnergyComponentDefinition definitionFor(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        return instance == null ? null : definitions.get(instance.typeId());
    }

    private boolean contains(int[] slots, int value) {
        for (int slot : slots) {
            if (slot == value) {
                return true;
            }
        }
        return false;
    }


    private boolean isInstanceChunkLoaded(SfxBlockInstanceRecord instance) {
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

    private Location toLocation(SfxBlockAnchorKey key) {
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

    public record SfxEnergyInspection(
            String typeId,
            SfxEnergyComponentType componentType,
            int storedEnergy,
            int capacity,
            int generationPerTick,
            boolean connected,
            boolean autoPaused
    ) {
    }

    public record SfxEnergyGridInspection(
            SfxBlockAnchorKey regulatorKey,
            SfxEnergyGridStatus status,
            int members,
            int generators,
            int reactors,
            int capacitors,
            int connectors,
            int consumers,
            int storedEnergy,
            int capacity,
            int generationPerTick,
            int consumptionPerTick,
            int autoPaused
    ) {
    }




}
