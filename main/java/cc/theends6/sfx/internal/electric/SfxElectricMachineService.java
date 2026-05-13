package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
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
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
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
    private static final int[] INPUT_SLOTS = {19, 20};
    private static final int[] OUTPUT_SLOTS = {24, 25};
    private static final int DISPLAY_SLOT = 22;
    private static final long FLUSH_INTERVAL = 20L;
    private static final int[] BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 31, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] BORDER_IN = {9, 10, 11, 12, 18, 21, 27, 28, 29, 30};
    private static final int[] BORDER_OUT = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxPlayerDataService profiles;
    private final SfxElectricMachineRegistry registry;
    private final SfxElectricMachineMenuRenderer menuRenderer;
    private final SfxElectricRecipeProcessor recipeProcessor;
    private final Map<UUID, SfxElectricMachineState> stateCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyInstances = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeInstances = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> recentEnergyConsumption = new ConcurrentHashMap<>();
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
            DefaultManualMachineRegistry manualMachines
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = SfxElectricMachineDefinitions.create(plugin, manualMachines);
        this.menuRenderer = new SfxElectricMachineMenuRenderer(items, localization, profiles);
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
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            dropStack(block, state.input(slot));
        }
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
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
        blockData.unregisterAt(block.getLocation());
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
        if (instance == null || !registry.contains(instance.typeId())) {
            return;
        }
        event.setCancelled(true);
        runtime.executeForPlayer(event.getPlayer(), () -> openMachine(event.getPlayer(), instance));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxElectricMachineHolder holder)) {
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
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxElectricMachineHolder holder)) {
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
        recentEnergyConsumption.clear();
    }

    private void bootstrapLoadedStates() {
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !registry.contains(instance.typeId())) {
                continue;
            }
            stateCache.put(instance.instanceId(), SfxElectricMachineState.decode(instance.stateBlob()));
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
        int total = 0;
        for (UUID instanceId : instanceIds) {
            total += requestedEnergyConsumption(instanceId);
        }
        return total;
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
        if (state.hasPendingOutput()) {
            return 0;
        }
        SfxElectricRecipe activeRecipe = recipeProcessor.activeRecipe(definition, state);
        if (activeRecipe != null && state.hasReservedInput()) {
            if (state.progressWork() >= recipeProcessor.requiredWork(activeRecipe)
                    && !recipeProcessor.canFitCompletionOutputForRecipe(state, activeRecipe)) {
                return 0;
            }
            return definition.energyConsumptionPerTick();
        }
        SfxElectricRecipeMatch match = recipeProcessor.findRecipeMatch(definition, state);
        if (match == null) {
            return 0;
        }
        if (!recipeProcessor.canFitOutputForRecipe(state, match.recipe())) {
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
                    continue;
                }
                Location location = locationFor(instance);
                if (location == null) {
                    continue;
                }
                runtime.executeAt(location, () -> tickMachine(instanceId));
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

    private void tickMachine(UUID instanceId) {
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

        SfxElectricRecipe activeRecipe = recipeProcessor.activeRecipe(definition, state);
        SfxElectricMachineRenderStatus status = SfxElectricMachineRenderStatus.IDLE;

        if (state.hasPendingOutput()) {
            SfxElectricStack pendingOutput = state.pendingOutput();
            Integer outputSlot = pendingOutput == null ? null : recipeProcessor.findOutputSlot(state, pendingOutput);
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
                if (!recipeProcessor.canFitOutputForRecipe(state, match.recipe())) {
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
                } else if (definition.energyConsumptionPerTick() > 0 && state.storedEnergy() < definition.energyConsumptionPerTick()) {
                    status = SfxElectricMachineRenderStatus.NO_POWER;
                } else {
                    if (definition.energyConsumptionPerTick() > 0) {
                        state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
                        recentEnergyConsumption.merge(instanceId, definition.energyConsumptionPerTick(), Integer::sum);
                    }
                    int progressed = Math.min(totalWork, state.progressWork() + definition.speed());
                    state.progressWork(progressed);
                    status = progressed >= totalWork
                            ? completeActiveRecipe(instanceId, state, activeRecipe, definition, session)
                            : SfxElectricMachineRenderStatus.WORKING;
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
        List<SfxElectricStack> recipeOutputs = activeRecipe.hasRandomOutput()
                ? recipeProcessor.rollOutputs(activeRecipe)
                : activeRecipe.outputs();
        int[] outputSlots = recipeProcessor.findCompletionOutputSlots(state, activeRecipe, recipeOutputs);
        if (outputSlots == null) {
            activeInstances.add(instanceId);
            return SfxElectricMachineRenderStatus.BLOCKED_OUTPUT;
        }
        List<SfxElectricStack> completionOutputs = activeRecipe.hasRandomOutput()
                ? recipeOutputs
                : activeRecipe.outputs();
        recipeProcessor.pushOutputs(state, outputSlots, completionOutputs);
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
        Inventory inventory = plugin.getServer().createInventory(new SfxElectricMachineHolder(instance.instanceId()), INVENTORY_SIZE, title);
        SfxElectricMachineSession session = new SfxElectricMachineSession(player.getUniqueId(), instance.instanceId(), inventory);
        sessionsByViewer.put(player.getUniqueId(), session);
        sessionsByInstance.put(instance.instanceId(), session);
        activeInstances.add(instance.instanceId());
        render(session, definition, inventory, state, recipeProcessor.activeRecipe(definition, state), recipeProcessor.deriveStatus(definition, state));
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
        render(session, definition, session.inventory(), state, recipeProcessor.activeRecipe(definition, state), recipeProcessor.deriveStatus(definition, state));
    }

    private SfxElectricMachineState currentState(UUID instanceId, SfxBlockInstanceRecord instance) {
        return stateCache.computeIfAbsent(instanceId, ignored -> SfxElectricMachineState.decode(instance.stateBlob()));
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
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            state.input(slot, SfxElectricStack.fromItemStack(items, inventory.getItem(INPUT_SLOTS[slot])));
        }
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            state.output(slot, SfxElectricStack.fromItemStack(items, inventory.getItem(OUTPUT_SLOTS[slot])));
        }
    }

    private void render(SfxElectricMachineSession session, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricRecipe recipe, SfxElectricMachineRenderStatus status) {
        if (session != null) {
            session.markRendered(tickCounter, status);
            menuRenderer.render(session.viewerId(), definition, inventory, state, recipe, status);
        }
    }

    private Location locationFor(SfxBlockInstanceRecord instance) {
        org.bukkit.World world = plugin.getServer().getWorld(instance.anchorKey().worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, instance.anchorKey().x(), instance.anchorKey().y(), instance.anchorKey().z());
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
        if (isSmoothUiEnabled(session.viewerId())) {
            session.markRendered(tickCounter, status);
            return true;
        }
        if (session.lastRenderedStatus() != status || tickCounter - session.lastRenderedTick() >= 10L) {
            session.markRendered(tickCounter, status);
            return true;
        }
        return false;
    }

    private boolean contains(int[] slots, int value) {
        for (int slot : slots) {
            if (slot == value) {
                return true;
            }
        }
        return false;
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
        if (remaining <= 0) {
            current.setAmount(0);
            return true;
        }
        current.setAmount(remaining);
        return remaining < original;
    }



}
