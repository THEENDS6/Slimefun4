package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxAnchoredInteraction;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayMode;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.display.SfxFloatingTextKey;
import cc.theends6.sfx.internal.display.SfxFloatingTextProjection;
import cc.theends6.sfx.internal.gps.SfxGpsElectricBridge;
import cc.theends6.sfx.internal.gps.SfxGpsExtractionResult;
import cc.theends6.sfx.internal.gps.SfxGeoChunkKey;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import cc.theends6.sfx.internal.machine.SfxMachineLegacyHookBridge;
import cc.theends6.sfx.internal.ui.SfxMachineMenuTransactions;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachineExecution;
import cc.theends6.sfx.internal.machine.SfxMachineEffectDispatcher;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseContext;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseResult;
import cc.theends6.sfx.internal.machine.SfxMachineState;
import cc.theends6.sfx.internal.machine.SfxMachineTickSettings;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxEventGuards;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxInventorySlots;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
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

public final class SfxElectricMachineService implements Listener {
    private static final int INVENTORY_SIZE = 45;
    private static final long FLUSH_INTERVAL = 20L;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    final SfxBlockDataService blockData;
    private final SfxPlayerDataService profiles;
    final SfxElectricMachineRegistry registry;
    private final SfxElectricMachineMenuRenderer menuRenderer;
    private final SfxSimpleIoMachineMenuRenderer simpleIoMenuRenderer;
    private final SfxElectricAssemblerMenuRenderer assemblerMenuRenderer;
    private final SfxGeoMinerMachineMenuRenderer geoMinerMenuRenderer;
    private final SfxAutoBrewerMenuRenderer autoBrewerMenuRenderer;
    private final SfxAutoCrafterMenuRenderer autoCrafterMenuRenderer;
    private final SfxVirtualContainerService virtualContainers;
    private final SfxFloatingTextDisplayService floatingTextDisplays;
    final SfxElectricRecipeProcessor recipeProcessor;
    final Map<UUID, SfxElectricMachineState> stateCache = new ConcurrentHashMap<>();
    final Set<UUID> dirtyInstances = ConcurrentHashMap.newKeySet();
    final Set<UUID> activeInstances = ConcurrentHashMap.newKeySet();
    final Map<UUID, Integer> recentEnergyConsumption = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLogicTicks = new ConcurrentHashMap<>();
    final Map<UUID, Integer> supplementalEnergyThisSecond = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> supplementalEnergyAveragePerTick = new ConcurrentHashMap<>();
    private volatile long supplementalEnergyWindow = -1L;
    private final SfxMachineTickSettings tickSettings;
    final SfxMachineRuntimeEngine machineRuntime;
    private final Map<UUID, SfxElectricMachineSession> sessionsByViewer = new ConcurrentHashMap<>();
    final Map<UUID, SfxElectricMachineSession> sessionsByInstance = new ConcurrentHashMap<>();
    private volatile boolean running;
    private volatile long tickCounter;

    public SfxElectricMachineService(
            JavaPlugin plugin,
            SfxRuntime runtime,
            SfxItems items,
            SfxLocalization localization,
            SfxBlockDataService blockData,
            SfxPlayerDataService profiles,
            DefaultManualMachineRegistry manualMachines,
            SfxVirtualContainerService virtualContainers,
            SfxFloatingTextDisplayService floatingTextDisplays,
            SfxMachineRuntimeEngine sharedMachineRuntime
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.tickSettings = SfxMachineTickSettings.from(plugin.getConfig());
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.virtualContainers = Objects.requireNonNull(virtualContainers, "virtualContainers");
        this.floatingTextDisplays = Objects.requireNonNull(floatingTextDisplays, "floatingTextDisplays");
        this.registry = SfxElectricMachineDefinitions.create(plugin, items, manualMachines, blockData, virtualContainers);
        this.machineRuntime = sharedMachineRuntime == null ? new SfxMachineRuntimeEngine() : sharedMachineRuntime;
        this.machineRuntime.registerDefinitions(SfxElectricMachineFrameworkBridge.definitions(this.registry));
        registerFrameworkEffects();
        this.menuRenderer = new SfxElectricMachineMenuRenderer(items, localization, profiles);
        this.simpleIoMenuRenderer = new SfxSimpleIoMachineMenuRenderer(items, localization, profiles);
        this.assemblerMenuRenderer = new SfxElectricAssemblerMenuRenderer(items, localization, profiles);
        this.geoMinerMenuRenderer = new SfxGeoMinerMachineMenuRenderer(items, localization, profiles);
        this.autoBrewerMenuRenderer = new SfxAutoBrewerMenuRenderer(items, localization, profiles);
        this.autoCrafterMenuRenderer = new SfxAutoCrafterMenuRenderer(items, localization, profiles);
        this.recipeProcessor = new SfxElectricRecipeProcessor(items);
        bootstrapLoadedStates();
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        scheduleTick();
        scheduleFlush();
    }

    private void registerFrameworkEffects() {
        for (String effectName : List.of(
                "recipe:resolve-operation",
                "inventory:reserve-output",
                "inventory:commit-output",
                "brew:validate-potions",
                "brew:refund-on-interrupt",
                "brew:commit-multi-bottle-output",
                "crafting:simulate",
                "crafting:commit-transaction",
                "gps:check-signal-and-scan",
                "geo:extract-resource",
                "visual:update-floating-text",
                "fluid:locate-source",
                "fluid:remove-source-and-update",
                "meta:validate-input",
                "meta:apply-transform",
                "xp:collect-orbs",
                "electric:special-tick",
                "electric:world-action"
        )) {
            machineRuntime.registerEffectHook(effectName, context -> frameworkElectricEffect(effectName, context));
        }
    }

    private SfxMachinePhaseResult frameworkElectricEffect(String effectName, SfxMachinePhaseContext phaseContext) {
        SfxElectricMachineDefinition definition = phaseContext.attachment("electric.definition", SfxElectricMachineDefinition.class).orElse(null);
        SfxElectricMachineState state = phaseContext.attachment("electric.state", SfxElectricMachineState.class).orElse(null);
        if (definition == null || state == null) {
            return SfxMachinePhaseResult.cont();
        }
        phaseContext.put("electric.framework.effect", effectName);
        switch (effectName) {
            case "brew:validate-potions", "crafting:simulate", "gps:check-signal-and-scan", "fluid:locate-source", "meta:validate-input", "xp:collect-orbs", "electric:special-tick", "electric:world-action" -> {
                if (definition.recipeProvider().hasWorldAction() || definition.recipeProvider().hasSpecialTick()) {
                    return frameworkRunSpecialOperation(phaseContext, effectName);
                }
                return SfxMachinePhaseResult.cont();
            }
            case "recipe:resolve-operation" -> {
                SfxElectricRecipe active = recipeProcessor.activeRecipe(definition, state);
                phaseContext.put("electric.activeRecipe", active);
                if (active == null) {
                    SfxElectricRecipeMatch match = recipeProcessor.findRecipeMatch(definition, state);
                    phaseContext.put("electric.recipeMatch", match);
                }
                return SfxMachinePhaseResult.cont();
            }
            case "inventory:reserve-output" -> {
                SfxElectricRecipeMatch match = phaseContext.attachment("electric.recipeMatch", SfxElectricRecipeMatch.class).orElse(null);
                SfxElectricRecipe recipe = phaseContext.attachment("electric.activeRecipe", SfxElectricRecipe.class).orElse(null);
                if (match != null) recipe = match.recipe();
                if (recipe != null) {
                    boolean fits = recipeProcessor.canFitOutputForRecipe(definition, state, recipe);
                    phaseContext.put("electric.outputFit", fits);
                    if (!fits) {
                        return SfxMachinePhaseResult.blocked(cc.theends6.sfx.internal.machine.SfxMachineStatus.OUTPUT_FULL, "electric framework output reservation failed");
                    }
                }
                return SfxMachinePhaseResult.cont();
            }
            case "inventory:commit-output", "brew:commit-multi-bottle-output", "crafting:commit-transaction", "geo:extract-resource", "fluid:remove-source-and-update", "meta:apply-transform" -> {
                phaseContext.put("electric.output.committed.by", effectName);
                return SfxMachinePhaseResult.cont();
            }
            case "brew:refund-on-interrupt" -> {
                phaseContext.put("electric.interrupt.refund.requested", Boolean.TRUE);
                return SfxMachinePhaseResult.cont();
            }
            case "visual:update-floating-text" -> {
                SfxElectricMachineRenderStatus status = phaseContext.attachment("electric.renderStatus", SfxElectricMachineRenderStatus.class).orElse(null);
                if ("sf:geo_miner".equals(definition.id()) && phaseContext.location() != null) {
                    updateGeoMinerFloatingText(phaseContext.location(), state, status == null ? retainedSpecialStatus(definition, state, null) : status);
                    phaseContext.put("electric.visual.geo.updated", Boolean.TRUE);
                }
                return SfxMachinePhaseResult.cont();
            }
            default -> {
                return SfxMachinePhaseResult.cont();
            }
        }
    }

    private SfxMachinePhaseResult frameworkRunSpecialOperation(SfxMachinePhaseContext phaseContext, String effectName) {
        SfxElectricMachineDefinition definition = phaseContext.attachment("electric.definition", SfxElectricMachineDefinition.class).orElse(null);
        SfxElectricMachineState state = phaseContext.attachment("electric.state", SfxElectricMachineState.class).orElse(null);
        SfxElectricMachineSession session = phaseContext.attachment("electric.session", SfxElectricMachineSession.class).orElse(null);
        if (definition == null || state == null) {
            return SfxMachinePhaseResult.cont();
        }
        Location location = phaseContext.location();
        SfxMachineTickContext context = phaseContext.tickContext();
        if (location == null || context == null) {
            return SfxMachinePhaseResult.blocked(cc.theends6.sfx.internal.machine.SfxMachineStatus.BLOCKED, "missing location/context");
        }
        SfxElectricMachineTickResult customResult = null;
        if (definition.recipeProvider().hasWorldAction()) {
            customResult = definition.recipeProvider().tickWorldAction(plugin, items, definition, state, location);
        } else if (definition.recipeProvider().hasSpecialTick()) {
            int interval = Math.max(1, definition.recipeProvider().specialTickIntervalTicks());
            if (state.hasProgress() || interval <= 1 || tickCounter % interval == 0L) {
                customResult = definition.recipeProvider().tickSpecial(plugin, items, definition, state, location, context);
            } else {
                SfxElectricMachineRenderStatus status = retainedSpecialStatus(definition, state, session);
                customResult = new SfxElectricMachineTickResult(status, 0, false, true);
            }
        }
        if (customResult == null) {
            return SfxMachinePhaseResult.cont();
        }
        phaseContext.put("electric.specialResult", customResult);
        phaseContext.put("electric.special.executed.by", effectName);
        return SfxMachinePhaseResult.complete(SfxElectricMachineFrameworkBridge.status(customResult.status()), "electric special operation executed through framework effect");
    }

    Map<String, Object> electricFrameworkAttributes(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineSession session) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("electric.definition", definition);
        attributes.put("electric.state", state);
        if (session != null) attributes.put("electric.session", session);
        attributes.put("electric.recipeProcessor", recipeProcessor);
        attributes.put("electric.service", this);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkElectricEffect);
        return attributes;
    }

    SfxElectricMachineTickResult runFrameworkSpecialOperation(UUID instanceId, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineSession session, Location location, SfxMachineTickContext context, Map<String, Object> attributes) {
        if (!definition.recipeProvider().hasWorldAction() && !definition.recipeProvider().hasSpecialTick()) {
            return null;
        }
        SfxMachinePhaseResult before = machineRuntime.runPhase(definition.id(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, instanceId, location, context, null, cc.theends6.sfx.internal.machine.SfxMachineStatus.IDLE, attributes);
        if (!cc.theends6.sfx.internal.machine.SfxMachinePipelineGuard.proceed(before, attributes, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.BLOCKED_OUTPUT, true);
        }
        Object result = attributes.get("electric.specialResult");
        return result instanceof SfxElectricMachineTickResult tickResult ? tickResult : null;
    }

    void runFrameworkOutputCommit(UUID instanceId, SfxElectricMachineDefinition definition, Location location, SfxMachineTickContext context, Map<String, Object> attributes, SfxElectricMachineRenderStatus status) {
        if (definition == null || location == null || context == null) {
            return;
        }
        if (status != null) {
            attributes.put("electric.renderStatus", status);
        }
        SfxMachinePhaseResult afterOutput = machineRuntime.runPhase(definition.id(), SfxMachinePhase.AFTER_OUTPUT, instanceId, location, context, null, SfxElectricMachineFrameworkBridge.status(status == null ? SfxElectricMachineRenderStatus.IDLE : status), attributes);
        cc.theends6.sfx.internal.machine.SfxMachinePipelineGuard.proceed(afterOutput, attributes, SfxMachinePhase.AFTER_OUTPUT.name());
    }

    public boolean supportsType(String typeId) {
        return registry.contains(typeId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            if (!registry.contains(marker.itemId())) {
                return;
            }
            UUID instanceId = blockData.findAnchor(event.getBlockPlaced().getLocation())
                    .map(SfxAnchorRecord::instanceId)
                    .orElseGet(() -> blockData.registerSingleBlock(
                            marker.itemId(),
                            event.getBlockPlaced().getLocation(),
                            event.getBlockPlaced().getType(),
                            event.getPlayer().getUniqueId()));
            stateCache.putIfAbsent(instanceId, SfxElectricMachineState.empty());
            SfxMachineLegacyHookBridge.place(machineRuntime, marker.itemId(), instanceId, event.getBlockPlaced().getLocation(), "electric", "SfxElectricMachineService.onPlace");
            if ("sf:fluid_pump".equals(marker.itemId())) {
                SfxAreaElectricMachineProviders.warmFluidPumpPoolCache(plugin, event.getBlockPlaced().getLocation());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<SfxAnchorRecord> optional = blockData.findAnchor(event.getBlock().getLocation());
        if (optional.isEmpty()) {
            return;
        }
        SfxAnchorRecord anchor = optional.get();
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null || !registry.contains(instance.typeId())) {
            return;
        }

        event.setDropItems(false);
        destroyAnchoredBlock(event.getBlock(), instance.instanceId(), instance.typeId());
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (block == null || instanceId == null || typeId == null || !registry.contains(typeId)) {
            return;
        }
        SfxElectricMachineSession session = sessionsByInstance.remove(instanceId);
        if (session != null) {
            sessionsByViewer.remove(session.viewerId());
            syncSessionState(session);
            Player viewer = plugin.getServer().getPlayer(session.viewerId());
            if (viewer != null) {
                runtime.executeForPlayer(viewer, viewer::closeInventory);
            }
        }

        if ("sf:geo_miner".equals(typeId)) {
            removeGeoMinerFloatingText(block.getLocation());
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxElectricMachineState state = stateCache.get(instanceId);
        if (state == null && instance != null) {
            state = currentState(instanceId, instance);
        }
        if (state == null) {
            state = SfxElectricMachineState.empty();
        }
        SfxElectricMachineDefinition destroyDefinition = registry.definition(typeId).orElse(null);
        if (destroyDefinition != null) {
            Location destroyLocation = block.getLocation();
            for (SfxElectricStack extraDrop : destroyDefinition.recipeProvider().dropsOnDestroy(plugin, items, destroyDefinition, state, destroyLocation)) {
                dropStack(block, extraDrop);
            }
        }
        for (int slot = 0; slot < state.inputCapacity(); slot++) {
            dropStack(block, state.input(slot));
        }
        for (int slot = 0; slot < state.outputCapacity(); slot++) {
            dropStack(block, state.output(slot));
        }
        for (SfxElectricStack reservedInput : state.reservedInputs()) {
            dropStack(block, reservedInput);
        }
        dropStack(block, state.pendingOutput());
        dropPluginBlock(block, typeId);
        stateCache.remove(instanceId);
        dirtyInstances.remove(instanceId);
        activeInstances.remove(instanceId);
        lastLogicTicks.remove(instanceId);
        blockData.unregisterAt(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        SfxAnchoredInteraction interaction = SfxAnchoredInteraction.resolve(event, blockData);
        if (interaction == null || !registry.contains(interaction.instance().typeId())) {
            return;
        }
        SfxBlockInstanceRecord instance = interaction.instance();
        SfxMachineLegacyHookBridge.interact(machineRuntime, instance.typeId(), instance.instanceId(), interaction.block().getLocation(), "electric", "SfxElectricMachineService.onInteract");
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        boolean autoCrafter = definition != null && definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER;
        boolean autoCrafterSelection = autoCrafter
                && event.getPlayer().isSneaking()
                && event.getItem() != null
                && !event.getItem().getType().isAir();
        if (!autoCrafterSelection && SfxInteractionRules.prefersBlockPlacement(items, event)) {
            return;
        }
        SfxEventGuards.denyBlockAndItemUse(event);
        if (definition != null && definition.menuStyle() == SfxElectricMachineMenuStyle.NONE) {
            return;
        }
        if (autoCrafterSelection) {
            runtime.executeForPlayer(event.getPlayer(), () -> openAutoCrafterSelection(event.getPlayer(), instance, definition, event.getItem(), 0));
        } else if (autoCrafter && (currentState(instance.instanceId(), instance).activeRecipeKey() == null || currentState(instance.instanceId(), instance).activeRecipeKey().isBlank())) {
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text("electric-ui.auto-crafter.select-a-recipe", "<yellow>Sneak-right-click while holding a target item to select a recipe.</yellow>")));
        } else {
            runtime.executeForPlayer(event.getPlayer(), () -> openMachine(event.getPlayer(), instance));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof SfxAutoCrafterSelectionHolder selectionHolder) {
            event.setCancelled(true);
            handleAutoCrafterSelectionClick(player, selectionHolder, event.getRawSlot());
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof SfxElectricMachineHolder holder)) {
            return;
        }
        if (SfxMachineMenuTransactions.isCreativeCloneClick(player, event)) {
            return;
        }
        SfxElectricMachineDefinition clickDefinition = definitionFor(holder.instanceId());
        if (clickDefinition != null) {
            SfxMachineLegacyHookBridge.menuClick(machineRuntime, clickDefinition.id(), holder.instanceId(), null, "electric", "SfxElectricMachineService.onInventoryClick");
        }
        if (clickDefinition == null) {
            event.setCancelled(true);
            return;
        }
        boolean topSlot = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (topSlot && clickDefinition.menuStyle() != SfxElectricMachineMenuStyle.AUTO_CRAFTER) {
            boolean managedInput = contains(clickDefinition.inputSlots(), event.getRawSlot());
            boolean managedOutput = contains(clickDefinition.outputSlots(), event.getRawSlot());
            Predicate<ItemStack> validator = stack -> isValidMachineInput(clickDefinition, event.getRawSlot(), stack);
            if (SfxMachineMenuTransactions.handleManagedHotbarOrOffhand(event, event.getView().getTopInventory(), event.getRawSlot(), player, managedInput, managedOutput, validator)) {
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
                return;
            }
            if ((managedInput || managedOutput) && SfxMachineMenuTransactions.handleManagedDoubleClick(event, event.getView().getTopInventory(), player, slot -> contains(managedOutput ? clickDefinition.outputSlots() : clickDefinition.inputSlots(), slot))) {
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
                return;
            }
        }
        if (clickDefinition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER) {
            event.setCancelled(true);
            if (topSlot) {
                handleAutoCrafterButton(holder.instanceId(), event.getRawSlot(), event.getClick());
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            }
            return;
        }
        if (topSlot && SfxMachineMenuTransactions.cancelUnsupportedManagedClick(event)) {
            return;
        }
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
        if (topSlot && clickDefinition.menuStyle() == SfxElectricMachineMenuStyle.ASSEMBLER && isAssemblerButton(event.getRawSlot())) {
            event.setCancelled(true);
            handleAssemblerButton(holder.instanceId(), event.getRawSlot(), event.getClick());
            runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            return;
        }
        if (topSlot && contains(clickDefinition.outputSlots(), event.getRawSlot())) {
            if (!SfxMachineMenuTransactions.isTakingFromOutput(event)) {
                event.setCancelled(true);
                return;
            }
            if (SfxMachineMenuTransactions.dropFromTopSlot(event, event.getView().getTopInventory(), event.getRawSlot(), player)) {
                event.setCancelled(true);
            }
            runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            return;
        }
        if (topSlot && !contains(clickDefinition.inputSlots(), event.getRawSlot())) {
            event.setCancelled(true);
            return;
        }
        if (topSlot && clickDefinition.menuStyle() == SfxElectricMachineMenuStyle.ASSEMBLER) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isValidAssemblerInput(clickDefinition, event.getRawSlot(), cursor)) {
                event.setCancelled(true);
                return;
            }
        }
        if (topSlot && clickDefinition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_BREWER) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isValidAutoBrewerInput(event.getRawSlot(), cursor)) {
                event.setCancelled(true);
                return;
            }
        }
        if (topSlot && event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            return;
        }
        if (topSlot && SfxMachineMenuTransactions.dropFromTopSlot(event, event.getView().getTopInventory(), event.getRawSlot(), player)) {
            event.setCancelled(true);
            runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            return;
        }
        if (topSlot && SfxMachineMenuTransactions.cancelUnsupportedManagedClick(event)) {
            return;
        }
        runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SfxAutoCrafterSelectionHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxElectricMachineHolder holder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        SfxElectricMachineDefinition dragDefinition = definitionFor(holder.instanceId());
        if (dragDefinition == null) {
            event.setCancelled(true);
            return;
        }
        if (dragDefinition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER) {
            event.setCancelled(true);
            return;
        }
        boolean valid = event.getRawSlots().stream()
                .filter(slot -> slot < topSize)
                .allMatch(slot -> contains(dragDefinition.inputSlots(), slot)
                        && (dragDefinition.menuStyle() != SfxElectricMachineMenuStyle.ASSEMBLER
                        || isValidAssemblerInput(dragDefinition, slot, event.getNewItems().get(slot)))
                        && (dragDefinition.menuStyle() != SfxElectricMachineMenuStyle.AUTO_BREWER
                        || isValidAutoBrewerInput(slot, event.getNewItems().get(slot))));
        if (!valid) {
            event.setCancelled(true);
            return;
        }
        runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SfxElectricMachineHolder holder)) {
            return;
        }
        SfxElectricMachineDefinition closeDefinition = definitionFor(holder.instanceId());
        if (closeDefinition != null) {
            SfxMachineLegacyHookBridge.menuClose(machineRuntime, closeDefinition.id(), holder.instanceId(), null, "electric", "SfxElectricMachineService.onInventoryClose");
        }
        SfxElectricMachineSession session = sessionsByInstance.remove(holder.instanceId());
        if (session == null) {
            return;
        }
        sessionsByViewer.remove(session.viewerId());
        syncSessionState(session);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        SfxElectricMachineSession session = sessionsByViewer.remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        sessionsByInstance.remove(session.instanceId());
        syncSessionState(session);
    }

    public void shutdown() {
        running = false;
        for (SfxElectricMachineSession session : List.copyOf(sessionsByViewer.values())) {
            syncSessionState(session);
            Player player = plugin.getServer().getPlayer(session.viewerId());
            if (player != null) {
                runtime.executeForPlayer(player, player::closeInventory);
            }
        }
        flushDirty();
        sessionsByViewer.clear();
        sessionsByInstance.clear();
        stateCache.clear();
        dirtyInstances.clear();
        activeInstances.clear();
        lastLogicTicks.clear();
        recentEnergyConsumption.clear();
        supplementalEnergyThisSecond.clear();
        supplementalEnergyAveragePerTick.clear();
        supplementalEnergyWindow = -1L;
    }

    private void bootstrapLoadedStates() {
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !registry.contains(instance.typeId())) {
                continue;
            }
            SfxElectricMachineState state = SfxElectricMachineState.decode(instance.stateBlob());
            stateCache.put(instance.instanceId(), state);
            activeInstances.add(instance.instanceId());
        }
    }

    public int consumerCapacity(String typeId) {
        return registry.definition(typeId).map(SfxElectricMachineDefinition::energyCapacity).orElse(0);
    }

    public int consumerStoredEnergy(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !registry.contains(instance.typeId())) {
            return 0;
        }
        return currentState(instanceId, instance).storedEnergy();
    }

    public int drainRecentEnergyConsumption(Collection<UUID> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (UUID instanceId : instanceIds) {
            Integer consumed = recentEnergyConsumption.remove(instanceId);
            if (consumed != null && consumed > 0) {
                total += consumed;
            }
        }
        return total;
    }

    public int requestedEnergyConsumption(Collection<UUID> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return 0;
        }
        refreshSupplementalEnergyAverages();
        int total = 0;
        for (UUID instanceId : instanceIds) {
            total += requestedEnergyConsumption(instanceId);
            total += supplementalEnergyAveragePerTick.getOrDefault(instanceId, 0);
        }
        return total;
    }

    private void refreshSupplementalEnergyAverages() {
        long window = Math.max(0L, tickCounter / 20L);
        if (window == supplementalEnergyWindow) {
            return;
        }
        supplementalEnergyWindow = window;
        supplementalEnergyAveragePerTick.clear();
        for (Map.Entry<UUID, Integer> entry : supplementalEnergyThisSecond.entrySet()) {
            int total = Math.max(0, entry.getValue());
            if (total > 0) {
                supplementalEnergyAveragePerTick.put(entry.getKey(), (total + 19) / 20);
            }
        }
        supplementalEnergyThisSecond.clear();
    }

    private int requestedEnergyConsumption(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return 0;
        }
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        if (definition == null || definition.energyConsumptionPerTick() <= 0) {
            return 0;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        if (!state.enabled()) {
            return 0;
        }
        Location location = locationFor(instance);
        if (definition.recipeProvider().hasWorldAction()) {
            return location == null ? 0 : definition.recipeProvider().requestedEnergyConsumption(plugin, items, definition, state, location);
        }
        if (location != null && definition.recipeProvider().hasSpecialTick()) {
            int customRequest = definition.recipeProvider().requestedEnergyConsumption(plugin, items, definition, state, location);
            if (customRequest > 0) {
                return customRequest;
            }
        }
        if (state.hasPendingOutput()) {
            return 0;
        }
        SfxElectricRecipe activeRecipe = recipeProcessor.activeRecipe(definition, state);
        if (activeRecipe != null && state.hasReservedInput()) {
            if (state.progressWork() >= recipeProcessor.requiredWork(activeRecipe)
                    && !recipeProcessor.canFitCompletionOutputForRecipe(definition, state, activeRecipe)) {
                return 0;
            }
            return definition.energyConsumptionPerTick();
        }
        SfxElectricRecipeMatch match = recipeProcessor.findRecipeMatch(definition, state);
        if (match == null) {
            return 0;
        }
        if (!recipeProcessor.canFitOutputForRecipe(definition, state, match.recipe())) {
            return 0;
        }
        return definition.energyConsumptionPerTick();
    }

    public int chargeConsumer(UUID instanceId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return 0;
        }
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        if (definition == null || definition.energyCapacity() <= 0) {
            return 0;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        int accepted = Math.max(0, Math.min(amount, definition.energyCapacity() - state.storedEnergy()));
        if (accepted <= 0) {
            return 0;
        }
        state.storedEnergy(state.storedEnergy() + accepted);
        dirtyInstances.add(instanceId);
        activeInstances.add(instanceId);
        return accepted;
    }

    public void wakeGeoExtractorsInChunk(SfxGeoChunkKey key) {
        if (key == null) {
            return;
        }
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            if (anchor == null || anchor.key() == null) {
                continue;
            }
            if (!key.worldId().equals(anchor.key().worldId())
                    || key.chunkX() != (anchor.key().x() >> 4)
                    || key.chunkZ() != (anchor.key().z() >> 4)) {
                continue;
            }
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || (!"sf:geo_miner".equals(instance.typeId()) && !"sf:oil_pump".equals(instance.typeId()))) {
                continue;
            }
            UUID instanceId = instance.instanceId();
            activeInstances.add(instanceId);
            lastLogicTicks.remove(instanceId);
            Location location = locationFor(instance);
            if (location != null) {
                boolean hasViewers = sessionsByInstance.containsKey(instanceId);
                runtime.executeAt(location, () -> tickMachine(instanceId, new SfxMachineTickContext(tickCounter, 1L, hasViewers)));
            }
        }
    }

    private void scheduleTick() {
        runtime.executeGlobalLater(1L, () -> {
            if (!running) {
                return;
            }
            tickCounter++;
            for (UUID instanceId : List.copyOf(activeInstances)) {
                SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
                if (instance == null) {
                    activeInstances.remove(instanceId);
                    lastLogicTicks.remove(instanceId);
                    continue;
                }
                Location location = locationFor(instance);
                if (location == null) {
                    continue;
                }
                boolean hasViewers = sessionsByInstance.containsKey(instanceId);
                long lastTick = lastLogicTicks.getOrDefault(instanceId, 0L);
                int interval = tickSettings.intervalFor(hasViewers);
                if (lastTick > 0L && tickCounter - lastTick < interval) {
                    continue;
                }
                long elapsedTicks = lastTick <= 0L ? 1L : Math.max(1L, tickCounter - lastTick);
                lastLogicTicks.put(instanceId, tickCounter);
                SfxMachineTickContext context = new SfxMachineTickContext(tickCounter, elapsedTicks, hasViewers);
                runtime.executeAt(location, () -> tickMachine(instanceId, context));
            }
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

    private void flushDirty() {
        for (UUID instanceId : List.copyOf(dirtyInstances)) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            SfxElectricMachineState state = stateCache.get(instanceId);
            if (instance == null || state == null) {
                dirtyInstances.remove(instanceId);
                continue;
            }
            SfxBlockLifecycleState lifecycle = state.hasProgress() ? SfxBlockLifecycleState.ACTIVE : SfxBlockLifecycleState.IDLE;
            blockData.updateInstanceState(instanceId, state.encode(), lifecycle);
            dirtyInstances.remove(instanceId);
        }
    }

    private void tickMachine(UUID instanceId, SfxMachineTickContext context) {
        SfxElectricMachineTickController.tickMachine(this, instanceId, context);
    }


    SfxElectricMachineRenderStatus completeActiveRecipe(
            UUID instanceId,
            SfxElectricMachineState state,
            SfxElectricRecipe activeRecipe,
            SfxElectricMachineDefinition definition,
            SfxElectricMachineSession session,
            Location location,
            SfxMachineTickContext context,
            Map<String, Object> frameworkAttributes
    ) {
        List<SfxElectricStack> recipeOutputs = state.activeOutputs().isEmpty()
                ? activeRecipe.outputs()
                : state.activeOutputs();
        int[] outputSlots = recipeProcessor.findCompletionOutputSlots(definition, state, activeRecipe, recipeOutputs);
        if (outputSlots == null) {
            activeInstances.add(instanceId);
            return SfxElectricMachineRenderStatus.BLOCKED_OUTPUT;
        }
        recipeProcessor.pushOutputs(state, outputSlots, recipeOutputs);
        state.resetProgress();
        runFrameworkOutputCommit(instanceId, definition, location, context, frameworkAttributes, SfxElectricMachineRenderStatus.WORKING);
        SfxElectricRecipeStart nextStart = recipeProcessor.tryStartNextRecipe(definition, state);
        if (nextStart != null) {
            activeInstances.add(instanceId);
            playCompleteSound(session);
            return SfxElectricMachineRenderStatus.WORKING;
        }
        playCompleteSound(session);
        return state.hasAnyInput() ? recipeProcessor.deriveStatus(definition, state) : SfxElectricMachineRenderStatus.IDLE;
    }


    private void openMachine(Player player, SfxBlockInstanceRecord instance) {
        SfxMachineLegacyHookBridge.menuOpen(machineRuntime, instance.typeId(), instance.instanceId(), locationFor(instance), "electric", "SfxElectricMachineService.openMachine");
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        if (definition == null || definition.menuStyle() == SfxElectricMachineMenuStyle.NONE) {
            return;
        }
        SfxElectricMachineSession existing = sessionsByInstance.get(instance.instanceId());
        if (existing != null && !existing.viewerId().equals(player.getUniqueId())) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.busy", "<red>This machine is already open.</red>")));
            return;
        }

        SfxElectricMachineSession previous = sessionsByViewer.remove(player.getUniqueId());
        if (previous != null) {
            sessionsByInstance.remove(previous.instanceId());
            syncSessionState(previous);
        }

        SfxElectricMachineState state = currentState(instance.instanceId(), instance);
        Component title = localization.itemName(definition.id(), Text.renderFlexible(definition.title()));
        Inventory inventory = plugin.getServer().createInventory(new SfxElectricMachineHolder(instance.instanceId()), definition.menuStyle().inventorySize(), title);
        SfxElectricMachineSession session = new SfxElectricMachineSession(player.getUniqueId(), instance.instanceId(), inventory);
        sessionsByViewer.put(player.getUniqueId(), session);
        sessionsByInstance.put(instance.instanceId(), session);
        activeInstances.add(instance.instanceId());
        render(session, definition, inventory, state, recipeProcessor.activeRecipe(definition, state), sessionRenderStatus(definition, state, session));
        player.openInventory(inventory);
    }

    private void refreshSession(UUID instanceId) {
        SfxElectricMachineSession session = sessionsByInstance.get(instanceId);
        if (session == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        if (definition == null) {
            return;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        syncInventoryToState(session.inventory(), state);
        dirtyInstances.add(instanceId);
        activeInstances.add(instanceId);
        render(session, definition, session.inventory(), state, recipeProcessor.activeRecipe(definition, state), sessionRenderStatus(definition, state, session));
    }

    private SfxElectricMachineRenderStatus sessionRenderStatus(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineSession session) {
        if (!state.enabled()) {
            return SfxElectricMachineRenderStatus.PAUSED;
        }
        if (definition.recipeProvider().hasSpecialTick() || definition.recipeProvider().hasWorldAction()) {
            return retainedSpecialStatus(definition, state, session);
        }
        return recipeProcessor.deriveStatus(definition, state);
    }

    SfxElectricMachineState currentState(UUID instanceId, SfxBlockInstanceRecord instance) {
        return stateCache.computeIfAbsent(instanceId, ignored -> SfxElectricMachineState.decode(instance.stateBlob()));
    }

    private SfxElectricMachineRenderStatus retainedSpecialStatus(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricMachineSession session) {
        if (!state.enabled()) {
            return SfxElectricMachineRenderStatus.PAUSED;
        }
        if (definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER) {
            if (state.progressWork() > 0) {
                return SfxElectricMachineRenderStatus.WORKING;
            }
            if (state.activeRecipeKey() == null || state.activeRecipeKey().isBlank()) {
                return SfxElectricMachineRenderStatus.NO_RECIPE;
            }
            return session != null && session.lastRenderedStatus() != null ? session.lastRenderedStatus() : SfxElectricMachineRenderStatus.IDLE;
        }
        if (state.hasProgress()) {
            if (definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_BREWER) {
                int remainingWork = Math.max(0, state.activeBaseTicks() - state.progressWork());
                int requiredFuel = Math.min(Math.max(1, definition.speed()), Math.max(1, remainingWork));
                if (state.specialData() < requiredFuel) {
                    return SfxElectricMachineRenderStatus.NO_BLAZE_FUEL;
                }
            }
            return SfxElectricMachineRenderStatus.WORKING;
        }
        if (definition.id().equals("sf:xp_collector")) {
            if (session != null && session.lastRenderedStatus() == SfxElectricMachineRenderStatus.OUTPUT_FULL) {
                return SfxElectricMachineRenderStatus.OUTPUT_FULL;
            }
            return SfxElectricMachineRenderStatus.IDLE;
        }
        if (definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_BREWER) {
            return SfxElectricMachineRenderStatus.IDLE;
        }
        if (definition.inputSlots().length > 0 && !state.hasAnyInput()) {
            return SfxElectricMachineRenderStatus.NO_INPUT;
        }
        if (session != null && session.lastRenderedStatus() != null) {
            return session.lastRenderedStatus();
        }
        return SfxElectricMachineRenderStatus.IDLE;
    }


    public SfxVirtualContainerService.PlannedStack planFirstCargoOutput(UUID instanceId, Predicate<ItemStack> filter, int maxAmount) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxElectricMachineDefinition definition = definitionForInstance(instance);
        if (definition == null || definition.outputSlots().length <= 0) {
            return new SfxVirtualContainerService.PlannedStack(null, List.of());
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        int limit = Math.max(1, maxAmount);
        for (int slot = 0; slot < definition.outputSlots().length; slot++) {
            SfxElectricStack electricStack = state.output(slot);
            ItemStack stack = electricStack == null ? null : electricStack.toItemStack(items);
            if (SfxElectricCargoInventoryOps.isEmpty(stack) || (filter != null && !filter.test(stack))) {
                continue;
            }
            int amount = Math.max(1, Math.min(Math.min(limit, stack.getMaxStackSize()), stack.getAmount()));
            ItemStack planned = stack.clone();
            planned.setAmount(amount);
            ItemStack template = stack.clone();
            template.setAmount(1);
            return new SfxVirtualContainerService.PlannedStack(planned, List.of(new SfxVirtualContainerService.SlotTake(slot, template, amount)));
        }
        return new SfxVirtualContainerService.PlannedStack(null, List.of());
    }

    public List<SfxVirtualContainerService.PlannedStack> planCargoOutputBatch(UUID instanceId, Predicate<ItemStack> filter, int maxItems, int maxDistinctTypes, boolean allowMultipleSlots) {
        List<SfxVirtualContainerService.PlannedStack> result = new ArrayList<>();
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxElectricMachineDefinition definition = definitionForInstance(instance);
        if (definition == null || definition.outputSlots().length <= 0 || maxItems <= 0) {
            return result;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        int remaining = Math.min(128, maxItems);
        Map<String, ItemStack> grouped = new LinkedHashMap<>();
        Map<String, List<SfxVirtualContainerService.SlotTake>> takes = new LinkedHashMap<>();
        for (int slot = 0; slot < definition.outputSlots().length && remaining > 0; slot++) {
            SfxElectricStack electricStack = state.output(slot);
            ItemStack stack = electricStack == null ? null : electricStack.toItemStack(items);
            if (SfxElectricCargoInventoryOps.isEmpty(stack) || (filter != null && !filter.test(stack))) {
                continue;
            }
            String key = cargoItemKey(stack);
            if (!grouped.containsKey(key) && grouped.size() >= Math.max(1, maxDistinctTypes)) {
                continue;
            }
            int amount = Math.min(stack.getAmount(), remaining);
            ItemStack batchStack = grouped.computeIfAbsent(key, ignored -> {
                ItemStack clone = stack.clone();
                clone.setAmount(0);
                return clone;
            });
            batchStack.setAmount(batchStack.getAmount() + amount);
            ItemStack template = stack.clone();
            template.setAmount(1);
            takes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new SfxVirtualContainerService.SlotTake(slot, template, amount));
            remaining -= amount;
            if (!allowMultipleSlots) {
                break;
            }
        }
        for (Map.Entry<String, ItemStack> entry : grouped.entrySet()) {
            result.add(new SfxVirtualContainerService.PlannedStack(entry.getValue(), List.copyOf(takes.getOrDefault(entry.getKey(), List.of()))));
        }
        return result;
    }

    public boolean canRemoveCargoOutput(UUID instanceId, List<SfxVirtualContainerService.SlotTake> takes) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxElectricMachineDefinition definition = definitionForInstance(instance);
        if (definition == null || takes == null || takes.isEmpty()) {
            return false;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        for (SfxVirtualContainerService.SlotTake take : takes) {
            if (take == null || take.amount() <= 0 || take.slot() < 0 || take.slot() >= definition.outputSlots().length) {
                return false;
            }
            SfxElectricStack electricStack = state.output(take.slot());
            ItemStack stack = electricStack == null ? null : electricStack.toItemStack(items);
            if (SfxElectricCargoInventoryOps.isEmpty(stack) || !stack.isSimilar(take.template()) || stack.getAmount() < take.amount()) {
                return false;
            }
        }
        return true;
    }

    public boolean removeCargoOutput(UUID instanceId, List<SfxVirtualContainerService.SlotTake> takes) {
        if (!canRemoveCargoOutput(instanceId, takes)) {
            return false;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxElectricMachineDefinition definition = definitionForInstance(instance);
        if (definition == null) {
            return false;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        for (SfxVirtualContainerService.SlotTake take : takes) {
            SfxElectricStack electricStack = state.output(take.slot());
            ItemStack stack = electricStack == null ? null : electricStack.toItemStack(items);
            int remaining = stack.getAmount() - take.amount();
            if (remaining <= 0) {
                state.output(take.slot(), null);
            } else {
                stack.setAmount(remaining);
                state.output(take.slot(), SfxElectricStack.fromItemStack(items, stack));
            }
        }
        markCargoMutated(instanceId);
        return true;
    }

    public int cargoInputCapacity(UUID instanceId, ItemStack stack, boolean smartFill) {
        return cargoCapacity(instanceId, stack, smartFill, true, false);
    }

    public int cargoInputCapacitySingleSlot(UUID instanceId, ItemStack stack, boolean smartFill) {
        return cargoCapacity(instanceId, stack, smartFill, true, true);
    }

    public int cargoOutputCapacity(UUID instanceId, ItemStack stack, boolean smartFill) {
        return cargoCapacity(instanceId, stack, smartFill, false, false);
    }

    public int cargoOutputCapacitySingleSlot(UUID instanceId, ItemStack stack, boolean smartFill) {
        return cargoCapacity(instanceId, stack, smartFill, false, true);
    }

    public ItemStack insertCargoInput(UUID instanceId, ItemStack stack, boolean smartFill) {
        return insertCargoStack(instanceId, stack, smartFill, true, false);
    }

    public ItemStack insertCargoInputSingleSlot(UUID instanceId, ItemStack stack, boolean smartFill) {
        return insertCargoStack(instanceId, stack, smartFill, true, true);
    }

    public ItemStack insertCargoOutput(UUID instanceId, ItemStack stack, boolean smartFill) {
        return insertCargoStack(instanceId, stack, smartFill, false, false);
    }

    public ItemStack insertCargoOutputSingleSlot(UUID instanceId, ItemStack stack, boolean smartFill) {
        return insertCargoStack(instanceId, stack, smartFill, false, true);
    }

    private int cargoCapacity(UUID instanceId, ItemStack probe, boolean smartFill, boolean inputInventory, boolean singleSlot) {
        if (SfxElectricCargoInventoryOps.isEmpty(probe)) {
            return 0;
        }
        CargoInventorySnapshot snapshot = cargoInventorySnapshot(instanceId, inputInventory);
        if (snapshot == null || snapshot.contents().length == 0) {
            return 0;
        }
        if (singleSlot) {
            return SfxElectricCargoInventoryOps.capacityForSingleSlot(snapshot.contents(), probe, smartFill);
        }
        return SfxElectricCargoInventoryOps.capacityFor(snapshot.contents(), probe, smartFill);
    }

    private ItemStack insertCargoStack(UUID instanceId, ItemStack input, boolean smartFill, boolean inputInventory, boolean singleSlot) {
        if (SfxElectricCargoInventoryOps.isEmpty(input)) {
            return null;
        }
        CargoInventorySnapshot snapshot = cargoInventorySnapshot(instanceId, inputInventory);
        if (snapshot == null || snapshot.contents().length == 0) {
            return input;
        }
        ItemStack[] before = SfxElectricCargoInventoryOps.cloneContents(snapshot.contents());
        ItemStack remainder = singleSlot
                ? SfxElectricCargoInventoryOps.insertSingleSlot(snapshot.contents(), input, smartFill)
                : SfxElectricCargoInventoryOps.insert(snapshot.contents(), input, smartFill);
        if (!SfxElectricCargoInventoryOps.sameContents(before, snapshot.contents())) {
            writeCargoInventory(snapshot);
            markCargoMutated(instanceId);
        }
        return SfxElectricCargoInventoryOps.isEmpty(remainder) ? null : remainder;
    }

    private CargoInventorySnapshot cargoInventorySnapshot(UUID instanceId, boolean inputInventory) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxElectricMachineDefinition definition = definitionForInstance(instance);
        if (definition == null) {
            return null;
        }
        int slotCount = inputInventory ? definition.inputSlots().length : definition.outputSlots().length;
        if (slotCount <= 0) {
            return null;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        ItemStack[] contents = new ItemStack[slotCount];
        for (int slot = 0; slot < slotCount; slot++) {
            SfxElectricStack electricStack = inputInventory ? state.input(slot) : state.output(slot);
            contents[slot] = electricStack == null ? null : electricStack.toItemStack(items);
        }
        return new CargoInventorySnapshot(instanceId, state, inputInventory, contents);
    }

    private void writeCargoInventory(CargoInventorySnapshot snapshot) {
        for (int slot = 0; slot < snapshot.contents().length; slot++) {
            SfxElectricStack stack = SfxElectricStack.fromItemStack(items, snapshot.contents()[slot]);
            if (snapshot.inputInventory()) {
                snapshot.state().input(slot, stack);
            } else {
                snapshot.state().output(slot, stack);
            }
        }
    }

    private void markCargoMutated(UUID instanceId) {
        dirtyInstances.add(instanceId);
        activeInstances.add(instanceId);
        refreshOpenSessionFromState(instanceId);
    }

    private void refreshOpenSessionFromState(UUID instanceId) {
        SfxElectricMachineSession session = sessionsByInstance.get(instanceId);
        if (session == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        if (definition == null) {
            return;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        render(session, definition, session.inventory(), state, recipeProcessor.activeRecipe(definition, state), sessionRenderStatus(definition, state, session));
    }

    private SfxElectricMachineDefinition definitionForInstance(SfxBlockInstanceRecord instance) {
        return instance == null ? null : registry.definition(instance.typeId()).orElse(null);
    }

    private String cargoItemKey(ItemStack stack) {
        return SfxElectricCargoInventoryOps.itemKey(items, stack);
    }

    private void syncSessionState(SfxElectricMachineSession session) {
        SfxBlockInstanceRecord instance = blockData.findInstance(session.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxElectricMachineState state = currentState(session.instanceId(), instance);
        syncInventoryToState(session.inventory(), state);
        dirtyInstances.add(session.instanceId());
        activeInstances.add(session.instanceId());
    }

    void syncInventoryToState(Inventory inventory, SfxElectricMachineState state) {
        SfxElectricMachineDefinition definition = definitionFromInventory(inventory);
        if (definition == null) {
            return;
        }
        if (definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER) {
            return;
        }
        int[] inputSlots = definition.inputSlots();
        for (int slot = 0; slot < inputSlots.length; slot++) {
            ItemStack stack = inventory.getItem(inputSlots[slot]);
            state.input(slot, SfxElectricStack.fromItemStack(items, stack));
        }
        for (int slot = inputSlots.length; slot < state.inputCapacity(); slot++) {
            state.input(slot, null);
        }
        int[] outputSlots = definition.outputSlots();
        for (int slot = 0; slot < outputSlots.length; slot++) {
            state.output(slot, SfxElectricStack.fromItemStack(items, inventory.getItem(outputSlots[slot])));
        }
        for (int slot = outputSlots.length; slot < state.outputCapacity(); slot++) {
            state.output(slot, null);
        }
    }

    void render(SfxElectricMachineSession session, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        if (session != null) {
            session.markRendered(tickCounter, status);
            if (definition.menuStyle() == SfxElectricMachineMenuStyle.SIMPLE_IO) {
                simpleIoMenuRenderer.render(session.viewerId(), definition, inventory, state, status);
            } else if (definition.menuStyle() == SfxElectricMachineMenuStyle.GEO_MINER) {
                geoMinerMenuRenderer.render(session.viewerId(), definition, inventory, state, status);
            } else if (definition.menuStyle() == SfxElectricMachineMenuStyle.ASSEMBLER) {
                assemblerMenuRenderer.render(session.viewerId(), definition, inventory, state, status);
            } else if (definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_BREWER) {
                autoBrewerMenuRenderer.render(session.viewerId(), definition, inventory, state, status);
            } else if (definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER) {
                SfxAutoCrafterRecipeChoice choice = definition.recipeProvider() instanceof SfxAutoCrafterRecipeProvider provider
                        ? provider.choiceForKey(plugin, state.activeRecipeKey())
                        : null;
                autoCrafterMenuRenderer.render(session.viewerId(), definition, inventory, state, status, choice);
            } else {
                menuRenderer.render(session.viewerId(), definition, inventory, state, recipe, status);
            }
        }
    }


    private void updateGeoMinerFloatingText(Location location, SfxElectricMachineState state, SfxElectricMachineRenderStatus status) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Component text = switch (status) {
            case CHUNK_NOT_SCANNED -> localization.component("gps.ui.geo-miner-hologram.scan-required", "&4GEO-Scan required!");
            case NO_GEO_RESOURCE -> localization.component("gps.ui.geo-miner-hologram.finished", "&7Finished");
            case WORKING -> localization.component(
                    "gps.ui.geo-miner-hologram.mining",
                    "&7Mining: &r{resource}",
                    Map.of("resource", geoMinerResourceName(location)));
            default -> state.hasProgress()
                    ? localization.component(
                    "gps.ui.geo-miner-hologram.mining",
                    "&7Mining: &r{resource}",
                    Map.of("resource", geoMinerResourceName(location)))
                    : localization.component("gps.ui.geo-miner-hologram.idling", "&7Idling...");
        };
        floatingTextDisplays.update(new SfxFloatingTextProjection(
                geoMinerFloatingTextKey(location),
                location.getBlockX() + 0.5D,
                location.getBlockY() + 1.2D,
                location.getBlockZ() + 0.5D,
                text,
                32 * 32,
                true,
                SfxFloatingTextDisplayMode.ARMOR_STAND));
    }

    private void removeGeoMinerFloatingText(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        floatingTextDisplays.remove(geoMinerFloatingTextKey(location));
    }

    private SfxFloatingTextKey geoMinerFloatingTextKey(Location location) {
        return new SfxFloatingTextKey("geo_miner", location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private String geoMinerResourceName(Location location) {
        SfxGpsExtractionResult result = SfxGpsElectricBridge.peekExtraction(location, false);
        SfxElectricStack output = result.output();
        if (output == null) {
            return localization.text("gps.ui.geo-miner-hologram.resource-fallback", "GEO Resource");
        }
        if (output.isSfxItem()) {
            Component fallback = Component.text(output.itemId());
            return PlainTextComponentSerializer.plainText().serialize(localization.itemName(output.itemId(), fallback));
        }
        if (output.material() != null) {
            return output.material().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        }
        return localization.text("gps.ui.geo-miner-hologram.resource-fallback", "GEO Resource");
    }

    boolean isInstanceChunkLoaded(SfxBlockInstanceRecord instance) {
        if (instance == null) {
            return false;
        }
        Location location = locationFor(instance);
        if (location == null) {
            return false;
        }
        org.bukkit.World world = location.getWorld();
        return world != null && world.isChunkLoaded(instance.anchorKey().x() >> 4, instance.anchorKey().z() >> 4);
    }

    Location locationFor(SfxBlockInstanceRecord instance) {
        org.bukkit.World world = plugin.getServer().getWorld(instance.anchorKey().worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, instance.anchorKey().x(), instance.anchorKey().y(), instance.anchorKey().z());
    }

    private void dropPluginBlock(Block block, String typeId) {
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
    }

    private void dropStack(Block block, SfxElectricStack stack) {
        SfxBlockDrops.dropStack(block, items, stack);
    }

    void playCompleteSound(SfxElectricMachineSession session) {
        if (session == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(session.viewerId());
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!profiles.find(player.getUniqueId()).map(profile -> profile.machineCompletionSound()).orElse(true)) {
            return;
        }
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.8f);
    }

    private boolean isSmoothUiEnabled(UUID viewerId) {
        return profiles.find(viewerId)
                .map(profile -> profile.machineSmoothUi())
                .orElse(true);
    }

    boolean shouldRenderSession(SfxElectricMachineSession session, SfxElectricMachineRenderStatus status) {
        
        
        
        session.markRendered(tickCounter, status);
        return true;
    }

    private boolean contains(int[] slots, int value) {
        for (int slot : slots) {
            if (slot == value) {
                return true;
            }
        }
        return false;
    }

    private SfxElectricMachineDefinition definitionFromInventory(Inventory inventory) {
        if (!(inventory.getHolder() instanceof SfxElectricMachineHolder holder)) {
            return null;
        }
        return definitionFor(holder.instanceId());
    }

    private SfxElectricMachineDefinition definitionFor(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        return instance == null ? null : registry.definition(instance.typeId()).orElse(null);
    }

    private boolean moveShiftClickedStackToInputs(Inventory topInventory, ItemStack current, SfxElectricMachineDefinition definition) {
        if (definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER) {
            return false;
        }
        return SfxInventorySlots.moveStackToSlots(topInventory, definition.inputSlots(), current, (slot, stack) -> {
            if (definition.menuStyle() == SfxElectricMachineMenuStyle.ASSEMBLER) {
                return isValidAssemblerInput(definition, slot, stack);
            }
            if (definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_BREWER) {
                return isValidAutoBrewerInput(slot, stack);
            }
            return true;
        });
    }

    private void handleAutoCrafterButton(UUID instanceId, int slot, ClickType click) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        if (slot == SfxAutoCrafterMenuRenderer.ENABLE_SLOT) {
            if (click.isRightClick()) {
                state.activeRecipeKey(null);
                state.progressWork(0);
            } else {
                state.enabled(!state.enabled());
            }
            dirtyInstances.add(instanceId);
            activeInstances.add(instanceId);
        }
    }

    private void openAutoCrafterSelection(Player player, SfxBlockInstanceRecord instance, SfxElectricMachineDefinition definition, ItemStack hand, int index) {
        if (!(definition.recipeProvider() instanceof SfxAutoCrafterRecipeProvider provider)) {
            return;
        }
        List<SfxAutoCrafterRecipeChoice> choices = provider.selectionChoices(plugin, hand);
        if (choices.isEmpty()) {
            player.sendMessage(Text.prefixed(plugin, localization.text("electric-ui.auto-crafter.recipe-not-found", "<red>No matching auto-crafter recipe was found for this item.</red>")));
            return;
        }
        openAutoCrafterSelection(player, instance, definition, choices, Math.max(0, Math.min(index, choices.size() - 1)));
    }

    private void openAutoCrafterSelection(Player player, SfxBlockInstanceRecord instance, SfxElectricMachineDefinition definition, List<SfxAutoCrafterRecipeChoice> choices, int index) {
        Component title = localization.itemName(definition.id(), Text.renderFlexible(definition.title()));
        Inventory inventory = plugin.getServer().createInventory(new SfxAutoCrafterSelectionHolder(instance.instanceId(), choices, index), 54, title);
        autoCrafterMenuRenderer.renderSelection(definition, inventory, choices, index);
        player.openInventory(inventory);
    }

    private void handleAutoCrafterSelectionClick(Player player, SfxAutoCrafterSelectionHolder holder, int rawSlot) {
        if (holder.choices().isEmpty()) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            player.closeInventory();
            return;
        }
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        if (rawSlot == SfxAutoCrafterMenuRenderer.PREVIOUS_SLOT && holder.index() > 0) {
            openAutoCrafterSelection(player, instance, definition, holder.choices(), holder.index() - 1);
            return;
        }
        if (rawSlot == SfxAutoCrafterMenuRenderer.NEXT_SLOT && holder.index() < holder.choices().size() - 1) {
            openAutoCrafterSelection(player, instance, definition, holder.choices(), holder.index() + 1);
            return;
        }
        if (rawSlot != SfxAutoCrafterMenuRenderer.SELECT_SLOT) {
            return;
        }
        SfxAutoCrafterRecipeChoice choice = holder.choices().get(holder.index());
        SfxElectricMachineState state = currentState(instance.instanceId(), instance);
        state.activeRecipeKey(choice.key());
        state.progressWork(0);
        state.activeBaseTicks(SfxAutoCrafterRecipeProvider.WORK_TICKS);
        dirtyInstances.add(instance.instanceId());
        activeInstances.add(instance.instanceId());
        player.sendMessage(Text.prefixed(plugin, localization.text("electric-ui.auto-crafter.recipe-selected", "<green>Selected recipe: </green><white>{recipe}</white>", Map.of("recipe", choice.key()))));
        player.closeInventory();
        openMachine(player, instance);
    }

    private boolean isAssemblerButton(int slot) {
        return slot == SfxElectricAssemblerMenuRenderer.ENABLE_SLOT
                || slot == SfxElectricAssemblerMenuRenderer.STATUS_SLOT
                || slot == SfxElectricAssemblerMenuRenderer.OFFSET_SLOT;
    }

    private void handleAssemblerButton(UUID instanceId, int slot, ClickType click) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        if (slot == SfxElectricAssemblerMenuRenderer.ENABLE_SLOT) {
            state.enabled(!state.enabled());
            dirtyInstances.add(instanceId);
            activeInstances.add(instanceId);
            return;
        }
        if (slot == SfxElectricAssemblerMenuRenderer.OFFSET_SLOT) {
            int delta = click.isRightClick() ? -5 : 5;
            SfxAreaElectricMachineProviders.assemblerOffsetTenths(state, SfxAreaElectricMachineProviders.assemblerOffsetTenths(state) + delta);
            dirtyInstances.add(instanceId);
            activeInstances.add(instanceId);
        }
    }

    private boolean isValidMachineInput(SfxElectricMachineDefinition definition, int rawSlot, ItemStack item) {
        if (definition.menuStyle() == SfxElectricMachineMenuStyle.ASSEMBLER) {
            return isValidAssemblerInput(definition, rawSlot, item);
        }
        if (definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_BREWER) {
            return isValidAutoBrewerInput(rawSlot, item);
        }
        return true;
    }

    private boolean isValidAssemblerInput(SfxElectricMachineDefinition definition, int rawSlot, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        SfxElectricAssemblerSpec spec = definition.assemblerSpec();
        if (spec == null) {
            return false;
        }
        if (contains(SfxElectricAssemblerMenuRenderer.HEAD_SLOTS, rawSlot)) {
            return item.getType() == spec.headMaterial();
        }
        if (contains(SfxElectricAssemblerMenuRenderer.BODY_SLOTS, rawSlot)) {
            return spec.bodyMaterials().contains(item.getType());
        }
        return false;
    }



    private boolean isValidAutoBrewerInput(int rawSlot, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        if (rawSlot == SfxAutoBrewerMenuRenderer.BLAZE_SLOT) {
            return item.getType() == Material.BLAZE_POWDER && !item.hasItemMeta();
        }
        SfxPotionBrewEngine brewEngine = new SfxPotionBrewEngine(plugin);
        if (rawSlot == SfxAutoBrewerMenuRenderer.INGREDIENT_SLOT) {
            SfxElectricStack stack = SfxElectricStack.fromItemStack(items, item);
            return stack != null && brewEngine.isBrewingIngredient(stack);
        }
        for (int potionSlot : SfxAutoBrewerMenuRenderer.POTION_SLOTS) {
            if (rawSlot == potionSlot) {
                return brewEngine.isValidPotionItem(items, item);
            }
        }
        return false;
    }

}
