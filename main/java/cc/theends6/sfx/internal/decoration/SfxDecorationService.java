package cc.theends6.sfx.internal.decoration;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runtime service for SFX decorative blocks.
 *
 * <p>This service intentionally keeps decoration behavior outside GPS/structure logic:
 * GPS pylon state changes are pushed into this service, and purely animated decorations
 * are processed in a bounded global sweep.</p>
 */
public final class SfxDecorationService implements Listener {
    private static final List<Material> WOOL_RAINBOW = List.of(
            Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
            Material.LIGHT_BLUE_WOOL, Material.BLUE_WOOL, Material.PURPLE_WOOL, Material.MAGENTA_WOOL
    );
    private static final List<Material> GLASS_RAINBOW = List.of(
            Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS
    );
    private static final List<Material> PANE_RAINBOW = List.of(
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE
    );
    private static final List<Material> TERRACOTTA_RAINBOW = List.of(
            Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA,
            Material.LIGHT_BLUE_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.PURPLE_TERRACOTTA, Material.MAGENTA_TERRACOTTA
    );
    private static final List<Material> CONCRETE_RAINBOW = List.of(
            Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE
    );
    private static final List<Material> GLAZED_RAINBOW = List.of(
            Material.RED_GLAZED_TERRACOTTA, Material.ORANGE_GLAZED_TERRACOTTA, Material.YELLOW_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA,
            Material.LIGHT_BLUE_GLAZED_TERRACOTTA, Material.BLUE_GLAZED_TERRACOTTA, Material.PURPLE_GLAZED_TERRACOTTA, Material.MAGENTA_GLAZED_TERRACOTTA
    );
    private static final List<Material> XMAS_WOOL = List.of(Material.RED_WOOL, Material.GREEN_WOOL, Material.WHITE_WOOL);
    private static final List<Material> XMAS_GLASS = List.of(Material.RED_STAINED_GLASS, Material.GREEN_STAINED_GLASS, Material.WHITE_STAINED_GLASS);
    private static final List<Material> XMAS_PANE = List.of(Material.RED_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE);
    private static final List<Material> XMAS_TERRACOTTA = List.of(Material.RED_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.WHITE_TERRACOTTA);
    private static final List<Material> XMAS_CONCRETE = List.of(Material.RED_CONCRETE, Material.GREEN_CONCRETE, Material.WHITE_CONCRETE);
    private static final List<Material> XMAS_GLAZED = List.of(Material.RED_GLAZED_TERRACOTTA, Material.GREEN_GLAZED_TERRACOTTA, Material.WHITE_GLAZED_TERRACOTTA);
    private static final List<Material> VALENTINE_WOOL = List.of(Material.PINK_WOOL, Material.MAGENTA_WOOL, Material.RED_WOOL, Material.WHITE_WOOL);
    private static final List<Material> VALENTINE_GLASS = List.of(Material.PINK_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.WHITE_STAINED_GLASS);
    private static final List<Material> VALENTINE_PANE = List.of(Material.PINK_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE);
    private static final List<Material> VALENTINE_TERRACOTTA = List.of(Material.PINK_TERRACOTTA, Material.MAGENTA_TERRACOTTA, Material.RED_TERRACOTTA, Material.WHITE_TERRACOTTA);
    private static final List<Material> VALENTINE_CONCRETE = List.of(Material.PINK_CONCRETE, Material.MAGENTA_CONCRETE, Material.RED_CONCRETE, Material.WHITE_CONCRETE);
    private static final List<Material> VALENTINE_GLAZED = List.of(Material.PINK_GLAZED_TERRACOTTA, Material.MAGENTA_GLAZED_TERRACOTTA, Material.RED_GLAZED_TERRACOTTA, Material.WHITE_GLAZED_TERRACOTTA);
    private static final List<Material> HALLOWEEN_WOOL = List.of(Material.ORANGE_WOOL, Material.BLACK_WOOL, Material.PURPLE_WOOL);
    private static final List<Material> HALLOWEEN_GLASS = List.of(Material.ORANGE_STAINED_GLASS, Material.BLACK_STAINED_GLASS, Material.PURPLE_STAINED_GLASS);
    private static final List<Material> HALLOWEEN_PANE = List.of(Material.ORANGE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE);
    private static final List<Material> HALLOWEEN_TERRACOTTA = List.of(Material.ORANGE_TERRACOTTA, Material.BLACK_TERRACOTTA, Material.PURPLE_TERRACOTTA);
    private static final List<Material> HALLOWEEN_CONCRETE = List.of(Material.ORANGE_CONCRETE, Material.BLACK_CONCRETE, Material.PURPLE_CONCRETE);
    private static final List<Material> HALLOWEEN_GLAZED = List.of(Material.ORANGE_GLAZED_TERRACOTTA, Material.BLACK_GLAZED_TERRACOTTA, Material.PURPLE_GLAZED_TERRACOTTA);

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxBlockDataService blockData;
    private final Map<String, SfxDecorationDefinition> definitions = new ConcurrentHashMap<>();
    private final Set<UUID> animatedInstances = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SfxDecorationState> states = new ConcurrentHashMap<>();
    private long animationPhase;
    private boolean shutdown;

    public SfxDecorationService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxBlockDataService blockData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        registerDefaults();
    }

    public void start() {
        rebuildIndex();
        scheduleAnimationTick();
    }

    public void shutdown() {
        shutdown = true;
        animatedInstances.clear();
        states.clear();
    }

    public boolean supportsType(String typeId) {
        return typeId != null && definitions.containsKey(typeId);
    }

    public boolean isStructuralDecoration(String typeId) {
        SfxDecorationDefinition definition = definitions.get(typeId);
        return definition != null && definition.structural();
    }

    public Optional<SfxDecorationDefinition> definition(String typeId) {
        return Optional.ofNullable(definitions.get(typeId));
    }

    public void handlePlaced(UUID instanceId, String typeId) {
        if (instanceId == null || typeId == null) {
            return;
        }
        SfxDecorationDefinition definition = definitions.get(typeId);
        if (definition == null) {
            return;
        }
        if (definition.animated()) {
            animatedInstances.add(instanceId);
        }
        states.putIfAbsent(instanceId, SfxDecorationState.DEFAULT);
    }

    public void setState(Location location, SfxDecorationState state) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        SfxAnchorRecord anchor = blockData.findAnchorFast(location).orElse(null);
        if (anchor == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null || !supportsType(instance.typeId())) {
            return;
        }
        states.put(instance.instanceId(), state == null ? SfxDecorationState.DEFAULT : state);
        applyState(location, instance, 0L);
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (instanceId != null) {
            animatedInstances.remove(instanceId);
            states.remove(instanceId);
        }
        if (block != null && typeId != null) {
            SfxBlockDrops.dropPluginBlock(block, items, typeId);
            blockData.unregisterAt(block.getLocation());
        }
    }

    public void rebuildIndex() {
        animatedInstances.clear();
        states.clear();
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            handlePlaced(instance.instanceId(), instance.typeId());
        }
    }

    private void scheduleAnimationTick() {
        if (shutdown) {
            return;
        }
        runtime.executeGlobalLater(10L, () -> {
            if (shutdown) {
                return;
            }
            animateLoadedDecorations();
            scheduleAnimationTick();
        });
    }

    private void animateLoadedDecorations() {
        animationPhase++;
        List<UUID> snapshot = new ArrayList<>(animatedInstances);
        for (UUID instanceId : snapshot) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            if (instance == null) {
                animatedInstances.remove(instanceId);
                states.remove(instanceId);
                continue;
            }
            if (!supportsType(instance.typeId())) {
                animatedInstances.remove(instanceId);
                continue;
            }
            Location location = locationOf(instance);
            if (location == null || location.getWorld() == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            runtime.executeAt(location, () -> applyState(location, instance, animationPhase));
        }
    }

    private Location locationOf(SfxBlockInstanceRecord instance) {
        if (instance == null || instance.anchorKey() == null) {
            return null;
        }
        World world = Bukkit.getWorld(instance.anchorKey().worldId());
        return world == null ? null : new Location(world, instance.anchorKey().x(), instance.anchorKey().y(), instance.anchorKey().z());
    }

    private void applyState(Location location, SfxBlockInstanceRecord instance, long phase) {
        if (instance == null || location == null || location.getWorld() == null) {
            return;
        }
        SfxAnchorRecord liveAnchor = blockData.findAnchorFast(location).orElse(null);
        if (liveAnchor == null || !instance.instanceId().equals(liveAnchor.instanceId())) {
            animatedInstances.remove(instance.instanceId());
            states.remove(instance.instanceId());
            return;
        }
        SfxBlockInstanceRecord liveInstance = blockData.findInstance(instance.instanceId()).orElse(null);
        if (liveInstance == null || !Objects.equals(liveInstance.typeId(), instance.typeId())) {
            animatedInstances.remove(instance.instanceId());
            states.remove(instance.instanceId());
            return;
        }
        SfxDecorationDefinition definition = definitions.get(liveInstance.typeId());
        if (definition == null) {
            animatedInstances.remove(instance.instanceId());
            states.remove(instance.instanceId());
            return;
        }
        Block block = location.getBlock();
        if (block.getType().isAir()) {
            animatedInstances.remove(instance.instanceId());
            states.remove(instance.instanceId());
            return;
        }
        Material next = definition.materialFor(states.getOrDefault(instance.instanceId(), SfxDecorationState.DEFAULT), phase);
        if (block.getType() != next) {
            block.setType(next, false);
        }
    }

    private void registerDefaults() {
        register(new SfxDecorationDefinition("sf:gps_teleporter_pylon", true, true,
                List.of(Material.CYAN_STAINED_GLASS, Material.PURPLE_STAINED_GLASS),
                Material.CYAN_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.RED_STAINED_GLASS));
        register(new SfxDecorationDefinition("sf:hardened_glass", false, false, List.of(Material.LIGHT_GRAY_STAINED_GLASS), null, null, null));
        register(new SfxDecorationDefinition("sf:wither_proof_obsidian", false, false, List.of(Material.OBSIDIAN), null, null, null));
        register(new SfxDecorationDefinition("sf:wither_proof_glass", false, false, List.of(Material.PURPLE_STAINED_GLASS), null, null, null));
        registerRainbow("sf:rainbow_wool", WOOL_RAINBOW);
        registerRainbow("sf:rainbow_glass", GLASS_RAINBOW);
        registerRainbow("sf:rainbow_clay", TERRACOTTA_RAINBOW);
        registerRainbow("sf:rainbow_glass_pane", PANE_RAINBOW);
        registerRainbow("sf:rainbow_concrete", CONCRETE_RAINBOW);
        registerRainbow("sf:rainbow_glazed_terracotta", GLAZED_RAINBOW);
        registerRainbow("sf:rainbow_wool_xmas", XMAS_WOOL);
        registerRainbow("sf:rainbow_glass_xmas", XMAS_GLASS);
        registerRainbow("sf:rainbow_clay_xmas", XMAS_TERRACOTTA);
        registerRainbow("sf:rainbow_glass_pane_xmas", XMAS_PANE);
        registerRainbow("sf:rainbow_concrete_xmas", XMAS_CONCRETE);
        registerRainbow("sf:rainbow_glazed_terracotta_xmas", XMAS_GLAZED);
        registerRainbow("sf:rainbow_wool_valentine", VALENTINE_WOOL);
        registerRainbow("sf:rainbow_glass_valentine", VALENTINE_GLASS);
        registerRainbow("sf:rainbow_clay_valentine", VALENTINE_TERRACOTTA);
        registerRainbow("sf:rainbow_glass_pane_valentine", VALENTINE_PANE);
        registerRainbow("sf:rainbow_concrete_valentine", VALENTINE_CONCRETE);
        registerRainbow("sf:rainbow_glazed_terracotta_valentine", VALENTINE_GLAZED);
        registerRainbow("sf:rainbow_wool_halloween", HALLOWEEN_WOOL);
        registerRainbow("sf:rainbow_glass_halloween", HALLOWEEN_GLASS);
        registerRainbow("sf:rainbow_clay_halloween", HALLOWEEN_TERRACOTTA);
        registerRainbow("sf:rainbow_glass_pane_halloween", HALLOWEEN_PANE);
        registerRainbow("sf:rainbow_concrete_halloween", HALLOWEEN_CONCRETE);
        registerRainbow("sf:rainbow_glazed_terracotta_halloween", HALLOWEEN_GLAZED);
    }

    private void registerRainbow(String id, List<Material> materials) {
        register(new SfxDecorationDefinition(id, true, false, materials, null, null, null));
    }

    private void register(SfxDecorationDefinition definition) {
        definitions.put(definition.itemId(), definition);
    }
}
