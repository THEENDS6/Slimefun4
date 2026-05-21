package cc.theends6.sfx.internal.gps;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.decoration.SfxDecorationState;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Slimefun-compatible GPS foundation for SFX.
 *
 * <p>This implementation focuses on registering the gameplay surface: transmitters,
 * waypoints, GEO scanning/mining, oil pumping, teleporters and elevator plates. It is
 * intentionally conservative and uses existing SFX block persistence as the source of
 * truth for placed GPS blocks.</p>
 */
public final class SfxGpsService implements Listener {
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


    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxMenus menus;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxDecorationService decorations;
    private final SfxElectricMachineService electricMachines;
    private final SfxGpsDataStore dataStore;
    private final Set<UUID> activeTeleports = ConcurrentHashMap.newKeySet();

    public SfxGpsService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxMenus menus,
                         SfxLocalization localization, SfxBlockDataService blockData, SfxDecorationService decorations,
                         SfxElectricMachineService electricMachines) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.decorations = Objects.requireNonNull(decorations, "decorations");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.dataStore = new SfxGpsDataStore(plugin);
        this.dataStore.load();
        SfxGpsElectricBridge.bind(this);
    }

    public void shutdown() {
        dataStore.save();
        SfxGpsElectricBridge.unbind(this);
        activeTeleports.clear();
    }

    public boolean supportsType(String typeId) {
        return PLACEABLE_GPS_TYPES.contains(typeId);
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
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        Player player = event.getPlayer();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            Optional<SfxItemMarker> marker = items.readMarker(player.getInventory().getItemInMainHand());
            if (marker.isPresent() && handleGpsItemUse(player, marker.get(), event.getClickedBlock())) {
                event.setCancelled(true);
                return;
            }
        }
        if ((action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        SfxAnchorRecord anchor = blockData.findAnchor(block.getLocation()).orElse(null);
        if (anchor == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null || !supportsType(instance.typeId())) {
            return;
        }
        event.setCancelled(true);
        handleGpsBlockUse(player, block, instance);
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

    private boolean handleGpsItemUse(Player player, SfxItemMarker marker, Block clickedBlock) {
        return switch (marker.itemId()) {
            case "sf:gps_marker_tool" -> {
                createWaypoint(player, player.getLocation(), uniqueWaypointName(player.getUniqueId(), "Waypoint"));
                yield true;
            }
            case "sf:portable_geo_scanner" -> {
                scanChunk(player, player.getLocation());
                yield true;
            }
            case "sf:portable_teleporter" -> {
                openWaypointMenu(player, player.getUniqueId(), null, true);
                yield true;
            }
            default -> false;
        };
    }

    private void handleGpsBlockUse(Player player, Block block, SfxBlockInstanceRecord instance) {
        switch (instance.typeId()) {
            case "sf:gps_control_panel" -> openControlPanel(player);
            case "sf:gps_geo_scanner" -> scanChunk(player, block.getLocation());
            case "sf:gps_teleportation_matrix" -> activateTeleporter(player, block.getLocation(), false);
            case "sf:gps_activation_device_shared", "sf:gps_activation_device_personal" -> activateFromPlate(player, block.getLocation(), instance.typeId());
            case "sf:elevator_plate" -> useElevator(player, block.getLocation(), player.isSneaking());
            default -> send(player, "gps.messages.component-registered", "<gray>This GPS component is registered.</gray>");
        }
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
        builder.button(2, new SfxMenuButton(ItemBuilder.of(Material.COMPASS)
                .name("<aqua>GPS Transmitters")
                .lore("<gray>Online transmitters: <yellow>" + transmitters.size())
                .build(), click -> openTransmitterControlPanel(click.player())));
        builder.button(4, new SfxMenuButton(ItemBuilder.of(Material.MAP)
                .name("<aqua>Network Info")
                .lore("<gray>Status: " + (complexity > 0 ? "<green>ONLINE" : "<red>OFFLINE"),
                        "<gray>Complexity: <yellow>" + complexity,
                        "<gray>Transmitters: <yellow>" + transmitters.size())
                .build(), click -> openTransmitterControlPanel(click.player())));
        builder.button(6, new SfxMenuButton(ItemBuilder.of(Material.REDSTONE_TORCH)
                .name("<aqua>Waypoints")
                .lore("<gray>Stored: <yellow>" + dataStore.waypoints(owner).size(), "<yellow>Click to manage")
                .build(), click -> openControlWaypointPanel(click.player())));

        int index = 0;
        for (SfxAnchorRecord anchor : transmitters.stream().limit(CONTROL_PANEL_CONTENT.length).toList()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            int strength = transmitterStrength(instance, anchor);
            int ping = Math.max(1, 1000 / Math.max(1, strength));
            builder.button(CONTROL_PANEL_CONTENT[index++], new SfxMenuButton(ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                    .name("<green>GPS Transmitter")
                    .lore("<gray>World: <white>" + escape(worldName(anchor)),
                            "<gray>X/Y/Z: <white>" + anchor.key().x() + " / " + anchor.key().y() + " / " + anchor.key().z(),
                            "<gray>Signal Strength: <yellow>" + strength,
                            "<gray>Ping: <yellow>" + ping + " ms")
                    .build(), click -> openTransmitterControlPanel(click.player())));
        }
        if (transmitters.isEmpty()) {
            builder.button(31, new SfxMenuButton(ItemBuilder.of(Material.BARRIER)
                    .name("<red>No online transmitters")
                    .lore("<gray>Place and power GPS transmitters to build the network.")
                    .build(), click -> openTransmitterControlPanel(click.player())));
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
        builder.button(2, new SfxMenuButton(ItemBuilder.of(Material.COMPASS)
                .name("<aqua>GPS Transmitters")
                .lore("<yellow>Click to view transmitters")
                .build(), click -> openTransmitterControlPanel(click.player())));
        builder.button(4, new SfxMenuButton(ItemBuilder.of(Material.MAP)
                .name("<aqua>Network Info")
                .lore("<gray>Status: " + (complexity > 0 ? "<green>ONLINE" : "<red>OFFLINE"),
                        "<gray>Complexity: <yellow>" + complexity,
                        "<gray>Waypoints: <yellow>" + dataStore.waypoints(owner).size())
                .build(), click -> openControlWaypointPanel(click.player())));
        builder.button(6, new SfxMenuButton(ItemBuilder.of(Material.REDSTONE_TORCH)
                .name("<aqua>Waypoints")
                .lore("<gray>Click a waypoint to delete it.")
                .build(), click -> openControlWaypointPanel(click.player())));

        int index = 0;
        for (SfxGpsWaypoint waypoint : waypoints) {
            Material icon = waypoint.toLocation() == null ? Material.BARRIER : Material.ENDER_PEARL;
            builder.button(CONTROL_PANEL_CONTENT[index++], new SfxMenuButton(ItemBuilder.of(icon)
                    .name("<aqua>" + escape(waypoint.name()))
                    .lore("<gray>World: <white>" + escape(waypoint.worldName()),
                            "<gray>X/Y/Z: <white>" + blockCoords(waypoint),
                            "<yellow>Click to delete")
                    .build(), click -> {
                        dataStore.removeWaypoint(click.player().getUniqueId(), waypoint.name());
                        send(click.player(), "gps.messages.waypoint-deleted", "<red>Waypoint deleted: <white>{name}</white>", Map.of("name", escape(waypoint.name())));
                        openControlWaypointPanel(click.player());
                    }));
        }
        if (waypoints.isEmpty()) {
            builder.button(31, new SfxMenuButton(ItemBuilder.of(Material.BARRIER).name("<red>No waypoints").build(), click -> openControlWaypointPanel(click.player())));
        }
        menus.openRoot(player, builder.build());
    }

    private void addControlPanelFrame(SfxMenu.Builder builder) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int slot : CONTROL_PANEL_FRAME) {
            builder.button(slot, new SfxMenuButton(filler, click -> {}));
        }
    }

    private void openWaypointMenu(Player player, UUID owner, Location matrixLocation, boolean portable) {
        List<SfxGpsWaypoint> waypoints = dataStore.waypoints(owner);
        SfxMenu.Builder builder = SfxMenu.builder(component("gps.ui.waypoints.title", "<dark_aqua>GPS Waypoints")).rows(6);
        int slot = 0;
        for (SfxGpsWaypoint waypoint : waypoints.stream().sorted(Comparator.comparing(SfxGpsWaypoint::createdAt).reversed()).limit(45).toList()) {
            Location target = waypoint.toLocation();
            Material icon = target == null ? Material.BARRIER : Material.ENDER_PEARL;
            builder.button(slot++, new SfxMenuButton(ItemBuilder.of(icon)
                    .name("<aqua>" + escape(waypoint.name()))
                    .lore("<gray>World: <white>" + escape(waypoint.worldName()),
                            "<gray>X/Y/Z: <white>" + blockCoords(waypoint),
                            "<yellow>Click to teleport")
                    .build(), click -> startTeleport(click.player(), waypoint, matrixLocation, portable)));
        }
        if (waypoints.isEmpty()) {
            builder.button(22, new SfxMenuButton(ItemBuilder.of(Material.BARRIER).name("<red>No waypoints").build(), click -> {}));
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
        dataStore.markScanned(key);
        Map<SfxGeoResourceType, Integer> resources = dataStore.resources(key);
        SfxMenu.Builder builder = SfxMenu.builder(component("gps.ui.geo-scan.title", "<dark_aqua>GEO Scan")).rows(6);
        int slot = 10;
        for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
            int amount = resources.getOrDefault(type, 0);
            builder.button(slot++, new SfxMenuButton(ItemBuilder.of(amount > 0 ? Material.EMERALD : Material.REDSTONE)
                    .name((amount > 0 ? "<green>" : "<red>") + type.displayName())
                    .lore("<gray>Remaining: <yellow>" + amount, "<gray>Chunk: <white>" + key.chunkX() + ", " + key.chunkZ())
                    .build(), click -> {}));
        }
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
        SfxGeoResourceType selected = oilOnly ? SfxGeoResourceType.OIL : selectBestResource(key);
        if (selected == null || dataStore.resources(key).getOrDefault(selected, 0) <= 0) {
            send(player, "gps.messages.no-geo-resource", "<red>No usable GEO resource remains in this chunk.</red>");
            return;
        }
        if (!dataStore.consume(key, selected, 1)) {
            send(player, "gps.messages.no-geo-resource", "<red>No usable GEO resource remains in this chunk.</red>");
            return;
        }
        ItemStack output = createResourceItem(selected, oilOnly);
        player.getInventory().addItem(output).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        send(player, "gps.messages.extracted", "<green>Extracted <white>{resource}</white>.</green>", Map.of("resource", escape(selected.displayName())));
    }

    private SfxGeoResourceType selectBestResource(SfxGeoChunkKey key) {
        Map<SfxGeoResourceType, Integer> resources = dataStore.resources(key);
        return resources.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && entry.getKey() != SfxGeoResourceType.OIL)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
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
        SfxGeoResourceType selected = oilOnly ? SfxGeoResourceType.OIL : selectBestResource(key);
        if (selected == null || dataStore.resources(key).getOrDefault(selected, 0) <= 0) {
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
        SfxGeoResourceType selected = oilOnly ? SfxGeoResourceType.OIL : selectBestResource(key);
        if (selected == null || !dataStore.consume(key, selected, 1)) {
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

    private void activateFromPlate(Player player, Location plateLocation, String plateType) {
        Location matrix = plateLocation.clone().subtract(0, 1, 0);
        SfxBlockInstanceRecord instance = instanceAt(matrix).orElse(null);
        if (instance == null || !instance.typeId().equals("sf:gps_teleportation_matrix")) {
            send(player, "gps.messages.activation-not-on-matrix", "<red>This activation device is not placed on a GPS Teleporter Matrix.</red>");
            return;
        }
        if (plateType.endsWith("_personal") && instance.ownerId() != null && !instance.ownerId().equals(player.getUniqueId())) {
            send(player, "gps.messages.personal-device-owner", "<red>This personal activation device belongs to another player.</red>");
            return;
        }
        activateTeleporter(player, matrix, true);
    }

    private void activateTeleporter(Player player, Location matrixLocation, boolean fromPlate) {
        SfxBlockInstanceRecord matrix = instanceAt(matrixLocation).orElse(null);
        if (matrix == null || !matrix.typeId().equals("sf:gps_teleportation_matrix")) {
            return;
        }
        if (networkComplexity(matrix.ownerId()) < TELEPORT_REQUIRED_COMPLEXITY) {
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
        openWaypointMenu(player, matrix.ownerId(), matrixLocation, false);
    }

    private void startTeleport(Player player, SfxGpsWaypoint waypoint, Location matrixLocation, boolean portable) {
        Location target = waypoint.toLocation();
        if (target == null || target.getWorld() == null) {
            send(player, "gps.messages.target-world-not-loaded", "<red>The target world is not loaded.</red>");
            return;
        }
        if (activeTeleports.contains(player.getUniqueId())) {
            return;
        }
        menus.close(player);
        activeTeleports.add(player.getUniqueId());
        Location origin = player.getLocation().clone();
        if (matrixLocation != null) {
            updateTeleporterDecorations(matrixLocation, false, false);
            updateTeleporterDecorations(matrixLocation, true, false);
        }
        send(player, "gps.messages.teleporting", "<aqua>Teleporting to <white>{waypoint}</white> in 3 seconds...</aqua>", Map.of("waypoint", escape(waypoint.name())));
        runtime.executeForPlayerLater(player, 60L, () -> {
            activeTeleports.remove(player.getUniqueId());
            if (!portable && matrixLocation != null && player.getLocation().distanceSquared(origin) > 4.0D) {
                updateTeleporterDecorations(matrixLocation, true, true);
                send(player, "gps.messages.teleport-cancelled-moved", "<red>Teleport cancelled because you moved.</red>");
                return;
            }
            Location safe = safeDestination(target);
            player.teleportAsync(safe);
            if (matrixLocation != null) {
                updateTeleporterDecorations(matrixLocation, true, false);
            }
        });
    }

    private Location safeDestination(Location target) {
        World world = target.getWorld();
        if (world == null) {
            return target;
        }
        int x = target.getBlockX();
        int z = target.getBlockZ();
        int y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, target.getBlockY()));
        for (int dy = 0; dy < 12; dy++) {
            Location candidate = new Location(world, x + 0.5, y + dy, z + 0.5, target.getYaw(), target.getPitch());
            if (candidate.getBlock().getType().isAir() && candidate.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                return candidate;
            }
        }
        return target;
    }

    private void useElevator(Player player, Location plateLocation, boolean down) {
        Location target = findElevatorTarget(plateLocation, down);
        if (target == null) {
            send(player, "gps.messages.no-elevator-target", "<red>No elevator plate found {direction}.</red>", Map.of("direction", down ? "below" : "above"));
            return;
        }
        player.teleportAsync(target.clone().add(0.5, 1.0, 0.5).setDirection(player.getLocation().getDirection()));
    }

    private Location findElevatorTarget(Location source, boolean down) {
        if (source == null || source.getWorld() == null) {
            return null;
        }
        World world = source.getWorld();
        int step = down ? -1 : 1;
        for (int y = source.getBlockY() + step; y > world.getMinHeight() && y < world.getMaxHeight(); y += step) {
            Location check = new Location(world, source.getBlockX(), y, source.getBlockZ());
            SfxBlockInstanceRecord instance = instanceAt(check).orElse(null);
            if (instance != null && instance.typeId().equals("sf:elevator_plate")) {
                return check;
            }
        }
        return null;
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
}
