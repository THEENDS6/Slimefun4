package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.bukkit.entity.Item;
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
    private final SfxEnergyGridBuilder gridBuilder;
    private final Map<String, SfxEnergyComponentDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, SfxEnergyNodeState> nodeStates = new ConcurrentHashMap<>();
    private final Map<UUID, SfxEnergyGridStatus> nodeGridStatuses = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyNodes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeNodes = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SfxEnergyGeneratorSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, SfxEnergyGeneratorSession> sessionsByInstance = new ConcurrentHashMap<>();
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
            SfxFloatingTextDisplayService floatingTextDisplay
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.configurableMachines = Objects.requireNonNull(configurableMachines, "configurableMachines");
        this.displayController = new SfxEnergyDisplayController(plugin, localization, Objects.requireNonNull(floatingTextDisplay, "floatingTextDisplay"));
        this.capacitorProjector = new SfxCapacitorAppearanceProjector(runtime, blockData, definitions);
        this.generatorMenuRenderer = new SfxEnergyGeneratorMenuRenderer(items, localization);
        this.definitions.putAll(SfxEnergyDefinitions.create(plugin));
        this.gridBuilder = new SfxEnergyGridBuilder(blockData, definitions, electricMachines, configurableMachines, RANGE);
        bootstrapLoadedStates();
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
        if (event.getPlayer().isSneaking()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        SfxAnchorRecord anchor = blockData.findAnchor(clicked.getLocation()).orElse(null);
        if (anchor == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        event.setCancelled(true);
        if (definition.isFueledGenerator()) {
            runtime.executeForPlayer(event.getPlayer(), () -> openGenerator(event.getPlayer(), instance, definition));
            return;
        }
        SfxEnergyNodeState state = currentState(instance.instanceId(), instance);
        if (definition.componentType() == SfxEnergyComponentType.CAPACITOR) {
            return;
        } else if (definition.componentType() == SfxEnergyComponentType.REGULATOR) {
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text(
                    "energy.messages.regulator-status",
                    "<yellow>Energy regulator active.</yellow>")));
        } else {
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text(
                    "energy.messages.connector-status",
                    "<yellow>Energy connector linked to nearby networks.</yellow>")));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxEnergyGeneratorHolder holder)) {
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }
        boolean topSlot = event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (event.isShiftClick() && !topSlot) {
            if (moveShiftClickedStackToInputs(event.getView().getTopInventory(), event.getCurrentItem())) {
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
        if (topSlot && contains(OUTPUT_SLOTS, event.getRawSlot()) && event.isShiftClick()) {
            runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            return;
        }
        if (topSlot && !contains(INPUT_SLOTS, event.getRawSlot()) && !contains(OUTPUT_SLOTS, event.getRawSlot())) {
            event.setCancelled(true);
            return;
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
        boolean onlyEditable = event.getRawSlots().stream()
                .filter(slot -> slot < topSize)
                .allMatch(slot -> contains(INPUT_SLOTS, slot) || contains(OUTPUT_SLOTS, slot));
        if (!onlyEditable) {
            event.setCancelled(true);
            return;
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
        nodeStates.clear();
        nodeGridStatuses.clear();
        dirtyNodes.clear();
        activeNodes.clear();
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
        Map<UUID, SfxEnergyGridResult> results = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> memberships = new HashMap<>();
        nodeGridStatuses.clear();

        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition == null || definition.componentType() != SfxEnergyComponentType.REGULATOR) {
                continue;
            }
            SfxEnergyGridResult result = gridBuilder.buildGrid(instance.instanceId(), anchor.key());
            results.put(instance.instanceId(), result);
            for (UUID memberId : result.members()) {
                memberships.computeIfAbsent(memberId, ignored -> new LinkedHashSet<>()).add(instance.instanceId());
            }
        }

        Set<UUID> sharedMembers = new LinkedHashSet<>();
        memberships.forEach((member, regulators) -> {
            if (regulators.size() > 1) {
                sharedMembers.add(member);
            }
        });

        for (SfxEnergyGridResult result : results.values()) {
            SfxEnergyGridStatus status = result.status();
            if (status == SfxEnergyGridStatus.ONLINE && result.members().stream().anyMatch(sharedMembers::contains)) {
                status = SfxEnergyGridStatus.SHARED_NODE_CONFLICT;
            }
            for (UUID memberId : result.members()) {
                nodeGridStatuses.put(memberId, status);
            }
            if (status == SfxEnergyGridStatus.ONLINE) {
                processGrid(result);
            } else {
                displayStatus(result.regulatorKey(), status, 0, 0, 0, 0, 0);
            }
        }
    }

    private void processGrid(SfxEnergyGridResult result) {
        int available = 0;
        int supply = 0;
        List<SfxEnergyNodeRef> capacitorRefs = new ArrayList<>();
        List<SfxEnergyNodeRef> generatorRefs = new ArrayList<>();
        List<SfxBlockInstanceRecord> electricConsumers = new ArrayList<>();
        List<SfxBlockInstanceRecord> configurableConsumers = new ArrayList<>();
        List<SfxBlockInstanceRecord> configurableProducers = new ArrayList<>();
        List<UUID> electricConsumerIds = new ArrayList<>();
        List<UUID> configurableConsumerIds = new ArrayList<>();

        for (UUID memberId : result.members()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(memberId).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition != null) {
                SfxEnergyNodeState state = currentState(memberId, instance);
                switch (definition.componentType()) {
                    case CAPACITOR -> capacitorRefs.add(new SfxEnergyNodeRef(instance, definition, state));
                    case GENERATOR -> generatorRefs.add(new SfxEnergyNodeRef(instance, definition, state));
                    case REGULATOR, CONNECTOR -> {
                    }
                }
            } else if (electricMachines.supportsType(instance.typeId())) {
                electricConsumers.add(instance);
                electricConsumerIds.add(instance.instanceId());
            } else if (configurableMachines.isConsumer(instance.typeId())) {
                configurableConsumers.add(instance);
                configurableConsumerIds.add(instance.instanceId());
            } else if (configurableMachines.isProducer(instance.typeId())) {
                configurableProducers.add(instance);
            }
        }

        sortCapacitors(capacitorRefs);

        for (SfxEnergyNodeRef generator : generatorRefs) {
            if (generator.state().storedEnergy() > 0) {
                available += generator.state().storedEnergy();
                generator.state().storedEnergy(0);
                dirtyNodes.add(generator.instance().instanceId());
            }
            int produced = generate(generator.instance(), generator.definition(), generator.state());
            available += produced;
            supply += produced;
        }

        for (SfxBlockInstanceRecord producer : configurableProducers) {
            int produced = configurableMachines.drainProducerEnergy(producer.instanceId());
            if (produced > 0) {
                available += produced;
                supply += produced;
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

        for (SfxBlockInstanceRecord consumer : electricConsumers) {
            int remainingDemand = Math.max(0, electricMachines.consumerCapacity(consumer.typeId()) - electricMachines.consumerStoredEnergy(consumer.instanceId()));
            if (remainingDemand <= 0) {
                continue;
            }
            for (SfxEnergyNodeRef capacitor : capacitorRefs) {
                if (remainingDemand <= 0) {
                    break;
                }
                int stored = capacitor.state().storedEnergy();
                if (stored <= 0) {
                    continue;
                }
                int offered = Math.min(stored, remainingDemand);
                int accepted = electricMachines.chargeConsumer(consumer.instanceId(), offered);
                if (accepted <= 0) {
                    break;
                }
                capacitor.state().storedEnergy(stored - accepted);
                dirtyNodes.add(capacitor.instance().instanceId());
                remainingDemand -= accepted;
            }
        }
        for (SfxBlockInstanceRecord consumer : configurableConsumers) {
            int remainingDemand = Math.max(0, configurableMachines.consumerCapacity(consumer.typeId()) - configurableMachines.consumerStoredEnergy(consumer.instanceId()));
            if (remainingDemand <= 0) {
                continue;
            }
            for (SfxEnergyNodeRef capacitor : capacitorRefs) {
                if (remainingDemand <= 0) {
                    break;
                }
                int stored = capacitor.state().storedEnergy();
                if (stored <= 0) {
                    continue;
                }
                int offered = Math.min(stored, remainingDemand);
                int accepted = configurableMachines.chargeConsumer(consumer.instanceId(), offered);
                if (accepted <= 0) {
                    break;
                }
                capacitor.state().storedEnergy(stored - accepted);
                dirtyNodes.add(capacitor.instance().instanceId());
                remainingDemand -= accepted;
            }
        }

        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            if (available <= 0) {
                break;
            }
            int stored = capacitor.state().storedEnergy();
            int accepted = Math.max(0, Math.min(available, capacitor.definition().capacity() - stored));
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
            int accepted = Math.max(0, Math.min(available, generator.definition().capacity() - stored));
            if (accepted > 0) {
                generator.state().storedEnergy(stored + accepted);
                dirtyNodes.add(generator.instance().instanceId());
                available -= accepted;
            }
        }

        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            scheduleCapacitorAppearanceUpdate(capacitor);
        }

        int requestedConsumption = electricMachines.requestedEnergyConsumption(electricConsumerIds)
                + configurableMachines.requestedEnergyConsumption(configurableConsumerIds);
        electricMachines.drainRecentEnergyConsumption(result.members());
        configurableMachines.drainRecentEnergyConsumption(new ArrayList<>(result.members()));
        int totalStored = totalStoredEnergy(capacitorRefs, generatorRefs, electricConsumers)
                + configurableMachines.totalStoredEnergy(join(configurableConsumers, configurableProducers));
        int totalCapacity = totalCapacity(capacitorRefs, generatorRefs, electricConsumers)
                + configurableMachines.totalCapacity(join(configurableConsumers, configurableProducers));
        int net = supply - requestedConsumption;
        displayStatus(result.regulatorKey(), SfxEnergyGridStatus.ONLINE, supply, requestedConsumption, net, totalStored, totalCapacity);
        refreshOpenSfxEnergyGeneratorSessions();
    }

    private List<SfxBlockInstanceRecord> join(List<SfxBlockInstanceRecord> first, List<SfxBlockInstanceRecord> second) {
        List<SfxBlockInstanceRecord> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
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

    private int totalStoredEnergy(List<SfxEnergyNodeRef> capacitorRefs, List<SfxEnergyNodeRef> generatorRefs, List<SfxBlockInstanceRecord> consumers) {
        int total = 0;
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            total += capacitor.state().storedEnergy();
        }
        for (SfxEnergyNodeRef generator : generatorRefs) {
            total += generator.state().storedEnergy();
        }
        for (SfxBlockInstanceRecord consumer : consumers) {
            total += electricMachines.consumerStoredEnergy(consumer.instanceId());
        }
        return total;
    }

    private int totalCapacity(List<SfxEnergyNodeRef> capacitorRefs, List<SfxEnergyNodeRef> generatorRefs, List<SfxBlockInstanceRecord> consumers) {
        int total = 0;
        for (SfxEnergyNodeRef capacitor : capacitorRefs) {
            total += capacitor.definition().capacity();
        }
        for (SfxEnergyNodeRef generator : generatorRefs) {
            total += generator.definition().capacity();
        }
        for (SfxBlockInstanceRecord consumer : consumers) {
            total += electricMachines.consumerCapacity(consumer.typeId());
        }
        return total;
    }

    private int generate(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
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

        if (definition.capacity() > 0 && state.storedEnergy() + definition.energyPerTick() > definition.capacity()) {
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
                toLocation(capacitor.instance().anchorKey()),
                capacitor.state().storedEnergy(),
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

    private SfxEnergyGeneratorRenderStatus generatorRenderStatus(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        SfxEnergyGridStatus gridStatus = nodeGridStatuses.get(instance.instanceId());
        if (gridStatus == SfxEnergyGridStatus.SHARED_NODE_CONFLICT || gridStatus == SfxEnergyGridStatus.MULTIPLE_REGULATORS) {
            return SfxEnergyGeneratorRenderStatus.CONFLICT;
        }
        boolean connected = gridStatus == SfxEnergyGridStatus.ONLINE;
        SfxEnergyFuelMatch fuelMatch = definition.isSolarGenerator() ? null : findFuelMatch(definition, state);
        boolean hasFuelLoaded = definition.isSolarGenerator() || state.hasActiveFuel() || fuelMatch != null;
        if (!connected && hasFuelLoaded) {
            return SfxEnergyGeneratorRenderStatus.NO_NETWORK;
        }
        if (state.hasPendingOutput() && findOutputSlot(state, state.pendingOutput()) == null) {
            return SfxEnergyGeneratorRenderStatus.OUTPUT_FULL;
        }
        if (!state.hasActiveFuel() && fuelMatch != null && fuelMatch.output() != null && findOutputSlot(state, fuelMatch.output()) == null) {
            return SfxEnergyGeneratorRenderStatus.OUTPUT_FULL;
        }
        if (!state.hasActiveFuel()) {
            return SfxEnergyGeneratorRenderStatus.IDLE;
        }
        return SfxEnergyGeneratorRenderStatus.ACTIVE;
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

    private boolean moveShiftClickedStackToInputs(Inventory topInventory, ItemStack current) {
        if (current == null || current.getType().isAir()) {
            return false;
        }
        int original = current.getAmount();
        int remaining = current.getAmount();
        for (int slot : INPUT_SLOTS) {
            ItemStack existing = topInventory.getItem(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(current)) {
                continue;
            }
            int room = existing.getMaxStackSize() - existing.getAmount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining);
            existing.setAmount(existing.getAmount() + moved);
            remaining -= moved;
            if (remaining <= 0) {
                current.setAmount(0);
                return true;
            }
        }
        for (int slot : INPUT_SLOTS) {
            ItemStack existing = topInventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            int moved = Math.min(current.getMaxStackSize(), remaining);
            ItemStack inserted = current.clone();
            inserted.setAmount(moved);
            topInventory.setItem(slot, inserted);
            remaining -= moved;
            if (remaining <= 0) {
                current.setAmount(0);
                return true;
            }
        }
        current.setAmount(remaining);
        return remaining < original;
    }

    private boolean contains(int[] slots, int value) {
        for (int slot : slots) {
            if (slot == value) {
                return true;
            }
        }
        return false;
    }

    private Location toLocation(SfxBlockAnchorKey key) {
        World world = plugin.getServer().getWorld(key.worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, key.x(), key.y(), key.z());
    }

    private void dropPluginBlock(Block block, String typeId) {
        Item dropped = block.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5), items.create(typeId));
        dropped.setPickupDelay(0);
    }

    private void dropStack(Block block, SfxElectricStack stack) {
        if (stack == null) {
            return;
        }
        Item dropped = block.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5), stack.toItemStack(items));
        dropped.setPickupDelay(0);
    }




}
