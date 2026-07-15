package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;
import cc.theends6.sfx.internal.machine.SfxMachineLegacyHookBridge;
import cc.theends6.sfx.internal.technical.SfxRechargeableItemService;
import cc.theends6.sfx.internal.ui.SfxMachineMenuTransactions;
import cc.theends6.sfx.api.machine.runtime.SfxMachineStatusKey;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.util.SfxInventorySlots;
import cc.theends6.sfx.api.text.Text;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class SfxEnergyBackedElectricMenuService implements Listener {
    private final SfxEnergyService energy;
    private final SfxRechargeableItemService rechargeableItems;
    private final SfxEnergyGeneratorMenuRenderer renderer;
    private final Map<UUID, SfxEnergyGeneratorSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, SfxEnergyGeneratorSession> sessionsByInstance = new ConcurrentHashMap<>();

    SfxEnergyBackedElectricMenuService(SfxEnergyService energy, SfxRechargeableItemService rechargeableItems) {
        this.energy = energy;
        this.rechargeableItems = rechargeableItems;
        this.renderer = new SfxEnergyGeneratorMenuRenderer(energy, energy.plugin, energy.items, energy.localization, rechargeableItems);
    }

    void open(Player player, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition) {
        SfxMachineLegacyHookBridge.menuOpen(energy.machineRuntime, definition.id(), instance.instanceId(), energy.toLocation(instance.anchorKey()), "electric-energy-menu", "SfxEnergyBackedElectricMenuService.open");
        SfxEnergyGeneratorSession existing = sessionsByInstance.get(instance.instanceId());
        if (existing != null && !existing.viewerId().equals(player.getUniqueId())) {
            player.sendMessage(Text.prefixed(energy.plugin, energy.localization.text("machines.busy")));
            return;
        }
        SfxEnergyGeneratorSession previous = sessionsByViewer.remove(player.getUniqueId());
        if (previous != null) {
            sessionsByInstance.remove(previous.instanceId());
            syncSessionState(previous);
        }

        SfxEnergyNodeState state = energy.currentState(instance.instanceId(), instance);
        Component title = energy.localization.itemName(definition.id());
        Inventory inventory = energy.plugin.getServer().createInventory(new SfxEnergyGeneratorHolder(instance.instanceId()), definition.ui().inventorySize(), title);
        SfxEnergyGeneratorSession session = new SfxEnergyGeneratorSession(player.getUniqueId(), instance.instanceId(), inventory);
        sessionsByViewer.put(player.getUniqueId(), session);
        sessionsByInstance.put(instance.instanceId(), session);
        energy.markActive(instance.instanceId());
        if (definition.isSolarGenerator()) {
            energy.refreshSolarExposure(energy.toLocation(instance.anchorKey()));
        }
        render(session, instance, definition, inventory, state);
        player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxEnergyGeneratorHolder holder)) {
            return;
        }
        if (SfxMachineMenuTransactions.isCreativeCloneClick(player, event)) {
            return;
        }
        handleClick(event, holder, player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxEnergyGeneratorHolder holder)) {
            return;
        }
        handleDrag(event, holder, player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SfxEnergyGeneratorHolder holder)) {
            return;
        }
        handleClose(event, holder);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handleQuit(event);
    }

    private void handleClick(InventoryClickEvent event, SfxEnergyGeneratorHolder holder, Player player) {
        SfxEnergyComponentDefinition clickDefinition = energy.definitionFor(holder.instanceId());
        if (clickDefinition != null) {
            SfxMachineLegacyHookBridge.menuClick(energy.machineRuntime, clickDefinition.id(), holder.instanceId(), null, "electric-energy-menu", "SfxEnergyBackedElectricMenuService.handleClick");
        }
        traceChargingBench(clickDefinition, "click action=" + event.getAction()
                + " click=" + event.getClick()
                + " raw=" + event.getRawSlot()
                + " shift=" + event.isShiftClick()
                + " current=" + describe(event.getCurrentItem())
                + " cursor=" + describe(event.getCursor()));
        if (clickDefinition == null) {
            event.setCancelled(true);
            return;
        }
        int[] inputSlots = clickDefinition.ui().inputSlots();
        int[] outputSlots = clickDefinition.ui().outputSlots();
        SfxDynamicEnergyGeneratorProvider provider = energy.dynamicGenerator(clickDefinition);
        boolean topSlot = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (event.isShiftClick() && !topSlot) {
            if (moveShiftClickedStackToInputs(event.getView().getTopInventory(), event.getCurrentItem(), clickDefinition, provider)) {
                if (event.getCurrentItem() != null && event.getCurrentItem().getAmount() <= 0) {
                    event.setCurrentItem(null);
                }
                event.setCancelled(true);
                traceChargingBench(clickDefinition, "shift-input accepted current=" + describe(event.getCurrentItem()));
                commitTopInventory(holder.instanceId(), event.getView().getTopInventory());
            } else {
                event.setCancelled(true);
                traceChargingBench(clickDefinition, "shift-input rejected current=" + describe(event.getCurrentItem()));
            }
            return;
        }
        if (topSlot && (contains(inputSlots, event.getRawSlot()) || contains(outputSlots, event.getRawSlot()))) {
            handleStorageSlotClick(event, holder, player, clickDefinition, provider);
            return;
        }
        if (topSlot && provider != null) {
            syncTopInventoryToState(holder.instanceId(), event.getView().getTopInventory());
            SfxBlockInstanceRecord instance = energy.blockData.findInstance(holder.instanceId()).orElse(null);
            if (instance != null) {
                SfxEnergyNodeState state = energy.currentState(holder.instanceId(), instance);
                if (provider.handleMenuClick(energy.plugin, energy.items, clickDefinition, state,
                        energy.toLocation(instance.anchorKey()), player, event.getRawSlot(), event.getClick(),
                        energy.generatorAccess(instance))) {
                    event.setCancelled(true);
                    energy.markDirty(holder.instanceId());
                    energy.markActive(holder.instanceId());
                    render(sessionsByInstance.get(holder.instanceId()), instance, clickDefinition, event.getView().getTopInventory(), state);
                    return;
                }
            }
        }
        if (topSlot && SfxMachineMenuTransactions.cancelUnsupportedManagedClick(event)) {
            traceChargingBench(clickDefinition, "dangerous-click cancelled action=" + event.getAction() + " click=" + event.getClick());
            return;
        }
        if (topSlot && !contains(inputSlots, event.getRawSlot())) {
            event.setCancelled(true);
            traceChargingBench(clickDefinition, "non-editable-slot cancelled raw=" + event.getRawSlot());
            return;
        }
        energy.runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
    }

    private void handleStorageSlotClick(InventoryClickEvent event, SfxEnergyGeneratorHolder holder, Player player, SfxEnergyComponentDefinition definition, SfxDynamicEnergyGeneratorProvider provider) {
        event.setCancelled(true);
        Inventory topInventory = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        int[] inputSlots = definition.ui().inputSlots();
        int[] outputSlots = definition.ui().outputSlots();
        boolean inputSlot = contains(inputSlots, rawSlot);
        boolean outputSlot = contains(outputSlots, rawSlot);
        syncTopInventoryToState(holder.instanceId(), topInventory);
        if (SfxMachineMenuTransactions.handleManagedHotbarOrOffhand(event, topInventory, rawSlot, player, inputSlot, outputSlot, stack -> isValidInputStack(definition, provider, rawSlot, stack))) {
            commitTopInventory(holder.instanceId(), topInventory);
            traceChargingBench(definition, "hotbar/offhand transaction handled raw=" + rawSlot);
            return;
        }
        if ((inputSlot || outputSlot) && SfxMachineMenuTransactions.handleManagedDoubleClick(event, topInventory, player, slot -> contains(outputSlot ? outputSlots : inputSlots, slot))) {
            commitTopInventory(holder.instanceId(), topInventory);
            traceChargingBench(definition, "double-click collect handled raw=" + rawSlot);
            return;
        }
        if (SfxMachineMenuTransactions.cancelUnsupportedManagedClick(event)) {
            traceChargingBench(definition, "unsupported-click cancelled raw=" + event.getRawSlot());
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (SfxMachineMenuTransactions.moveTopSlotToPlayer(topInventory, rawSlot, player)) {
                commitTopInventory(holder.instanceId(), topInventory);
                traceChargingBench(definition, (inputSlot ? "shift-input-take" : "shift-output-take") + " accepted raw=" + rawSlot);
            } else {
                traceChargingBench(definition, (inputSlot ? "shift-input-take" : "shift-output-take") + " rejected raw=" + rawSlot);
            }
            return;
        }
        if (SfxMachineMenuTransactions.dropFromTopSlot(event, topInventory, rawSlot, player)) {
            commitTopInventory(holder.instanceId(), topInventory);
            traceChargingBench(definition, (inputSlot ? "drop-input" : "drop-output") + " accepted raw=" + rawSlot);
            return;
        }

        if (outputSlot) {
            if (SfxMachineMenuTransactions.takeFromSlotToCursor(event, topInventory, rawSlot)) {
                commitTopInventory(holder.instanceId(), topInventory);
            } else {
                traceChargingBench(definition, "output-click cancelled raw=" + rawSlot + " current=" + describe(topInventory.getItem(rawSlot)) + " cursor=" + describe(event.getCursor()));
            }
            return;
        }

        if (!inputSlot) {
            return;
        }
        if (SfxMachineMenuTransactions.handleInputSlotCursorTransaction(event, topInventory, rawSlot, stack -> isValidInputStack(definition, provider, rawSlot, stack))) {
            commitTopInventory(holder.instanceId(), topInventory);
        } else {
            traceChargingBench(definition, "input-click cancelled raw=" + rawSlot + " current=" + describe(topInventory.getItem(rawSlot)) + " cursor=" + describe(event.getCursor()));
        }
    }

    private void handleDrag(InventoryDragEvent event, SfxEnergyGeneratorHolder holder, Player player) {
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        SfxEnergyComponentDefinition dragDefinition = energy.definitionFor(holder.instanceId());
        if (dragDefinition == null) {
            event.setCancelled(true);
            return;
        }
        traceChargingBench(dragDefinition, "drag rawSlots=" + event.getRawSlots()
                + " oldCursor=" + describe(event.getOldCursor())
                + " newItems=" + event.getNewItems().values().stream().map(this::describe).toList());
        int[] inputSlots = dragDefinition.ui().inputSlots();
        boolean onlyEditable = event.getRawSlots().stream()
                .filter(slot -> slot < topSize)
                .allMatch(slot -> contains(inputSlots, slot));
        if (!onlyEditable) {
            event.setCancelled(true);
            return;
        }
        SfxDynamicEnergyGeneratorProvider dragProvider = energy.dynamicGenerator(dragDefinition);
        boolean valid = event.getNewItems().entrySet().stream()
                .filter(entry -> entry.getKey() < topSize)
                .allMatch(entry -> contains(inputSlots, entry.getKey()) && isValidInputStack(dragDefinition, dragProvider, entry.getKey(), entry.getValue()));
        if (!valid) {
            event.setCancelled(true);
            traceChargingBench(dragDefinition, "drag-invalid cancelled rawSlots=" + event.getRawSlots());
            return;
        }
        energy.runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
    }

    private void handleClose(InventoryCloseEvent event, SfxEnergyGeneratorHolder holder) {
        SfxEnergyComponentDefinition closeDefinition = energy.definitionFor(holder.instanceId());
        if (closeDefinition != null) {
            SfxMachineLegacyHookBridge.menuClose(energy.machineRuntime, closeDefinition.id(), holder.instanceId(), null, "electric-energy-menu", "SfxEnergyBackedElectricMenuService.handleClose");
        }
        SfxEnergyGeneratorSession session = sessionsByInstance.remove(holder.instanceId());
        if (session == null) {
            return;
        }
        sessionsByViewer.remove(session.viewerId());
        syncSessionState(session);
        energy.markActive(holder.instanceId());
    }

    private void handleQuit(PlayerQuitEvent event) {
        SfxEnergyGeneratorSession session = sessionsByViewer.remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        sessionsByInstance.remove(session.instanceId());
        syncSessionState(session);
        energy.markActive(session.instanceId());
    }

    void closeAndSync(UUID instanceId) {
        SfxEnergyGeneratorSession session = sessionsByInstance.remove(instanceId);
        if (session == null) {
            return;
        }
        sessionsByViewer.remove(session.viewerId());
        syncSessionState(session);
        Player viewer = energy.plugin.getServer().getPlayer(session.viewerId());
        if (viewer != null) {
            energy.runtime.executeForPlayer(viewer, viewer::closeInventory);
        }
    }

    void shutdown() {
        for (SfxEnergyGeneratorSession session : List.copyOf(sessionsByViewer.values())) {
            syncSessionState(session);
            Player player = energy.plugin.getServer().getPlayer(session.viewerId());
            if (player != null) {
                energy.runtime.executeForPlayer(player, player::closeInventory);
            }
        }
        sessionsByViewer.clear();
        sessionsByInstance.clear();
    }

    void refreshOpenSessions() {
        for (SfxEnergyGeneratorSession session : List.copyOf(sessionsByInstance.values())) {
            SfxBlockInstanceRecord instance = energy.blockData.findInstance(session.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxEnergyComponentDefinition definition = energy.definitions.get(instance.typeId());
            if (definition == null) {
                continue;
            }
            SfxEnergyNodeState state = energy.currentState(instance.instanceId(), instance);
            renderStatusOnly(session, instance, definition, session.inventory(), state);
        }
    }

    void renderStorageSlots(UUID instanceId, SfxEnergyNodeState state) {
        SfxEnergyGeneratorSession session = sessionsByInstance.get(instanceId);
        SfxEnergyComponentDefinition definition = energy.definitionFor(instanceId);
        if (session != null && definition != null) {
            renderer.renderStorageSlots(definition, session.inventory(), state);
        }
    }

    private void refreshSession(UUID instanceId) {
        SfxEnergyGeneratorSession session = sessionsByInstance.get(instanceId);
        if (session == null) {
            return;
        }
        SfxBlockInstanceRecord instance = energy.blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyComponentDefinition definition = energy.definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        SfxEnergyNodeState state = energy.currentState(instanceId, instance);
        syncInventoryToState(definition, session.inventory(), state);
        traceChargingBench(definition, "refresh sync input0=" + describe(state.input(0)) + " input1=" + describe(state.input(1)) + " output0=" + describe(state.output(0)) + " output1=" + describe(state.output(1)));
        energy.markDirty(instanceId);
        energy.markActive(instanceId);
        render(session, instance, definition, session.inventory(), state);
    }

    private void syncSessionState(SfxEnergyGeneratorSession session) {
        SfxBlockInstanceRecord instance = energy.blockData.findInstance(session.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyNodeState state = energy.currentState(session.instanceId(), instance);
        SfxEnergyComponentDefinition definition = energy.definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        syncInventoryToState(definition, session.inventory(), state);
        energy.markDirty(session.instanceId());
    }

    private SfxMachineStatusKey renderStatus(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        SfxEnergyGridStatus gridStatus = energy.nodeGridStatuses.get(instance.instanceId());
        if (gridStatus == SfxEnergyGridStatus.SHARED_NODE_CONFLICT || gridStatus == SfxEnergyGridStatus.MULTIPLE_REGULATORS) {
            return SfxMachineStatusKey.NETWORK_CONFLICT;
        }
        boolean connected = gridStatus == SfxEnergyGridStatus.ONLINE;
        SfxDynamicEnergyGeneratorProvider provider = energy.dynamicGenerator(definition);
        SfxEnergyFuelMatch fuelMatch = definition.isSolarGenerator() || provider != null ? null : energy.findFuelMatch(definition, state);
        boolean hasFuelLoaded = definition.isSolarGenerator() || state.hasActiveFuel() || fuelMatch != null || (provider != null && state.hasAnyInput());
        if (!connected && hasFuelLoaded) {
            return SfxMachineStatusKey.NO_NETWORK;
        }
        if (provider != null) {
            SfxMachineStatusKey providerStatus = provider.status(energy.plugin, energy.items, definition, state, energy.toLocation(instance.anchorKey()), energy.generatorAccess(instance));
            if (providerStatus != null) {
                return providerStatus;
            }
        }
        if (state.hasPendingOutput() && energy.findOutputSlot(definition, state, state.pendingOutput()) == null) {
            return SfxMachineStatusKey.OUTPUT_FULL;
        }
        if (!state.hasActiveFuel() && fuelMatch != null && fuelMatch.output() != null && energy.findOutputSlot(definition, state, fuelMatch.output()) == null) {
            return SfxMachineStatusKey.OUTPUT_FULL;
        }
        if (!state.hasActiveFuel()) {
            return SfxMachineStatusKey.IDLE;
        }
        return SfxMachineStatusKey.WORKING;
    }

    private void render(SfxEnergyGeneratorSession session, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state) {
        renderer.render(definition, inventory, state, renderStatus(instance, definition, state), energy.dynamicGenerator(definition), energy.toLocation(instance.anchorKey()));
    }

    private void renderStatusOnly(SfxEnergyGeneratorSession session, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state) {
        renderer.renderStatusOnly(definition, inventory, state, renderStatus(instance, definition, state), energy.dynamicGenerator(definition), energy.toLocation(instance.anchorKey()));
    }

    private void syncInventoryToState(SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state) {
        int[] inputSlots = definition.ui().inputSlots();
        for (int i = 0; i < inputSlots.length; i++) {
            state.input(i, SfxElectricStack.fromItemStack(energy.items, inventory.getItem(inputSlots[i])));
        }
        int[] outputSlots = definition.ui().outputSlots();
        for (int i = 0; i < outputSlots.length; i++) {
            state.output(i, SfxElectricStack.fromItemStack(energy.items, inventory.getItem(outputSlots[i])));
        }
    }

    private void syncTopInventoryToState(UUID instanceId, Inventory inventory) {
        SfxBlockInstanceRecord instance = energy.blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyComponentDefinition definition = energy.definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        SfxEnergyNodeState state = energy.currentState(instanceId, instance);
        syncInventoryToState(definition, inventory, state);
        energy.markDirty(instanceId);
        energy.markActive(instanceId);
    }

    private void commitTopInventory(UUID instanceId, Inventory inventory) {
        SfxBlockInstanceRecord instance = energy.blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyComponentDefinition definition = energy.definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        SfxEnergyNodeState state = energy.currentState(instanceId, instance);
        syncInventoryToState(definition, inventory, state);
        energy.markDirty(instanceId);
        energy.markActive(instanceId);
        SfxEnergyGeneratorSession session = sessionsByInstance.get(instanceId);
        if (session != null) {
            render(session, instance, definition, session.inventory(), state);
        }
    }

    private boolean moveShiftClickedStackToInputs(Inventory topInventory, ItemStack current, SfxEnergyComponentDefinition definition, SfxDynamicEnergyGeneratorProvider provider) {
        int[] slots = provider == null
                ? definition.ui().inputSlots()
                : provider.shiftInputSlots(energy.plugin, energy.items, definition, current);
        return SfxInventorySlots.moveStackToSlots(topInventory, slots, current, (slot, stack) -> isValidInputStack(definition, provider, slot, stack));
    }

    private boolean isValidInputStack(SfxEnergyComponentDefinition definition, SfxDynamicEnergyGeneratorProvider provider, int rawSlot, ItemStack item) {
        if (definition == null) {
            return true;
        }
        if (definition.isCharger() && !isValidChargingBenchInput(item)) {
            return false;
        }
        int logicalSlot = logicalInputSlot(definition, rawSlot);
        return provider == null || logicalSlot < 0 || provider.acceptsInput(energy.plugin, energy.items, definition, logicalSlot, item);
    }

    private int logicalInputSlot(SfxEnergyComponentDefinition definition, int rawSlot) {
        int[] inputSlots = definition.ui().inputSlots();
        for (int index = 0; index < inputSlots.length; index++) {
            if (inputSlots[index] == rawSlot) {
                return index;
            }
        }
        return -1;
    }

    private boolean isValidChargingBenchInput(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        return !rechargeableItems.isRechargeable(item) || item.getAmount() == 1;
    }

    private void traceChargingBench(SfxEnergyComponentDefinition definition, String message) {
        if (definition != null && definition.isCharger()) {
            SfxValidationDiagnostics.log(energy.plugin, "charging-bench", message);
        }
    }

    private String describe(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "empty";
        }
        return stack.getType().name() + "*" + stack.getAmount() + energy.items.readMarker(stack).map(marker -> "[" + marker.itemId() + "]").orElse("");
    }

    private String describe(SfxElectricStack stack) {
        if (stack == null) {
            return "empty";
        }
        return stack.toItemStack(energy.items).getType().name() + "*" + stack.amount() + (stack.isSfxItem() ? "[" + stack.itemId() + "]" : "");
    }

    private boolean contains(int[] slots, int value) {
        for (int slot : slots) {
            if (slot == value) {
                return true;
            }
        }
        return false;
    }
}
