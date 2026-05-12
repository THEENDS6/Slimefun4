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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
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
    private final Map<UUID, SfxElectricMachineState> stateCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyInstances = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeInstances = ConcurrentHashMap.newKeySet();
    private final Map<UUID, MachineSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, MachineSession> sessionsByInstance = new ConcurrentHashMap<>();
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
        this.registry = createRegistry(plugin, manualMachines);
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
        MachineSession session = sessionsByInstance.remove(instanceId);
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
        dropStack(block, state.reservedInput());
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
        runtime.executeAt(clicked.getLocation(), () -> openMachine(event.getPlayer(), instance));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof MachineHolder holder)) {
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
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof MachineHolder holder)) {
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
        if (!(event.getInventory().getHolder() instanceof MachineHolder holder)) {
            return;
        }
        MachineSession session = sessionsByInstance.remove(holder.instanceId());
        if (session == null) {
            return;
        }
        sessionsByViewer.remove(session.viewerId());
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxElectricMachineState state = currentState(holder.instanceId(), instance);
        syncInventoryToState(session.inventory(), state);
        dirtyInstances.add(holder.instanceId());
        activeInstances.add(holder.instanceId());
    }

    public void shutdown() {
        running = false;
        for (MachineSession session : List.copyOf(sessionsByViewer.values())) {
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

    private SfxElectricMachineRegistry createRegistry(JavaPlugin plugin, DefaultManualMachineRegistry manualMachines) {
        SfxElectricMachineRegistry result = new SfxElectricMachineRegistry();
        SfxElectricRecipeProvider furnaceRecipes = new SfxVanillaFurnaceRecipeProvider(plugin, 4);
        SfxElectricRecipeProvider grinderRecipes = new SfxClassicOreGrinderRecipeProvider(
                manualMachines.recipesFor("sf:grind_stone"),
                manualMachines.recipesFor("sf:ore_crusher"),
                4);
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace", "Electric Furnace", 1, 1280, 2, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace_2", "Electric Furnace - II", 2, 2560, 3, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace_3", "Electric Furnace - III", 4, 5120, 5, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder", "Electric Ore Grinder", 1, 2560, 6, Material.IRON_PICKAXE, grinderRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder_2", "Electric Ore Grinder - II", 4, 10240, 15, Material.IRON_PICKAXE, grinderRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder_3", "Electric Ore Grinder - III", 10, 20480, 45, Material.IRON_PICKAXE, grinderRecipes));
        return result;
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
        MachineSession session = sessionsByInstance.get(instanceId);
        if (session != null) {
            syncInventoryToState(session.inventory(), state);
        }

        SfxElectricRecipe activeRecipe = activeRecipe(definition, state);
        MachineRenderStatus status = MachineRenderStatus.IDLE;

        if (state.hasPendingOutput()) {
            SfxElectricStack pendingOutput = state.pendingOutput();
            Integer outputSlot = pendingOutput == null ? null : findOutputSlot(state, pendingOutput);
            if (pendingOutput != null && outputSlot != null) {
                pushOutput(state, outputSlot, pendingOutput);
                state.resetProgress();
                dirtyInstances.add(instanceId);
                playCompleteSound(session);
                RecipeStart nextStart = tryStartNextRecipe(definition, state);
                if (nextStart != null) {
                    activeRecipe = nextStart.recipe();
                    status = MachineRenderStatus.WORKING;
                    activeInstances.add(instanceId);
                } else {
                    activeRecipe = null;
                    status = state.hasAnyInput() ? deriveStatus(definition, state) : MachineRenderStatus.IDLE;
                }
            } else {
                status = MachineRenderStatus.BLOCKED_OUTPUT;
                activeInstances.add(instanceId);
            }
        } else if (activeRecipe == null) {
            RecipeMatch match = findRecipeMatch(definition, state);
            if (match == null) {
                if (state.hasProgress()) {
                    state.resetProgress();
                    dirtyInstances.add(instanceId);
                }
                status = state.hasAnyInput() ? MachineRenderStatus.NO_RECIPE : MachineRenderStatus.IDLE;
            } else {
                if (findOutputSlot(state, match.recipe().output()) == null) {
                    status = MachineRenderStatus.OUTPUT_FULL;
                } else {
                    SfxElectricStack reservedInput = consumeInput(state, match.inputSlot(), match.recipe().input().amount());
                    state.activeRecipeKey(match.recipe().key());
                    state.activeInputSlot(match.inputSlot());
                    state.progressWork(0);
                    state.reservedInput(reservedInput);
                    state.pendingOutput(null);
                    activeRecipe = match.recipe();
                    status = MachineRenderStatus.WORKING;
                    dirtyInstances.add(instanceId);
                    activeInstances.add(instanceId);
                }
            }
        } else {
            activeInstances.add(instanceId);
            if (state.reservedInput() == null) {
                state.resetProgress();
                dirtyInstances.add(instanceId);
                status = state.hasAnyInput() ? deriveStatus(definition, state) : MachineRenderStatus.IDLE;
            } else {
                if (definition.energyConsumptionPerTick() > 0 && state.storedEnergy() < definition.energyConsumptionPerTick()) {
                    status = MachineRenderStatus.NO_POWER;
                } else {
                    if (definition.energyConsumptionPerTick() > 0) {
                        state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
                    }
                    int totalWork = requiredWork(activeRecipe);
                    int progressed = Math.min(totalWork, state.progressWork() + definition.speed());
                    state.progressWork(progressed);
                    status = MachineRenderStatus.WORKING;
                    if (progressed >= totalWork) {
                        Integer outputSlot = findOutputSlot(state, activeRecipe.output());
                        if (outputSlot == null) {
                            state.pendingOutput(activeRecipe.output());
                            status = MachineRenderStatus.BLOCKED_OUTPUT;
                        } else {
                            pushOutput(state, outputSlot, activeRecipe.output());
                            state.resetProgress();
                            RecipeStart nextStart = tryStartNextRecipe(definition, state);
                            if (nextStart != null) {
                                activeRecipe = nextStart.recipe();
                                status = MachineRenderStatus.WORKING;
                                activeInstances.add(instanceId);
                            } else {
                                status = state.hasAnyInput() ? deriveStatus(definition, state) : MachineRenderStatus.IDLE;
                            }
                            playCompleteSound(session);
                        }
                    }
                }
                dirtyInstances.add(instanceId);
            }
        }

        if (session != null && shouldRenderSession(session, status)) {
            render(session, definition, session.inventory(), state, activeRecipe(definition, state), status);
        }
        if (session == null && !state.hasAnyInput() && !state.hasProgress()) {
            activeInstances.remove(instanceId);
        }
    }

    private void openMachine(Player player, SfxBlockInstanceRecord instance) {
        SfxElectricMachineDefinition definition = registry.definition(instance.typeId()).orElse(null);
        if (definition == null) {
            return;
        }
        MachineSession existing = sessionsByInstance.get(instance.instanceId());
        if (existing != null && !existing.viewerId().equals(player.getUniqueId())) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.busy", "<red>This machine is already open.</red>")));
            return;
        }

        MachineSession previous = sessionsByViewer.remove(player.getUniqueId());
        if (previous != null) {
            sessionsByInstance.remove(previous.instanceId());
            syncSessionState(previous);
        }

        SfxElectricMachineState state = currentState(instance.instanceId(), instance);
        Component title = localization.itemName(definition.id(), Component.text(definition.title()));
        Inventory inventory = plugin.getServer().createInventory(new MachineHolder(instance.instanceId()), INVENTORY_SIZE, title);
        MachineSession session = new MachineSession(player.getUniqueId(), instance.instanceId(), inventory);
        sessionsByViewer.put(player.getUniqueId(), session);
        sessionsByInstance.put(instance.instanceId(), session);
        activeInstances.add(instance.instanceId());
        render(session, definition, inventory, state, activeRecipe(definition, state), deriveStatus(definition, state));
        player.openInventory(inventory);
    }

    private void refreshSession(UUID instanceId) {
        MachineSession session = sessionsByInstance.get(instanceId);
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
        render(session, definition, session.inventory(), state, activeRecipe(definition, state), deriveStatus(definition, state));
    }

    private SfxElectricMachineState currentState(UUID instanceId, SfxBlockInstanceRecord instance) {
        return stateCache.computeIfAbsent(instanceId, ignored -> SfxElectricMachineState.decode(instance.stateBlob()));
    }

    private void syncSessionState(MachineSession session) {
        SfxBlockInstanceRecord instance = blockData.findInstance(session.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxElectricMachineState state = currentState(session.instanceId(), instance);
        syncInventoryToState(session.inventory(), state);
        dirtyInstances.add(session.instanceId());
        activeInstances.add(session.instanceId());
    }

    private SfxElectricRecipe activeRecipe(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        String key = state.activeRecipeKey();
        if (key == null) {
            return null;
        }
        for (SfxElectricRecipe recipe : definition.recipeProvider().recipes()) {
            if (recipe.key().equals(key)) {
                return recipe;
            }
        }
        state.resetProgress();
        return null;
    }

    private RecipeMatch findRecipeMatch(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        for (SfxElectricRecipe recipe : definition.recipeProvider().recipes()) {
            for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
                SfxElectricStack input = state.input(slot);
                if (input != null && input.matches(recipe.input())) {
                    return new RecipeMatch(slot, recipe);
                }
            }
        }
        return null;
    }

    private MachineRenderStatus deriveStatus(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        if (state.hasPendingOutput()) {
            return MachineRenderStatus.BLOCKED_OUTPUT;
        }
        SfxElectricRecipe recipe = activeRecipe(definition, state);
        if (recipe != null) {
            if (definition.energyConsumptionPerTick() > 0 && state.storedEnergy() < definition.energyConsumptionPerTick()) {
                return MachineRenderStatus.NO_POWER;
            }
            return MachineRenderStatus.WORKING;
        }
        RecipeMatch match = findRecipeMatch(definition, state);
        if (match == null && state.hasAnyInput()) {
            return MachineRenderStatus.NO_RECIPE;
        }
        if (match != null && findOutputSlot(state, match.recipe().output()) == null) {
            return MachineRenderStatus.OUTPUT_FULL;
        }
        return MachineRenderStatus.IDLE;
    }

    private int requiredWork(SfxElectricRecipe recipe) {
        return Math.max(1, recipe.baseTicks() * 20);
    }

    private RecipeStart tryStartNextRecipe(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        RecipeMatch match = findRecipeMatch(definition, state);
        if (match == null || findOutputSlot(state, match.recipe().output()) == null) {
            return null;
        }
        SfxElectricStack reservedInput = consumeInput(state, match.inputSlot(), match.recipe().input().amount());
        if (reservedInput == null) {
            return null;
        }
        state.activeRecipeKey(match.recipe().key());
        state.activeInputSlot(match.inputSlot());
        state.progressWork(0);
        state.reservedInput(reservedInput);
        state.pendingOutput(null);
        return new RecipeStart(match.recipe(), match.inputSlot());
    }

    private Integer findOutputSlot(SfxElectricMachineState state, SfxElectricStack recipeOutput) {
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            SfxElectricStack current = state.output(slot);
            if (current != null && recipeOutput.canMerge(current, items)) {
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

    private SfxElectricStack consumeInput(SfxElectricMachineState state, int slot, int amount) {
        SfxElectricStack input = state.input(slot);
        if (input == null) {
            return null;
        }
        SfxElectricStack reserved = input.copyWithAmount(amount);
        int remaining = input.amount() - amount;
        state.input(slot, remaining <= 0 ? null : input.copyWithAmount(remaining));
        return reserved;
    }

    private void pushOutput(SfxElectricMachineState state, int slot, SfxElectricStack recipeOutput) {
        SfxElectricStack current = state.output(slot);
        if (current == null) {
            state.output(slot, recipeOutput);
            return;
        }
        state.output(slot, current.copyWithAmount(current.amount() + recipeOutput.amount()));
    }

    private void syncInventoryToState(Inventory inventory, SfxElectricMachineState state) {
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            state.input(slot, SfxElectricStack.fromItemStack(items, inventory.getItem(INPUT_SLOTS[slot])));
        }
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            state.output(slot, SfxElectricStack.fromItemStack(items, inventory.getItem(OUTPUT_SLOTS[slot])));
        }
    }

    private void render(MachineSession session, SfxElectricMachineDefinition definition, Inventory inventory, SfxElectricMachineState state, SfxElectricRecipe recipe, MachineRenderStatus status) {
        if (session != null) {
            session.markRendered(tickCounter, status);
        }
        fillInventoryFrame(inventory);
        inventory.setItem(DISPLAY_SLOT, progressIcon(session, definition, state, recipe, status));
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            inventory.setItem(INPUT_SLOTS[slot], state.input(slot) == null ? null : state.input(slot).toItemStack(items));
        }
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            inventory.setItem(OUTPUT_SLOTS[slot], state.output(slot) == null ? null : state.output(slot).toItemStack(items));
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

    private ItemStack progressIcon(MachineSession session, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe, MachineRenderStatus status) {
        return switch (status) {
            case WORKING -> buildProgressIcon(session, definition, state, recipe, false);
            case BLOCKED_OUTPUT -> recipe == null
                    ? namedItem(
                            Material.RED_STAINED_GLASS_PANE,
                            localization.component("electric-ui.blocked.name", "<red>Blocked</red>"),
                            List.of(localization.component("electric-ui.blocked.lore", "<gray>Output is full. Free a slot to continue.</gray>")))
                    : buildProgressIcon(session, definition, state, recipe, true);
            case OUTPUT_FULL -> namedItem(
                    Material.RED_STAINED_GLASS_PANE,
                    localization.component("electric-ui.output-full.name", "<red>Output Full</red>"),
                    List.of(localization.component("electric-ui.output-full.lore", "<gray>Free an output slot to continue.</gray>")));
            case NO_POWER -> recipe == null
                    ? namedItem(
                            Material.RED_STAINED_GLASS_PANE,
                            localization.component("electric-ui.no-power.name", "<red>No Power</red>"),
                            List.of(localization.component("electric-ui.no-power.lore", "<gray>Charge this machine to continue.</gray>")))
                    : buildNoPowerIcon(definition, state, recipe);
            case NO_RECIPE -> namedItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    localization.component("electric-ui.no-recipe.name", "<gray>No Recipe</gray>"),
                    List.of(localization.component("electric-ui.no-recipe.lore", "<gray>The current input has no matching recipe.</gray>")));
            case IDLE -> namedItem(
                    Material.BLACK_STAINED_GLASS_PANE,
                    localization.component("electric-ui.idle.name", "<gray>Idle</gray>"),
                    List.of(localization.component("electric-ui.idle.lore", "<gray>Waiting for input.</gray>")));
        };
    }

    private ItemStack buildProgressIcon(MachineSession session, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe, boolean blocked) {
        int totalWork = requiredWork(recipe);
        int currentWork = blocked && state.hasPendingOutput() ? totalWork : Math.min(totalWork, state.progressWork());
        int remainingWork = Math.max(0, totalWork - currentWork);
        int remainingTicks = blocked ? 0 : (int) Math.ceil(remainingWork / (double) Math.max(1, definition.speed()));
        boolean extendedUi = isExtendedUiEnabled(session);
        ItemStack stack = new ItemStack(definition.progressMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof Damageable damageable && stack.getType().getMaxDurability() > 0) {
            damageable.setDamage(progressDamage(stack, remainingWork, totalWork));
        }
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(progressBarLine(currentWork, totalWork));
        lore.add(Component.text(" "));
        lore.add(localization.component(
                blocked ? "electric-ui.blocked.time-left" : "electric-ui.progress.time-left",
                "<gray>{time}</gray>",
                Map.of("time", formatTimeLeft(Math.max(0, remainingTicks / 20)))));
        if (extendedUi) {
            lore.add(Component.empty());
            lore.add(localization.component(
                    "electric-ui.progress.recipe",
                    "<gray>Recipe: </gray><white>{recipe}</white>",
                    Map.of("recipe", displayStackName(recipe.output()))));
            lore.add(localization.component(
                    "electric-ui.progress.speed",
                    "<gray>Speed: </gray><aqua>{speed}x</aqua>",
                    Map.of("speed", definition.speed())));
            lore.add(localization.component(
                    "electric-ui.progress.work",
                    "<gray>Progress: </gray><white>{current}</white><gray>/</gray><white>{total}</white><gray> (+{rate}/tick)</gray>",
                    Map.of("current", currentWork, "total", totalWork, "rate", definition.speed())));
        }
        meta.displayName(blocked
                ? localization.component("electric-ui.blocked.name", "<red>Blocked</red>")
                : Component.text(" "));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildNoPowerIcon(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe) {
        int totalWork = requiredWork(recipe);
        int currentWork = Math.min(totalWork, state.progressWork());
        int remainingWork = Math.max(0, totalWork - currentWork);
        ItemStack stack = new ItemStack(definition.progressMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof Damageable damageable && stack.getType().getMaxDurability() > 0) {
            damageable.setDamage(progressDamage(stack, remainingWork, totalWork));
        }
        meta.displayName(localization.component("electric-ui.no-power.name", "<red>No Power</red>"));
        meta.lore(List.of(
                progressBarLine(currentWork, totalWork),
                Component.text(" "),
                localization.component("electric-ui.no-power.lore", "<gray>Charge this machine to continue.</gray>"),
                localization.component(
                        "electric-ui.energy-buffer",
                        "<gray>Stored: </gray><yellow>{stored}</yellow><gray>/</gray><yellow>{capacity}</yellow><gray> J</gray>",
                        Map.of("stored", state.storedEnergy(), "capacity", definition.energyCapacity()))));
        stack.setItemMeta(meta);
        return stack;
    }

    private Component progressBarLine(int currentWork, int totalWork) {
        float progressPercentage = Math.round(((currentWork * 100.0F) / totalWork) * 100.0F) / 100.0F;
        int filled = Math.min(20, Math.max(0, (int) (progressPercentage / 5.0F)));
        StringBuilder builder = new StringBuilder();
        builder.append(progressColor(progressPercentage));
        for (int i = 0; i < filled; i++) {
            builder.append(':');
        }
        builder.append("&7");
        for (int i = filled; i < 20; i++) {
            builder.append(':');
        }
        builder.append(" - ").append(progressPercentage).append('%');
        return Text.legacy(builder.toString());
    }

    private String progressColor(float percentage) {
        if (percentage < 16.0F) {
            return "&4";
        }
        if (percentage < 32.0F) {
            return "&c";
        }
        if (percentage < 48.0F) {
            return "&6";
        }
        if (percentage < 64.0F) {
            return "&e";
        }
        if (percentage < 80.0F) {
            return "&2";
        }
        return "&a";
    }

    private int progressDamage(ItemStack item, int remainingWork, int totalWork) {
        return Math.max(0, Math.min(item.getType().getMaxDurability(), (item.getType().getMaxDurability() / totalWork) * remainingWork));
    }

    private String formatTimeLeft(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds - minutes * 60;
        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
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

    private void playCompleteSound(MachineSession session) {
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

    private boolean isExtendedUiEnabled(MachineSession session) {
        if (session == null) {
            return true;
        }
        return profiles.find(session.viewerId())
                .map(profile -> profile.machineUiExtended())
                .orElse(true);
    }

    private boolean isSmoothUiEnabled(UUID viewerId) {
        return profiles.find(viewerId)
                .map(profile -> profile.machineSmoothUi())
                .orElse(true);
    }

    private boolean shouldRenderSession(MachineSession session, MachineRenderStatus status) {
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

    private String displayStackName(SfxElectricStack stack) {
        if (stack == null) {
            return "";
        }
        if (stack.isSfxItem()) {
            ItemStack item = stack.toItemStack(items);
            ItemMeta meta = item.getItemMeta();
            Component fallback = meta != null && meta.hasDisplayName() ? meta.displayName() : Component.text(stack.itemId());
            return plainText(localization.itemName(stack.itemId(), fallback));
        }
        return plainText(Component.translatable(stack.material().translationKey()));
    }

    private String plainText(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
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

    private enum MachineRenderStatus {
        IDLE,
        NO_RECIPE,
        WORKING,
        BLOCKED_OUTPUT,
        OUTPUT_FULL,
        NO_POWER
    }

    private record RecipeMatch(int inputSlot, SfxElectricRecipe recipe) {
    }

    private record RecipeStart(SfxElectricRecipe recipe, int inputSlot) {
    }

    private static final class MachineSession {
        private final UUID viewerId;
        private final UUID instanceId;
        private final Inventory inventory;
        private long lastRenderedTick = Long.MIN_VALUE;
        private MachineRenderStatus lastRenderedStatus;

        private MachineSession(UUID viewerId, UUID instanceId, Inventory inventory) {
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

        long lastRenderedTick() {
            return lastRenderedTick;
        }

        MachineRenderStatus lastRenderedStatus() {
            return lastRenderedStatus;
        }

        void markRendered(long tick, MachineRenderStatus status) {
            this.lastRenderedTick = tick;
            this.lastRenderedStatus = status;
        }
    }

    private record MachineHolder(UUID instanceId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
