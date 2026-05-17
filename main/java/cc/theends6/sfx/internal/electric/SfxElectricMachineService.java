package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxAnchoredInteraction;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import cc.theends6.sfx.internal.machine.SfxMachineTickSettings;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxEventGuards;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxInventorySlots;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
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
    private final SfxBlockDataService blockData;
    private final SfxPlayerDataService profiles;
    private final SfxElectricMachineRegistry registry;
    private final SfxElectricMachineMenuRenderer menuRenderer;
    private final SfxSimpleIoMachineMenuRenderer simpleIoMenuRenderer;
    private final SfxElectricAssemblerMenuRenderer assemblerMenuRenderer;
    private final SfxAutoBrewerMenuRenderer autoBrewerMenuRenderer;
    private final SfxAutoCrafterMenuRenderer autoCrafterMenuRenderer;
    private final SfxVirtualContainerService virtualContainers;
    private final SfxElectricRecipeProcessor recipeProcessor;
    private final Map<UUID, SfxElectricMachineState> stateCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyInstances = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeInstances = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> recentEnergyConsumption = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLogicTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> supplementalEnergyThisSecond = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> supplementalEnergyAveragePerTick = new ConcurrentHashMap<>();
    private volatile long supplementalEnergyWindow = -1L;
    private final SfxMachineTickSettings tickSettings;
    private final Map<UUID, SfxElectricMachineSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, SfxElectricMachineSession> sessionsByInstance = new ConcurrentHashMap<>();
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
            SfxVirtualContainerService virtualContainers
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.tickSettings = SfxMachineTickSettings.from(plugin.getConfig());
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.virtualContainers = Objects.requireNonNull(virtualContainers, "virtualContainers");
        this.registry = SfxElectricMachineDefinitions.create(plugin, items, manualMachines, blockData, virtualContainers);
        this.menuRenderer = new SfxElectricMachineMenuRenderer(items, localization, profiles);
        this.simpleIoMenuRenderer = new SfxSimpleIoMachineMenuRenderer(items, localization, profiles);
        this.assemblerMenuRenderer = new SfxElectricAssemblerMenuRenderer(items, localization, profiles);
        this.autoBrewerMenuRenderer = new SfxAutoBrewerMenuRenderer(items, localization, profiles);
        this.autoCrafterMenuRenderer = new SfxAutoCrafterMenuRenderer(items, localization, profiles);
        this.recipeProcessor = new SfxElectricRecipeProcessor(items);
        bootstrapLoadedStates();
        running = true;
        scheduleTick();
        scheduleFlush();
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
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }
        SfxElectricMachineDefinition clickDefinition = definitionFor(holder.instanceId());
        if (clickDefinition == null) {
            event.setCancelled(true);
            return;
        }
        boolean topSlot = event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (clickDefinition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER) {
            event.setCancelled(true);
            if (topSlot) {
                handleAutoCrafterButton(holder.instanceId(), event.getRawSlot(), event.getClick());
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            }
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
            if (!isTakingFromOutput(event)) {
                event.setCancelled(true);
                return;
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
                if (location == null || !isInstanceChunkLoaded(instance)) {
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
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            activeInstances.remove(instanceId);
            return;
        }
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        if (definition == null) {
            activeInstances.remove(instanceId);
            return;
        }
        SfxElectricMachineState state = currentState(instanceId, instance);
        SfxElectricMachineSession session = sessionsByInstance.get(instanceId);
        if (session != null) {
            syncInventoryToState(session.inventory(), state);
        }
        if (!state.enabled()) {
            SfxElectricMachineRenderStatus paused = SfxElectricMachineRenderStatus.PAUSED;
            if (session != null && shouldRenderSession(session, paused)) {
                render(session, definition, session.inventory(), state, recipeProcessor.activeRecipe(definition, state), paused);
            }
            if (session != null || state.hasProgress() || state.hasAnyInput()) {
                activeInstances.add(instanceId);
            } else {
                activeInstances.remove(instanceId);
            }
            return;
        }

        Location location = locationFor(instance);
        if (location == null || !isInstanceChunkLoaded(instance)) {
            activeInstances.add(instanceId);
            return;
        }
        SfxElectricMachineTickResult customResult = null;
        if (location != null && definition.recipeProvider().hasWorldAction()) {
            customResult = definition.recipeProvider().tickWorldAction(plugin, items, definition, state, location);
        } else if (location != null && definition.recipeProvider().hasSpecialTick()) {
            int interval = Math.max(1, definition.recipeProvider().specialTickIntervalTicks());
            if (state.hasProgress() || interval <= 1 || tickCounter % interval == 0L) {
                customResult = definition.recipeProvider().tickSpecial(plugin, items, definition, state, location, context);
            } else {
                SfxElectricMachineRenderStatus status = retainedSpecialStatus(definition, state, session);
                customResult = new SfxElectricMachineTickResult(status, 0, false, true);
            }
        }
        if (customResult != null) {
            if (customResult.consumedEnergy() > 0) {
                recentEnergyConsumption.merge(instanceId, customResult.consumedEnergy(), Integer::sum);
            }
            if (customResult.supplementalEnergy() > 0) {
                supplementalEnergyThisSecond.merge(instanceId, customResult.supplementalEnergy(), Integer::sum);
            }
            if (customResult.changed()) {
                dirtyInstances.add(instanceId);
            }
            if (session != null && shouldRenderSession(session, customResult.status())) {
                SfxElectricRecipe renderRecipe = definition.menuStyle() == SfxElectricMachineMenuStyle.SIMPLE_IO
                        || definition.menuStyle() == SfxElectricMachineMenuStyle.ASSEMBLER
                        || definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_BREWER
                        || definition.menuStyle() == SfxElectricMachineMenuStyle.AUTO_CRAFTER
                        ? null
                        : recipeProcessor.activeRecipe(definition, state);
                render(session, definition, session.inventory(), state, renderRecipe, customResult.status());
            }
            if (customResult.keepActive() || state.hasAnyInput()) {
                activeInstances.add(instanceId);
            } else if (session == null && !state.hasProgress()) {
                activeInstances.remove(instanceId);
            }
            return;
        }

        SfxElectricRecipe activeRecipe = recipeProcessor.activeRecipe(definition, state);
        SfxElectricMachineRenderStatus status = SfxElectricMachineRenderStatus.IDLE;

        if (state.hasPendingOutput()) {
            SfxElectricStack pendingOutput = state.pendingOutput();
            Integer outputSlot = pendingOutput == null ? null : recipeProcessor.findOutputSlot(definition, state, pendingOutput);
            if (pendingOutput != null && outputSlot != null) {
                recipeProcessor.pushOutput(state, outputSlot, pendingOutput);
                state.resetProgress();
                dirtyInstances.add(instanceId);
                playCompleteSound(session);
                SfxElectricRecipeStart nextStart = recipeProcessor.tryStartNextRecipe(definition, state);
                if (nextStart != null) {
                    activeRecipe = nextStart.recipe();
                    status = SfxElectricMachineRenderStatus.WORKING;
                    activeInstances.add(instanceId);
                } else {
                    activeRecipe = null;
                    status = state.hasAnyInput() ? recipeProcessor.deriveStatus(definition, state) : SfxElectricMachineRenderStatus.IDLE;
                }
            } else {
                status = SfxElectricMachineRenderStatus.BLOCKED_OUTPUT;
                activeInstances.add(instanceId);
            }
        } else if (activeRecipe == null) {
            SfxElectricRecipeMatch match = recipeProcessor.findRecipeMatch(definition, state);
            if (match == null) {
                if (state.hasProgress()) {
                    state.resetProgress();
                    dirtyInstances.add(instanceId);
                }
                status = state.hasAnyInput() ? SfxElectricMachineRenderStatus.NO_RECIPE : SfxElectricMachineRenderStatus.IDLE;
            } else {
                if (!recipeProcessor.canFitOutputForRecipe(definition, state, match.recipe())) {
                    status = SfxElectricMachineRenderStatus.OUTPUT_FULL;
                } else {
                    SfxElectricRecipeStart start = recipeProcessor.tryStartNextRecipe(definition, state);
                    if (start != null) {
                        activeRecipe = start.recipe();
                        status = SfxElectricMachineRenderStatus.WORKING;
                        activeInstances.add(instanceId);
                    } else {
                        activeRecipe = null;
                        status = recipeProcessor.deriveStatus(definition, state);
                    }
                    dirtyInstances.add(instanceId);
                }
            }
        } else {
            activeInstances.add(instanceId);
            if (!state.hasReservedInput()) {
                state.resetProgress();
                dirtyInstances.add(instanceId);
                status = state.hasAnyInput() ? recipeProcessor.deriveStatus(definition, state) : SfxElectricMachineRenderStatus.IDLE;
            } else {
                int totalWork = recipeProcessor.requiredWork(activeRecipe);
                if (state.progressWork() >= totalWork) {
                    status = completeActiveRecipe(instanceId, state, activeRecipe, definition, session);
                } else {
                    int elapsed = Math.max(1, context.elapsedTicksInt());
                    int speed = Math.max(1, definition.speed());
                    int remainingWork = Math.max(0, totalWork - state.progressWork());
                    int ticksNeeded = Math.max(1, (remainingWork + speed - 1) / speed);
                    int progressTicks = Math.min(elapsed, ticksNeeded);
                    int energyPerTick = Math.max(0, definition.energyConsumptionPerTick());
                    if (energyPerTick > 0) {
                        progressTicks = Math.min(progressTicks, state.storedEnergy() / energyPerTick);
                    }
                    if (progressTicks <= 0) {
                        status = SfxElectricMachineRenderStatus.NO_POWER;
                    } else {
                        if (energyPerTick > 0) {
                            int consumed = energyPerTick * progressTicks;
                            state.storedEnergy(state.storedEnergy() - consumed);
                            recentEnergyConsumption.merge(instanceId, consumed, Integer::sum);
                        }
                        int progressed = Math.min(totalWork, state.progressWork() + speed * progressTicks);
                        state.progressWork(progressed);
                        status = progressed >= totalWork
                                ? completeActiveRecipe(instanceId, state, activeRecipe, definition, session)
                                : SfxElectricMachineRenderStatus.WORKING;
                    }
                }
                dirtyInstances.add(instanceId);
            }
        }

        if (session != null && shouldRenderSession(session, status)) {
            render(session, definition, session.inventory(), state, recipeProcessor.activeRecipe(definition, state), status);
        }
        if (session == null && !state.hasAnyInput() && !state.hasProgress()) {
            activeInstances.remove(instanceId);
        }
    }


    private SfxElectricMachineRenderStatus completeActiveRecipe(
            UUID instanceId,
            SfxElectricMachineState state,
            SfxElectricRecipe activeRecipe,
            SfxElectricMachineDefinition definition,
            SfxElectricMachineSession session
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
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        if (definition == null) {
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
        Component title = localization.itemName(definition.id(), Component.text(definition.title()));
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

    private SfxElectricMachineState currentState(UUID instanceId, SfxBlockInstanceRecord instance) {
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

    private void syncInventoryToState(Inventory inventory, SfxElectricMachineState state) {
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

    private void render(SfxElectricMachineSession session, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        if (session != null) {
            session.markRendered(tickCounter, status);
            if (definition.menuStyle() == SfxElectricMachineMenuStyle.SIMPLE_IO) {
                simpleIoMenuRenderer.render(session.viewerId(), definition, inventory, state, status);
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


    private boolean isInstanceChunkLoaded(SfxBlockInstanceRecord instance) {
        if (instance == null) {
            return false;
        }
        org.bukkit.World world = plugin.getServer().getWorld(instance.anchorKey().worldId());
        return world != null && world.isChunkLoaded(instance.anchorKey().x() >> 4, instance.anchorKey().z() >> 4);
    }

    private Location locationFor(SfxBlockInstanceRecord instance) {
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

    private void playCompleteSound(SfxElectricMachineSession session) {
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

    private boolean shouldRenderSession(SfxElectricMachineSession session, SfxElectricMachineRenderStatus status) {
        
        
        
        session.markRendered(tickCounter, status);
        return true;
    }

    private boolean isTakingFromOutput(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentItem = current != null && !current.getType().isAir();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        return currentItem && (cursorEmpty || event.isShiftClick());
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
        Component title = localization.itemName(definition.id(), Component.text(definition.title()));
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
