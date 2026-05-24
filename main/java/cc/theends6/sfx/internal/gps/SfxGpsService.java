package cc.theends6.sfx.internal.gps;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.persistence.SfxDirtyPersistenceService;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.decoration.SfxDecorationState;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.machine.SfxMachineEffectDispatcher;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseContext;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseResult;
import cc.theends6.sfx.internal.machine.SfxMachinePipelineGuard;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;









public final class SfxGpsService implements Listener, SfxDirtyPersistenceService {
    private static final Set<String> PLACEABLE_GPS_TYPES = Set.of(
            "sf:gps_transmitter",
            "sf:gps_transmitter_2",
            "sf:gps_transmitter_3",
            "sf:gps_transmitter_4",
            "sf:gps_control_panel",
            "sf:gps_geo_scanner",
            "sf:geo_miner",
            "sf:oil_pump",
            "sf:gps_teleporter_pylon",
            "sf:gps_teleportation_matrix",
            "sf:gps_activation_device_shared",
            "sf:gps_activation_device_personal",
            "sf:elevator_plate"
    );
    private static final Set<String> TRANSMITTER_TYPES = Set.of(
            "sf:gps_transmitter",
            "sf:gps_transmitter_2",
            "sf:gps_transmitter_3",
            "sf:gps_transmitter_4"
    );
    private static final Map<String, Integer> TRANSMITTER_MULTIPLIERS = Map.of(
            "sf:gps_transmitter", 1,
            "sf:gps_transmitter_2", 4,
            "sf:gps_transmitter_3", 16,
            "sf:gps_transmitter_4", 64
    );
    private static final Map<String, Integer> TRANSMITTER_BONUSES = Map.of(
            "sf:gps_transmitter", 0,
            "sf:gps_transmitter_2", 100,
            "sf:gps_transmitter_3", 500,
            "sf:gps_transmitter_4", 600
    );
    private static final Map<String, Integer> TRANSMITTER_CONSUMPTION = Map.of(
            "sf:gps_transmitter", 2,
            "sf:gps_transmitter_2", 6,
            "sf:gps_transmitter_3", 22,
            "sf:gps_transmitter_4", 92
    );
    private static final int GEO_SCAN_REQUIRED_COMPLEXITY = 600;
    private static final int TELEPORT_REQUIRED_COMPLEXITY = 400;
    private static final int GEO_MINE_REQUIRED_COMPLEXITY = 800;
    private static final int MAX_WAYPOINTS_PER_PLAYER = 64;
    private static final int TELEPORTER_RADIUS = 1;
    private static final long GPS_PHYSICAL_RELEASE_CHECK_TICKS = 5L;
    private static final int[] CONTROL_PANEL_FRAME = {
            0, 1, 3, 5, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 26, 27, 35, 36, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };
    private static final int[] CONTROL_PANEL_CONTENT = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] GEO_SCAN_FRAME = {
            0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 48, 49, 50, 52, 53
    };
    private static final int[] GEO_SCAN_CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final String HEAD_GLOBE_OVERWORLD = "c9c8881e42915a9d29bb61a16fb26d059913204d265df5b439b3d792acd56";
    private static final String HEAD_MINECRAFT_CHUNK = "8449b9318e33158e64a46ab0de121c3d40000e3332c1574932b3c849d8fa0dc2";


    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxMenus menus;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxDecorationService decorations;
    private final SfxElectricMachineService electricMachines;
    private final SfxMachineRuntimeEngine machineRuntime;
    private final SfxGpsDataStore dataStore;
    private final Set<UUID> activeTeleports = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeTeleporterMenus = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeElevatorMenus = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingWaypointInput> pendingWaypointInputs = new ConcurrentHashMap<>();
    private final Map<UUID, PendingElevatorNameInput> pendingElevatorNameInputs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> elevatorCooldowns = new ConcurrentHashMap<>();
    private final Set<String> pressedPhysicalUses = ConcurrentHashMap.newKeySet();
    private final Set<String> scheduledPhysicalChecks = ConcurrentHashMap.newKeySet();

    public SfxGpsService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxMenus menus,
                         SfxLocalization localization, SfxBlockDataService blockData, SfxDecorationService decorations,
                         SfxElectricMachineService electricMachines, SfxGpsDataRepository dataRepository,
                         SfxMachineRuntimeEngine machineRuntime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.decorations = Objects.requireNonNull(decorations, "decorations");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.machineRuntime = Objects.requireNonNull(machineRuntime, "machineRuntime");
        this.dataStore = new SfxGpsDataStore(plugin, Objects.requireNonNull(dataRepository, "dataRepository"));
        try {
            this.dataStore.load();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize SFX GPS data storage", exception);
        }
        SfxGpsElectricBridge.bind(this);
    }

    @Override
    public void shutdown() {
        dataStore.shutdown();
        SfxGpsElectricBridge.unbind(this);
        activeTeleports.clear();
        activeTeleporterMenus.clear();
        activeElevatorMenus.clear();
        pendingWaypointInputs.clear();
        pendingElevatorNameInputs.clear();
        elevatorCooldowns.clear();
        pressedPhysicalUses.clear();
        scheduledPhysicalChecks.clear();
    }


    @Override
    public void requestDirtyFlushAsync() {
        dataStore.requestDirtyFlushAsync();
    }

    @Override
    public void requestChunkFlushAsync(org.bukkit.World world, int chunkX, int chunkZ) {
        dataStore.requestChunkFlushAsync(world, chunkX, chunkZ);
    }

    @Override
    public void flushAllBlocking() {
        dataStore.flushAllBlocking();
    }

    public boolean supportsType(String typeId) {
        return PLACEABLE_GPS_TYPES.contains(typeId);
    }


    public SfxMachinePhaseResult frameworkEffect(String effectName, SfxMachinePhaseContext context) {
        if (context == null) {
            return SfxMachinePhaseResult.cont();
        }
        context.put("gps.framework.effect", effectName);
        context.put("gps.framework.effect.handled", Boolean.TRUE);
        Player player = context.attachment("gps.player", Player.class).orElse(null);
        if (player != null) {
            context.put("gps.framework.player", player.getUniqueId());
            context.put("gps.framework.complexity", networkComplexity(player.getUniqueId()));
        }
        if (context.location() != null && context.location().getWorld() != null) {
            context.put("gps.framework.world", context.location().getWorld().getName());
        }
        return SfxMachinePhaseResult.cont();
    }

    private Map<String, Object> gpsInteractionAttributes(Player player, SfxBlockInstanceRecord instance) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("gps.player", player);
        attributes.put("gps.instance", instance);
        attributes.put("gps.service", this);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkEffect);
        return attributes;
    }

    private void runFrameworkInteraction(Player player, Block block, SfxBlockInstanceRecord instance, Map<String, Object> attributes) {
        if (block == null || instance == null) {
            return;
        }
        if (attributes == null) {
            attributes = gpsInteractionAttributes(player, instance);
        }
        if (SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, instance.instanceId(), block.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE, attributes), attributes, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
            machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.AFTER_TICK, instance.instanceId(), block.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE, attributes);
        }
    }

    public void handlePlaced(UUID instanceId, String typeId) {
        if ("sf:gps_teleporter_pylon".equals(typeId)) {
            decorations.handlePlaced(instanceId, typeId);
        }
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if ("sf:gps_teleporter_pylon".equals(typeId)) {
            decorations.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (block != null && typeId != null) {
            SfxBlockDrops.dropPluginBlock(block, items, typeId);
            blockData.unregisterAt(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();

        if (action == Action.PHYSICAL) {
            Block block = event.getClickedBlock();
            if (block == null) {
                return;
            }
            SfxBlockInstanceRecord instance = instanceAt(block.getLocation()).orElse(null);
            if (instance == null || !supportsType(instance.typeId())) {
                return;
            }
            Map<String, Object> physicalFramework = gpsInteractionAttributes(player, instance);
            runFrameworkInteraction(player, block, instance, physicalFramework);
            if (!SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.BEFORE_PROGRESS, instance.instanceId(), block.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE, physicalFramework), physicalFramework, SfxMachinePhase.BEFORE_PROGRESS.name())) {
                return;
            }
            scheduleGpsPhysicalUse(player, block, instance);
            SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.ON_COMPLETE, instance.instanceId(), block.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.RUNNING, physicalFramework), physicalFramework, SfxMachinePhase.ON_COMPLETE.name());
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            Optional<SfxItemMarker> marker = items.readMarker(player.getInventory().getItemInMainHand());
            if (marker.isPresent() && handleGpsItemUse(player, marker.get(), event.getClickedBlock(), event.getBlockFace())) {
                event.setCancelled(true);
                return;
            }
        }

        if (action != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        SfxBlockInstanceRecord instance = instanceAt(block.getLocation()).orElse(null);
        if (instance == null || !supportsType(instance.typeId())) {
            return;
        }
        Map<String, Object> clickFramework = gpsInteractionAttributes(player, instance);
        runFrameworkInteraction(player, block, instance, clickFramework);
        if (handleGpsBlockRightClick(player, block, instance)) {
            SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(instance.typeId(), SfxMachinePhase.ON_COMPLETE, instance.instanceId(), block.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.RUNNING, clickFramework), clickFramework, SfxMachinePhase.ON_COMPLETE.name());
            event.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingWaypointInput waypointInput = pendingWaypointInputs.remove(player.getUniqueId());
        PendingElevatorNameInput elevatorInput = waypointInput == null ? pendingElevatorNameInputs.remove(player.getUniqueId()) : null;
        if (waypointInput == null && elevatorInput == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        runtime.executeForPlayer(player, () -> {
            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("取消")) {
                send(player, "gps.messages.chat-input-cancelled", "<red>Input cancelled.</red>");
                return;
            }
            if (waypointInput != null) {
                completeWaypointInput(player, waypointInput, message);
            } else if (elevatorInput != null) {
                completeElevatorNameInput(player, elevatorInput, message);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGpsBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location[] neighbours = surroundingTeleporterMatrices(block.getLocation());
        for (Location location : neighbours) {
            SfxBlockInstanceRecord matrix = instanceAt(location).orElse(null);
            if (matrix != null && matrix.typeId().equals("sf:gps_teleportation_matrix")) {
                updateTeleporterDecorations(location, false, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!hasSfxItem(player, "sf:gps_emergency_transmitter")) {
            return;
        }
        String name = uniqueWaypointName(player.getUniqueId(), "Deathpoint");
        dataStore.addWaypoint(SfxGpsDataStore.waypoint(player.getUniqueId(), name, player.getLocation()));
        send(player, "gps.messages.emergency-waypoint-created", "<red>GPS emergency waypoint created: <white>{name}</white>", Map.of("name", escape(name)));
    }

    private boolean handleGpsItemUse(Player player, SfxItemMarker marker, Block clickedBlock, BlockFace clickedFace) {
        return switch (marker.itemId()) {
            case "sf:gps_marker_tool" -> {
                Location waypointLocation = markerTargetLocation(player, clickedBlock, clickedFace);
                pendingWaypointInputs.put(player.getUniqueId(), new PendingWaypointInput(waypointLocation));
                send(player, "gps.messages.marker-enter-name", "<yellow>Please type the waypoint name in chat. Type <white>cancel</white> to cancel.</yellow>");
                yield true;
            }
            case "sf:portable_geo_scanner" -> {
                scanChunk(player, player.getLocation());
                yield true;
            }
            case "sf:portable_teleporter" -> {
                Location source = player.getLocation().clone();
                int complexity = networkComplexity(player.getUniqueId());
                openWaypointMenu(player, player.getUniqueId(), null, true, source, complexity, false);
                yield true;
            }
            default -> false;
        };
    }

    private boolean handleGpsBlockRightClick(Player player, Block block, SfxBlockInstanceRecord instance) {
        return switch (instance.typeId()) {
            case "sf:gps_control_panel" -> {
                openControlPanel(player);
                yield true;
            }
            case "sf:gps_geo_scanner" -> {
                scanChunk(player, block.getLocation());
                yield true;
            }
            case "sf:elevator_plate" -> {
                openElevatorEditor(player, block.getLocation(), instance);
                yield true;
            }
            default -> false;
        };
    }

    private void scheduleGpsPhysicalUse(Player player, Block block, SfxBlockInstanceRecord instance) {
        String typeId = instance.typeId();
        if (!isGpsPhysicalTrigger(typeId)) {
            return;
        }
        String key = physicalUseKey(player, block);
        if (pressedPhysicalUses.contains(key) || !scheduledPhysicalChecks.add(key)) {
            return;
        }
        Location location = block.getLocation().clone();
        UUID instanceId = instance.instanceId();
        runtime.executeForPlayerLater(player, 1L, () -> {
            scheduledPhysicalChecks.remove(key);
            Block liveBlock = location.getBlock();
            SfxBlockInstanceRecord liveInstance = instanceAt(location).orElse(null);
            if (liveInstance == null || !liveInstance.instanceId().equals(instanceId) || !typeId.equals(liveInstance.typeId())) {
                pressedPhysicalUses.remove(key);
                return;
            }
            if (!isPhysicalPlatePressed(player, liveBlock)) {
                pressedPhysicalUses.remove(key);
                return;
            }
            if (!pressedPhysicalUses.add(key)) {
                schedulePhysicalReleaseWatch(player, location, key, instanceId, typeId);
                return;
            }
            handleGpsPhysicalActivation(player, liveBlock, liveInstance);
            schedulePhysicalReleaseWatch(player, location, key, instanceId, typeId);
        });
    }

    private boolean isGpsPhysicalTrigger(String typeId) {
        return "sf:gps_activation_device_shared".equals(typeId)
                || "sf:gps_activation_device_personal".equals(typeId)
                || "sf:elevator_plate".equals(typeId);
    }

    private void handleGpsPhysicalActivation(Player player, Block block, SfxBlockInstanceRecord instance) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        String typeId = instance.typeId();
        if (activeTeleports.contains(playerId)) {
            return;
        }
        if (("sf:gps_activation_device_shared".equals(typeId) || "sf:gps_activation_device_personal".equals(typeId))
                && activeTeleporterMenus.contains(playerId)) {
            return;
        }
        if ("sf:elevator_plate".equals(typeId) && activeElevatorMenus.contains(playerId)) {
            return;
        }
        Long cooldownUntil = elevatorCooldowns.get(playerId);
        if (cooldownUntil != null && cooldownUntil > now) {
            return;
        }
        switch (typeId) {
            case "sf:gps_activation_device_shared", "sf:gps_activation_device_personal" -> activateFromPlate(player, block.getLocation(), instance);
            case "sf:elevator_plate" -> openElevatorFloorSelector(player, block.getLocation());
            default -> {
            }
        }
    }

    private void schedulePhysicalReleaseWatch(Player player, Location location, String key, UUID instanceId, String typeId) {
        runtime.executeForPlayerLater(player, GPS_PHYSICAL_RELEASE_CHECK_TICKS, () -> {
            Block liveBlock = location.getBlock();
            SfxBlockInstanceRecord liveInstance = instanceAt(location).orElse(null);
            if (liveInstance == null || !liveInstance.instanceId().equals(instanceId) || !typeId.equals(liveInstance.typeId())
                    || !isPhysicalPlatePressed(player, liveBlock)) {
                pressedPhysicalUses.remove(key);
                scheduledPhysicalChecks.remove(key);
                return;
            }
            schedulePhysicalReleaseWatch(player, location, key, instanceId, typeId);
        });
    }


    private boolean isPhysicalPlatePressed(Player player, Block block) {
        if (block == null) {
            return false;
        }
        Object data = block.getBlockData();
        if (data instanceof Powerable powerable && powerable.isPowered()) {
            return true;
        }
        if (data instanceof AnaloguePowerable analoguePowerable && analoguePowerable.getPower() > 0) {
            return true;
        }
        return playerIsStandingOnPlate(player, block);
    }

    private boolean playerIsStandingOnPlate(Player player, Block block) {
        if (player == null || block == null || player.getWorld() == null || block.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().getUID().equals(block.getWorld().getUID())) {
            return false;
        }
        Location location = player.getLocation();
        if (location.getBlockX() != block.getX() || location.getBlockZ() != block.getZ()) {
            return false;
        }
        double minY = block.getY();
        double maxY = block.getY() + 2.05D;
        return location.getY() >= minY && location.getY() <= maxY;
    }

    private String physicalUseKey(Player player, Block block) {
        Location location = block.getLocation();
        UUID worldId = location.getWorld() == null ? new UUID(0L, 0L) : location.getWorld().getUID();
        return player.getUniqueId() + ":" + worldId + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void openControlPanel(Player player) {
        openTransmitterControlPanel(player);
    }

    private void openTransmitterControlPanel(Player player) {
        UUID owner = player.getUniqueId();
        int complexity = networkComplexity(owner);
        List<SfxAnchorRecord> transmitters = onlineTransmitters(owner);
        SfxMenu.Builder builder = SfxMenu.builder(component("gps.ui.control-panel.title", "<dark_aqua>GPS Control Panel")).rows(6);
        addControlPanelFrame(builder);
        builder.button(2, new SfxMenuButton(namedItem(
                itemIcon("sf:gps_transmitter", Material.COMPASS),
                component("gps.ui.control-panel.transmitters.name", "<aqua>GPS Transmitters"),
                List.of(component("gps.ui.control-panel.transmitters.lore", "<gray>Online transmitters: <yellow>{count}", Map.of("count", transmitters.size())))),
                click -> openTransmitterControlPanel(click.player())));
        builder.button(4, new SfxMenuButton(namedItem(
                ItemBuilder.of(Material.MAP).build(),
                component("gps.ui.control-panel.network-info.name", "<aqua>Network Info"),
                List.of(
                        component(complexity > 0 ? "gps.ui.control-panel.network-info.status-online" : "gps.ui.control-panel.network-info.status-offline", complexity > 0 ? "<gray>Status: <green>ONLINE" : "<gray>Status: <red>OFFLINE"),
                        component("gps.ui.control-panel.network-info.complexity", "<gray>Complexity: <yellow>{complexity}", Map.of("complexity", complexity)),
                        component("gps.ui.control-panel.network-info.transmitters", "<gray>Transmitters: <yellow>{count}", Map.of("count", transmitters.size())))),
                click -> openTransmitterControlPanel(click.player())));
        builder.button(6, new SfxMenuButton(namedItem(
                headIcon(HEAD_GLOBE_OVERWORLD, Material.REDSTONE_TORCH),
                component("gps.ui.control-panel.waypoints.name", "<aqua>Waypoints"),
                List.of(
                        component("gps.ui.control-panel.waypoints.stored", "<gray>Stored: <yellow>{count}", Map.of("count", dataStore.waypoints(owner).size())),
                        component("gps.ui.control-panel.waypoints.view", "<yellow>Click to manage"))),
                click -> openControlWaypointPanel(click.player())));

        int index = 0;
        for (SfxAnchorRecord anchor : transmitters.stream().limit(CONTROL_PANEL_CONTENT.length).toList()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            int strength = transmitterStrength(instance, anchor);
            double ping = 1000D / Math.max(1, anchor.key().y());
            String pingText = String.format(java.util.Locale.ROOT, "%.2f", ping);
            builder.button(CONTROL_PANEL_CONTENT[index++], new SfxMenuButton(namedItem(
                    itemIcon(instance.typeId(), Material.PLAYER_HEAD),
                    localization.itemName(instance.typeId(), component("gps.ui.control-panel.transmitter-entry.name", "<green>GPS Transmitter")),
                    List.of(
                            component("gps.ui.control-panel.transmitter-entry.world", "<gray>World: <white>{world}", Map.of("world", escape(worldName(anchor)))),
                            component("gps.ui.control-panel.transmitter-entry.coords", "<gray>X/Y/Z: <white>{x} / {y} / {z}", Map.of("x", anchor.key().x(), "y", anchor.key().y(), "z", anchor.key().z())),
                            component("gps.ui.control-panel.transmitter-entry.strength", "<gray>Signal Strength: <yellow>{strength}", Map.of("strength", strength)),
                            component("gps.ui.control-panel.transmitter-entry.ping", "<gray>Ping: <yellow>{ping}ms", Map.of("ping", pingText)))),
                    click -> openTransmitterControlPanel(click.player())));
        }
        if (transmitters.isEmpty()) {
            builder.button(31, new SfxMenuButton(namedItem(
                    ItemBuilder.of(Material.BARRIER).build(),
                    component("gps.ui.control-panel.no-transmitters.name", "<red>No online transmitters"),
                    List.of(component("gps.ui.control-panel.no-transmitters.lore", "<gray>Place and power GPS transmitters to build the network."))),
                    click -> openTransmitterControlPanel(click.player())));
        }
        menus.openRoot(player, builder.build());
    }

    private void openControlWaypointPanel(Player player) {
        UUID owner = player.getUniqueId();
        int complexity = networkComplexity(owner);
        List<SfxGpsWaypoint> waypoints = dataStore.waypoints(owner).stream()
                .sorted(Comparator.comparing(SfxGpsWaypoint::createdAt).reversed())
                .limit(CONTROL_PANEL_CONTENT.length)
                .toList();
        SfxMenu.Builder builder = SfxMenu.builder(component("gps.ui.control-panel.title", "<dark_aqua>GPS Control Panel")).rows(6);
        addControlPanelFrame(builder);
        builder.button(2, new SfxMenuButton(namedItem(
                itemIcon("sf:gps_transmitter", Material.COMPASS),
                component("gps.ui.control-panel.transmitters.name", "<aqua>GPS Transmitters"),
                List.of(component("gps.ui.control-panel.transmitters.view", "<yellow>Click to view transmitters"))),
                click -> openTransmitterControlPanel(click.player())));
        builder.button(4, new SfxMenuButton(namedItem(
                ItemBuilder.of(Material.MAP).build(),
                component("gps.ui.control-panel.network-info.name", "<aqua>Network Info"),
                List.of(
                        component(complexity > 0 ? "gps.ui.control-panel.network-info.status-online" : "gps.ui.control-panel.network-info.status-offline", complexity > 0 ? "<gray>Status: <green>ONLINE" : "<gray>Status: <red>OFFLINE"),
                        component("gps.ui.control-panel.network-info.complexity", "<gray>Complexity: <yellow>{complexity}", Map.of("complexity", complexity)),
                        component("gps.ui.control-panel.network-info.waypoints", "<gray>Waypoints: <yellow>{count}", Map.of("count", dataStore.waypoints(owner).size())))),
                click -> openControlWaypointPanel(click.player())));
        builder.button(6, new SfxMenuButton(namedItem(
                headIcon(HEAD_GLOBE_OVERWORLD, Material.REDSTONE_TORCH),
                component("gps.ui.control-panel.waypoints.name", "<aqua>Waypoints"),
                List.of(component("gps.ui.control-panel.waypoints.delete-hint", "<gray>Click a waypoint to delete it."))),
                click -> openControlWaypointPanel(click.player())));

        int index = 0;
        for (SfxGpsWaypoint waypoint : waypoints) {
            ItemStack icon = waypoint.toLocation() == null ? ItemBuilder.of(Material.BARRIER).build() : headIcon(HEAD_GLOBE_OVERWORLD, Material.ENDER_PEARL);
            builder.button(CONTROL_PANEL_CONTENT[index++], new SfxMenuButton(namedItem(
                    icon,
                    component("gps.ui.control-panel.waypoint-entry.name", "<aqua>{name}", Map.of("name", escape(waypoint.name()))),
                    List.of(
                            component("gps.ui.control-panel.waypoint-entry.world", "<gray>World: <white>{world}", Map.of("world", escape(waypoint.worldName()))),
                            component("gps.ui.control-panel.waypoint-entry.coords", "<gray>X/Y/Z: <white>{coords}", Map.of("coords", blockCoords(waypoint))),
                            component("gps.ui.control-panel.waypoint-entry.delete", "<yellow>Click to delete"))),
                    click -> {
                        dataStore.removeWaypoint(click.player().getUniqueId(), waypoint.name());
                        send(click.player(), "gps.messages.waypoint-deleted", "<red>Waypoint deleted: <white>{name}</white>", Map.of("name", escape(waypoint.name())));
                        openControlWaypointPanel(click.player());
                    }));
        }
        if (waypoints.isEmpty()) {
            builder.button(31, new SfxMenuButton(namedItem(ItemBuilder.of(Material.BARRIER).build(), component("gps.ui.control-panel.no-waypoints.name", "<red>No waypoints"), List.of()), click -> openControlWaypointPanel(click.player())));
        }
        menus.openRoot(player, builder.build());
    }

    private void addControlPanelFrame(SfxMenu.Builder builder) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int slot : CONTROL_PANEL_FRAME) {
            builder.button(slot, new SfxMenuButton(filler, click -> {}));
        }
    }

    private void openWaypointMenu(Player player, UUID owner, Location matrixLocation, boolean portable, Location source, int complexity, boolean trackTeleporterMenu) {
        if (trackTeleporterMenu && !activeTeleporterMenus.add(player.getUniqueId())) {
            return;
        }
        List<SfxGpsWaypoint> waypoints = dataStore.waypoints(owner);
        SfxMenu.Builder builder = SfxMenu.builder(component("gps.ui.waypoints.title", "<dark_aqua>GPS Waypoints")).rows(6);
        int slot = 0;
        for (SfxGpsWaypoint waypoint : waypoints.stream().sorted(Comparator.comparing(SfxGpsWaypoint::createdAt).reversed()).limit(45).toList()) {
            Location target = waypoint.toLocation();
            int intervals = target == null ? 0 : teleportationTime(complexity, source, target);
            String seconds = teleportTimeText(intervals);
            ItemStack icon = target == null ? ItemBuilder.of(Material.BARRIER).build() : headIcon(HEAD_GLOBE_OVERWORLD, Material.ENDER_PEARL);
            builder.button(slot++, new SfxMenuButton(namedItem(icon,
                    component("gps.ui.waypoints.entry.name", "<aqua>{name}", Map.of("name", escape(waypoint.name()))),
                    List.of(
                            component("gps.ui.waypoints.entry.world", "<gray>World: <white>{world}", Map.of("world", escape(waypoint.worldName()))),
                            component("gps.ui.waypoints.entry.coords", "<gray>X/Y/Z: <white>{coords}", Map.of("coords", blockCoords(waypoint))),
                            component("gps.ui.waypoints.entry.time", "<gray>Estimated Time: <yellow>{time}s", Map.of("time", seconds)),
                            component("gps.ui.waypoints.entry.teleport", "<yellow>Click to teleport"))),
                    click -> startTeleport(click.player(), waypoint, matrixLocation, portable, source, complexity)));
        }
        if (waypoints.isEmpty()) {
            builder.button(22, new SfxMenuButton(namedItem(ItemBuilder.of(Material.BARRIER).build(), component("gps.ui.waypoints.no-waypoints", "<red>No waypoints"), List.of()), click -> {}));
        }
        if (trackTeleporterMenu) {
            builder.onClose(closed -> activeTeleporterMenus.remove(closed.getUniqueId()));
        }
        menus.openRoot(player, builder.build());
    }

    private void scanChunk(Player player, Location location) {
        int complexity = networkComplexity(player.getUniqueId());
        if (complexity < GEO_SCAN_REQUIRED_COMPLEXITY) {
            send(player, "gps.messages.complexity-too-low", "<red>Your GPS network complexity is too low. Required: <white>{required}</white>", Map.of("required", GEO_SCAN_REQUIRED_COMPLEXITY));
            return;
        }
        SfxGeoChunkKey key = SfxGeoChunkKey.from(location);
        dataStore.markScanned(key, location);
        electricMachines.wakeGeoExtractorsInChunk(key);
        openGeoScanResults(player, location, 0);
    }

    private void openGeoScanResults(Player player, Location location, int page) {
        SfxGeoChunkKey key = SfxGeoChunkKey.from(location);
        Map<SfxGeoResourceType, Integer> resources = dataStore.resources(key, location);
        List<SfxGeoResourceType> resourceTypes = new ArrayList<>(List.of(SfxGeoResourceType.values()));
        resourceTypes.sort(Comparator.comparing(type -> resourceNameText(type).toLowerCase(java.util.Locale.ROOT)));
        int pages = Math.max(1, (resourceTypes.size() - 1) / GEO_SCAN_CONTENT.length + 1);
        int safePage = Math.max(0, Math.min(page, pages - 1));

        SfxMenu.Builder builder = SfxMenu.builder(component("gps.ui.geo-scan.title", "<dark_aqua>GEO Scan")).rows(6);
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int slot : GEO_SCAN_FRAME) {
            builder.button(slot, new SfxMenuButton(filler, click -> {}));
        }
        builder.button(4, new SfxMenuButton(namedItem(
                headIcon(HEAD_MINECRAFT_CHUNK, Material.FILLED_MAP),
                component("gps.ui.geo-scan.chunk.name", "<yellow>Chunk"),
                List.of(
                        component("gps.ui.geo-scan.chunk.world", "<gray>World: <white>{world}", Map.of("world", escape(key.worldName()))),
                        component("gps.ui.geo-scan.chunk.coords", "<gray>X: <white>{x}</white> Z: <white>{z}", Map.of("x", key.chunkX(), "z", key.chunkZ())))),
                click -> {}));

        int start = safePage * GEO_SCAN_CONTENT.length;
        int end = Math.min(resourceTypes.size(), start + GEO_SCAN_CONTENT.length);
        for (int i = start; i < end; i++) {
            SfxGeoResourceType type = resourceTypes.get(i);
            int amount = resources.getOrDefault(type, 0);
            ItemStack icon = createResourceItem(type, type == SfxGeoResourceType.OIL);
            if (amount > 1) {
                icon.setAmount(Math.max(1, Math.min(icon.getMaxStackSize(), amount)));
            }
            int slot = GEO_SCAN_CONTENT[i - start];
            builder.button(slot, new SfxMenuButton(namedItem(
                    icon,
                    component("gps.ui.geo-scan.resource.name", "<white>{resource}", Map.of("resource", resourceNameText(type))),
                    List.of(component(
                            amount == 1 ? "gps.ui.geo-scan.resource.amount-singular" : "gps.ui.geo-scan.resource.amount-plural",
                            "<dark_gray>⇨ <yellow>{amount} Units",
                            Map.of("amount", amount)))),
                    click -> {}));
        }
        builder.button(47, new SfxMenuButton(namedItem(ItemBuilder.of(Material.ARROW).build(), component("gps.ui.geo-scan.previous", "<yellow>Previous Page"), List.of(component("gps.ui.geo-scan.page", "<gray>Page <white>{page}</white>/<white>{pages}", Map.of("page", safePage + 1, "pages", pages)))), click -> {
            if (safePage > 0) {
                openGeoScanResults(click.player(), location, safePage - 1);
            }
        }));
        builder.button(51, new SfxMenuButton(namedItem(ItemBuilder.of(Material.ARROW).build(), component("gps.ui.geo-scan.next", "<yellow>Next Page"), List.of(component("gps.ui.geo-scan.page", "<gray>Page <white>{page}</white>/<white>{pages}", Map.of("page", safePage + 1, "pages", pages)))), click -> {
            if (safePage + 1 < pages) {
                openGeoScanResults(click.player(), location, safePage + 1);
            }
        }));
        menus.openRoot(player, builder.build());
    }

    private void mineGeoResource(Player player, Location location, boolean oilOnly) {
        int complexity = networkComplexity(player.getUniqueId());
        if (complexity < GEO_MINE_REQUIRED_COMPLEXITY) {
            send(player, "gps.messages.complexity-too-low", "<red>Your GPS network complexity is too low. Required: <white>{required}</white>", Map.of("required", GEO_MINE_REQUIRED_COMPLEXITY));
            return;
        }
        SfxGeoChunkKey key = SfxGeoChunkKey.from(location);
        if (!dataStore.isScanned(key)) {
            send(player, "gps.messages.chunk-not-scanned", "<red>This chunk has not been GEO-scanned yet.</red>");
            return;
        }
        SfxGeoResourceType selected = oilOnly ? SfxGeoResourceType.OIL : selectBestResource(key, location);
        if (selected == null || dataStore.resources(key, location).getOrDefault(selected, 0) <= 0) {
            send(player, "gps.messages.no-geo-resource", "<red>No usable GEO resource remains in this chunk.</red>");
            return;
        }
        if (!dataStore.consume(key, selected, 1, location)) {
            send(player, "gps.messages.no-geo-resource", "<red>No usable GEO resource remains in this chunk.</red>");
            return;
        }
        ItemStack output = createResourceItem(selected, oilOnly);
        player.getInventory().addItem(output).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        send(player, "gps.messages.extracted", "<green>Extracted <white>{resource}</white>.</green>", Map.of("resource", escape(resourceNameText(selected))));
    }

    private SfxGeoResourceType selectBestResource(SfxGeoChunkKey key, Location location) {
        Map<SfxGeoResourceType, Integer> resources = dataStore.resources(key, location);
        for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
            if (type != SfxGeoResourceType.OIL && resources.getOrDefault(type, 0) > 0) {
                return type;
            }
        }
        return null;
    }

    private ItemStack createResourceItem(SfxGeoResourceType type, boolean oilPump) {
        if (oilPump || type == SfxGeoResourceType.OIL) {
            return items.create("sf:bucket_of_oil");
        }
        try {
            return items.create(type.itemId());
        } catch (RuntimeException ignored) {
            Material fallback = switch (type) {
                case SALT -> Material.SUGAR;
                case URANIUM -> Material.LIME_DYE;
                case NETHER_ICE -> Material.BLUE_ICE;
                case OIL -> Material.BUCKET;
            };
            return new ItemStack(fallback);
        }
    }


    public SfxGpsExtractionResult peekExtraction(Location location, boolean oilOnly) {
        if (location == null || location.getWorld() == null) {
            return SfxGpsExtractionResult.notScanned();
        }
        SfxGeoChunkKey key = SfxGeoChunkKey.from(location);
        if (!dataStore.isScanned(key)) {
            return SfxGpsExtractionResult.notScanned();
        }
        SfxGeoResourceType selected = oilOnly ? SfxGeoResourceType.OIL : selectBestResource(key, location);
        if (selected == null || dataStore.resources(key, location).getOrDefault(selected, 0) <= 0) {
            return SfxGpsExtractionResult.empty();
        }
        return SfxGpsExtractionResult.output(createResourceStack(selected, oilOnly));
    }

    public SfxGpsExtractionResult consumeExtraction(Location location, boolean oilOnly) {
        SfxGpsExtractionResult peek = peekExtraction(location, oilOnly);
        if (!peek.scanned() || !peek.hasResource() || peek.output() == null) {
            return peek;
        }
        SfxGeoChunkKey key = SfxGeoChunkKey.from(location);
        SfxGeoResourceType selected = oilOnly ? SfxGeoResourceType.OIL : selectBestResource(key, location);
        if (selected == null || !dataStore.consume(key, selected, 1, location)) {
            return SfxGpsExtractionResult.empty();
        }
        return SfxGpsExtractionResult.output(createResourceStack(selected, oilOnly));
    }

    private SfxElectricStack createResourceStack(SfxGeoResourceType type, boolean oilPump) {
        if (oilPump || type == SfxGeoResourceType.OIL) {
            return SfxElectricStack.sfx("sf:bucket_of_oil", 1);
        }
        return SfxElectricStack.sfx(type.itemId(), 1);
    }

    private void activateFromPlate(Player player, Location plateLocation, SfxBlockInstanceRecord plate) {
        Location matrix = plateLocation.clone().subtract(0, 1, 0);
        SfxBlockInstanceRecord matrixInstance = instanceAt(matrix).orElse(null);
        if (matrixInstance == null || !matrixInstance.typeId().equals("sf:gps_teleportation_matrix")) {
            send(player, "gps.messages.activation-not-on-matrix", "<red>This activation device is not placed on a GPS Teleporter Matrix.</red>");
            return;
        }
        if (plate.typeId().endsWith("_personal") && plate.ownerId() != null && !plate.ownerId().equals(player.getUniqueId())) {
            send(player, "gps.messages.personal-device-owner", "<red>This personal activation device belongs to another player.</red>");
            return;
        }
        activateTeleporter(player, matrix);
    }

    private void activateTeleporter(Player player, Location matrixLocation) {
        SfxBlockInstanceRecord matrix = instanceAt(matrixLocation).orElse(null);
        if (matrix == null || !matrix.typeId().equals("sf:gps_teleportation_matrix")) {
            return;
        }
        int complexity = networkComplexity(matrix.ownerId());
        if (complexity < TELEPORT_REQUIRED_COMPLEXITY) {
            updateTeleporterDecorations(matrixLocation, false, true);
            send(player, "gps.messages.matrix-complexity-too-low", "<red>The matrix owner's GPS network complexity is too low.</red>");
            return;
        }
        List<Location> pylons = teleporterPylons(matrixLocation);
        if (pylons.size() < 8) {
            updateTeleporterDecorations(matrixLocation, false, true);
            send(player, "gps.messages.teleporter-incomplete", "<red>The GPS Teleporter structure is incomplete.</red>");
            return;
        }
        updateTeleporterDecorations(matrixLocation, true, false);
        Location source = teleporterSource(matrixLocation);
        openWaypointMenu(player, matrix.ownerId(), matrixLocation, false, source, complexity, true);
    }

    private void startTeleport(Player player, SfxGpsWaypoint waypoint, Location matrixLocation, boolean portable, Location source, int complexity) {
        Location target = waypoint.toLocation();
        if (target == null || target.getWorld() == null) {
            send(player, "gps.messages.target-world-not-loaded", "<red>The target world is not loaded.</red>");
            return;
        }
        if (source == null || source.getWorld() == null) {
            source = player.getLocation().clone();
        }
        if (activeTeleports.contains(player.getUniqueId())) {
            return;
        }
        if (!portable) {
            activeTeleporterMenus.remove(player.getUniqueId());
        }
        menus.close(player);
        activeTeleports.add(player.getUniqueId());
        if (matrixLocation != null) {
            updateTeleporterDecorations(matrixLocation, true, false);
        }
        int intervals = teleportationTime(complexity, source, target);
        send(player, "gps.messages.teleporting", "<aqua>Teleporting to <white>{waypoint}</white>...</aqua>", Map.of("waypoint", escape(waypoint.name())));
        int totalTicks = Math.max(1, intervals * 10);
        updateTeleportProgress(player, source.clone(), target.clone(), matrixLocation == null ? null : matrixLocation.clone(), portable, totalTicks, 0);
    }

    private void updateTeleportProgress(Player player, Location source, Location target, Location matrixLocation, boolean portable, int totalTicks, int elapsedTicks) {
        if (!activeTeleports.contains(player.getUniqueId())) {
            return;
        }
        if (!portable && !playerRemainedAtSource(player, source)) {
            activeTeleports.remove(player.getUniqueId());
            if (matrixLocation != null) {
                updateTeleporterDecorations(matrixLocation, true, true);
            }
            showTeleportEndTitle(player, "gps.ui.teleport.cancelled.title", "&cTeleportation Cancelled", "gps.ui.teleport.cancelled.subtitle", "&7You moved away from the teleporter.", 0);
            send(player, "gps.messages.teleport-cancelled-moved", "<red>Teleport cancelled because you moved.</red>");
            return;
        }
        if (elapsedTicks >= totalTicks) {
            activeTeleports.remove(player.getUniqueId());
            player.teleportAsync(target).thenRun(() -> runtime.executeForPlayer(player, () -> {
                if (target.getWorld() != null) {
                    target.getWorld().spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 80, 0.3D, 0.8D, 0.3D, 0.1D);
                    target.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                }
                showTeleportEndTitle(player, "gps.ui.teleport.complete.title", "&aTeleported", "gps.ui.teleport.complete.subtitle", "&7Destination reached.", 100);
                if (matrixLocation != null) {
                    updateTeleporterDecorations(matrixLocation, true, false);
                }
            }));
            return;
        }
        int progress = Math.max(0, Math.min(99, (int) Math.floor((elapsedTicks * 100.0D) / Math.max(1, totalTicks))));
        showTeleportProgressTitle(player, "gps.ui.teleport.progress.title", "&bTeleporting", "gps.ui.teleport.progress.subtitle", "&e{progress}%", progress);
        if (source.getWorld() != null && elapsedTicks % 10 == 0) {
            int particles = Math.max(1, progress * 2);
            source.getWorld().spawnParticle(Particle.PORTAL, source, particles, 0.5D, 0.75D, 0.5D, 0.02D);
            source.getWorld().playSound(source, Sound.BLOCK_PORTAL_AMBIENT, 0.25F, 1.8F);
        }
        runtime.executeForPlayerLater(player, 1L, () -> updateTeleportProgress(player, source, target, matrixLocation, portable, totalTicks, elapsedTicks + 1));
    }

    private boolean playerRemainedAtSource(Player player, Location source) {
        if (player == null || !player.isValid() || source == null || source.getWorld() == null || player.getWorld() == null) {
            return false;
        }
        if (!player.getWorld().getUID().equals(source.getWorld().getUID())) {
            return false;
        }
        return player.getLocation().distanceSquared(source) < 2.0D;
    }

    private int teleportationTime(int complexity, Location source, Location destination) {
        if (complexity < 100) {
            return 100;
        }
        long speed = 50_000L + (long) complexity * (long) complexity;
        int unsafeTime = (int) Math.min(4L * teleportDistanceSquared(source, destination) / Math.max(1L, speed), 40L);
        return Math.max(1, unsafeTime);
    }

    private int teleportDistanceSquared(Location source, Location destination) {
        if (source == null || destination == null || source.getWorld() == null || destination.getWorld() == null) {
            return 150_000_000;
        }
        if (source.getWorld().getUID().equals(destination.getWorld().getUID())) {
            return Math.min((int) source.distanceSquared(destination), 100_000_000);
        }
        return 150_000_000;
    }

    private String teleportTimeText(int intervals) {
        double seconds = Math.max(1, intervals) * 0.5D;
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return String.valueOf((int) Math.rint(seconds));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private Location teleporterSource(Location matrixLocation) {
        return matrixLocation.clone().add(0.5D, 2.0D, 0.5D);
    }

    private void showTeleportProgressTitle(Player player, String titleKey, String titleFallback, String subtitleKey, String subtitleFallback, int progress) {
        showTeleportTitle(player, titleKey, titleFallback, subtitleKey, subtitleFallback, progress, 0, 60, 0);
    }

    private void showTeleportEndTitle(Player player, String titleKey, String titleFallback, String subtitleKey, String subtitleFallback, int progress) {
        showTeleportTitle(player, titleKey, titleFallback, subtitleKey, subtitleFallback, progress, 20, 60, 20);
    }

    private void showTeleportTitle(Player player, String titleKey, String titleFallback, String subtitleKey, String subtitleFallback,
                                   int progress, int fadeIn, int stay, int fadeOut) {
        String title = localization.text(titleKey, titleFallback, Map.of("progress", progress));
        String subtitle = localization.text(subtitleKey, subtitleFallback, Map.of("progress", progress));
        player.sendTitle(colorize(title), colorize(subtitle), fadeIn, stay, fadeOut);
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', Text.toLegacy(Text.renderFlexible(text)));
    }

    private void openElevatorFloorSelector(Player player, Location plateLocation) {
        List<Location> floors = elevatorFloors(plateLocation);
        if (floors.size() <= 1) {
            send(player, "gps.messages.no-elevator-target", "<red>No elevator plate found {direction}.</red>",
                    Map.of("direction", localization.text("gps.ui.elevator.direction.vertical", "above or below")));
            return;
        }
        if (!activeElevatorMenus.add(player.getUniqueId())) {
            return;
        }
        SfxMenu.Builder builder = SfxMenu.builder(component("gps.ui.elevator.title", "<dark_aqua>Elevator")).rows(6);
        int slot = 0;
        for (Location floor : floors.stream().limit(54).toList()) {
            boolean current = floor.getBlockY() == plateLocation.getBlockY();
            ItemStack icon = ItemBuilder.of(current ? Material.LIME_STAINED_GLASS_PANE : Material.ENDER_PEARL).build();
            builder.button(slot++, new SfxMenuButton(namedItem(icon,
                    component(current ? "gps.ui.elevator.current-floor.name" : "gps.ui.elevator.floor.name", current ? "<green>{name}" : "<aqua>{name}", Map.of("name", escape(elevatorName(floor)))),
                    List.of(
                            component("gps.ui.elevator.floor.y", "<gray>Y: <white>{y}", Map.of("y", floor.getBlockY())),
                            component(current ? "gps.ui.elevator.current-floor.lore" : "gps.ui.elevator.floor.teleport", current ? "<gray>This is your current floor." : "<yellow>Click to teleport"))),
                    click -> {
                        if (!current) {
                            teleportElevator(click.player(), floor);
                        }
                    }));
        }
        builder.onClose(closed -> activeElevatorMenus.remove(closed.getUniqueId()));
        menus.openRoot(player, builder.build());
    }

    private void openElevatorEditor(Player player, Location plateLocation, SfxBlockInstanceRecord instance) {
        if (instance.ownerId() != null && !instance.ownerId().equals(player.getUniqueId())) {
            send(player, "gps.messages.elevator-owner-only", "<red>Only the owner can rename this elevator floor.</red>");
            return;
        }
        pendingElevatorNameInputs.put(player.getUniqueId(), new PendingElevatorNameInput(plateLocation.clone()));
        send(player, "gps.messages.elevator-enter-name", "<yellow>Please type the elevator floor name in chat. Type <white>cancel</white> to cancel.</yellow>");
    }

    private void teleportElevator(Player player, Location floor) {
        activeElevatorMenus.remove(player.getUniqueId());
        menus.close(player);
        elevatorCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 1500L);
        Location target = floor.clone().add(0.5D, 0.0D, 0.5D);
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.teleportAsync(target);
        if (target.getWorld() != null) {
            target.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 1.2F);
        }
    }

    private List<Location> elevatorFloors(Location source) {
        if (source == null || source.getWorld() == null) {
            return List.of();
        }
        List<Location> floors = new ArrayList<>();
        World world = source.getWorld();
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
            Location check = new Location(world, source.getBlockX(), y, source.getBlockZ());
            SfxBlockInstanceRecord instance = instanceAt(check).orElse(null);
            if (instance != null && instance.typeId().equals("sf:elevator_plate")) {
                floors.add(check);
            }
        }
        floors.sort(Comparator.comparingInt(Location::getBlockY));
        return floors;
    }

    private String elevatorName(Location location) {
        String custom = dataStore.elevatorName(location);
        return custom == null || custom.isBlank()
                ? localization.text("gps.ui.elevator.default-floor", "Floor {y}", Map.of("y", location.getBlockY()))
                : custom;
    }

    private Location markerTargetLocation(Player player, Block clickedBlock, BlockFace clickedFace) {
        Location location;
        if (clickedBlock != null && clickedFace != null) {
            location = clickedBlock.getRelative(clickedFace).getLocation();
        } else {
            location = player.getLocation().clone();
        }
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        return location;
    }

    private void completeWaypointInput(Player player, PendingWaypointInput input, String name) {
        if (name == null || name.isBlank()) {
            send(player, "gps.messages.chat-input-empty", "<red>The name cannot be empty.</red>");
            return;
        }
        createWaypoint(player, input.location(), uniqueWaypointName(player.getUniqueId(), name.trim()));
    }

    private void completeElevatorNameInput(Player player, PendingElevatorNameInput input, String name) {
        if (name == null || name.isBlank()) {
            send(player, "gps.messages.chat-input-empty", "<red>The name cannot be empty.</red>");
            return;
        }
        dataStore.setElevatorName(input.location(), name.trim());
        send(player, "gps.messages.elevator-name-set", "<green>Elevator floor renamed to <white>{name}</white>.</green>", Map.of("name", escape(name.trim())));
    }

    private void createWaypoint(Player player, Location location, String name) {
        List<SfxGpsWaypoint> existing = dataStore.waypoints(player.getUniqueId());
        if (existing.size() >= MAX_WAYPOINTS_PER_PLAYER) {
            send(player, "gps.messages.waypoint-limit", "<red>You have reached the GPS waypoint limit.</red>");
            return;
        }
        dataStore.addWaypoint(SfxGpsDataStore.waypoint(player.getUniqueId(), name, location));
        send(player, "gps.messages.waypoint-created", "<green>Waypoint created: <white>{name}</white>", Map.of("name", escape(name)));
    }

    private int networkComplexity(UUID owner) {
        if (owner == null) {
            return 0;
        }
        int complexity = 0;
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !TRANSMITTER_TYPES.contains(instance.typeId()) || !owner.equals(instance.ownerId())) {
                continue;
            }
            int requiredEnergy = Math.max(1, TRANSMITTER_CONSUMPTION.getOrDefault(instance.typeId(), 1));
            if (electricMachines.consumerStoredEnergy(instance.instanceId()) < requiredEnergy) {
                continue;
            }
            int y = Math.max(1, anchor.key().y());
            complexity += y * TRANSMITTER_MULTIPLIERS.getOrDefault(instance.typeId(), 1)
                    + TRANSMITTER_BONUSES.getOrDefault(instance.typeId(), 0);
        }
        return complexity;
    }


    private List<SfxAnchorRecord> onlineTransmitters(UUID owner) {
        List<SfxAnchorRecord> result = new ArrayList<>();
        if (owner == null) {
            return result;
        }
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !TRANSMITTER_TYPES.contains(instance.typeId()) || !owner.equals(instance.ownerId())) {
                continue;
            }
            int requiredEnergy = Math.max(1, TRANSMITTER_CONSUMPTION.getOrDefault(instance.typeId(), 1));
            if (electricMachines.consumerStoredEnergy(instance.instanceId()) < requiredEnergy) {
                continue;
            }
            result.add(anchor);
        }
        result.sort(Comparator.comparingInt(anchor -> -transmitterStrength(blockData.findInstance(anchor.instanceId()).orElse(null), anchor)));
        return result;
    }

    private int transmitterStrength(SfxBlockInstanceRecord instance, SfxAnchorRecord anchor) {
        if (instance == null || anchor == null) {
            return 0;
        }
        int y = Math.max(1, anchor.key().y());
        return y * TRANSMITTER_MULTIPLIERS.getOrDefault(instance.typeId(), 1)
                + TRANSMITTER_BONUSES.getOrDefault(instance.typeId(), 0);
    }

    private String worldName(SfxAnchorRecord anchor) {
        if (anchor == null || anchor.key() == null) {
            return "world";
        }
        World world = Bukkit.getWorld(anchor.key().worldId());
        return world == null ? anchor.key().worldId().toString() : world.getName();
    }

    private int countTransmitters(UUID owner) {
        int count = 0;
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance != null && TRANSMITTER_TYPES.contains(instance.typeId()) && owner.equals(instance.ownerId())) {
                count++;
            }
        }
        return count;
    }

    private List<Location> teleporterPylons(Location matrix) {
        List<Location> result = new ArrayList<>();
        for (Location location : pylonLocations(matrix)) {
            SfxBlockInstanceRecord instance = instanceAt(location).orElse(null);
            if (instance != null && instance.typeId().equals("sf:gps_teleporter_pylon")) {
                result.add(location);
            }
        }
        return result;
    }

    private void updateTeleporterDecorations(Location matrix, boolean ready, boolean error) {
        SfxDecorationState state = error ? SfxDecorationState.ERROR : ready ? SfxDecorationState.READY : SfxDecorationState.DEFAULT;
        for (Location location : pylonLocations(matrix)) {
            decorations.setState(location, state);
        }
    }

    private List<Location> pylonLocations(Location matrix) {
        if (matrix == null || matrix.getWorld() == null) {
            return List.of();
        }
        List<Location> locations = new ArrayList<>(8);
        int mx = matrix.getBlockX();
        int my = matrix.getBlockY();
        int mz = matrix.getBlockZ();
        for (int dx = -TELEPORTER_RADIUS; dx <= TELEPORTER_RADIUS; dx++) {
            for (int dz = -TELEPORTER_RADIUS; dz <= TELEPORTER_RADIUS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                locations.add(new Location(matrix.getWorld(), mx + dx, my, mz + dz));
            }
        }
        return locations;
    }

    private Location[] surroundingTeleporterMatrices(Location location) {
        if (location == null || location.getWorld() == null) {
            return new Location[0];
        }
        List<Location> result = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                result.add(new Location(location.getWorld(), location.getBlockX() + dx, location.getBlockY(), location.getBlockZ() + dz));
            }
        }
        return result.toArray(Location[]::new);
    }

    private Optional<SfxBlockInstanceRecord> instanceAt(Location location) {
        SfxAnchorRecord anchor = blockData.findAnchor(location).orElse(null);
        return anchor == null ? Optional.empty() : blockData.findInstance(anchor.instanceId());
    }

    private boolean hasSfxItem(Player player, String itemId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Optional<SfxItemMarker> marker = items.readMarker(stack);
            if (marker.isPresent() && marker.get().itemId().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    private String uniqueWaypointName(UUID owner, String base) {
        Set<String> existing = new HashSet<>();
        for (SfxGpsWaypoint waypoint : dataStore.waypoints(owner)) {
            existing.add(waypoint.name().toLowerCase());
        }
        String candidate = base;
        int index = 1;
        while (existing.contains(candidate.toLowerCase())) {
            candidate = base + " #" + (++index);
        }
        return candidate;
    }

    private String blockCoords(SfxGpsWaypoint waypoint) {
        return (int) Math.floor(waypoint.x()) + ", " + (int) Math.floor(waypoint.y()) + ", " + (int) Math.floor(waypoint.z());
    }

    private ItemStack itemIcon(String itemId, Material fallback) {
        if (itemId != null) {
            try {
                return items.create(itemId);
            } catch (RuntimeException ignored) {
                
            }
        }
        return ItemBuilder.of(fallback == null ? Material.STONE : fallback).build();
    }

    private ItemStack headIcon(String textureHash, Material fallback) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            HeadTextures.apply(meta, textureHash);
            stack.setItemMeta(meta);
        }
        if (stack.getType() == Material.PLAYER_HEAD) {
            return stack;
        }
        return ItemBuilder.of(fallback == null ? Material.PLAYER_HEAD : fallback).build();
    }

    private ItemStack namedItem(ItemStack base, Component name, List<Component> lore) {
        ItemStack stack = base == null ? new ItemStack(Material.STONE) : base.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(Text.noItalic(name));
            }
            if (lore != null && !lore.isEmpty()) {
                List<Component> cleanLore = new ArrayList<>(lore.size());
                for (Component line : lore) {
                    cleanLore.add(Text.noItalic(line));
                }
                meta.lore(cleanLore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String resourceNameText(SfxGeoResourceType type) {
        if (type == null) {
            return localization.text("gps.resources.unknown", "GEO Resource");
        }
        return localization.text("gps.resources." + type.name().toLowerCase(java.util.Locale.ROOT), type.displayName());
    }


    private void send(Player player, String key, String fallback) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key, fallback)));
    }

    private void send(Player player, String key, String fallback, Map<String, ?> placeholders) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key, fallback, placeholders)));
    }

    private Component component(String key, String fallback) {
        return localization.component(key, fallback);
    }

    private Component component(String key, String fallback, Map<String, ?> placeholders) {
        return localization.component(key, fallback, placeholders);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("<", "").replace(">", "");
    }

    private record PendingWaypointInput(Location location) { }

    private record PendingElevatorNameInput(Location location) { }
}

