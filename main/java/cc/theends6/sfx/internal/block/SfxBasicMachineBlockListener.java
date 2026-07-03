package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.energy.SfxFuelBurnTimeBridge;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import cc.theends6.sfx.internal.machine.SfxMachineLegacyHookBridge;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachineExecution;
import cc.theends6.sfx.internal.machine.SfxMachineEffectDispatcher;
import cc.theends6.sfx.internal.machine.SfxMachineState;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseContext;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseResult;
import cc.theends6.sfx.internal.machine.SfxMachineTickSettings;
import cc.theends6.sfx.internal.machine.SfxOutputPolicies;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxBasicMachineBlockListener implements Listener {
    private static final Set<String> SUPPORTED_BLOCKS = Set.of(
            "sf:composter",
            "sf:crucible",
            "sf:output_chest",
            "sf:ignition_chamber",
            "sf:enhanced_furnace",
            "sf:enhanced_furnace_2",
            "sf:enhanced_furnace_3",
            "sf:enhanced_furnace_4",
            "sf:enhanced_furnace_5",
            "sf:enhanced_furnace_6",
            "sf:enhanced_furnace_7",
            "sf:enhanced_furnace_8",
            "sf:enhanced_furnace_9",
            "sf:enhanced_furnace_10",
            "sf:enhanced_furnace_11",
            "sf:reinforced_furnace",
            "sf:carbonado_edged_furnace"
    );

    private static final BlockFace[] OUTPUT_CHEST_SEARCH = {
            BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };
    private static final BlockFace[] IGNITION_SEARCH = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final Map<SfxBlockAnchorKey, ActiveCrucibleProcess> activeCrucibles = new ConcurrentHashMap<>();
    final Set<SfxBlockAnchorKey> enhancedFurnaces = ConcurrentHashMap.newKeySet();
    final Map<SfxBlockAnchorKey, VirtualFurnaceState> virtualFurnaces = new ConcurrentHashMap<>();
    final Set<SfxBlockAnchorKey> viewedFurnaces = ConcurrentHashMap.newKeySet();
    private final Map<Material, Optional<VirtualFurnaceRecipe>> furnaceRecipeCache = new ConcurrentHashMap<>();
    private volatile SfxFuelBurnTimeBridge fuelBurnTimeBridge;
    private final SfxMachineTickSettings tickSettings;
    private final SfxMachineRuntimeEngine machineRuntime;
    private volatile boolean furnaceTickerRunning;
    private volatile long furnaceTickCounter;

    public SfxBasicMachineBlockListener(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization, SfxBlockDataService blockData, SfxMachineRuntimeEngine sharedMachineRuntime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.tickSettings = SfxMachineTickSettings.from(plugin.getConfig());
        this.machineRuntime = sharedMachineRuntime == null ? new SfxMachineRuntimeEngine() : sharedMachineRuntime;
        this.machineRuntime.registerDefinitions(SfxBasicMachineFrameworkBridge.definitions());
        registerFrameworkEffects();
        bootstrapEnhancedFurnaces();
    }

    private void registerFrameworkEffects() {
        for (String effectName : List.of(
                "basic:hand-input",
                "hand:consume-input",
                "world:drop-result",
                "furnace:intercept-burn-smelt",
                "furnace:sync-virtual-state"
        )) {
            machineRuntime.registerEffectHook(effectName, context -> frameworkBasicEffect(effectName, context));
        }
    }

    private SfxMachinePhaseResult frameworkBasicEffect(String effectName, SfxMachinePhaseContext phaseContext) {
        phaseContext.put("basic.framework.effect", effectName);
        if ("basic:hand-input".equals(effectName) || "hand:consume-input".equals(effectName) || "world:drop-result".equals(effectName)) {
            return frameworkHandInput(phaseContext);
        }
        if ("furnace:intercept-burn-smelt".equals(effectName)) {
            return frameworkEnhancedFurnaceTick(phaseContext);
        }
        if ("furnace:sync-virtual-state".equals(effectName)) {
            return frameworkSyncEnhancedFurnace(phaseContext);
        }
        return SfxMachinePhaseResult.cont();
    }

    private SfxMachinePhaseResult frameworkHandInput(SfxMachinePhaseContext phaseContext) {
        PlayerInteractEvent event = phaseContext.attachment("basic.interactEvent", PlayerInteractEvent.class).orElse(null);
        Block block = phaseContext.attachment("basic.block", Block.class).orElse(null);
        if (event == null || block == null) {
            return SfxMachinePhaseResult.cont();
        }
        String typeId = phaseContext.definition() == null ? null : phaseContext.definition().id();
        if ("sf:composter".equals(typeId)) {
            handleComposter(event, block);
            phaseContext.put("basic.handled", Boolean.TRUE);
            return SfxMachinePhaseResult.complete(cc.theends6.sfx.internal.machine.SfxMachineStatus.RUNNING, "composter handled through framework effect");
        }
        if ("sf:crucible".equals(typeId)) {
            handleCrucible(event, block);
            phaseContext.put("basic.handled", Boolean.TRUE);
            return SfxMachinePhaseResult.complete(cc.theends6.sfx.internal.machine.SfxMachineStatus.RUNNING, "crucible handled through framework effect");
        }
        return SfxMachinePhaseResult.cont();
    }

    private SfxMachinePhaseResult frameworkEnhancedFurnaceTick(SfxMachinePhaseContext phaseContext) {
        return SfxEnhancedFurnaceTickController.tick(this, phaseContext);
    }

    private SfxMachinePhaseResult frameworkSyncEnhancedFurnace(SfxMachinePhaseContext phaseContext) {
        Block block = phaseContext.attachment("basic.block", Block.class).orElse(null);
        VirtualFurnaceState state = phaseContext.attachment("basic.furnaceState", VirtualFurnaceState.class).orElse(null);
        Integer cookTime = phaseContext.attachment("basic.furnace.cookTime", Integer.class).orElse(null);
        Boolean forceVisual = phaseContext.attachment("basic.furnace.forceVisual", Boolean.class).orElse(null);
        SfxMachineTickContext context = phaseContext.tickContext();
        if (block == null || state == null || context == null) {
            return SfxMachinePhaseResult.cont();
        }
        syncVirtualFurnaceWorld(block, state, cookTime == null ? currentCookTime(state) : cookTime, Boolean.TRUE.equals(forceVisual), context.hasViewers());
        return SfxMachinePhaseResult.cont();
    }

    private boolean runFrameworkHandInput(String typeId, SfxBlockInstanceRecord instance, PlayerInteractEvent event, Block block) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("basic.interactEvent", event);
        attributes.put("basic.block", block);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkBasicEffect);
        machineRuntime.runPhase(typeId, SfxMachinePhase.ON_COMPLETE, instance.instanceId(), block.getLocation(), new SfxMachineTickContext(0L, 1L, true), null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, attributes);
        Object handled = attributes.get("basic.handled");
        return handled instanceof Boolean value && value;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            if (!SUPPORTED_BLOCKS.contains(marker.itemId())) {
                return;
            }
            UUID instanceId = blockData.findAnchor(event.getBlockPlaced().getLocation())
                    .map(SfxAnchorRecord::instanceId)
                    .orElseGet(() -> blockData.registerSingleBlock(marker.itemId(), event.getBlockPlaced().getLocation(), event.getBlockPlaced().getType(), event.getPlayer().getUniqueId()));
            machineRuntime.recordState(instanceId, marker.itemId(), event.getBlockPlaced().getLocation(), cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE);
            SfxMachineLegacyHookBridge.place(machineRuntime, marker.itemId(), instanceId, event.getBlockPlaced().getLocation(), "basic", "SfxBasicMachineBlockListener.onPlace");
            if (furnaceStats(marker.itemId()) != null) {
                SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(event.getBlockPlaced().getLocation());
                enhancedFurnaces.add(key);
                markFurnaceExternalDirty(key);
            }
        });
    }

    public boolean supportsType(String typeId) {
        return SUPPORTED_BLOCKS.contains(typeId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<SfxAnchorRecord> anchor = blockData.findAnchor(event.getBlock().getLocation());
        if (anchor.isEmpty()) {
            return;
        }
        String typeId = instanceType(anchor.get().instanceId());
        if (!SUPPORTED_BLOCKS.contains(typeId)) {
            return;
        }
        event.setDropItems(false);
        destroyAnchoredBlock(event.getBlock(), typeId);
    }

    public void destroyAnchoredBlock(Block block, String typeId) {
        if (block == null || typeId == null || !SUPPORTED_BLOCKS.contains(typeId)) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(block.getLocation());
        clearActiveCrucible(key, true);
        enhancedFurnaces.remove(key);
        virtualFurnaces.remove(key);
        viewedFurnaces.remove(key);
        dropStoredContents(block);
        dropPluginBlock(block, typeId);
        blockData.unregisterAt(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick()) {
            return;
        }
        SfxAnchoredInteraction interaction = SfxAnchoredInteraction.resolve(event, blockData);
        if (interaction == null) {
            return;
        }
        Block clicked = interaction.block();
        String typeId = interaction.instance().typeId();
        SfxMachineLegacyHookBridge.interact(machineRuntime, typeId, interaction.instance().instanceId(), clicked.getLocation(), "basic", "SfxBasicMachineBlockListener.onInteract");
        if (typeId != null && SfxInteractionRules.prefersBlockPlacement(items, event)) {
            return;
        }
        if (furnaceStats(typeId) != null) {
            SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(clicked.getLocation());
            enhancedFurnaces.add(key);
            VirtualFurnaceState state = virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState());
            state.sleeping(false);
            state.externalDirty(true);
            return;
        }
        if ("sf:composter".equals(typeId) || "sf:crucible".equals(typeId)) {
            if (runFrameworkHandInput(typeId, interaction.instance(), event, clicked)) {
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickBlock(PlayerPickBlockEvent event) {
        Optional<SfxAnchorRecord> anchor = blockData.findAnchor(event.getBlock().getLocation());
        if (anchor.isEmpty()) {
            return;
        }
        String typeId = instanceType(anchor.get().instanceId());
        if (typeId == null) {
            return;
        }
        event.setCancelled(true);
        SfxPickBlockSupport.selectOrCreate(event.getPlayer(), items, typeId, event.getTargetSlot());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFuelBurn(FurnaceBurnEvent event) {
        if (event.getBlock().getType() != Material.FURNACE) {
            return;
        }
        FurnaceStats stats = furnaceStats(event.getBlock().getLocation());
        if (stats == null) {
            return;
        }
        event.setCancelled(true);
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(event.getBlock().getLocation());
        enhancedFurnaces.add(key);
        markFurnaceExternalDirty(key);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSmelt(FurnaceSmeltEvent event) {
        if (event.getBlock().getType() != Material.FURNACE) {
            return;
        }
        FurnaceStats stats = furnaceStats(event.getBlock().getLocation());
        if (stats == null) {
            return;
        }
        event.setCancelled(true);
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(event.getBlock().getLocation());
        enhancedFurnaces.add(key);
        markFurnaceExternalDirty(key);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Furnace furnace)) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(furnace.getLocation());
        SfxMachineLegacyHookBridge.menuOpen(machineRuntime, instanceTypeAt(furnace.getLocation()), null, furnace.getLocation(), "basic", "SfxBasicMachineBlockListener.onFurnaceInventoryOpen");
        if (furnaceStats(furnace.getLocation()) == null) {
            return;
        }
        enhancedFurnaces.add(key);
        viewedFurnaces.add(key);
        VirtualFurnaceState state = virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState());
        if (!state.initialized() || state.externalDirty()) {
            hydrateVirtualFurnaceState(state, event.getInventory());
        } else {
            applyVirtualFurnaceStateToInventory(state, event.getInventory());
        }
        state.sleeping(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Furnace furnace)) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(furnace.getLocation());
        SfxMachineLegacyHookBridge.menuClose(machineRuntime, instanceTypeAt(furnace.getLocation()), null, furnace.getLocation(), "basic", "SfxBasicMachineBlockListener.onFurnaceInventoryClose");
        VirtualFurnaceState state = virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState());
        hydrateVirtualFurnaceState(state, event.getInventory());
        runtime.executeAtLater(furnace.getLocation(), 1L, () -> {
            if (event.getInventory().getViewers().isEmpty()) {
                viewedFurnaces.remove(key);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceInventoryClick(InventoryClickEvent event) {
        markFurnaceInventoryExternalLater(event.getInventory());
        if (event.getClickedInventory() != null && event.getClickedInventory() != event.getInventory()) {
            markFurnaceInventoryExternalLater(event.getClickedInventory());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceInventoryDrag(InventoryDragEvent event) {
        markFurnaceInventoryExternalLater(event.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceInventoryMove(InventoryMoveItemEvent event) {
        wakeFurnaceInventory(event.getSource());
        wakeFurnaceInventory(event.getDestination());
    }

    private void wakeFurnaceInventory(Inventory inventory) {
        if (!(inventory.getHolder() instanceof Furnace furnace)) {
            return;
        }
        if (furnaceStats(furnace.getLocation()) == null) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(furnace.getLocation());
        enhancedFurnaces.add(key);
        markFurnaceInventoryExternalLater(inventory);
        virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState()).sleeping(false);
    }

    private void markFurnaceInventoryExternalLater(Inventory inventory) {
        if (!(inventory.getHolder() instanceof Furnace furnace)) {
            return;
        }
        if (furnaceStats(furnace.getLocation()) == null) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(furnace.getLocation());
        enhancedFurnaces.add(key);
        VirtualFurnaceState state = virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState());
        state.externalDirty(true);
        state.sleeping(false);
        runtime.executeAtLater(furnace.getLocation(), 1L, () -> {
            VirtualFurnaceState latest = virtualFurnaces.get(key);
            if (latest != null && latest.externalDirty()) {
                hydrateVirtualFurnaceState(latest, inventory);
            }
        });
    }

    private void markFurnaceExternalDirty(SfxBlockAnchorKey key) {
        if (key == null) {
            return;
        }
        VirtualFurnaceState state = virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState());
        state.externalDirty(true);
        state.sleeping(false);
    }

    public Optional<Inventory> findOutputChestFor(Block machineBlock, ItemStack output) {
        for (BlockFace face : OUTPUT_CHEST_SEARCH) {
            Block target = machineBlock.getRelative(face);
            if (target.getType() != Material.CHEST) {
                continue;
            }
            if (!"sf:output_chest".equals(instanceTypeAt(target.getLocation()))) {
                continue;
            }
            BlockState state = target.getState();
            if (state instanceof Chest chest) {
                Inventory inventory = chest.getInventory();
                if (fits(inventory, output)) {
                    return Optional.of(inventory);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Inventory> findAnyOutputChestFor(Block machineBlock) {
        for (BlockFace face : OUTPUT_CHEST_SEARCH) {
            Block target = machineBlock.getRelative(face);
            if (target.getType() != Material.CHEST) {
                continue;
            }
            if (!"sf:output_chest".equals(instanceTypeAt(target.getLocation()))) {
                continue;
            }
            BlockState state = target.getState();
            if (state instanceof Chest chest) {
                return Optional.of(chest.getInventory());
            }
        }
        return Optional.empty();
    }

    public boolean useIgnitionChamber(Player player, Block smelteryDispenserBlock) {
        for (BlockFace face : IGNITION_SEARCH) {
            Block target = smelteryDispenserBlock.getRelative(face);
            if (target.getType() != Material.DROPPER) {
                continue;
            }
            if (!"sf:ignition_chamber".equals(instanceTypeAt(target.getLocation()))) {
                continue;
            }
            BlockState state = target.getState();
            if (!(state instanceof Dropper dropper)) {
                continue;
            }
            Inventory inventory = dropper.getInventory();
            int slot = inventory.first(Material.FLINT_AND_STEEL);
            if (slot < 0) {
                if (player != null) {
                    player.sendMessage(Text.prefixed(plugin, localization.text("machines.ignition-chamber-no-flint")));
                }
                return false;
            }
            ItemStack tool = inventory.getItem(slot);
            if (tool == null) {
                return false;
            }
            if (!tool.getItemMeta().isUnbreakable()) {
                short durability = (short) (tool.getDurability() + 1);
                if (durability >= tool.getType().getMaxDurability()) {
                    inventory.setItem(slot, null);
                } else {
                    tool.setDurability(durability);
                    inventory.setItem(slot, tool);
                }
            }
            playSound(smelteryDispenserBlock.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
            return true;
        }
        return false;
    }

    private void handleComposter(PlayerInteractEvent event, Block block) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack input = itemInHand(event);
        if (input == null || input.getType().isAir()) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.empty")));
            return;
        }
        ItemStack output = composterOutput(input);
        if (output == null) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.wrong-item")));
            return;
        }
        consumeFromHand(player, event, input, requiredComposterAmount(input.getType()));
        Location origin = block.getLocation();
        playComposterEffects(origin, input.getType());
        runtime.executeAtLater(origin, 20L, () -> completeComposter(block, output));
    }

    private void handleCrucible(PlayerInteractEvent event, Block block) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        SfxBlockAnchorKey anchorKey = SfxBlockAnchorKey.fromLocation(block.getLocation());
        if (activeCrucibles.containsKey(anchorKey)) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.busy")));
            return;
        }
        ItemStack input = itemInHand(event);
        if (input == null || input.getType().isAir()) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.empty")));
            return;
        }
        CruciblePlan plan = cruciblePlan(input.getType());
        if (plan == null) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.wrong-item")));
            return;
        }
        Block outputBlock = block.getRelative(BlockFace.UP);
        if (!canStartCrucible(outputBlock, plan.water())) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.crucible-blocked")));
            return;
        }
        consumeFromHand(player, event, input, plan.inputAmount());
        UUID token = UUID.randomUUID();
        activeCrucibles.put(anchorKey, new ActiveCrucibleProcess(token, outputBlock.getLocation(), plan.water()));
        setInstanceState(block, SfxBlockLifecycleState.ACTIVE, "crucible:active:" + plan.water());
        generateLiquid(anchorKey, token, outputBlock, plan.water());
    }

    private void completeComposter(Block block, ItemStack output) {
        Optional<Inventory> outputChest = findOutputChestFor(block, output);
        Location dropLocation = block.getRelative(BlockFace.UP).getLocation().add(0.5, 0.5, 0.5);
        if (outputChest.isPresent()) {
            cc.theends6.sfx.internal.inventory.SfxInventoryMutationBridge.insertAllOrDrop(outputChest.get(), output.clone(), false, dropLocation, "basic:composter-output");
        } else {
            cc.theends6.sfx.internal.inventory.SfxInventoryMutationBridge.drop(dropLocation, output.clone());
        }
        playSound(block.getLocation(), Sound.BLOCK_COMPOSTER_READY, 1.0f, 1.0f);
    }

    private void generateLiquid(SfxBlockAnchorKey anchorKey, UUID token, Block block, boolean water) {
        if (!isCrucibleProcessActive(anchorKey, token)) {
            return;
        }
        if (water && block.getWorld().getEnvironment() == World.Environment.NETHER
                && !plugin.getConfig().getBoolean("plugin-blocks.crucible.allow-water-in-nether", false)) {
            block.getWorld().spawnParticle(Particle.SMOKE, block.getLocation().add(0.5, 0.5, 0.5), 4, 0.1, 0.1, 0.1, 0.01);
            playSound(block.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
            return;
        }

        if (block.getType() == (water ? Material.WATER : Material.LAVA)) {
            addLiquidLevel(anchorKey, token, block, water);
        } else if (block.getType() == (water ? Material.LAVA : Material.WATER)) {
            Levelled levelled = (Levelled) block.getBlockData();
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, instanceTypeAt(block.getLocation()), block, levelled.getLevel() == 0 || levelled.getLevel() == 8 ? Material.OBSIDIAN : Material.STONE, false, "basic", "generateLiquid:solidify");
            playSound(block.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
        } else {
            runtime.executeAtLater(block.getLocation(), 50L, () -> placeLiquid(anchorKey, token, block, water));
        }
    }

    private void addLiquidLevel(SfxBlockAnchorKey anchorKey, UUID token, Block block, boolean water) {
        if (!isCrucibleProcessActive(anchorKey, token)) {
            return;
        }
        Levelled levelled = (Levelled) block.getBlockData();
        int level = levelled.getLevel();
        if (level > 7) {
            level -= 8;
        }
        if (level == 0) {
            runCruciblePostTask(anchorKey, token, block, water, 1);
        } else {
            int next = 7 - level;
            runtime.executeAtLater(block.getLocation(), 50L, () -> runCruciblePostTask(anchorKey, token, block, water, next));
        }
    }

    private void placeLiquid(SfxBlockAnchorKey anchorKey, UUID token, Block block, boolean water) {
        if (!isCrucibleProcessActive(anchorKey, token)) {
            return;
        }
        if (block.getType().isAir()) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, instanceTypeAt(block.getLocation()), block, water ? Material.WATER : Material.LAVA, false, "basic", "placeLiquid");
        } else if (water && block.getBlockData() instanceof Waterlogged waterlogged) {
            waterlogged.setWaterlogged(true);
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(machineRuntime, instanceTypeAt(block.getLocation()), block, waterlogged, false, "basic", "placeLiquid:waterlog");
            playSound(block.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
            return;
        }
        runCruciblePostTask(anchorKey, token, block, water, 1);
    }

    private void runCruciblePostTask(SfxBlockAnchorKey anchorKey, UUID token, Block block, boolean water, int times) {
        if (!isCrucibleProcessActive(anchorKey, token)) {
            return;
        }
        if (!(block.getBlockData() instanceof Levelled levelled)) {
            playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
            return;
        }
        playSound(block.getLocation(), water ? Sound.ITEM_BUCKET_EMPTY : Sound.ITEM_BUCKET_EMPTY_LAVA, 1.0f, 1.0f);
        levelled.setLevel(8 - times);
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(machineRuntime, instanceTypeAt(block.getLocation()), block, levelled, false, "basic", "runCruciblePostTask:level");
        if (times < 8) {
            runtime.executeAtLater(block.getLocation(), 50L, () -> runCruciblePostTask(anchorKey, token, block, water, times + 1));
        } else {
            playSound(block.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1.0f, 1.0f);
            clearActiveCrucible(anchorKey, false);
            setInstanceState(block.getRelative(BlockFace.DOWN), SfxBlockLifecycleState.IDLE, "crucible:idle");
        }
    }

    private void playComposterEffects(Location origin, Material source) {
        Location center = origin.clone().add(0.5, 0.5, 0.5);
        runtime.executeAt(origin, () -> origin.getWorld().playEffect(center, org.bukkit.Effect.STEP_SOUND, source.isBlock() ? source : Material.HAY_BLOCK));
        runtime.executeAtLater(origin, 10L, () -> origin.getWorld().playEffect(center, org.bukkit.Effect.STEP_SOUND, source.isBlock() ? source : Material.HAY_BLOCK));
        runtime.executeAtLater(origin, 20L, () -> origin.getWorld().playEffect(center, org.bukkit.Effect.STEP_SOUND, source.isBlock() ? source : Material.HAY_BLOCK));
    }

    private FurnaceStats furnaceStats(Location location) {
        String typeId = instanceTypeAt(location);
        if (typeId == null) {
            return null;
        }
        return switch (typeId) {
            case "sf:enhanced_furnace" -> new FurnaceStats(2, 1, 1);
            case "sf:enhanced_furnace_2" -> new FurnaceStats(2, 1, 1);
            case "sf:enhanced_furnace_3" -> new FurnaceStats(3, 2, 1);
            case "sf:enhanced_furnace_4" -> new FurnaceStats(3, 3, 1);
            case "sf:enhanced_furnace_5" -> new FurnaceStats(4, 3, 1);
            case "sf:enhanced_furnace_6" -> new FurnaceStats(4, 3, 2);
            case "sf:enhanced_furnace_7" -> new FurnaceStats(5, 3, 2);
            case "sf:enhanced_furnace_8" -> new FurnaceStats(5, 4, 2);
            case "sf:enhanced_furnace_9" -> new FurnaceStats(6, 4, 2);
            case "sf:enhanced_furnace_10" -> new FurnaceStats(7, 4, 2);
            case "sf:enhanced_furnace_11" -> new FurnaceStats(8, 4, 2);
            case "sf:reinforced_furnace" -> new FurnaceStats(10, 5, 3);
            case "sf:carbonado_edged_furnace" -> new FurnaceStats(20, 10, 3);
            default -> null;
        };
    }

    private String instanceTypeAt(Location location) {
        return blockData.findAnchor(location)
                .map(anchor -> instanceType(anchor.instanceId()))
                .orElse(null);
    }

    private String instanceType(UUID instanceId) {
        return blockData.findInstance(instanceId).map(SfxBlockInstanceRecord::typeId).orElse(null);
    }

    private boolean fits(Inventory inventory, ItemStack stack) {
        return inventory != null && SfxOutputPolicies.canFitIntoContents(inventory.getStorageContents(), stack);
    }

    private ItemStack itemInHand(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null) {
            return item;
        }
        return switch (event.getHand()) {
            case HAND -> event.getPlayer().getInventory().getItemInMainHand();
            case OFF_HAND -> event.getPlayer().getInventory().getItemInOffHand();
            default -> null;
        };
    }

    private void consumeFromHand(Player player, PlayerInteractEvent event, ItemStack stack, int amount) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        int next = stack.getAmount() - amount;
        if (next <= 0) {
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            stack.setAmount(next);
        }
    }

    private ItemStack composterOutput(ItemStack input) {
        Material type = input.getType();
        if (Tag.LEAVES.isTagged(type) || Tag.SAPLINGS.isTagged(type)) {
            return input.getAmount() >= 8 ? new ItemStack(Material.DIRT) : null;
        }
        if (type == Material.STONE && input.getAmount() >= 4) {
            return new ItemStack(Material.NETHERRACK);
        }
        if (type == Material.SAND && input.getAmount() >= 2) {
            return new ItemStack(Material.SOUL_SAND);
        }
        if (type == Material.WHEAT && input.getAmount() >= 4) {
            return new ItemStack(Material.NETHER_WART);
        }
        return null;
    }

    private int requiredComposterAmount(Material type) {
        if (Tag.LEAVES.isTagged(type) || Tag.SAPLINGS.isTagged(type)) {
            return 8;
        }
        return switch (type) {
            case STONE -> 4;
            case SAND -> 2;
            case WHEAT -> 4;
            default -> 1;
        };
    }

    private CruciblePlan cruciblePlan(Material type) {
        if (type == Material.COBBLESTONE || type == Material.NETHERRACK) {
            return new CruciblePlan(16, false);
        }
        if (type == Material.STONE || type == Material.TERRACOTTA) {
            return new CruciblePlan(12, false);
        }
        if (type == Material.OBSIDIAN) {
            return new CruciblePlan(1, false);
        }
        if (type == Material.BLACKSTONE) {
            return new CruciblePlan(8, false);
        }
        if (type == Material.BASALT) {
            return new CruciblePlan(12, false);
        }
        if (type == Material.COBBLED_DEEPSLATE) {
            return new CruciblePlan(12, false);
        }
        if (type == Material.DEEPSLATE) {
            return new CruciblePlan(10, false);
        }
        if (type == Material.TUFF) {
            return new CruciblePlan(8, false);
        }
        if (Tag.LEAVES.isTagged(type)) {
            return new CruciblePlan(16, true);
        }
        if (isTerracottaVariant(type)) {
            return new CruciblePlan(12, false);
        }
        return null;
    }

    private boolean isEnhancedFurnaceLuckMaterial(Material type) {
        return type.name().endsWith("_ORE")
                || type == Material.RAW_IRON
                || type == Material.RAW_GOLD
                || type == Material.RAW_COPPER
                || type == Material.NETHER_GOLD_ORE
                || type == Material.DEEPSLATE_IRON_ORE
                || type == Material.DEEPSLATE_GOLD_ORE
                || type == Material.DEEPSLATE_COPPER_ORE
                || type == Material.DEEPSLATE_DIAMOND_ORE
                || type == Material.DEEPSLATE_EMERALD_ORE
                || type == Material.DEEPSLATE_REDSTONE_ORE
                || type == Material.DEEPSLATE_LAPIS_ORE
                || type == Material.DEEPSLATE_COAL_ORE;
    }

    private boolean isTerracottaVariant(Material type) {
        String name = type.name();
        return name.endsWith("TERRACOTTA") && !name.endsWith("GLAZED_TERRACOTTA");
    }

    private void bootstrapEnhancedFurnaces() {
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            if (furnaceStatsAt(anchor) != null) {
                enhancedFurnaces.add(anchor.key());
            }
        }
    }

    public synchronized void start() {
        if (furnaceTickerRunning) {
            return;
        }
        furnaceTickerRunning = true;
        scheduleEnhancedFurnaceTick();
    }

    public void shutdown() {
        furnaceTickerRunning = false;
        furnaceTickCounter = 0L;
        activeCrucibles.clear();
        enhancedFurnaces.clear();
        virtualFurnaces.clear();
        viewedFurnaces.clear();
        furnaceRecipeCache.clear();
    }

    private void scheduleEnhancedFurnaceTick() {
        runtime.executeGlobalLater(1L, () -> {
            if (!furnaceTickerRunning) {
                return;
            }
            long currentTick = ++furnaceTickCounter;
            for (SfxBlockAnchorKey key : enhancedFurnaces) {
                SfxAnchorRecord anchor = blockData.findAnchorFast(key).orElse(null);
                if (anchor == null) {
                    enhancedFurnaces.remove(key);
                    virtualFurnaces.remove(key);
                    viewedFurnaces.remove(key);
                    continue;
                }
                FurnaceStats stats = furnaceStatsAt(anchor);
                if (stats == null) {
                    enhancedFurnaces.remove(key);
                    virtualFurnaces.remove(key);
                    viewedFurnaces.remove(key);
                    continue;
                }
                VirtualFurnaceState state = virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState());
                boolean hasViewers = viewedFurnaces.contains(key);
                int interval = state.sleeping() && !hasViewers
                        ? tickSettings.sleepingProbeIntervalTicks()
                        : tickSettings.intervalFor(hasViewers);
                long lastTick = state.lastLogicTick();
                if (lastTick > 0L && currentTick - lastTick < interval) {
                    continue;
                }
                long elapsedTicks = lastTick <= 0L ? 1L : Math.max(1L, currentTick - lastTick);
                state.lastLogicTick(currentTick);
                World world = plugin.getServer().getWorld(key.worldId());
                if (world == null) {
                    continue;
                }
                Location location = new Location(world, key.x(), key.y(), key.z());
                SfxMachineTickContext context = new SfxMachineTickContext(currentTick, elapsedTicks, hasViewers);
                runtime.executeAt(location, () -> tickEnhancedFurnace(location.getBlock(), key, stats, context));
            }
            scheduleEnhancedFurnaceTick();
        });
    }

    private void tickEnhancedFurnace(Block block, SfxBlockAnchorKey key, FurnaceStats stats, SfxMachineTickContext context) {
        if (block.getType() != Material.FURNACE) {
            enhancedFurnaces.remove(key);
            virtualFurnaces.remove(key);
            viewedFurnaces.remove(key);
            return;
        }
        VirtualFurnaceState state = virtualFurnaces.computeIfAbsent(key, ignored -> new VirtualFurnaceState());
        String frameworkMachineId = blockData.findAnchor(block.getLocation())
                .flatMap(anchor -> blockData.findInstance(anchor.instanceId()))
                .map(SfxBlockInstanceRecord::typeId)
                .orElse("sf:enhanced_furnace");
        Map<String, Object> frameworkAttributes = new LinkedHashMap<>();
        frameworkAttributes.put("basic.block", block);
        frameworkAttributes.put("basic.furnaceKey", key);
        frameworkAttributes.put("basic.furnaceStats", stats);
        frameworkAttributes.put("basic.furnaceState", state);
        frameworkAttributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkBasicEffect);
        try (SfxMachineExecution machineExecution = machineRuntime.beginTick(blockData.findAnchor(block.getLocation()).map(SfxAnchorRecord::instanceId).orElse(null), frameworkMachineId, block.getLocation(), context, new SfxMachineState(), frameworkAttributes)) {
            Object status = frameworkAttributes.get("basic.furnace.status");
            machineExecution.status(status instanceof cc.theends6.sfx.internal.machine.SfxMachineStatus machineStatus
                    ? machineStatus
                    : cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE);
        }
    }

    void burnOneVirtualFuelTick(VirtualFurnaceState state) {
        if (state.burnTimeRemaining() > 0) {
            state.burnTimeRemaining(state.burnTimeRemaining() - 1);
        }
    }

    int currentCookTime(VirtualFurnaceState state) {
        ItemStack input = state.smelting();
        VirtualFurnaceRecipe recipe = resolveFurnaceRecipe(input).orElse(null);
        if (recipe != null) {
            return Math.max(1, recipe.cookingTime());
        }
        return Math.max(1, state.cookTimeTotal());
    }

    boolean canStartOrContinueVirtualSmelting(VirtualFurnaceState state, FurnaceStats stats) {
        ItemStack input = state.smelting();
        VirtualFurnaceRecipe recipe = resolveFurnaceRecipe(input).orElse(null);
        if (recipe == null || input == null || input.getType().isAir()) {
            return false;
        }
        ItemStack result = recipe.result();
        if (!canFitResult(state, result)) {
            return false;
        }
        return state.burnTimeRemaining() > 0 || enhancedFuelTicks(state.fuel(), stats) > 0;
    }

    Optional<VirtualFurnaceRecipe> resolveFurnaceRecipe(ItemStack input) {
        if (input == null || input.getType().isAir()) {
            return Optional.empty();
        }
        ItemStack probe = input.clone();
        probe.setAmount(1);
        return furnaceRecipeCache.computeIfAbsent(input.getType(), material -> {
            Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
            while (iterator.hasNext()) {
                Recipe recipe = iterator.next();
                if (!(recipe instanceof FurnaceRecipe furnaceRecipe)) {
                    continue;
                }
                if (!matchesFurnaceInput(furnaceRecipe, probe)) {
                    continue;
                }
                ItemStack result = furnaceRecipe.getResult();
                if (result == null || result.getType().isAir()) {
                    continue;
                }
                int cookingTime = Math.max(1, furnaceRecipe.getCookingTime());
                return Optional.of(new VirtualFurnaceRecipe(result.clone(), cookingTime));
            }
            return Optional.empty();
        });
    }

    private boolean matchesFurnaceInput(FurnaceRecipe furnaceRecipe, ItemStack probe) {
        try {
            RecipeChoice choice = furnaceRecipe.getInputChoice();
            if (choice != null) {
                return choice.test(probe);
            }
        } catch (Throwable ignored) {
            
        }
        ItemStack legacy = furnaceRecipe.getInput();
        return legacy != null && !legacy.getType().isAir() && legacy.getType() == probe.getType();
    }

    ItemStack applyEnhancedFurnaceFortune(ItemStack baseResult, Material inputType, FurnaceStats stats) {
        ItemStack result = baseResult.clone();
        if (stats.fortuneLevel() > 0 && isEnhancedFurnaceLuckMaterial(inputType)) {
            int bonus = ThreadLocalRandom.current().nextInt(stats.fortuneLevel() + 1);
            result.setAmount(Math.min(result.getMaxStackSize(), result.getAmount() + bonus));
        }
        return result;
    }

    int enhancedFuelTicks(ItemStack fuel, FurnaceStats stats) {
        if (fuel == null || fuel.getType().isAir()) {
            return 0;
        }
        SfxFuelBurnTimeBridge bridge = fuelBurnTimeBridge;
        int burnTicks = 0;
        if (bridge == null) {
            try {
                bridge = SfxFuelBurnTimeBridge.create();
                fuelBurnTimeBridge = bridge;
            } catch (RuntimeException ignored) {
                
            }
        }
        if (bridge != null) {
            burnTicks = bridge.burnTicks(fuel);
        }
        if (burnTicks <= 0) {
            burnTicks = fallbackFuelTicks(fuel.getType());
        }
        if (burnTicks <= 0) {
            return 0;
        }
        double burnMultiplier = stats.fuelEfficiency();
        if (plugin.getConfig().getBoolean("plugin-blocks.enhanced-furnace.speed-affects-fuel-consumption", false)
                && stats.processingSpeed() > 0) {
            burnMultiplier /= stats.processingSpeed();
        }
        return Math.max(1, Math.min(Short.MAX_VALUE - 1, (int) Math.ceil(burnTicks * burnMultiplier)));
    }

    private int fallbackFuelTicks(Material type) {
        if (type == null || type.isAir()) {
            return 0;
        }
        return switch (type) {
            case LAVA_BUCKET -> 20_000;
            case COAL_BLOCK -> 16_000;
            case DRIED_KELP_BLOCK -> 4_000;
            case BLAZE_ROD -> 2_400;
            case COAL, CHARCOAL -> 1_600;
            case BAMBOO -> 50;
            case STICK, BOWL, OAK_SAPLING, SPRUCE_SAPLING, BIRCH_SAPLING, JUNGLE_SAPLING, ACACIA_SAPLING,
                    DARK_OAK_SAPLING, MANGROVE_PROPAGULE, CHERRY_SAPLING, AZALEA, FLOWERING_AZALEA -> 100;
            case WOODEN_SWORD, WOODEN_SHOVEL, WOODEN_PICKAXE, WOODEN_AXE, WOODEN_HOE -> 200;
            default -> fallbackWoodFuelTicks(type);
        };
    }

    private int fallbackWoodFuelTicks(Material type) {
        String name = type.name();
        if (!isOverworldWoodFamily(name) && !name.contains("BAMBOO")) {
            return 0;
        }
        if (name.endsWith("_SLAB")) {
            return 150;
        }
        if (name.endsWith("_PLANKS")
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STAIRS")
                || name.endsWith("_FENCE")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_SIGN")
                || name.endsWith("_HANGING_SIGN")
                || name.endsWith("_BOAT")
                || name.endsWith("_CHEST_BOAT")) {
            return 300;
        }
        if (name.endsWith("_BUTTON")) {
            return 100;
        }
        return 0;
    }

    private boolean isOverworldWoodFamily(String name) {
        return name.contains("OAK")
                || name.contains("SPRUCE")
                || name.contains("BIRCH")
                || name.contains("JUNGLE")
                || name.contains("ACACIA")
                || name.contains("DARK_OAK")
                || name.contains("MANGROVE")
                || name.contains("CHERRY")
                || name.contains("PALE_OAK");
    }

    void consumeFuel(VirtualFurnaceState state, ItemStack fuel) {
        if (fuel == null || fuel.getType().isAir()) {
            return;
        }
        if (fuel.getType() == Material.LAVA_BUCKET) {
            state.fuel(new ItemStack(Material.BUCKET, 1));
            state.mirrorDirty(true);
            return;
        }
        int next = fuel.getAmount() - 1;
        if (next <= 0) {
            state.fuel(null);
        } else {
            ItemStack updated = fuel.clone();
            updated.setAmount(next);
            state.fuel(updated);
        }
        state.mirrorDirty(true);
    }

    void consumeSmeltingInput(VirtualFurnaceState state, ItemStack input) {
        if (input == null || input.getType().isAir()) {
            return;
        }
        int next = input.getAmount() - 1;
        if (next <= 0) {
            state.smelting(null);
        } else {
            ItemStack updated = input.clone();
            updated.setAmount(next);
            state.smelting(updated);
        }
        state.mirrorDirty(true);
    }

    boolean canFitResult(VirtualFurnaceState state, ItemStack result) {
        return state != null && SfxOutputPolicies.canFitSingle(state.result(), result);
    }

    void pushFurnaceResult(VirtualFurnaceState state, ItemStack result) {
        ItemStack existing = state.result();
        if (existing == null || existing.getType().isAir()) {
            state.result(result.clone());
            state.mirrorDirty(true);
            return;
        }
        if (existing.isSimilar(result)) {
            ItemStack updated = existing.clone();
            updated.setAmount(Math.min(updated.getMaxStackSize(), updated.getAmount() + result.getAmount()));
            state.result(updated);
            state.mirrorDirty(true);
        }
    }

    void consumeFuel(FurnaceInventory inventory, ItemStack fuel) {
        if (fuel == null || fuel.getType().isAir()) {
            return;
        }
        if (fuel.getType() == Material.LAVA_BUCKET) {
            inventory.setFuel(new ItemStack(Material.BUCKET, 1));
            return;
        }
        int next = fuel.getAmount() - 1;
        if (next <= 0) {
            inventory.setFuel(null);
        } else {
            fuel.setAmount(next);
            inventory.setFuel(fuel);
        }
    }

    void consumeSmeltingInput(FurnaceInventory inventory, ItemStack input) {
        int next = input.getAmount() - 1;
        if (next <= 0) {
            inventory.setSmelting(null);
        } else {
            input.setAmount(next);
            inventory.setSmelting(input);
        }
    }

    boolean canFitResult(FurnaceInventory inventory, ItemStack result) {
        return inventory != null && SfxOutputPolicies.canFitSingle(inventory.getResult(), result);
    }

    void pushFurnaceResult(FurnaceInventory inventory, ItemStack result) {
        ItemStack existing = inventory.getResult();
        if (existing == null || existing.getType().isAir()) {
            inventory.setResult(result.clone());
            return;
        }
        if (existing.isSimilar(result)) {
            existing.setAmount(Math.min(existing.getMaxStackSize(), existing.getAmount() + result.getAmount()));
            inventory.setResult(existing);
        }
    }

    void hydrateVirtualFurnaceFromWorld(Block block, VirtualFurnaceState state) {
        if (block == null || state == null || block.getType() != Material.FURNACE) {
            return;
        }
        BlockState blockState = block.getState();
        if (!(blockState instanceof Furnace furnace)) {
            return;
        }
        hydrateVirtualFurnaceState(state, furnace.getInventory());
    }

    private void hydrateVirtualFurnaceState(VirtualFurnaceState state, Inventory inventory) {
        if (state == null || inventory == null) {
            return;
        }
        ItemStack smelting = null;
        ItemStack fuel = null;
        ItemStack result = null;
        if (inventory instanceof FurnaceInventory furnaceInventory) {
            smelting = furnaceInventory.getSmelting();
            fuel = furnaceInventory.getFuel();
            result = furnaceInventory.getResult();
        } else {
            if (inventory.getSize() > 0) {
                smelting = inventory.getItem(0);
            }
            if (inventory.getSize() > 1) {
                fuel = inventory.getItem(1);
            }
            if (inventory.getSize() > 2) {
                result = inventory.getItem(2);
            }
        }
        state.smelting(smelting);
        state.fuel(fuel);
        state.result(result);
        state.initialized(true);
        state.externalDirty(false);
        state.mirrorDirty(false);
    }

    private void applyVirtualFurnaceStateToInventory(VirtualFurnaceState state, Inventory inventory) {
        if (state == null || inventory == null || !state.initialized()) {
            return;
        }
        if (inventory instanceof FurnaceInventory furnaceInventory) {
            furnaceInventory.setSmelting(cloneSlot(state.smelting()));
            furnaceInventory.setFuel(cloneSlot(state.fuel()));
            furnaceInventory.setResult(cloneSlot(state.result()));
            state.mirrorDirty(false);
            return;
        }
        if (inventory.getSize() > 0) {
            inventory.setItem(0, cloneSlot(state.smelting()));
        }
        if (inventory.getSize() > 1) {
            inventory.setItem(1, cloneSlot(state.fuel()));
        }
        if (inventory.getSize() > 2) {
            inventory.setItem(2, cloneSlot(state.result()));
        }
        state.mirrorDirty(false);
    }

    private void syncVirtualFurnaceWorld(Block block, VirtualFurnaceState state, int cookTimeTotal, boolean force, boolean hasViewers) {
        if (block == null || state == null || block.getType() != Material.FURNACE) {
            return;
        }
        boolean burning = state.burnTimeRemaining() > 0;
        if (!hasViewers && !state.mirrorDirty() && !force) {
            syncFurnaceLitAppearance(block, state, burning);
            return;
        }
        BlockState blockState = block.getState();
        if (!(blockState instanceof Furnace furnace)) {
            return;
        }
        FurnaceInventory inventory = furnace.getInventory();
        if (state.mirrorDirty() || hasViewers) {
            applyVirtualFurnaceStateToInventory(state, inventory);
        }
        syncVirtualFurnaceVisual(furnace, inventory, state, cookTimeTotal, force, hasViewers);
        restoreFurnaceInventory(inventory, state.smelting(), state.fuel(), state.result());
    }

    private void syncVirtualFurnaceVisualAndRestoreInventory(Furnace furnace, FurnaceInventory inventory, VirtualFurnaceState state, int cookTimeTotal, boolean force, boolean hasViewers) {
        ItemStack smelting = cloneSlot(inventory.getSmelting());
        ItemStack fuel = cloneSlot(inventory.getFuel());
        ItemStack result = cloneSlot(inventory.getResult());
        syncVirtualFurnaceVisual(furnace, inventory, state, cookTimeTotal, force, hasViewers);
        
        
        
        restoreFurnaceInventory(inventory, smelting, fuel, result);
    }

    private ItemStack cloneSlot(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.clone();
    }

    private void restoreFurnaceInventory(FurnaceInventory inventory, ItemStack smelting, ItemStack fuel, ItemStack result) {
        inventory.setSmelting(cloneSlot(smelting));
        inventory.setFuel(cloneSlot(fuel));
        inventory.setResult(cloneSlot(result));
    }

    private void syncVirtualFurnaceVisual(Furnace furnace, FurnaceInventory inventory, VirtualFurnaceState state, int cookTimeTotal, boolean force, boolean hasViewers) {
        boolean burning = state.burnTimeRemaining() > 0;

        
        
        
        
        
        int totalCookTime = Math.max(1, cookTimeTotal);
        state.cookTimeTotal(totalCookTime);
        furnace.setCookTimeTotal(totalCookTime);
        if (hasViewers) {
            furnace.setCookTime((short) Math.min(Short.MAX_VALUE, Math.max(0, Math.min(state.cookProgress(), totalCookTime - 1))));
            furnace.setBurnTime((short) Math.min(Short.MAX_VALUE, Math.max(0, state.burnTimeRemaining())));
        } else {
            
            
            
            furnace.setCookTime((short) 0);
            furnace.setBurnTime((short) Math.min(Short.MAX_VALUE, Math.max(0, state.burnTimeRemaining())));
        }
        furnace.update(true, false);

        
        
        syncFurnaceLitAppearance(furnace, burning);
    }

    private void syncFurnaceLitAppearance(Furnace furnace, boolean lit) {
        syncFurnaceLitAppearance(furnace.getBlock(), null, lit);
    }

    private void syncFurnaceLitAppearance(Block block, VirtualFurnaceState state, boolean lit) {
        if (block == null) {
            return;
        }
        if (state != null && state.lastLit() != null && state.lastLit() == lit) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Lightable lightable) || lightable.isLit() == lit) {
            if (state != null) {
                state.lastLit(lit);
            }
            return;
        }
        lightable.setLit(lit);
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(machineRuntime, instanceTypeAt(block.getLocation()), block, lightable, false, "basic", "syncFurnaceLitAppearance");
        if (state != null) {
            state.lastLit(lit);
        }
    }

    String inputKey(ItemStack input) {
        return input.getType().key().toString();
    }

    private boolean canStartCrucible(Block block, boolean water) {
        Material type = block.getType();
        if (type.isAir() || type == Material.WATER || type == Material.LAVA) {
            return true;
        }
        return water && block.getBlockData() instanceof Waterlogged;
    }

    private boolean isCrucibleProcessActive(SfxBlockAnchorKey anchorKey, UUID token) {
        ActiveCrucibleProcess process = activeCrucibles.get(anchorKey);
        return process != null && process.token().equals(token);
    }

    private void clearActiveCrucible(SfxBlockAnchorKey anchorKey, boolean clearOutput) {
        ActiveCrucibleProcess process = activeCrucibles.remove(anchorKey);
        if (process == null || !clearOutput) {
            return;
        }
        Block block = process.outputLocation().getBlock();
        if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, instanceTypeAt(block.getRelative(BlockFace.DOWN).getLocation()), block, Material.AIR, false, "basic", "clearActiveCrucible:clear-output");
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            waterlogged.setWaterlogged(false);
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(machineRuntime, instanceTypeAt(block.getLocation()), block, waterlogged, false, "basic", "clearActiveCrucible:waterlog");
        }
    }

    private void setInstanceState(Block anchorBlock, SfxBlockLifecycleState state, String payload) {
        blockData.findAnchor(anchorBlock.getLocation())
                .ifPresent(anchor -> blockData.updateInstanceState(anchor.instanceId(), payload.getBytes(StandardCharsets.UTF_8), state));
    }

    private void dropPluginBlock(Block block, String typeId) {
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
    }

    private void dropStoredContents(Block block) {
        SfxBlockAnchorKey key = block == null ? null : SfxBlockAnchorKey.fromLocation(block.getLocation());
        VirtualFurnaceState state = key == null ? null : virtualFurnaces.get(key);
        if (state != null && state.initialized()) {
            dropStoredSlot(block, state.smelting());
            dropStoredSlot(block, state.fuel());
            dropStoredSlot(block, state.result());
            return;
        }
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return;
        }
        Inventory inventory = holder.getInventory();
        for (ItemStack content : inventory.getContents()) {
            dropStoredSlot(block, content);
        }
        inventory.clear();
    }

    private void dropStoredSlot(Block block, ItemStack content) {
        if (content == null || content.getType().isAir()) {
            return;
        }
        SfxBlockDrops.dropItem(block, content.clone());
    }

    private FurnaceStats furnaceStatsAt(SfxAnchorRecord anchor) {
        String typeId = instanceType(anchor.instanceId());
        if (typeId == null) {
            return null;
        }
        return furnaceStats(typeId);
    }

    private FurnaceStats furnaceStats(String typeId) {
        return switch (typeId) {
            case "sf:enhanced_furnace" -> new FurnaceStats(2, 1, 1);
            case "sf:enhanced_furnace_2" -> new FurnaceStats(2, 1, 1);
            case "sf:enhanced_furnace_3" -> new FurnaceStats(3, 2, 1);
            case "sf:enhanced_furnace_4" -> new FurnaceStats(3, 3, 1);
            case "sf:enhanced_furnace_5" -> new FurnaceStats(4, 3, 1);
            case "sf:enhanced_furnace_6" -> new FurnaceStats(4, 3, 2);
            case "sf:enhanced_furnace_7" -> new FurnaceStats(5, 3, 2);
            case "sf:enhanced_furnace_8" -> new FurnaceStats(5, 4, 2);
            case "sf:enhanced_furnace_9" -> new FurnaceStats(6, 4, 2);
            case "sf:enhanced_furnace_10" -> new FurnaceStats(7, 4, 2);
            case "sf:enhanced_furnace_11" -> new FurnaceStats(8, 4, 2);
            case "sf:reinforced_furnace" -> new FurnaceStats(10, 5, 3);
            case "sf:carbonado_edged_furnace" -> new FurnaceStats(20, 10, 3);
            default -> null;
        };
    }

    private void playSound(Location location, Sound sound, float volume, float pitch) {
        World world = location.getWorld();
        if (world != null) {
            world.playSound(location.clone().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }


}
