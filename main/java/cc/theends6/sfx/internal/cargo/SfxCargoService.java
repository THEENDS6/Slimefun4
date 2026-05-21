package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxAnchoredInteraction;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayMode;
import cc.theends6.sfx.internal.display.SfxFloatingTextKey;
import cc.theends6.sfx.internal.display.SfxFloatingTextProjection;
import cc.theends6.sfx.internal.topology.SfxTopologyComponent;
import cc.theends6.sfx.internal.topology.SfxTopologyService;
import cc.theends6.sfx.internal.topology.SfxTopologyStatus;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxEventGuards;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import org.bukkit.Color;
import org.bukkit.Particle;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainer;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService.PlannedStack;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxCargoService implements Listener {
    private static final int RANGE = 6;
    private static final int FILTER_INVENTORY_SIZE = 54;
    private static final int OUTPUT_INVENTORY_SIZE = 27;
    private static final int TRASH_INVENTORY_SIZE = 27;
    private static final long TICK_INTERVAL = 10L;
    private static final long FLUSH_INTERVAL = 20L;
    private static final int[] FILTER_SLOTS = {19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final Set<Integer> FILTER_SLOT_SET = Set.of(19, 20, 21, 28, 29, 30, 37, 38, 39);
    private static final int[] INPUT_BORDER = {0, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 17, 18, 22, 23, 26, 27, 31, 32, 33, 34, 35, 36, 40, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
    private static final int[] ADVANCED_OUTPUT_BORDER = {0, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 18, 22, 23, 24, 26, 27, 31, 32, 33, 34, 35, 36, 40, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
    private static final int[] OUTPUT_BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int[] TRASH_INPUT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final Set<Integer> TRASH_INPUT_SLOT_SET = Set.of(10, 11, 12, 13, 14, 15, 16);
    private static final int[] TRASH_BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int[] CHANNEL_INPUT_SLOTS = {41, 42, 43};
    private static final int[] CHANNEL_OUTPUT_SLOTS = {12, 13, 14};

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxVirtualContainerService virtualContainers;
    private final SfxFloatingTextDisplayService floatingText;
    private final Set<SfxFloatingTextKey> displayKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, SfxCargoComponentDefinition> definitions = SfxCargoDefinitions.create();
    private final SfxTopologyService topology;
    private final Map<UUID, SfxCargoNodeState> states = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyStates = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Inventory> openMenus = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Integer>> distributionDebt = new ConcurrentHashMap<>();
    private final Map<UUID, CargoRuntimeNetwork> runtimeNetworks = new ConcurrentHashMap<>();
    private volatile long cargoStateRevision;
    private final Map<UUID, UUID> visualizers = new ConcurrentHashMap<>();
    private final Map<UUID, CargoTransferStats> managerStats = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public SfxCargoService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization, SfxBlockDataService blockData, SfxVirtualContainerService virtualContainers, SfxFloatingTextDisplayService floatingText) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.virtualContainers = Objects.requireNonNull(virtualContainers, "virtualContainers");
        this.floatingText = Objects.requireNonNull(floatingText, "floatingText");
        this.topology = new SfxTopologyService(blockData, new SfxCargoTopologyPolicy(definitions), new SfxCargoConnectivityPolicy(RANGE));
        bootstrapLoadedStates();
        topology.rebuild();
        scheduleTopologyRefresh();
        scheduleTick();
        scheduleFlush();
    }

    public boolean supportsType(String typeId) {
        return definitions.containsKey(typeId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (virtualContainers.findRegistered(event.getBlock().getLocation()).isPresent()) {
            runtimeNetworks.clear();
        }
    }

    public boolean canPlace(String typeId, BlockPlaceEvent event) {
        SfxCargoComponentDefinition definition = definitions.get(typeId);
        if (definition == null) {
            return true;
        }
        if (!definition.isTerminal()) {
            return true;
        }
        BlockFace face = attachedFace(event);
        return face != null && isHorizontal(face);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            SfxCargoComponentDefinition definition = definitions.get(marker.itemId());
            if (definition == null) {
                return;
            }
            UUID instanceId = blockData.findAnchor(event.getBlockPlaced().getLocation())
                    .map(SfxAnchorRecord::instanceId)
                    .orElseGet(() -> blockData.registerSingleBlock(marker.itemId(), event.getBlockPlaced().getLocation(), event.getBlockPlaced().getType(), event.getPlayer().getUniqueId()));
            BlockFace face = attachedFace(event);
            SfxCargoNodeState state = SfxCargoNodeState.defaultFor(definition.type(), face == null ? BlockFace.NORTH : face);
            states.put(instanceId, state);
            dirtyStates.add(instanceId);
            persistState(instanceId, state);
            scheduleTopologyRefresh();
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
        SfxCargoComponentDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        if (definition.type() == SfxCargoComponentType.CONNECTOR && SfxInteractionRules.isPlaceableHeldItem(items, event.getItem())) {
            return;
        }
        if (SfxInteractionRules.prefersBlockPlacement(items, event)) {
            return;
        }
        SfxEventGuards.denyBlockAndItemUse(event);
        runtime.executeForPlayer(event.getPlayer(), () -> handleInteract(event.getPlayer(), interaction.block(), instance, definition));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SfxTrashCanHolder) {
            boolean topSlot = event.getRawSlot() < top.getSize();
            if (topSlot) {
                if (!TRASH_INPUT_SLOT_SET.contains(event.getRawSlot())) {
                    event.setCancelled(true);
                }
                runtime.executeForPlayerLater(player, 1L, () -> clearTrash(top));
                return;
            }
            if (event.isShiftClick() && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                event.setCurrentItem(null);
                event.setCancelled(true);
            }
            runtime.executeForPlayerLater(player, 1L, () -> clearTrash(top));
            return;
        }
        if (!(top.getHolder() instanceof SfxCargoSessionHolder holder)) {
            return;
        }
        SfxCargoComponentDefinition definition = typeDefinition(holder.type());
        if (definition == null) {
            return;
        }

        if (usesFilter(holder.type())) {
            if (event.getClickedInventory() == top && FILTER_SLOT_SET.contains(event.getRawSlot())) {
                runtime.executeForPlayerLater(player, 1L, () -> saveFilterFromOpenMenu(holder.instanceId(), top));
                return;
            }
            if (event.getClickedInventory() != top) {
                runtime.executeForPlayerLater(player, 1L, () -> saveFilterFromOpenMenu(holder.instanceId(), top));
                return;
            }
        } else if (event.getClickedInventory() != top) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            return;
        }
        SfxCargoNodeState state = currentState(holder.instanceId());
        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        boolean changed = false;
        if (isPrevChannelSlot(holder.type(), slot)) {
            adjustChannel(state, -1, click.isShiftClick());
            changed = true;
        } else if (isNextChannelSlot(holder.type(), slot)) {
            adjustChannel(state, 1, click.isShiftClick());
            changed = true;
        } else if (slot == 15 && usesFilter(holder.type())) {
            state.filterMode = state.filterMode.toggle();
            changed = true;
        } else if (slot == 25 && usesFilter(holder.type())) {
            state.matchLore = !state.matchLore;
            changed = true;
        } else if (slot == 16 && (holder.type() == SfxCargoComponentType.INPUT_NODE || holder.type() == SfxCargoComponentType.ADVANCED_INPUT_NODE)) {
            state.smartFill = !state.smartFill;
            changed = true;
        } else if (slot == 24 && holder.type() == SfxCargoComponentType.INPUT_NODE) {
            state.roundRobin = !state.roundRobin;
            changed = true;
        } else if (slot == 14 && holder.type() == SfxCargoComponentType.ADVANCED_INPUT_NODE) {
            state.allowMultipleSlots = !state.allowMultipleSlots;
            changed = true;
        } else if (slot == 23 && holder.type() == SfxCargoComponentType.ADVANCED_INPUT_NODE) {
            state.batchLimit = SfxCargoNodeState.nextBatchLimit(state.batchLimit, click.isRightClick());
            state.maxItemsPerCycle = state.batchLimit;
            changed = true;
        } else if (slot == 24 && holder.type() == SfxCargoComponentType.ADVANCED_INPUT_NODE) {
            state.distributionMode = click.isRightClick() ? state.distributionMode.previous() : state.distributionMode.next();
            changed = true;
        } else if (slot == 16 && holder.type() == SfxCargoComponentType.ADVANCED_OUTPUT_NODE) {
            adjustPriority(state, click.isRightClick() ? -1 : 1, click.isShiftClick());
            changed = true;
        }
        if (!changed) {
            return;
        }
        syncFilterFromInventory(top, state);
        persistState(holder.instanceId(), state);
        renderMenu(top, holder.instanceId(), holder.type(), state);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SfxTrashCanHolder) {
            for (int raw : event.getRawSlots()) {
                if (raw < top.getSize() && !TRASH_INPUT_SLOT_SET.contains(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (event.getWhoClicked() instanceof Player player) {
                runtime.executeForPlayerLater(player, 1L, () -> clearTrash(top));
            } else {
                clearTrash(top);
            }
            return;
        }
        if (!(top.getHolder() instanceof SfxCargoSessionHolder holder)) {
            return;
        }
        if (!usesFilter(holder.type())) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize() && !FILTER_SLOT_SET.contains(raw)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            runtime.executeForPlayerLater(player, 1L, () -> saveFilterFromOpenMenu(holder.instanceId(), top));
        } else {
            saveFilterFromOpenMenu(holder.instanceId(), top);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SfxCargoSessionHolder holder) {
            SfxCargoNodeState state = currentState(holder.instanceId());
            syncFilterFromInventory(top, state);
            persistState(holder.instanceId(), state);
            openMenus.remove(holder.instanceId());
        } else if (top.getHolder() instanceof SfxTrashCanHolder holder) {
            clearTrash(top);
            openMenus.remove(holder.instanceId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        visualizers.remove(event.getPlayer().getUniqueId());
        // Bukkit will emit InventoryCloseEvent for normal disconnects; this branch only
        // removes stale menu references if another plugin cancels or replaces close flow.
        openMenus.entrySet().removeIf(entry -> {
            Inventory inventory = entry.getValue();
            return inventory.getViewers().stream().noneMatch(viewer -> viewer.getUniqueId().equals(event.getPlayer().getUniqueId()));
        });
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (block == null || instanceId == null || typeId == null || !definitions.containsKey(typeId)) {
            return;
        }
        Inventory open = openMenus.remove(instanceId);
        if (open != null) {
            for (var viewer : List.copyOf(open.getViewers())) {
                viewer.closeInventory();
            }
        }
        SfxFloatingTextKey displayKey = displayKey(SfxBlockAnchorKey.fromLocation(block.getLocation()));
        floatingText.remove(displayKey);
        displayKeys.remove(displayKey);
        SfxCargoNodeState state = states.get(instanceId);
        if (state == null) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            state = instance == null ? new SfxCargoNodeState() : decodeState(instance);
        }
        SfxCargoComponentDefinition definition = definitions.get(typeId);
        if (definition != null && usesFilter(definition.type())) {
            for (ItemStack stack : state.filterItems) {
                dropStack(block, stack);
            }
        }
        dropPluginBlock(block, typeId);
        states.remove(instanceId);
        dirtyStates.remove(instanceId);
        blockData.unregisterAt(block.getLocation());
        scheduleTopologyRefresh();
    }

    public void shutdown() {
        running = false;
        for (Inventory inventory : List.copyOf(openMenus.values())) {
            for (var viewer : List.copyOf(inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
        flushDirty();
        for (SfxFloatingTextKey key : Set.copyOf(displayKeys)) {
            floatingText.remove(key);
            displayKeys.remove(key);
        }
        openMenus.clear();
        states.clear();
        distributionDebt.clear();
        runtimeNetworks.clear();
        visualizers.clear();
        managerStats.clear();
    }

    private void bootstrapLoadedStates() {
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !definitions.containsKey(instance.typeId())) {
                continue;
            }
            states.put(instance.instanceId(), decodeState(instance));
        }
    }

    private void scheduleTick() {
        runtime.executeGlobalLater(TICK_INTERVAL, () -> {
            if (!running) {
                return;
            }
            tickCargo();
            clearOpenTrashMenus();
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

    private void scheduleTopologyRefresh() {
        runtime.executeGlobalLater(1L, () -> {
            if (!running) {
                return;
            }
            topology.rebuild();
            updateManagerDisplays();
        });
    }

    private void tickCargo() {
        virtualContainers.hydrateExternalBeforeLogic();
        topology.rebuildIfStale();
        updateManagerDisplays();
        for (SfxTopologyComponent component : topology.components()) {
            if (component.status() != SfxTopologyStatus.ONLINE || component.controllers().size() != 1 || component.terminals().isEmpty()) {
                continue;
            }
            UUID managerId = component.controllers().iterator().next();
            SfxBlockInstanceRecord manager = blockData.findInstance(managerId).orElse(null);
            Location managerLocation = manager == null ? null : toLocation(manager.anchorKey());
            if (managerLocation == null) {
                continue;
            }
            CargoRuntimeNetwork network = runtimeNetworkFor(component, managerId);
            if (network == null || network.inputs().isEmpty() || network.outputs().isEmpty()) {
                continue;
            }
            runtime.executeAt(managerLocation, () -> processCargoNetwork(network));
        }
        renderVisualizers();
    }

    private CargoRuntimeNetwork runtimeNetworkFor(SfxTopologyComponent component, UUID managerId) {
        if (component == null || managerId == null) {
            return null;
        }
        CargoRuntimeNetwork cached = runtimeNetworks.get(component.componentId());
        if (cached != null
                && cached.topologyRevision() == component.topologyRevision()
                && cached.cargoStateRevision() == cargoStateRevision
                && cached.containerRegistryRevision() == virtualContainers.registryRevision()
                && cached.managerId().equals(managerId)) {
            return cached;
        }
        Map<EndpointCacheKey, Optional<Endpoint>> endpointCache = new HashMap<>();
        List<NodeRef> inputs = new ArrayList<>();
        List<NodeRef> outputs = new ArrayList<>();
        for (UUID terminalId : component.terminals()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(terminalId).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxCargoComponentDefinition definition = definitions.get(instance.typeId());
            if (definition == null) {
                continue;
            }
            SfxCargoNodeState state = currentState(terminalId);
            if (definition.isInput()) {
                Endpoint endpoint = resolveEndpoint(instance, state, false, endpointCache);
                if (endpoint != null && endpoint.container != null) {
                    inputs.add(new NodeRef(instance, definition, state, endpoint));
                }
            } else if (definition.isOutput()) {
                Endpoint endpoint = resolveEndpoint(instance, state, true, endpointCache);
                if (endpoint != null) {
                    outputs.add(new NodeRef(instance, definition, state, endpoint));
                }
            }
        }
        inputs.sort(Comparator.comparing(ref -> ref.instance.anchorKey(), this::compareAnchorKeys));
        outputs.sort(Comparator.comparing((NodeRef ref) -> ref.state.priority).reversed().thenComparing(ref -> ref.instance.anchorKey(), this::compareAnchorKeys));
        CargoRuntimeNetwork network = new CargoRuntimeNetwork(
                component.componentId(),
                component.topologyRevision(),
                cargoStateRevision,
                virtualContainers.registryRevision(),
                managerId,
                List.copyOf(inputs),
                List.copyOf(outputs));
        runtimeNetworks.put(component.componentId(), network);
        return network;
    }

    private void processCargoNetwork(CargoRuntimeNetwork network) {
        if (!running || network == null) {
            return;
        }
        for (NodeRef input : network.inputs()) {
            int moved = input.definition.type() == SfxCargoComponentType.ADVANCED_INPUT_NODE
                    ? processAdvancedInput(input, network.outputs())
                    : processBasicInput(input, network.outputs());
            if (moved > 0) {
                recordManagerTransfer(network.managerId(), moved);
            }
        }
        virtualContainers.pushDirtyAfterLogic();
    }

    private void recordManagerTransfer(UUID managerId, int amount) {
        if (managerId == null || amount <= 0) {
            return;
        }
        managerStats.computeIfAbsent(managerId, ignored -> new CargoTransferStats()).record(amount);
    }

    private void renderVisualizers() {
        if (!plugin.getConfig().getBoolean("cargo.visualizer.enabled", true) || visualizers.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, UUID> entry : List.copyOf(visualizers.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                visualizers.remove(entry.getKey());
                continue;
            }
            UUID managerId = entry.getValue();
            runtime.executeForPlayer(player, () -> renderVisualizerFor(player, managerId));
        }
    }

    private void renderVisualizerFor(Player player, UUID managerId) {
        if (player == null || !player.isOnline() || managerId == null || !plugin.getConfig().getBoolean("cargo.visualizer.enabled", true)) {
            return;
        }
        SfxTopologyComponent component = topology.componentForMember(managerId).orElse(null);
        if (component == null) {
            return;
        }
        boolean includeManager = plugin.getConfig().getBoolean("cargo.visualizer.include-manager", false);
        for (UUID memberId : component.members()) {
            if (!includeManager && memberId.equals(managerId)) {
                continue;
            }
            SfxBlockInstanceRecord member = blockData.findInstance(memberId).orElse(null);
            if (member == null || !definitions.containsKey(member.typeId())) {
                continue;
            }
            spawnVisualizerParticle(player, member.anchorKey());
        }
    }

    private void spawnVisualizerParticle(Player player, SfxBlockAnchorKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || player.getWorld() != world) {
            return;
        }
        Location location = new Location(world, key.x() + 0.5D, key.y() + 0.5D, key.z() + 0.5D);
        if (location.distanceSquared(player.getLocation()) > plugin.getConfig().getInt("cargo.visualizer.view-distance-squared", 32 * 32)) {
            return;
        }
        Particle particle = visualizerParticle();
        if (particle == Particle.DUST) {
            int red = SfxCargoNodeState.clamp(plugin.getConfig().getInt("cargo.visualizer.dust.red", 255), 0, 255);
            int green = SfxCargoNodeState.clamp(plugin.getConfig().getInt("cargo.visualizer.dust.green", 0), 0, 255);
            int blue = SfxCargoNodeState.clamp(plugin.getConfig().getInt("cargo.visualizer.dust.blue", 0), 0, 255);
            float size = (float) Math.max(0.1D, plugin.getConfig().getDouble("cargo.visualizer.dust.size", 1.0D));
            player.spawnParticle(Particle.DUST, location, 1, 0.0D, 0.0D, 0.0D, 0.0D, new Particle.DustOptions(Color.fromRGB(red, green, blue), size));
            return;
        }
        try {
            player.spawnParticle(particle, location, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        } catch (RuntimeException ignored) {
            player.spawnParticle(Particle.DUST, location, 1, 0.0D, 0.0D, 0.0D, 0.0D, new Particle.DustOptions(Color.RED, 1.0F));
        }
    }

    private Particle visualizerParticle() {
        String raw = plugin.getConfig().getString("cargo.visualizer.particle", "dust");
        String normalized = raw == null ? "dust" : raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        if (normalized.isBlank() || "REDSTONE".equals(normalized)) {
            normalized = "DUST";
        }
        try {
            return Particle.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return Particle.DUST;
        }
    }

    private void updateManagerDisplays() {
        Set<SfxFloatingTextKey> seen = new LinkedHashSet<>();
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !"sf:cargo_manager".equals(instance.typeId())) {
                continue;
            }
            SfxTopologyComponent component = topology.componentForMember(instance.instanceId()).orElse(null);
            Component text = managerDisplayText(component);
            SfxFloatingTextKey key = displayKey(instance.anchorKey());
            seen.add(key);
            displayKeys.add(key);
            floatingText.update(new SfxFloatingTextProjection(
                    key,
                    instance.anchorKey().x() + 0.5D,
                    instance.anchorKey().y() + 1.15D,
                    instance.anchorKey().z() + 0.5D,
                    text,
                    plugin.getConfig().getInt("cargo.display.view-distance-squared", 32 * 32),
                    plugin.getConfig().getBoolean("cargo.display.see-through", false),
                    SfxFloatingTextDisplayMode.ARMOR_STAND));
        }
        for (SfxFloatingTextKey key : Set.copyOf(displayKeys)) {
            if (!seen.contains(key)) {
                floatingText.remove(key);
                displayKeys.remove(key);
            }
        }
    }

    private Component managerDisplayText(SfxTopologyComponent component) {
        if (component != null && (component.status() == SfxTopologyStatus.MULTIPLE_CONTROLLERS || component.controllers().size() > 1)) {
            return lc("cargo.status.multiple-regulators", "&cMultiple Cargo Regulators connected");
        }
        if (component == null || component.members().size() <= 1) {
            return lc("cargo.status.no-nodes", "&cNo Cargo Nodes found");
        }
        return lc("cargo.status.online", "&aStatus: ONLINE");
    }

    private SfxFloatingTextKey displayKey(SfxBlockAnchorKey key) {
        return new SfxFloatingTextKey("cargo-manager", key.worldId(), key.x(), key.y(), key.z());
    }

    private int processBasicInput(NodeRef input, List<NodeRef> outputs) {
        Endpoint source = input.endpoint;
        if (source == null || source.container == null) {
            return 0;
        }
        Predicate<ItemStack> filter = stack -> acceptsInputFilter(input.state, stack);
        PlannedStack plan = virtualContainers.planFirst(source.container, filter, 64);
        if (plan == null || plan.isEmpty()) {
            return 0;
        }
        return commitPlannedTransfer(input, source.container, plan, outputs, input.state.roundRobin ? SfxCargoDistributionMode.ROUND_ROBIN : SfxCargoDistributionMode.SEQUENTIAL);
    }

    private int processAdvancedInput(NodeRef input, List<NodeRef> outputs) {
        Endpoint source = input.endpoint;
        if (source == null || source.container == null) {
            return 0;
        }
        Predicate<ItemStack> filter = stack -> acceptsInputFilter(input.state, stack);
        int limit = SfxCargoNodeState.normalizeBatchLimit(input.state.batchLimit);
        List<PlannedStack> batch = virtualContainers.planBatch(source.container, filter, limit, input.state.maxDistinctTypes, input.state.allowMultipleSlots);
        if (batch.isEmpty()) {
            return 0;
        }
        int moved = 0;
        for (PlannedStack plan : batch) {
            moved += commitPlannedTransfer(input, source.container, plan, outputs, input.state.distributionMode);
        }
        return moved;
    }

    private int commitPlannedTransfer(NodeRef input, SfxVirtualContainer sourceContainer, PlannedStack plan, List<NodeRef> outputs, SfxCargoDistributionMode mode) {
        if (plan == null || plan.isEmpty() || !virtualContainers.canRemovePlanned(sourceContainer, plan.takes())) {
            return 0;
        }
        List<OutputMove> moves = planOutputMoves(input, plan.stack(), outputs, mode, sourceContainer);
        int planned = moves.stream().mapToInt(OutputMove::amount).sum();
        if (planned <= 0) {
            return 0;
        }
        if (!virtualContainers.removePlanned(sourceContainer, limitTakes(plan, planned))) {
            return 0;
        }
        ItemStack template = plan.stack().clone();
        template.setAmount(1);
        int inserted = 0;
        for (OutputMove move : moves) {
            if (move.amount() <= 0) {
                continue;
            }
            if (move.endpoint().trash) {
                inserted += move.amount();
                continue;
            }
            ItemStack part = template.clone();
            part.setAmount(move.amount());
            ItemStack remainder = input.definition.type() == SfxCargoComponentType.INPUT_NODE
                    ? move.endpoint().insertSingleSlot(part, input.state.smartFill)
                    : move.endpoint().insert(part, input.state.smartFill);
            inserted += move.amount() - (isEmpty(remainder) ? 0 : remainder.getAmount());
            if (!isEmpty(remainder)) {
                virtualContainers.insert(sourceContainer, remainder, false);
            }
        }
        if (inserted < planned) {
            ItemStack refund = template.clone();
            refund.setAmount(planned - inserted);
            virtualContainers.insert(sourceContainer, refund, false);
        }
        return inserted;
    }

    private List<SfxVirtualContainerService.SlotTake> limitTakes(PlannedStack plan, int amount) {
        List<SfxVirtualContainerService.SlotTake> limited = new ArrayList<>();
        int remaining = amount;
        for (SfxVirtualContainerService.SlotTake take : plan.takes()) {
            if (remaining <= 0) {
                break;
            }
            int use = Math.min(take.amount(), remaining);
            limited.add(new SfxVirtualContainerService.SlotTake(take.slot(), take.template(), use));
            remaining -= use;
        }
        return limited;
    }

    private List<OutputMove> planOutputMoves(NodeRef input, ItemStack stack, List<NodeRef> outputs, SfxCargoDistributionMode mode, SfxVirtualContainer sourceContainer) {
        if (isEmpty(stack)) {
            return List.of();
        }
        List<NodeRef> candidates = new ArrayList<>();
        for (NodeRef output : outputs) {
            if (output.state.channel != input.state.channel) {
                continue;
            }
            if (!acceptsOutputFilter(output.state, output.definition, stack)) {
                continue;
            }
            Endpoint endpoint = output.endpoint;
            if (endpoint == null || (!endpoint.trash && endpoint.container == sourceContainer)) {
                continue;
            }
            int capacity = input.definition.type() == SfxCargoComponentType.INPUT_NODE
                    ? endpoint.capacityForSingleSlot(stack, input.state.smartFill)
                    : endpoint.capacityFor(stack, input.state.smartFill);
            if (capacity <= 0) {
                continue;
            }
            candidates.add(output.withEndpoint(endpoint));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        candidates.sort(Comparator.comparingInt((NodeRef ref) -> ref.priority()).reversed().thenComparing(ref -> ref.instance.anchorKey(), this::compareAnchorKeys));
        List<OutputMove> moves = new ArrayList<>();
        int remaining = stack.getAmount();
        int index = 0;
        while (index < candidates.size() && remaining > 0) {
            int priority = candidates.get(index).priority();
            List<NodeRef> group = new ArrayList<>();
            while (index < candidates.size() && candidates.get(index).priority() == priority) {
                group.add(candidates.get(index++));
            }
            int before = remaining;
            if (mode == SfxCargoDistributionMode.EVEN) {
                remaining = planEvenMoves(input, priority, group, stack, remaining, moves);
            } else if (mode == SfxCargoDistributionMode.ROUND_ROBIN) {
                remaining = planRoundRobinMoves(input, group, stack, remaining, moves);
            } else {
                remaining = planSequentialMoves(input, group, stack, remaining, moves);
            }
            if (input.definition.type() == SfxCargoComponentType.INPUT_NODE && !moves.isEmpty()) {
                break;
            }
            if (remaining == before && !group.isEmpty()) {
                break;
            }
        }
        return moves;
    }

    private int planSequentialMoves(NodeRef input, List<NodeRef> group, ItemStack stack, int remaining, List<OutputMove> moves) {
        for (NodeRef output : group) {
            if (remaining <= 0) {
                break;
            }
            int amount = Math.min(remaining, input.definition.type() == SfxCargoComponentType.INPUT_NODE
                    ? output.endpoint.capacityForSingleSlot(stack, input.state.smartFill)
                    : output.endpoint.capacityFor(stack, input.state.smartFill));
            if (amount <= 0) {
                continue;
            }
            moves.add(new OutputMove(output.endpoint, amount));
            remaining -= amount;
            if (input.definition.type() == SfxCargoComponentType.INPUT_NODE) {
                break;
            }
        }
        return remaining;
    }

    private int planRoundRobinMoves(NodeRef input, List<NodeRef> group, ItemStack stack, int remaining, List<OutputMove> moves) {
        if (group.isEmpty() || remaining <= 0) {
            return remaining;
        }
        int start = input.state.roundRobinCursor % group.size();
        boolean movedAny = false;
        for (int i = 0; i < group.size() && remaining > 0; i++) {
            NodeRef output = group.get((start + i) % group.size());
            int amount = Math.min(remaining, input.definition.type() == SfxCargoComponentType.INPUT_NODE
                    ? output.endpoint.capacityForSingleSlot(stack, input.state.smartFill)
                    : output.endpoint.capacityFor(stack, input.state.smartFill));
            if (amount <= 0) {
                continue;
            }
            moves.add(new OutputMove(output.endpoint, amount));
            remaining -= amount;
            movedAny = true;
            input.state.roundRobinCursor = (start + i + 1) % group.size();
            if (input.definition.type() == SfxCargoComponentType.INPUT_NODE) {
                break;
            }
        }
        if (movedAny) {
            persistState(input.instance.instanceId(), input.state);
        }
        return remaining;
    }

    private int planEvenMoves(NodeRef input, int priority, List<NodeRef> group, ItemStack stack, int remaining, List<OutputMove> moves) {
        if (group.isEmpty() || remaining <= 0) {
            return remaining;
        }
        String key = input.instance.instanceId() + ":" + input.state.channel + ":" + priority + ":" + SfxCargoItemKey.of(items, stack).key();
        Map<UUID, Integer> debts = distributionDebt.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        group.sort(Comparator.comparingInt((NodeRef ref) -> debts.getOrDefault(ref.instance.instanceId(), 0)).reversed()
                .thenComparing(ref -> ref.instance.anchorKey(), this::compareAnchorKeys));
        int originalRemaining = remaining;
        Map<UUID, Integer> movedByNode = new HashMap<>();
        while (remaining > 0) {
            List<NodeRef> eligible = group.stream()
                    .filter(ref -> ref.endpoint.capacityFor(stack, input.state.smartFill) > movedByNode.getOrDefault(ref.instance.instanceId(), 0))
                    .toList();
            if (eligible.isEmpty()) {
                break;
            }
            int base = Math.max(1, (int) Math.ceil(remaining / (double) eligible.size()));
            boolean any = false;
            for (NodeRef output : eligible) {
                if (remaining <= 0) {
                    break;
                }
                int already = movedByNode.getOrDefault(output.instance.instanceId(), 0);
                int capacity = output.endpoint.capacityFor(stack, input.state.smartFill) - already;
                int amount = Math.min(Math.min(base, remaining), capacity);
                if (amount <= 0) {
                    continue;
                }
                moves.add(new OutputMove(output.endpoint, amount));
                movedByNode.merge(output.instance.instanceId(), amount, Integer::sum);
                remaining -= amount;
                any = true;
            }
            if (!any) {
                break;
            }
        }
        int totalMoved = originalRemaining - remaining;
        if (totalMoved > 0) {
            int eligibleCount = Math.max(1, group.size());
            int expected = totalMoved / eligibleCount;
            for (NodeRef node : group) {
                UUID id = node.instance.instanceId();
                int debt = debts.getOrDefault(id, 0);
                debt += expected - movedByNode.getOrDefault(id, 0);
                debts.put(id, Math.max(-4096, Math.min(4096, debt)));
            }
        }
        return remaining;
    }


    private Endpoint resolveOutputEndpoint(SfxBlockInstanceRecord node, SfxCargoNodeState state, SfxVirtualContainer sourceContainer, Map<EndpointCacheKey, Optional<Endpoint>> endpointCache) {
        Endpoint endpoint = resolveEndpointAt(node, state.attachedFace, true, endpointCache);
        if (endpoint != null && (endpoint.trash || endpoint.container != sourceContainer)) {
            return endpoint;
        }
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            if (face == state.attachedFace) {
                continue;
            }
            endpoint = resolveEndpointAt(node, face, true, endpointCache);
            if (endpoint != null && (endpoint.trash || endpoint.container != sourceContainer)) {
                state.attachedFace = face;
                persistState(node.instanceId(), state);
                return endpoint;
            }
        }
        return endpoint;
    }

    private Endpoint resolveEndpoint(SfxBlockInstanceRecord node, SfxCargoNodeState state, boolean outputSide, Map<EndpointCacheKey, Optional<Endpoint>> endpointCache) {
        Endpoint endpoint = resolveEndpointAt(node, state.attachedFace, outputSide, endpointCache);
        if (endpoint != null) {
            return endpoint;
        }
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            if (face == state.attachedFace) {
                continue;
            }
            endpoint = resolveEndpointAt(node, face, outputSide, endpointCache);
            if (endpoint != null) {
                state.attachedFace = face;
                persistState(node.instanceId(), state);
                return endpoint;
            }
        }
        return null;
    }

    private Endpoint resolveEndpointAt(SfxBlockInstanceRecord node, BlockFace face, boolean outputSide, Map<EndpointCacheKey, Optional<Endpoint>> endpointCache) {
        Location target = targetLocation(node.anchorKey(), face);
        if (target == null) {
            return null;
        }
        EndpointCacheKey key = new EndpointCacheKey(node.instanceId(), face, outputSide);
        Optional<Endpoint> cached = endpointCache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }
        Endpoint endpoint = null;
        SfxAnchorRecord anchor = blockData.findAnchor(target).orElse(null);
        if (anchor != null) {
            SfxBlockInstanceRecord targetInstance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (targetInstance != null) {
                SfxCargoComponentDefinition targetDefinition = definitions.get(targetInstance.typeId());
                if (targetDefinition != null && targetDefinition.type() == SfxCargoComponentType.TRASH_CAN && outputSide) {
                    endpoint = trashEndpoint();
                }
            }
        }
        if (endpoint == null) {
            endpoint = virtualContainers.findRegistered(target)
                    .map(this::containerEndpoint)
                    .orElseGet(() -> virtualContainers.ensureRegistered(target).map(this::containerEndpoint).orElse(null));
        }
        endpointCache.put(key, Optional.ofNullable(endpoint));
        return endpoint;
    }

    private Location toLocation(SfxBlockAnchorKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, key.x(), key.y(), key.z());
    }

    private Location targetLocation(SfxBlockAnchorKey key, BlockFace face) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || face == null) {
            return null;
        }
        return new Location(world, key.x() + face.getModX(), key.y() + face.getModY(), key.z() + face.getModZ());
    }

    private boolean acceptsInputFilter(SfxCargoNodeState state, ItemStack stack) {
        return acceptsFilter(state, stack);
    }

    private boolean acceptsOutputFilter(SfxCargoNodeState state, SfxCargoComponentDefinition definition, ItemStack stack) {
        if (definition.type() != SfxCargoComponentType.ADVANCED_OUTPUT_NODE) {
            return true;
        }
        return acceptsFilter(state, stack);
    }

    private boolean acceptsFilter(SfxCargoNodeState state, ItemStack stack) {
        boolean anyFilter = false;
        boolean matched = false;
        for (ItemStack filter : state.filterItems) {
            if (isEmpty(filter)) {
                continue;
            }
            anyFilter = true;
            if (matches(filter, stack, state.matchLore)) {
                matched = true;
                break;
            }
        }
        if (state.filterMode == SfxCargoFilterMode.WHITELIST) {
            return anyFilter && matched;
        }
        return !matched;
    }

    private boolean matches(ItemStack filter, ItemStack stack, boolean matchLore) {
        if (isEmpty(filter) || isEmpty(stack)) {
            return false;
        }
        if (matchLore) {
            ItemStack a = filter.clone();
            ItemStack b = stack.clone();
            a.setAmount(1);
            b.setAmount(1);
            return a.isSimilar(b);
        }
        return filter.getType() == stack.getType() && items.readMarker(filter).map(marker -> marker.itemId()).equals(items.readMarker(stack).map(marker -> marker.itemId()));
    }


    private void handleInteract(Player player, Block block, SfxBlockInstanceRecord instance, SfxCargoComponentDefinition definition) {
        if (definition.type() == SfxCargoComponentType.MANAGER) {
            topology.rebuildIfStale();
            if (player.isSneaking()) {
                toggleVisualizer(player, instance);
            } else {
                showManagerStatus(player, instance);
            }
            return;
        }
        if (definition.type() == SfxCargoComponentType.CONNECTOR) {
            topology.rebuildIfStale();
            boolean linked = topology.componentForMember(instance.instanceId()).isPresent();
            player.sendMessage(Text.prefixed(plugin, linked ? lt("cargo.message.connector-linked", "&aCargo connector linked.", Map.of()) : lt("cargo.message.connector-detached", "&cCargo connector is detached.", Map.of())));
            return;
        }
        if (definition.type() == SfxCargoComponentType.TRASH_CAN) {
            openTrashCan(player, instance);
            return;
        }
        openMenu(player, instance, definition.type());
    }

    private void showManagerStatus(Player player, SfxBlockInstanceRecord instance) {
        SfxTopologyComponent component = topology.componentForMember(instance.instanceId()).orElse(null);
        String status = component == null ? "NO_NETWORK" : component.status().name();
        player.sendMessage(Text.prefixed(plugin, lt("cargo.message.manager-status", "&eCargo Network: &f{status}", Map.of("status", status))));
        if (component != null) {
            player.sendMessage(Text.prefixed(plugin, lt("cargo.message.manager-size", "&7Backbone: &f{backbone} &7Terminals: &f{terminals}", Map.of("backbone", component.backboneNodes().size(), "terminals", component.terminals().size()))));
        }
        CargoTransferStats stats = managerStats.computeIfAbsent(instance.instanceId(), ignored -> new CargoTransferStats());
        player.sendMessage(Text.prefixed(plugin, lt("cargo.message.manager-stats", "&7Transfer: &f{minute}&7/min &8| &7Total: &f{total}", Map.of("minute", stats.lastMinute(), "total", stats.total()))));
        boolean enabled = instance.instanceId().equals(visualizers.get(player.getUniqueId()));
        player.sendMessage(Text.prefixed(plugin, lt("cargo.message.visualizer-state", "&7Particles: &f{state}", Map.of("state", enabled ? lt("cargo.message.visualizer-on", "ON", Map.of()) : lt("cargo.message.visualizer-off", "OFF", Map.of())))));
    }

    private void toggleVisualizer(Player player, SfxBlockInstanceRecord instance) {
        if (!plugin.getConfig().getBoolean("cargo.visualizer.enabled", true)) {
            player.sendMessage(Text.prefixed(plugin, lt("cargo.message.visualizer-disabled", "&cCargo particles are disabled in the config.", Map.of())));
            return;
        }
        UUID playerId = player.getUniqueId();
        UUID current = visualizers.get(playerId);
        if (instance.instanceId().equals(current)) {
            visualizers.remove(playerId);
            player.sendMessage(Text.prefixed(plugin, lt("cargo.message.visualizer-toggled-off", "&7Cargo particles: &cOFF", Map.of())));
            return;
        }
        visualizers.put(playerId, instance.instanceId());
        player.sendMessage(Text.prefixed(plugin, lt("cargo.message.visualizer-toggled-on", "&7Cargo particles: &aON", Map.of())));
        renderVisualizerFor(player, instance.instanceId());
    }


    private void openMenu(Player player, SfxBlockInstanceRecord instance, SfxCargoComponentType type) {
        SfxCargoNodeState state = currentState(instance.instanceId());
        int size = type == SfxCargoComponentType.OUTPUT_NODE ? OUTPUT_INVENTORY_SIZE : FILTER_INVENTORY_SIZE;
        SfxCargoSessionHolder holder = new SfxCargoSessionHolder(instance.instanceId(), type);
        Inventory inventory = plugin.getServer().createInventory(holder, size, titleFor(type));
        holder.bind(inventory);
        renderMenu(inventory, instance.instanceId(), type, state);
        openMenus.put(instance.instanceId(), inventory);
        player.openInventory(inventory);
    }

    private void openTrashCan(Player player, SfxBlockInstanceRecord instance) {
        SfxTrashCanHolder holder = new SfxTrashCanHolder(instance.instanceId());
        Inventory inventory = plugin.getServer().createInventory(holder, TRASH_INVENTORY_SIZE, trashTitle());
        holder.bind(inventory);
        renderTrash(inventory);
        openMenus.put(instance.instanceId(), inventory);
        player.openInventory(inventory);
    }

    private void renderMenu(Inventory inventory, UUID instanceId, SfxCargoComponentType type, SfxCargoNodeState state) {
        inventory.clear();
        if (type == SfxCargoComponentType.OUTPUT_NODE) {
            fillSlots(inventory, OUTPUT_BORDER, Material.CYAN_STAINED_GLASS_PANE);
            renderChannelSelector(inventory, state, 12, 13, 14);
            return;
        }

        if (type == SfxCargoComponentType.ADVANCED_OUTPUT_NODE) {
            fillSlots(inventory, ADVANCED_OUTPUT_BORDER, Material.CYAN_STAINED_GLASS_PANE);
        } else {
            fillSlots(inventory, INPUT_BORDER, Material.CYAN_STAINED_GLASS_PANE);
        }

        if (usesFilter(type)) {
            inventory.setItem(2, uiItem(Material.PAPER, "cargo.ui.items.name", "&3Items", "cargo.ui.items.lore", List.of("", "&bPut in all Items you want to", "&bblacklist/whitelist"), Map.of()));
            for (int i = 0; i < FILTER_SLOTS.length; i++) {
                inventory.setItem(FILTER_SLOTS[i], cloneOrNull(state.filterItems[i]));
            }
            inventory.setItem(15, modeItem(state));
            inventory.setItem(25, loreToggleItem(state.matchLore));
        }
        renderChannelSelector(inventory, state, 41, 42, 43);
        if (type == SfxCargoComponentType.INPUT_NODE || type == SfxCargoComponentType.ADVANCED_INPUT_NODE) {
            inventory.setItem(16, smartFillItem(state.smartFill));
        }
        if (type == SfxCargoComponentType.INPUT_NODE) {
            inventory.setItem(24, roundRobinItem(state.roundRobin));
        }
        if (type == SfxCargoComponentType.ADVANCED_INPUT_NODE) {
            inventory.setItem(14, multiSlotItem(state.allowMultipleSlots));
            inventory.setItem(23, batchLimitItem(SfxCargoNodeState.normalizeBatchLimit(state.batchLimit)));
            inventory.setItem(24, distributionItem(state.distributionMode));
        }
        if (type == SfxCargoComponentType.ADVANCED_OUTPUT_NODE) {
            inventory.setItem(16, priorityItem(state.priority));
        }
    }

    private void renderChannelSelector(Inventory inventory, SfxCargoNodeState state, int prevSlot, int currentSlot, int nextSlot) {
        inventory.setItem(prevSlot, uiItem(Material.ARROW, "cargo.ui.channel.previous.name", "&bPrevious Channel", "cargo.ui.channel.previous.lore", List.of("", "&e> Click to decrease the Channel ID by 1"), Map.of()));
        inventory.setItem(currentSlot, channelItem(state));
        inventory.setItem(nextSlot, uiItem(Material.ARROW, "cargo.ui.channel.next.name", "&bNext Channel", "cargo.ui.channel.next.lore", List.of("", "&e> Click to increase the Channel ID by 1"), Map.of()));
    }

    private void renderTrash(Inventory inventory) {
        inventory.clear();
        fillSlots(inventory, TRASH_BORDER, Material.RED_STAINED_GLASS_PANE);
    }

    private void fillSlots(Inventory inventory, int[] slots, Material material) {
        ItemStack pane = ItemBuilder.of(material).name(" ").build();
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, pane);
            }
        }
    }

    private Component titleFor(SfxCargoComponentType type) {
        return switch (type) {
            case INPUT_NODE -> lc("cargo.ui.title.input", "&cCargo Input");
            case ADVANCED_INPUT_NODE -> lc("cargo.ui.title.advanced-input", "&6Advanced Cargo Input");
            case OUTPUT_NODE -> lc("cargo.ui.title.output", "&cCargo Output");
            case ADVANCED_OUTPUT_NODE -> lc("cargo.ui.title.advanced-output", "&6Advanced Cargo Output");
            default -> lc("cargo.ui.title.generic", "&eCargo");
        };
    }

    private Component trashTitle() {
        return lc("cargo.ui.title.trash-can", "&3Trash Can");
    }

    private ItemStack channelItem(SfxCargoNodeState state) {
        return uiItem(channelMaterial(state.channel), "cargo.ui.channel.current.name", "&bChannel ID: &3{channel_display}", "cargo.ui.channel.current.lore", List.of(), Map.of("channel", state.channel, "channel_display", state.channel + 1));
    }

    private ItemStack modeItem(SfxCargoNodeState state) {
        if (state.filterMode == SfxCargoFilterMode.WHITELIST) {
            return uiItem(Material.WHITE_WOOL, "cargo.ui.filter.whitelist.name", "&7Type: &rWhitelist", "cargo.ui.filter.whitelist.lore", List.of("", "&e> Click to change it to Blacklist"), Map.of());
        }
        return uiItem(Material.BLACK_WOOL, "cargo.ui.filter.blacklist.name", "&7Type: &8Blacklist", "cargo.ui.filter.blacklist.lore", List.of("", "&e> Click to change it to Whitelist"), Map.of());
    }

    private ItemStack loreToggleItem(boolean enabled) {
        return uiItem(Material.MAP, enabled ? "cargo.ui.lore.enabled.name" : "cargo.ui.lore.disabled.name",
                enabled ? "&7Include Lore: &2✔" : "&7Include Lore: &4✘",
                "cargo.ui.lore.toggle-lore", List.of("", "&e> Click to toggle whether the Lore has to match"), Map.of());
    }

    private ItemStack smartFillItem(boolean enabled) {
        return uiItem(enabled ? Material.WRITTEN_BOOK : Material.WRITABLE_BOOK,
                enabled ? "cargo.ui.smart-fill.enabled.name" : "cargo.ui.smart-fill.disabled.name",
                enabled ? "&7\"Smart-Filling\" Mode: &2✔" : "&7\"Smart-Filling\" Mode: &4✘",
                "cargo.ui.smart-fill.lore",
                List.of("", "&e> Click to toggle Smart-Filling Mode", "", "&fIn this mode, the Cargo node will attempt", "&fto keep a constant amount of items", "&fin the inventory."),
                Map.of());
    }

    private ItemStack roundRobinItem(boolean enabled) {
        return uiItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? "cargo.ui.round-robin.enabled.name" : "cargo.ui.round-robin.disabled.name",
                enabled ? "&7Round-Robin Mode: &2✔" : "&7Round-Robin Mode: &4✘",
                "cargo.ui.round-robin.lore",
                List.of("", "&e> Click to toggle Round-Robin Mode", "", "&fThis mode makes the input node rotate", "&fbetween matching output nodes."),
                Map.of());
    }

    private ItemStack multiSlotItem(boolean enabled) {
        return uiItem(enabled ? Material.CHEST : Material.BARREL,
                enabled ? "cargo.ui.multi-slot.enabled.name" : "cargo.ui.multi-slot.disabled.name",
                enabled ? "&7Multi-slot Extraction: &2✔" : "&7Multi-slot Extraction: &4✘",
                "cargo.ui.multi-slot.lore",
                List.of("", "&e> Click to toggle", "", "&fWhen enabled, this node may pull", "&ffrom multiple source slots per cycle."),
                Map.of());
    }

    private ItemStack batchLimitItem(int limit) {
        return uiItem(Material.HOPPER,
                "cargo.ui.batch-limit.name",
                "&7Transfer Limit: &b{limit} items",
                "cargo.ui.batch-limit.lore",
                List.of("", "&e> Left-click: next lower limit", "&e> Right-click: next higher limit", "", "&fAvailable: &b128&7/&b64&7/&b16&7/&b4&7/&b1"),
                Map.of("limit", limit));
    }

    private ItemStack distributionItem(SfxCargoDistributionMode mode) {
        Material material = switch (mode) {
            case SEQUENTIAL -> Material.COMPASS;
            case ROUND_ROBIN -> Material.CLOCK;
            case EVEN -> Material.LIME_DYE;
        };
        String key = switch (mode) {
            case SEQUENTIAL -> "sequential";
            case ROUND_ROBIN -> "round-robin";
            case EVEN -> "even";
        };
        String fallbackName = switch (mode) {
            case SEQUENTIAL -> "&7Distribution: &fSequential";
            case ROUND_ROBIN -> "&7Distribution: &bRound-Robin";
            case EVEN -> "&7Distribution: &aAverage";
        };
        return uiItem(material,
                "cargo.ui.distribution." + key + ".name",
                fallbackName,
                "cargo.ui.distribution." + key + ".lore",
                List.of("", "&e> Left-click: next mode", "&e> Right-click: previous mode", "", "&fSequential fills outputs in order.", "&fRound-Robin rotates targets.", "&fAverage uses quota/debt balancing."),
                Map.of());
    }


    private ItemStack priorityItem(int priority) {
        return uiItem(priorityMaterial(priority), "cargo.ui.priority.name", "&ePriority: &f{priority} / 16", "cargo.ui.priority.lore", List.of("&7Higher priority outputs receive items first.", "&eLeft-click: &7+1", "&eRight-click: &7-1", "&eShift: &7±4"), Map.of("priority", priority));
    }

    private Material priorityMaterial(int priority) {
        return switch (SfxCargoNodeState.clamp(priority, 1, 16)) {
            case 1 -> Material.WHITE_STAINED_GLASS;
            case 2 -> Material.ORANGE_STAINED_GLASS;
            case 3 -> Material.MAGENTA_STAINED_GLASS;
            case 4 -> Material.LIGHT_BLUE_STAINED_GLASS;
            case 5 -> Material.YELLOW_STAINED_GLASS;
            case 6 -> Material.LIME_STAINED_GLASS;
            case 7 -> Material.PINK_STAINED_GLASS;
            case 8 -> Material.GRAY_STAINED_GLASS;
            case 9 -> Material.LIGHT_GRAY_STAINED_GLASS;
            case 10 -> Material.CYAN_STAINED_GLASS;
            case 11 -> Material.PURPLE_STAINED_GLASS;
            case 12 -> Material.BLUE_STAINED_GLASS;
            case 13 -> Material.BROWN_STAINED_GLASS;
            case 14 -> Material.GREEN_STAINED_GLASS;
            case 15 -> Material.RED_STAINED_GLASS;
            default -> Material.BLACK_STAINED_GLASS;
        };
    }

    private Material channelMaterial(int channel) {
        return switch (SfxCargoNodeState.clamp(channel, 0, 15)) {
            case 0 -> Material.WHITE_WOOL;
            case 1 -> Material.ORANGE_WOOL;
            case 2 -> Material.MAGENTA_WOOL;
            case 3 -> Material.LIGHT_BLUE_WOOL;
            case 4 -> Material.YELLOW_WOOL;
            case 5 -> Material.LIME_WOOL;
            case 6 -> Material.PINK_WOOL;
            case 7 -> Material.GRAY_WOOL;
            case 8 -> Material.LIGHT_GRAY_WOOL;
            case 9 -> Material.CYAN_WOOL;
            case 10 -> Material.PURPLE_WOOL;
            case 11 -> Material.BLUE_WOOL;
            case 12 -> Material.BROWN_WOOL;
            case 13 -> Material.GREEN_WOOL;
            case 14 -> Material.RED_WOOL;
            default -> Material.BLACK_WOOL;
        };
    }

    private ItemStack uiItem(Material material, String namePath, String fallbackName, String lorePath, List<String> fallbackLore, Map<String, ?> placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(lc(namePath, fallbackName, placeholders));
            List<Component> lore = localizedLore(lorePath, fallbackLore, placeholders);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component lc(String path, String fallback) {
        return localization.component(path, fallback);
    }

    private Component lc(String path, String fallback, Map<String, ?> placeholders) {
        return Text.renderFlexible(lt(path, fallback, placeholders));
    }

    private String lt(String path, String fallback, Map<String, ?> placeholders) {
        String value = localization.text(path, fallback);
        if (placeholders != null) {
            for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return value;
    }

    private List<Component> localizedLore(String path, List<String> fallback, Map<String, ?> placeholders) {
        List<String> lines = localization.list(path);
        if (lines.isEmpty()) {
            lines = fallback == null ? List.of() : fallback;
        }
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String rendered = line;
            if (placeholders != null) {
                for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                    rendered = rendered.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                }
            }
            components.add(Text.renderFlexible(rendered));
        }
        return components;
    }

    private void saveFilterFromOpenMenu(UUID instanceId, Inventory inventory) {
        SfxCargoNodeState state = currentState(instanceId);
        syncFilterFromInventory(inventory, state);
        persistState(instanceId, state);
    }

    private boolean isPrevChannelSlot(SfxCargoComponentType type, int slot) {
        return type == SfxCargoComponentType.OUTPUT_NODE ? slot == 12 : slot == 41;
    }

    private boolean isNextChannelSlot(SfxCargoComponentType type, int slot) {
        return type == SfxCargoComponentType.OUTPUT_NODE ? slot == 14 : slot == 43;
    }

    private void syncFilterFromInventory(Inventory inventory, SfxCargoNodeState state) {
        if (inventory == null || state == null || inventory.getSize() < FILTER_INVENTORY_SIZE) {
            return;
        }
        for (int i = 0; i < FILTER_SLOTS.length; i++) {
            ItemStack stack = inventory.getItem(FILTER_SLOTS[i]);
            if (isEmpty(stack)) {
                state.filterItems[i] = null;
            } else {
                state.filterItems[i] = stack.clone();
            }
        }
    }

    private void adjustChannel(SfxCargoNodeState state, int delta, boolean shift) {
        int step = shift ? 4 : 1;
        state.channel = Math.floorMod(state.channel + (delta * step), 16);
    }

    private void adjustPriority(SfxCargoNodeState state, int delta, boolean shift) {
        int step = shift ? 4 : 1;
        state.priority = SfxCargoNodeState.clamp(state.priority + delta * step, 1, 16);
    }

    private void clearOpenTrashMenus() {
        for (Inventory inventory : openMenus.values()) {
            if (inventory.getHolder() instanceof SfxTrashCanHolder) {
                clearTrash(inventory);
            }
        }
    }

    private void clearTrash(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (int slot : TRASH_INPUT_SLOTS) {
            inventory.setItem(slot, null);
        }
    }

    private SfxCargoNodeState decodeState(SfxBlockInstanceRecord instance) {
        if (instance == null) {
            return new SfxCargoNodeState();
        }
        SfxCargoNodeState state = SfxCargoNodeState.decode(instance.stateBlob());
        SfxCargoComponentDefinition definition = definitions.get(instance.typeId());
        if (definition != null && definition.type() == SfxCargoComponentType.ADVANCED_INPUT_NODE && stateVersion(instance.stateBlob()) < 3) {
            state.distributionMode = SfxCargoDistributionMode.SEQUENTIAL;
            state.allowMultipleSlots = true;
            state.batchLimit = 128;
            state.maxItemsPerCycle = 128;
        }
        return state;
    }

    private int stateVersion(byte[] blob) {
        if (blob == null || blob.length < Integer.BYTES) {
            return 0;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(blob))) {
            return input.readInt();
        } catch (IOException | RuntimeException ignored) {
            return 0;
        }
    }

    private SfxCargoNodeState currentState(UUID instanceId) {
        SfxCargoNodeState cached = states.get(instanceId);
        if (cached != null) {
            return cached;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxCargoNodeState decoded = instance == null ? new SfxCargoNodeState() : decodeState(instance);
        states.put(instanceId, decoded);
        return decoded;
    }

    private void persistState(UUID instanceId, SfxCargoNodeState state) {
        if (instanceId == null || state == null) {
            return;
        }
        states.put(instanceId, state);
        dirtyStates.add(instanceId);
        cargoStateRevision++;
        runtimeNetworks.clear();
    }

    private void flushDirty() {
        for (UUID instanceId : List.copyOf(dirtyStates)) {
            SfxCargoNodeState state = states.get(instanceId);
            if (state == null) {
                dirtyStates.remove(instanceId);
                continue;
            }
            blockData.updateInstanceState(instanceId, state.encode(), SfxBlockLifecycleState.IDLE);
            dirtyStates.remove(instanceId);
        }
    }

    private BlockFace attachedFace(BlockPlaceEvent event) {
        if (event == null || event.getBlockPlaced() == null || event.getBlockAgainst() == null) {
            return BlockFace.NORTH;
        }
        return event.getBlockPlaced().getFace(event.getBlockAgainst());
    }

    private boolean isHorizontal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    private boolean usesFilter(SfxCargoComponentType type) {
        return type == SfxCargoComponentType.INPUT_NODE
                || type == SfxCargoComponentType.ADVANCED_INPUT_NODE
                || type == SfxCargoComponentType.ADVANCED_OUTPUT_NODE;
    }


    private SfxCargoComponentDefinition typeDefinition(SfxCargoComponentType type) {
        for (SfxCargoComponentDefinition definition : definitions.values()) {
            if (definition.type() == type) {
                return definition;
            }
        }
        return null;
    }

    private Location below(SfxBlockAnchorKey key) {
        World world = Bukkit.getWorld(key.worldId());
        return world == null ? null : new Location(world, key.x(), key.y() - 1, key.z());
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

    private ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    private void dropPluginBlock(Block block, String typeId) {
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
    }

    private void dropStack(Block block, ItemStack stack) {
        if (block == null || isEmpty(stack)) {
            return;
        }
        SfxBlockDrops.dropItem(block, stack.clone());
    }

    private record OutputMove(Endpoint endpoint, int amount) {
    }

    private record EndpointCacheKey(UUID nodeId, BlockFace face, boolean outputSide) {
    }

    private record CargoRuntimeNetwork(
            UUID componentId,
            long topologyRevision,
            long cargoStateRevision,
            long containerRegistryRevision,
            UUID managerId,
            List<NodeRef> inputs,
            List<NodeRef> outputs
    ) {
    }

    private static final class CargoTransferStats {
        private final long[] buckets = new long[60];
        private long total;
        private long currentSecond = Long.MIN_VALUE;
        private int currentBucket;

        synchronized void record(int amount) {
            if (amount <= 0) {
                return;
            }
            rotateToNow();
            buckets[currentBucket] += amount;
            total += amount;
        }

        synchronized long total() {
            return total;
        }

        synchronized long lastMinute() {
            rotateToNow();
            long sum = 0L;
            for (long bucket : buckets) {
                sum += bucket;
            }
            return sum;
        }

        private void rotateToNow() {
            long now = System.currentTimeMillis() / 1000L;
            if (currentSecond == Long.MIN_VALUE) {
                currentSecond = now;
                currentBucket = (int) Math.floorMod(now, buckets.length);
                buckets[currentBucket] = 0L;
                return;
            }
            long delta = now - currentSecond;
            if (delta <= 0L) {
                return;
            }
            long steps = Math.min(delta, buckets.length);
            for (int i = 1; i <= steps; i++) {
                int index = (int) Math.floorMod(currentSecond + i, buckets.length);
                buckets[index] = 0L;
            }
            currentSecond = now;
            currentBucket = (int) Math.floorMod(now, buckets.length);
        }
    }

    private record NodeRef(SfxBlockInstanceRecord instance, SfxCargoComponentDefinition definition, SfxCargoNodeState state, Endpoint endpoint) {
        NodeRef(SfxBlockInstanceRecord instance, SfxCargoComponentDefinition definition, SfxCargoNodeState state) {
            this(instance, definition, state, null);
        }

        NodeRef withEndpoint(Endpoint endpoint) {
            return new NodeRef(instance, definition, state, endpoint);
        }

        int priority() {
            return definition.type() == SfxCargoComponentType.ADVANCED_OUTPUT_NODE ? state.priority : 1;
        }
    }


    private Endpoint containerEndpoint(SfxVirtualContainer container) {
        return new Endpoint(container, false);
    }

    private Endpoint trashEndpoint() {
        return new Endpoint(null, true);
    }

    private final class Endpoint {
        private final SfxVirtualContainer container;
        private final boolean trash;

        private Endpoint(SfxVirtualContainer container, boolean trash) {
            this.container = container;
            this.trash = trash;
        }


        int capacityFor(ItemStack stack, boolean smartFill) {
            if (trash) {
                return stack == null ? 0 : stack.getAmount();
            }
            return virtualContainers.capacityFor(container, stack, smartFill);
        }

        int capacityForSingleSlot(ItemStack stack, boolean smartFill) {
            if (trash) {
                return stack == null ? 0 : stack.getAmount();
            }
            return virtualContainers.capacityForSingleSlot(container, stack, smartFill);
        }

        ItemStack insert(ItemStack stack, boolean smartFill) {
            if (trash || isEmpty(stack)) {
                return null;
            }
            return virtualContainers.insert(container, stack, smartFill);
        }

        ItemStack insertSingleSlot(ItemStack stack, boolean smartFill) {
            if (trash || isEmpty(stack)) {
                return null;
            }
            return virtualContainers.insertSingleSlot(container, stack, smartFill);
        }
    }
}
