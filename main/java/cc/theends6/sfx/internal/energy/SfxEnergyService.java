package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final SfxEnergyDisplayService displayService;
    private final Map<String, SfxEnergyComponentDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, SfxEnergyNodeState> nodeStates = new ConcurrentHashMap<>();
    private final Map<UUID, GridStatus> nodeGridStatuses = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyNodes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeNodes = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GeneratorSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, GeneratorSession> sessionsByInstance = new ConcurrentHashMap<>();
    private volatile FuelBurnTimeBridge fuelBurnTimeBridge;
    private volatile boolean running;

    public SfxEnergyService(
            JavaPlugin plugin,
            SfxRuntime runtime,
            SfxItems items,
            SfxLocalization localization,
            SfxBlockDataService blockData,
            SfxElectricMachineService electricMachines
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.displayService = new SfxEnergyDisplayService(plugin, localization);
        initializeDefinitions();
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
            if (!definitions.containsKey(marker.itemId())) {
                return;
            }
            UUID instanceId = blockData.findAnchor(event.getBlockPlaced().getLocation())
                    .map(SfxAnchorRecord::instanceId)
                    .orElseGet(() -> blockData.registerSingleBlock(
                            marker.itemId(),
                            event.getBlockPlaced().getLocation(),
                            event.getBlockPlaced().getType(),
                            event.getPlayer().getUniqueId()));
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
            runtime.executeGlobal(() -> openGenerator(event.getPlayer(), instance, definition));
            return;
        }
        SfxEnergyNodeState state = currentState(instance.instanceId(), instance);
        if (definition.componentType() == SfxEnergyComponentType.CAPACITOR) {
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text(
                    "energy.messages.capacitor-status",
                    "<yellow>Stored {stored}/{capacity} J</yellow>",
                    Map.of("stored", state.storedEnergy(), "capacity", definition.capacity()))));
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
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof GeneratorHolder holder)) {
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
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof GeneratorHolder holder)) {
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
        if (!(event.getInventory().getHolder() instanceof GeneratorHolder holder)) {
            return;
        }
        GeneratorSession session = sessionsByInstance.remove(holder.instanceId());
        if (session == null) {
            return;
        }
        sessionsByViewer.remove(session.viewerId());
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyNodeState state = currentState(holder.instanceId(), instance);
        syncInventoryToState(session.inventory(), state);
        dirtyNodes.add(holder.instanceId());
        activeNodes.add(holder.instanceId());
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (block == null || instanceId == null || typeId == null || !definitions.containsKey(typeId)) {
            return;
        }
        GeneratorSession session = sessionsByInstance.remove(instanceId);
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
            displayService.remove(instance.anchorKey());
        }
        blockData.unregisterAt(block.getLocation());
    }

    public void shutdown() {
        running = false;
        for (GeneratorSession session : List.copyOf(sessionsByViewer.values())) {
            syncSessionState(session);
            Player player = plugin.getServer().getPlayer(session.viewerId());
            if (player != null) {
                runtime.executeForPlayer(player, player::closeInventory);
            }
        }
        flushDirty();
        displayService.shutdown();
        sessionsByViewer.clear();
        sessionsByInstance.clear();
        nodeStates.clear();
        nodeGridStatuses.clear();
        dirtyNodes.clear();
        activeNodes.clear();
    }

    private void initializeDefinitions() {
        boolean useSfxBalance = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true);
        int tier2BurnRate = useSfxBalance ? 15 : 10;
        int coalTicksMultiplier = useSfxBalance ? 2 : 1;
        int lavaSecondsMultiplier = useSfxBalance ? 2 : 1;
        int bioSecondsMultiplier = useSfxBalance ? 4 : 1;
        int combustionOilSeconds = useSfxBalance ? 40 : 30;
        int combustionFuelSeconds = useSfxBalance ? 120 : 90;
        int combustionCapacity = useSfxBalance ? 20480 : 5120;
        int combustionEnergy = useSfxBalance ? 64 : 12;

        define(new SfxEnergyComponentDefinition("sf:energy_regulator", SfxEnergyComponentType.REGULATOR, 0, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(new SfxEnergyComponentDefinition("sf:energy_connector", SfxEnergyComponentType.CONNECTOR, 0, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(new SfxEnergyComponentDefinition("sf:small_capacitor", SfxEnergyComponentType.CAPACITOR, 2560, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(new SfxEnergyComponentDefinition("sf:medium_capacitor", SfxEnergyComponentType.CAPACITOR, 10240, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(new SfxEnergyComponentDefinition("sf:big_capacitor", SfxEnergyComponentType.CAPACITOR, 20480, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(new SfxEnergyComponentDefinition("sf:large_capacitor", SfxEnergyComponentType.CAPACITOR, 163840, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(new SfxEnergyComponentDefinition("sf:carbonado_edged_capacitor", SfxEnergyComponentType.CAPACITOR, 1310720, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(new SfxEnergyComponentDefinition("sf:energized_capacitor", SfxEnergyComponentType.CAPACITOR, 10485760, 0, 0, 10, false, Material.REDSTONE, List.of()));

        define(new SfxEnergyComponentDefinition("sf:solar_generator", SfxEnergyComponentType.GENERATOR, 0, 2, 0, 10, false, Material.DAYLIGHT_DETECTOR, List.of()));
        define(new SfxEnergyComponentDefinition("sf:solar_generator_2", SfxEnergyComponentType.GENERATOR, 0, 8, 0, 10, false, Material.DAYLIGHT_DETECTOR, List.of()));
        define(new SfxEnergyComponentDefinition("sf:solar_generator_3", SfxEnergyComponentType.GENERATOR, 0, 32, 0, 10, false, Material.DAYLIGHT_DETECTOR, List.of()));
        define(new SfxEnergyComponentDefinition("sf:solar_generator_4", SfxEnergyComponentType.GENERATOR, 0, 128, 64, 10, false, Material.DAYLIGHT_DETECTOR, List.of()));

        define(new SfxEnergyComponentDefinition("sf:coal_generator", SfxEnergyComponentType.GENERATOR, 1280, 8, 0, 10, true, Material.FLINT_AND_STEEL, List.of()));
        define(new SfxEnergyComponentDefinition("sf:coal_generator_2", SfxEnergyComponentType.GENERATOR, 5120, 15, 0, tier2BurnRate, true, Material.FLINT_AND_STEEL, List.of()));

        define(new SfxEnergyComponentDefinition(
                "sf:lava_generator",
                SfxEnergyComponentType.GENERATOR,
                10240,
                10,
                0,
                10,
                false,
                Material.FLINT_AND_STEEL,
                List.of(new SfxEnergyComponentDefinition.FuelRule("lava", SfxElectricStack.vanilla(Material.LAVA_BUCKET, 1), SfxElectricStack.vanilla(Material.BUCKET, 1), 40 * lavaSecondsMultiplier))));
        define(new SfxEnergyComponentDefinition(
                "sf:lava_generator_2",
                SfxEnergyComponentType.GENERATOR,
                20480,
                20,
                0,
                tier2BurnRate,
                false,
                Material.FLINT_AND_STEEL,
                List.of(new SfxEnergyComponentDefinition.FuelRule("lava", SfxElectricStack.vanilla(Material.LAVA_BUCKET, 1), SfxElectricStack.vanilla(Material.BUCKET, 1), 40 * lavaSecondsMultiplier))));

        define(new SfxEnergyComponentDefinition(
                "sf:bio_reactor",
                SfxEnergyComponentType.GENERATOR,
                2560,
                4,
                0,
                10,
                false,
                Material.GOLDEN_HOE,
                bioFuelRules(bioSecondsMultiplier)));

        define(new SfxEnergyComponentDefinition(
                "sf:combustion_reactor",
                SfxEnergyComponentType.GENERATOR,
                combustionCapacity,
                combustionEnergy,
                0,
                10,
                false,
                Material.FLINT_AND_STEEL,
                List.of(
                        new SfxEnergyComponentDefinition.FuelRule("oil", SfxElectricStack.sfx("sf:bucket_of_oil", 1), SfxElectricStack.vanilla(Material.BUCKET, 1), combustionOilSeconds),
                        new SfxEnergyComponentDefinition.FuelRule("fuel", SfxElectricStack.sfx("sf:bucket_of_fuel", 1), SfxElectricStack.vanilla(Material.BUCKET, 1), combustionFuelSeconds))));

        define(new SfxEnergyComponentDefinition(
                "sf:magnesium_generator",
                SfxEnergyComponentType.GENERATOR,
                2560,
                18,
                0,
                10,
                false,
                Material.FLINT_AND_STEEL,
                List.of(new SfxEnergyComponentDefinition.FuelRule("magnesium", SfxElectricStack.sfx("sf:magnesium_salt", 1), null, 20))));

    }

    private void define(SfxEnergyComponentDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    private List<SfxEnergyComponentDefinition.FuelRule> bioFuelRules(int secondsMultiplier) {
        List<SfxEnergyComponentDefinition.FuelRule> fuels = new ArrayList<>();
        fuel(fuels, "rotten_flesh", Material.ROTTEN_FLESH, 2 * secondsMultiplier);
        fuel(fuels, "spider_eye", Material.SPIDER_EYE, 2 * secondsMultiplier);
        fuel(fuels, "bone", Material.BONE, 2 * secondsMultiplier);
        fuel(fuels, "string", Material.STRING, 2 * secondsMultiplier);
        fuel(fuels, "apple", Material.APPLE, 3 * secondsMultiplier);
        fuel(fuels, "melon_slice", Material.MELON_SLICE, 3 * secondsMultiplier);
        fuel(fuels, "melon", Material.MELON, 27 * secondsMultiplier);
        fuel(fuels, "pumpkin", Material.PUMPKIN, 3 * secondsMultiplier);
        fuel(fuels, "pumpkin_seeds", Material.PUMPKIN_SEEDS, 3 * secondsMultiplier);
        fuel(fuels, "melon_seeds", Material.MELON_SEEDS, 3 * secondsMultiplier);
        fuel(fuels, "wheat", Material.WHEAT, 3 * secondsMultiplier);
        fuel(fuels, "wheat_seeds", Material.WHEAT_SEEDS, 3 * secondsMultiplier);
        fuel(fuels, "carrot", Material.CARROT, 3 * secondsMultiplier);
        fuel(fuels, "potato", Material.POTATO, 3 * secondsMultiplier);
        fuel(fuels, "sugar_cane", Material.SUGAR_CANE, 3 * secondsMultiplier);
        fuel(fuels, "nether_wart", Material.NETHER_WART, 3 * secondsMultiplier);
        fuel(fuels, "red_mushroom", Material.RED_MUSHROOM, 2 * secondsMultiplier);
        fuel(fuels, "brown_mushroom", Material.BROWN_MUSHROOM, 2 * secondsMultiplier);
        fuel(fuels, "vine", Material.VINE, 2 * secondsMultiplier);
        fuel(fuels, "cactus", Material.CACTUS, 2 * secondsMultiplier);
        fuel(fuels, "lily_pad", Material.LILY_PAD, 2 * secondsMultiplier);
        fuel(fuels, "chorus_fruit", Material.CHORUS_FRUIT, 8 * secondsMultiplier);
        fuel(fuels, "kelp", Material.KELP, 1 * secondsMultiplier);
        fuel(fuels, "dried_kelp", Material.DRIED_KELP, 2 * secondsMultiplier);
        fuel(fuels, "dried_kelp_block", Material.DRIED_KELP_BLOCK, 20 * secondsMultiplier);
        fuel(fuels, "seagrass", Material.SEAGRASS, 1 * secondsMultiplier);
        fuel(fuels, "sea_pickle", Material.SEA_PICKLE, 2 * secondsMultiplier);
        fuel(fuels, "bamboo", Material.BAMBOO, 1 * secondsMultiplier);
        fuel(fuels, "sweet_berries", Material.SWEET_BERRIES, 2 * secondsMultiplier);
        fuel(fuels, "cocoa_beans", Material.COCOA_BEANS, 2 * secondsMultiplier);
        fuel(fuels, "beetroot", Material.BEETROOT, 3 * secondsMultiplier);
        fuel(fuels, "beetroot_seeds", Material.BEETROOT_SEEDS, 3 * secondsMultiplier);
        fuel(fuels, "honeycomb", Material.HONEYCOMB, 4 * secondsMultiplier);
        fuel(fuels, "honeycomb_block", Material.HONEYCOMB_BLOCK, 40 * secondsMultiplier);
        fuel(fuels, "shroomlight", Material.SHROOMLIGHT, 4 * secondsMultiplier);
        fuel(fuels, "crimson_fungus", Material.CRIMSON_FUNGUS, 2 * secondsMultiplier);
        fuel(fuels, "warped_fungus", Material.WARPED_FUNGUS, 2 * secondsMultiplier);
        fuels.add(new SfxEnergyComponentDefinition.FuelRule("strange_nether_goo", SfxElectricStack.sfx("sf:strange_nether_goo", 1), null, 16 * secondsMultiplier));
        optionalFuel(fuels, "glow_berries", Material.GLOW_BERRIES, 2 * secondsMultiplier);
        optionalFuel(fuels, "small_dripleaf", Material.SMALL_DRIPLEAF, 3 * secondsMultiplier);
        optionalFuel(fuels, "big_dripleaf", Material.BIG_DRIPLEAF, 3 * secondsMultiplier);
        optionalFuel(fuels, "glow_lichen", Material.GLOW_LICHEN, 2 * secondsMultiplier);
        optionalFuel(fuels, "spore_blossom", Material.SPORE_BLOSSOM, 20 * secondsMultiplier);
        tagFuels(fuels, "small_flower", Tag.SMALL_FLOWERS, 1 * secondsMultiplier);
        tagFuels(fuels, "leaf", Tag.LEAVES, 1 * secondsMultiplier);
        tagFuels(fuels, "sapling", Tag.SAPLINGS, 1 * secondsMultiplier);
        tagFuels(fuels, "coral", Tag.CORALS, 2 * secondsMultiplier);
        tagFuels(fuels, "coral_block", Tag.CORAL_BLOCKS, 2 * secondsMultiplier);
        return fuels;
    }

    private void fuel(List<SfxEnergyComponentDefinition.FuelRule> fuels, String key, Material material, int seconds) {
        fuels.add(new SfxEnergyComponentDefinition.FuelRule(key, SfxElectricStack.vanilla(material, 1), null, seconds));
    }

    private void optionalFuel(List<SfxEnergyComponentDefinition.FuelRule> fuels, String key, Material material, int seconds) {
        if (material != null) {
            fuel(fuels, key, material, seconds);
        }
    }

    private void tagFuels(List<SfxEnergyComponentDefinition.FuelRule> fuels, String prefix, Tag<Material> tag, int seconds) {
        for (Material material : tag.getValues()) {
            fuels.add(new SfxEnergyComponentDefinition.FuelRule(prefix + ":" + material.key(), SfxElectricStack.vanilla(material, 1), null, seconds));
        }
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
            nodeStates.put(instance.instanceId(), SfxEnergyNodeState.decode(instance.stateBlob()));
            activeNodes.add(instance.instanceId());
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
        syncOpenGeneratorSessionsToState();
        Map<UUID, GridResult> results = new LinkedHashMap<>();
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
            GridResult result = buildGrid(instance.instanceId(), anchor.key());
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

        for (GridResult result : results.values()) {
            GridStatus status = result.status();
            if (status == GridStatus.ONLINE && result.members().stream().anyMatch(sharedMembers::contains)) {
                status = GridStatus.SHARED_NODE_CONFLICT;
            }
            for (UUID memberId : result.members()) {
                nodeGridStatuses.put(memberId, status);
            }
            if (status == GridStatus.ONLINE) {
                processGrid(result);
            } else {
                displayStatus(result.regulatorKey(), status, 0, 0, 0, 0, 0);
            }
        }
    }

    private GridResult buildGrid(UUID regulatorId, SfxBlockAnchorKey regulatorKey) {
        Set<UUID> members = new LinkedHashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(regulatorId);
        members.add(regulatorId);
        boolean multipleRegulators = false;

        while (!queue.isEmpty()) {
            UUID currentId = queue.removeFirst();
            SfxBlockInstanceRecord current = blockData.findInstance(currentId).orElse(null);
            if (current == null) {
                continue;
            }
            if (currentId.equals(regulatorId)) {
                // keep going
            } else {
                SfxEnergyComponentDefinition currentDefinition = definitions.get(current.typeId());
                if (currentDefinition != null && !currentDefinition.expandsNetwork()) {
                    continue;
                }
            }

            for (SfxBlockInstanceRecord neighbour : registeredEnergyNeighbours(current.anchorKey())) {
                if (!members.add(neighbour.instanceId())) {
                    continue;
                }
                SfxEnergyComponentDefinition neighbourDefinition = definitions.get(neighbour.typeId());
                if (neighbourDefinition != null && neighbourDefinition.componentType() == SfxEnergyComponentType.REGULATOR && !neighbour.instanceId().equals(regulatorId)) {
                    multipleRegulators = true;
                }
                queue.addLast(neighbour.instanceId());
            }
        }

        if (members.size() <= 1) {
            return new GridResult(regulatorId, regulatorKey, members, GridStatus.NO_NETWORK);
        }
        return new GridResult(regulatorId, regulatorKey, members, multipleRegulators ? GridStatus.MULTIPLE_REGULATORS : GridStatus.ONLINE);
    }

    private List<SfxBlockInstanceRecord> registeredEnergyNeighbours(SfxBlockAnchorKey origin) {
        List<SfxBlockInstanceRecord> neighbours = new ArrayList<>();
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            if (!anchor.key().worldId().equals(origin.worldId()) || anchor.key().equals(origin)) {
                continue;
            }
            if (!isReachable(origin, anchor.key())) {
                continue;
            }
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            if (definitions.containsKey(instance.typeId()) || electricMachines.supportsType(instance.typeId())) {
                neighbours.add(instance);
            }
        }
        return neighbours;
    }

    private boolean isReachable(SfxBlockAnchorKey first, SfxBlockAnchorKey second) {
        int dx = Math.abs(first.x() - second.x());
        int dy = Math.abs(first.y() - second.y());
        int dz = Math.abs(first.z() - second.z());
        int changedAxes = (dx > 0 ? 1 : 0) + (dy > 0 ? 1 : 0) + (dz > 0 ? 1 : 0);
        if (changedAxes != 1) {
            return false;
        }
        return dx + dy + dz <= RANGE;
    }

    private void processGrid(GridResult result) {
        int available = 0;
        int demand = 0;
        int supply = 0;
        int consumption = 0;
        List<NodeRef> capacitorRefs = new ArrayList<>();
        List<NodeRef> generatorRefs = new ArrayList<>();
        List<SfxBlockInstanceRecord> consumers = new ArrayList<>();

        for (UUID memberId : result.members()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(memberId).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
            if (definition != null) {
                SfxEnergyNodeState state = currentState(memberId, instance);
                switch (definition.componentType()) {
                    case CAPACITOR -> capacitorRefs.add(new NodeRef(instance, definition, state));
                    case GENERATOR -> generatorRefs.add(new NodeRef(instance, definition, state));
                    case REGULATOR, CONNECTOR -> {
                    }
                }
            } else if (electricMachines.supportsType(instance.typeId())) {
                consumers.add(instance);
            }
        }

        int initialStored = totalStoredEnergy(capacitorRefs, generatorRefs, consumers);

        for (NodeRef capacitor : capacitorRefs) {
            if (capacitor.state().storedEnergy() > 0) {
                available += capacitor.state().storedEnergy();
                capacitor.state().storedEnergy(0);
                dirtyNodes.add(capacitor.instance().instanceId());
            }
        }

        for (NodeRef generator : generatorRefs) {
            if (generator.state().storedEnergy() > 0) {
                available += generator.state().storedEnergy();
                generator.state().storedEnergy(0);
                dirtyNodes.add(generator.instance().instanceId());
            }
            int produced = generate(generator.instance(), generator.definition(), generator.state());
            available += produced;
            supply += produced;
        }

        for (SfxBlockInstanceRecord consumer : consumers) {
            int capacity = electricMachines.consumerCapacity(consumer.typeId());
            int stored = electricMachines.consumerStoredEnergy(consumer.instanceId());
            demand += Math.max(0, capacity - stored);
        }

        int supplied = 0;
        for (SfxBlockInstanceRecord consumer : consumers) {
            if (available <= 0) {
                break;
            }
            int accepted = electricMachines.chargeConsumer(consumer.instanceId(), available);
            if (accepted > 0) {
                available -= accepted;
                supplied += accepted;
                consumption += accepted;
            }
        }

        for (NodeRef capacitor : capacitorRefs) {
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

        for (NodeRef generator : generatorRefs) {
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

        int totalStored = totalStoredEnergy(capacitorRefs, generatorRefs, consumers);
        int totalCapacity = totalCapacity(capacitorRefs, generatorRefs, consumers);
        int net = supply - consumption;
        displayStatus(result.regulatorKey(), GridStatus.ONLINE, supply, consumption, net, totalStored, totalCapacity);
        refreshOpenGeneratorSessions();
    }

    private int totalStoredEnergy(List<NodeRef> capacitorRefs, List<NodeRef> generatorRefs, List<SfxBlockInstanceRecord> consumers) {
        int total = 0;
        for (NodeRef capacitor : capacitorRefs) {
            total += capacitor.state().storedEnergy();
        }
        for (NodeRef generator : generatorRefs) {
            total += generator.state().storedEnergy();
        }
        for (SfxBlockInstanceRecord consumer : consumers) {
            total += electricMachines.consumerStoredEnergy(consumer.instanceId());
        }
        return total;
    }

    private int totalCapacity(List<NodeRef> capacitorRefs, List<NodeRef> generatorRefs, List<SfxBlockInstanceRecord> consumers) {
        int total = 0;
        for (NodeRef capacitor : capacitorRefs) {
            total += capacitor.definition().capacity();
        }
        for (NodeRef generator : generatorRefs) {
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
            FuelMatch fuel = findFuelMatch(definition, state);
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

    private FuelMatch findFuelMatch(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            SfxElectricStack input = state.input(slot);
            if (input == null) {
                continue;
            }
            if (definition.usesVanillaCoalResolver()) {
                int totalTenths = resolveCoalFuelTenths(input, definition);
                if (totalTenths > 0) {
                    return new FuelMatch(slot, input.copyWithAmount(1), null, stackKey(input), totalTenths);
                }
                continue;
            }
            for (SfxEnergyComponentDefinition.FuelRule rule : definition.fuelRules()) {
                if (input.sameKind(rule.input()) && input.amount() >= rule.input().amount()) {
                    return new FuelMatch(slot, rule.input(), rule.output(), rule.key(), rule.seconds() * 20 * 10);
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
        if (material == Material.LAVA_BUCKET) {
            return 0;
        }
        int burnTicks = vanillaFuelTicks(input.toItemStack(items));
        if (burnTicks <= 0) {
            return 0;
        }
        int multiplier = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true) ? 2 : 1;
        return burnTicks * multiplier;
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
        FuelBurnTimeBridge bridge = fuelBurnTimeBridge;
        if (bridge == null) {
            bridge = FuelBurnTimeBridge.create();
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

    private void displayStatus(SfxBlockAnchorKey regulatorKey, GridStatus status, int supply, int consumption, int net, int totalStored, int totalCapacity) {
        switch (status) {
            case NO_NETWORK -> displayService.update(regulatorKey, new SfxEnergyDisplayService.DisplayText(
                    "energy.regulator.no-network",
                    Map.of(),
                    "<red>No Energy Network found</red>"));
            case MULTIPLE_REGULATORS -> displayService.update(regulatorKey, new SfxEnergyDisplayService.DisplayText(
                    "energy.regulator.multi-regulator",
                    Map.of(),
                    "<red>Multiple Energy Regulators connected</red>"));
            case SHARED_NODE_CONFLICT -> displayService.update(regulatorKey, new SfxEnergyDisplayService.DisplayText(
                    "energy.regulator.shared-node-conflict",
                    Map.of(),
                    "<red>Energy network conflict</red>"));
            case ONLINE -> {
                boolean classic = "classic".equalsIgnoreCase(plugin.getConfig().getString("energy.regulator-display.mode", "sfx"));
                String key;
                String fallback;
                if (classic) {
                    key = net >= 0 ? "energy.regulator.classic-positive" : "energy.regulator.classic-negative";
                    fallback = net >= 0
                            ? "<green>+{net} J ⚡</green>"
                            : "<red>{net} J ⚡</red>";
                } else {
                    key = net >= 0 ? "energy.regulator.sfx-positive" : "energy.regulator.sfx-negative";
                    fallback = net >= 0
                            ? "<green>+{net} J</green><gray> | {stored}/{capacity} J</gray>"
                            : "<red>{net} J</red><gray> | {stored}/{capacity} J</gray>";
                }
                displayService.update(regulatorKey, new SfxEnergyDisplayService.DisplayText(
                        key,
                        Map.of("net", net, "supply", supply, "consumption", consumption, "stored", totalStored, "capacity", totalCapacity),
                        fallback));
            }
        }
    }

    private void openGenerator(Player player, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition) {
        GeneratorSession existing = sessionsByInstance.get(instance.instanceId());
        if (existing != null && !existing.viewerId().equals(player.getUniqueId())) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.busy", "<red>This machine is already open.</red>")));
            return;
        }
        GeneratorSession previous = sessionsByViewer.remove(player.getUniqueId());
        if (previous != null) {
            sessionsByInstance.remove(previous.instanceId());
            syncSessionState(previous);
        }

        SfxEnergyNodeState state = currentState(instance.instanceId(), instance);
        Component title = localization.itemName(definition.id(), Component.text(definition.id()));
        Inventory inventory = plugin.getServer().createInventory(new GeneratorHolder(instance.instanceId()), INVENTORY_SIZE, title);
        GeneratorSession session = new GeneratorSession(player.getUniqueId(), instance.instanceId(), inventory);
        sessionsByViewer.put(player.getUniqueId(), session);
        sessionsByInstance.put(instance.instanceId(), session);
        activeNodes.add(instance.instanceId());
        render(session, instance, definition, inventory, state);
        player.openInventory(inventory);
    }

    private void refreshSession(UUID instanceId) {
        GeneratorSession session = sessionsByInstance.get(instanceId);
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

    private void refreshOpenGeneratorSessions() {
        for (GeneratorSession session : List.copyOf(sessionsByInstance.values())) {
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

    private void syncOpenGeneratorSessionsToState() {
        for (GeneratorSession session : List.copyOf(sessionsByInstance.values())) {
            SfxBlockInstanceRecord instance = blockData.findInstance(session.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyNodeState state = currentState(session.instanceId(), instance);
            syncInventoryToState(session.inventory(), state);
            dirtyNodes.add(session.instanceId());
        }
    }

    private void syncSessionState(GeneratorSession session) {
        SfxBlockInstanceRecord instance = blockData.findInstance(session.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyNodeState state = currentState(session.instanceId(), instance);
        syncInventoryToState(session.inventory(), state);
        dirtyNodes.add(session.instanceId());
    }

    private void render(GeneratorSession session, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state) {
        fillInventoryFrame(inventory);
        inventory.setItem(DISPLAY_SLOT, progressIcon(instance, definition, state));
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            inventory.setItem(INPUT_SLOTS[i], state.input(i) == null ? null : state.input(i).toItemStack(items));
        }
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            inventory.setItem(OUTPUT_SLOTS[i], state.output(i) == null ? null : state.output(i).toItemStack(items));
        }
    }

    private void fillInventoryFrame(Inventory inventory) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        ItemStack inputBorder = namedItem(
                Material.CYAN_STAINED_GLASS_PANE,
                localization.component("electric-ui.input.name", "<aqua>Input</aqua>"),
                List.of(localization.component("electric-ui.input.lore", "<gray>Place items here.</gray>")));
        ItemStack outputBorder = namedItem(
                Material.ORANGE_STAINED_GLASS_PANE,
                localization.component("electric-ui.output.name", "<gold>Output</gold>"),
                List.of(localization.component("electric-ui.output.lore", "<gray>Take finished items here.</gray>")));
        for (int slot : BORDER) {
            inventory.setItem(slot, filler);
        }
        for (int slot : BORDER_IN) {
            inventory.setItem(slot, inputBorder);
        }
        for (int slot : BORDER_OUT) {
            inventory.setItem(slot, outputBorder);
        }
    }

    private ItemStack progressIcon(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        GeneratorRenderStatus status = generatorRenderStatus(instance, definition, state);
        if (status == GeneratorRenderStatus.NO_NETWORK) {
            return namedItem(
                    Material.RED_STAINED_GLASS_PANE,
                    localization.component("energy.generator.no-network.name", "<red>Not Connected</red>"),
                    List.of(localization.component("energy.generator.no-network.lore", "<gray>This generator is not connected to an energy network.</gray>")));
        }
        if (status == GeneratorRenderStatus.CONFLICT) {
            return namedItem(
                    Material.RED_STAINED_GLASS_PANE,
                    localization.component("energy.generator.conflict.name", "<red>Network Conflict</red>"),
                    List.of(localization.component("energy.generator.conflict.lore", "<gray>Resolve regulator or shared-node conflicts first.</gray>")));
        }
        if (status == GeneratorRenderStatus.IDLE && !definition.isSolarGenerator()) {
            return namedItem(
                    Material.BLACK_STAINED_GLASS_PANE,
                    localization.component("energy.generator.idle.name", "<gray>Idle</gray>"),
                    List.of(localization.component("energy.generator.idle.lore", "<gray>Insert fuel to start generating power.</gray>")));
        }
        if (definition.isSolarGenerator()) {
            return namedItem(
                    definition.progressMaterial(),
                    localization.component("energy.generator.solar.name", "<yellow>Solar Generator</yellow>"),
                    List.of(localization.component(
                            "energy.generator.buffer",
                            "<gray>Stored: </gray><yellow>{stored}</yellow><gray>/</gray><yellow>{capacity}</yellow><gray> J</gray>",
                            Map.of("stored", state.storedEnergy(), "capacity", definition.capacity())),
                            localization.component(
                                    "energy.generator.production",
                                    "<gray>Production: </gray><green>{energy} J/t</green>",
                                    Map.of("energy", definition.energyPerTick()))));
        }

        int total = Math.max(1, state.fuelTotalTenths());
        int progress = Math.min(total, state.fuelProgressTenths());
        ItemStack stack = new ItemStack(definition.progressMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof Damageable damageable && stack.getType().getMaxDurability() > 0) {
            damageable.setDamage(Math.max(0, Math.min(stack.getType().getMaxDurability(),
                    (stack.getType().getMaxDurability() * (total - progress)) / total)));
        }
        meta.displayName(localization.component("energy.generator.active.name", "<green>Generating</green>"));
        meta.lore(List.of(
                progressBarLine(progress, total),
                localization.component(
                        "energy.generator.time-left",
                        "<gray>{time}s</gray>",
                        Map.of("time", formatGeneratorSeconds(total - progress, definition.fuelBurnRateTenths()))),
                localization.component("energy.generator.active.lore", "<gray>Fuel is currently being converted into energy.</gray>"),
                localization.component(
                        "energy.generator.buffer",
                        "<gray>Stored: </gray><yellow>{stored}</yellow><gray>/</gray><yellow>{capacity}</yellow><gray> J</gray>",
                        Map.of("stored", state.storedEnergy(), "capacity", definition.capacity())),
                localization.component(
                        "energy.generator.production",
                        "<gray>Production: </gray><green>{energy} J/t</green>",
                        Map.of("energy", definition.energyPerTick()))));
        stack.setItemMeta(meta);
        return stack;
    }

    private GeneratorRenderStatus generatorRenderStatus(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        GridStatus gridStatus = nodeGridStatuses.get(instance.instanceId());
        if (gridStatus == GridStatus.SHARED_NODE_CONFLICT || gridStatus == GridStatus.MULTIPLE_REGULATORS) {
            return GeneratorRenderStatus.CONFLICT;
        }
        boolean connected = gridStatus == GridStatus.ONLINE;
        boolean hasFuelLoaded = definition.isSolarGenerator() || state.hasActiveFuel() || findFuelMatch(definition, state) != null;
        if (!connected && hasFuelLoaded) {
            return GeneratorRenderStatus.NO_NETWORK;
        }
        if (!state.hasActiveFuel()) {
            return GeneratorRenderStatus.IDLE;
        }
        return GeneratorRenderStatus.ACTIVE;
    }

    private Component progressBarLine(int current, int total) {
        float percentage = Math.round(((current * 100.0F) / total) * 100.0F) / 100.0F;
        int filled = Math.min(20, Math.max(0, (int) (percentage / 5.0F)));
        StringBuilder builder = new StringBuilder();
        builder.append(percentage < 50.0F ? "&6" : "&a");
        for (int i = 0; i < filled; i++) {
            builder.append(':');
        }
        builder.append("&7");
        for (int i = filled; i < 20; i++) {
            builder.append(':');
        }
        builder.append(" - ").append(percentage).append('%');
        return Text.legacy(builder.toString());
    }

    private String formatGeneratorSeconds(int remainingTenths, int burnRateTenths) {
        int remainingTicks = (int) Math.ceil(Math.max(0, remainingTenths) / (double) Math.max(1, burnRateTenths));
        double seconds = remainingTicks / 20.0D;
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return String.valueOf((int) Math.rint(seconds));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
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

    private enum GridStatus {
        ONLINE,
        NO_NETWORK,
        MULTIPLE_REGULATORS,
        SHARED_NODE_CONFLICT
    }

    private enum GeneratorRenderStatus {
        IDLE,
        ACTIVE,
        NO_NETWORK,
        CONFLICT
    }

    private record GridResult(UUID regulatorId, SfxBlockAnchorKey regulatorKey, Set<UUID> members, GridStatus status) {
    }

    private record NodeRef(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
    }

    private record FuelMatch(int inputSlot, SfxElectricStack input, SfxElectricStack output, String key, int totalTenths) {
    }

    private static final class FuelBurnTimeBridge {
        private final Method asNmsCopy;
        private final Method vanillaBurnTimes;
        private final Method registryAccess;
        private final Method getServer;
        private final Method burnDuration;

        private FuelBurnTimeBridge(
                Method asNmsCopy,
                Method vanillaBurnTimes,
                Method registryAccess,
                Method getServer,
                Method burnDuration
        ) {
            this.asNmsCopy = asNmsCopy;
            this.vanillaBurnTimes = vanillaBurnTimes;
            this.registryAccess = registryAccess;
            this.getServer = getServer;
            this.burnDuration = burnDuration;
        }

        static FuelBurnTimeBridge create() {
            try {
                Class<?> craftServer = Class.forName("org.bukkit.craftbukkit.CraftServer");
                Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
                Class<?> minecraftServer = Class.forName("net.minecraft.server.MinecraftServer");
                Class<?> fuelValues = Class.forName("net.minecraft.world.level.block.entity.FuelValues");
                Class<?> holderLookupProvider = Class.forName("net.minecraft.core.HolderLookup$Provider");
                Class<?> featureFlagSet = Class.forName("net.minecraft.world.flag.FeatureFlagSet");
                Class<?> nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");

                Method asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
                Method getServer = craftServer.getMethod("getServer");
                Method registryAccess = minecraftServer.getMethod("registryAccess");
                Method vanillaBurnTimes = fuelValues.getMethod("vanillaBurnTimes", holderLookupProvider, featureFlagSet);
                Method burnDuration = fuelValues.getMethod("burnDuration", nmsItemStack);
                return new FuelBurnTimeBridge(
                        asNmsCopy,
                        vanillaBurnTimes,
                        registryAccess,
                        getServer,
                        burnDuration
                );
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to bind vanilla fuel burn time bridge", exception);
            }
        }

        int burnTicks(ItemStack stack) {
            try {
                Object craftServerInstance = Bukkit.getServer();
                Object minecraftServerInstance = getServer.invoke(craftServerInstance);
                Object registry = registryAccess.invoke(minecraftServerInstance);
                Object worldData = minecraftServerInstance.getClass().getMethod("getWorldData").invoke(minecraftServerInstance);
                Object dataConfiguration = worldData.getClass().getMethod("getDataConfiguration").invoke(worldData);
                Object featureFlags = dataConfiguration.getClass().getMethod("enabledFeatures").invoke(dataConfiguration);
                Object fuelValues = vanillaBurnTimes.invoke(null, registry, featureFlags);
                Object nmsStack = asNmsCopy.invoke(null, stack);
                return (int) burnDuration.invoke(fuelValues, nmsStack);
            } catch (ReflectiveOperationException exception) {
                return 0;
            }
        }
    }

    private static final class GeneratorSession {
        private final UUID viewerId;
        private final UUID instanceId;
        private final Inventory inventory;

        private GeneratorSession(UUID viewerId, UUID instanceId, Inventory inventory) {
            this.viewerId = viewerId;
            this.instanceId = instanceId;
            this.inventory = inventory;
        }

        UUID viewerId() {
            return viewerId;
        }

        UUID instanceId() {
            return instanceId;
        }

        Inventory inventory() {
            return inventory;
        }
    }

    private record GeneratorHolder(UUID instanceId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
