package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.machine.SfxMachineLegacyHookBridge;
import cc.theends6.sfx.internal.technical.SfxRechargeableItemService;
import cc.theends6.sfx.internal.ui.SfxInventoryPolicy;
import cc.theends6.sfx.internal.ui.SfxMachineStatusKey;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.util.SfxInventorySlots;
import cc.theends6.sfx.internal.util.Text;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class SfxEnergyMachineService implements Listener {
    private static final int INVENTORY_SIZE = 45;
    private static final int[] INPUT_SLOTS = {19, 20};
    private static final int[] OUTPUT_SLOTS = {24, 25};

    private final SfxEnergyService energy;
    private final SfxRechargeableItemService rechargeableItems;
    private final SfxEnergyGeneratorMenuRenderer renderer;
    private final Map<UUID, SfxEnergyGeneratorSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, SfxEnergyGeneratorSession> sessionsByInstance = new ConcurrentHashMap<>();

    SfxEnergyMachineService(SfxEnergyService energy, SfxRechargeableItemService rechargeableItems) {
        this.energy = energy;
        this.rechargeableItems = rechargeableItems;
        this.renderer = new SfxEnergyGeneratorMenuRenderer(energy.plugin, energy.items, energy.localization, rechargeableItems);
    }

    void open(Player player, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition) {
        SfxMachineLegacyHookBridge.menuOpen(energy.machineRuntime, definition.id(), instance.instanceId(), energy.toLocation(instance.anchorKey()), "energy-machine", "SfxEnergyMachineService.open");
        SfxEnergyGeneratorSession existing = sessionsByInstance.get(instance.instanceId());
        if (existing != null && !existing.viewerId().equals(player.getUniqueId())) {
            player.sendMessage(Text.prefixed(energy.plugin, energy.localization.text("machines.busy", "<red>This machine is already open.</red>")));
            return;
        }
        SfxEnergyGeneratorSession previous = sessionsByViewer.remove(player.getUniqueId());
        if (previous != null) {
            sessionsByInstance.remove(previous.instanceId());
            syncSessionState(previous);
        }

        SfxEnergyNodeState state = energy.currentState(instance.instanceId(), instance);
        Component title = energy.localization.itemName(definition.id(), Component.text(definition.id()));
        Inventory inventory = energy.plugin.getServer().createInventory(new SfxEnergyGeneratorHolder(instance.instanceId()), INVENTORY_SIZE, title);
        SfxEnergyGeneratorSession session = new SfxEnergyGeneratorSession(player.getUniqueId(), instance.instanceId(), inventory);
        sessionsByViewer.put(player.getUniqueId(), session);
        sessionsByInstance.put(instance.instanceId(), session);
        energy.markActive(instance.instanceId());
        render(session, instance, definition, inventory, state);
        player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxEnergyGeneratorHolder holder)) {
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
            SfxMachineLegacyHookBridge.menuClick(energy.machineRuntime, clickDefinition.id(), holder.instanceId(), null, "energy-machine", "SfxEnergyMachineService.handleClick");
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
        boolean topSlot = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (event.isShiftClick() && !topSlot) {
            if (moveShiftClickedStackToInputs(event.getView().getTopInventory(), event.getCurrentItem(), clickDefinition)) {
                if (event.getCurrentItem() != null && event.getCurrentItem().getAmount() <= 0) {
                    event.setCurrentItem(null);
                }
                event.setCancelled(true);
                traceChargingBench(clickDefinition, "shift-input accepted current=" + describe(event.getCurrentItem()));
                energy.runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
            } else {
                event.setCancelled(true);
                traceChargingBench(clickDefinition, "shift-input rejected current=" + describe(event.getCurrentItem()));
            }
            return;
        }
        if (topSlot && (contains(INPUT_SLOTS, event.getRawSlot()) || contains(OUTPUT_SLOTS, event.getRawSlot()))) {
            handleStorageSlotClick(event, holder, player, clickDefinition);
            return;
        }
        if (SfxInventoryPolicy.cancelDangerousClick(event)) {
            traceChargingBench(clickDefinition, "dangerous-click cancelled action=" + event.getAction() + " click=" + event.getClick());
            return;
        }
        if (topSlot && !contains(INPUT_SLOTS, event.getRawSlot())) {
            event.setCancelled(true);
            traceChargingBench(clickDefinition, "non-editable-slot cancelled raw=" + event.getRawSlot());
            return;
        }
        energy.runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
    }

    private void handleStorageSlotClick(InventoryClickEvent event, SfxEnergyGeneratorHolder holder, Player player, SfxEnergyComponentDefinition definition) {
        event.setCancelled(true);
        if (event.getClick() == ClickType.MIDDLE || event.getAction() == InventoryAction.CLONE_STACK) {
            traceChargingBench(definition, "clone-click cancelled raw=" + event.getRawSlot());
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        boolean inputSlot = contains(INPUT_SLOTS, rawSlot);
        boolean outputSlot = contains(OUTPUT_SLOTS, rawSlot);
        syncTopInventoryToState(holder.instanceId(), topInventory);

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (moveTopSlotToPlayer(topInventory, rawSlot, player)) {
                commitTopInventory(holder.instanceId(), topInventory);
                traceChargingBench(definition, (inputSlot ? "shift-input-take" : "shift-output-take") + " accepted raw=" + rawSlot);
            } else {
                traceChargingBench(definition, (inputSlot ? "shift-input-take" : "shift-output-take") + " rejected raw=" + rawSlot);
            }
            return;
        }

        if (outputSlot) {
            if (takeFromSlotToCursor(event, topInventory, rawSlot)) {
                commitTopInventory(holder.instanceId(), topInventory);
            } else {
                traceChargingBench(definition, "output-click cancelled raw=" + rawSlot + " current=" + describe(topInventory.getItem(rawSlot)) + " cursor=" + describe(event.getCursor()));
            }
            return;
        }

        if (!inputSlot) {
            return;
        }
        if (handleInputSlotCursorTransaction(event, topInventory, rawSlot, definition)) {
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
        boolean onlyEditable = event.getRawSlots().stream()
                .filter(slot -> slot < topSize)
                .allMatch(slot -> contains(INPUT_SLOTS, slot));
        if (!onlyEditable) {
            event.setCancelled(true);
            return;
        }
        if (dragDefinition.isCharger()) {
            boolean valid = event.getNewItems().entrySet().stream()
                    .filter(entry -> entry.getKey() < topSize)
                    .allMatch(entry -> contains(INPUT_SLOTS, entry.getKey()) && isValidChargingBenchInput(entry.getValue()));
            if (!valid) {
                event.setCancelled(true);
                traceChargingBench(dragDefinition, "drag-invalid cancelled rawSlots=" + event.getRawSlots());
                return;
            }
        }
        energy.runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.instanceId()));
    }

    private void handleClose(InventoryCloseEvent event, SfxEnergyGeneratorHolder holder) {
        SfxEnergyComponentDefinition closeDefinition = energy.definitionFor(holder.instanceId());
        if (closeDefinition != null) {
            SfxMachineLegacyHookBridge.menuClose(energy.machineRuntime, closeDefinition.id(), holder.instanceId(), null, "energy-machine", "SfxEnergyMachineService.handleClose");
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
        if (session != null) {
            renderer.renderStorageSlots(session.inventory(), state);
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
        syncInventoryToState(session.inventory(), state);
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
        syncInventoryToState(session.inventory(), state);
        energy.markDirty(session.instanceId());
    }

    private SfxMachineStatusKey renderStatus(SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, SfxEnergyNodeState state) {
        SfxEnergyGridStatus gridStatus = energy.nodeGridStatuses.get(instance.instanceId());
        if (gridStatus == SfxEnergyGridStatus.SHARED_NODE_CONFLICT || gridStatus == SfxEnergyGridStatus.MULTIPLE_REGULATORS) {
            return SfxMachineStatusKey.NETWORK_CONFLICT;
        }
        boolean connected = gridStatus == SfxEnergyGridStatus.ONLINE;
        SfxEnergyFuelMatch fuelMatch = definition.isSolarGenerator() ? null : energy.findFuelMatch(definition, state);
        boolean hasFuelLoaded = definition.isSolarGenerator() || state.hasActiveFuel() || fuelMatch != null;
        if (!connected && hasFuelLoaded) {
            return SfxMachineStatusKey.NO_NETWORK;
        }
        if (state.hasPendingOutput() && energy.findOutputSlot(state, state.pendingOutput()) == null) {
            return SfxMachineStatusKey.OUTPUT_FULL;
        }
        if (!state.hasActiveFuel() && fuelMatch != null && fuelMatch.output() != null && energy.findOutputSlot(state, fuelMatch.output()) == null) {
            return SfxMachineStatusKey.OUTPUT_FULL;
        }
        if (!state.hasActiveFuel()) {
            return SfxMachineStatusKey.IDLE;
        }
        return SfxMachineStatusKey.WORKING;
    }

    private void render(SfxEnergyGeneratorSession session, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state) {
        renderer.render(definition, inventory, state, renderStatus(instance, definition, state));
    }

    private void renderStatusOnly(SfxEnergyGeneratorSession session, SfxBlockInstanceRecord instance, SfxEnergyComponentDefinition definition, Inventory inventory, SfxEnergyNodeState state) {
        renderer.renderStatusOnly(definition, inventory, state, renderStatus(instance, definition, state));
    }

    private void syncInventoryToState(Inventory inventory, SfxEnergyNodeState state) {
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            state.input(i, SfxElectricStack.fromItemStack(energy.items, inventory.getItem(INPUT_SLOTS[i])));
        }
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            state.output(i, SfxElectricStack.fromItemStack(energy.items, inventory.getItem(OUTPUT_SLOTS[i])));
        }
    }

    private void syncTopInventoryToState(UUID instanceId, Inventory inventory) {
        SfxBlockInstanceRecord instance = energy.blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyNodeState state = energy.currentState(instanceId, instance);
        syncInventoryToState(inventory, state);
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
        syncInventoryToState(inventory, state);
        energy.markDirty(instanceId);
        energy.markActive(instanceId);
        SfxEnergyGeneratorSession session = sessionsByInstance.get(instanceId);
        if (session != null) {
            render(session, instance, definition, session.inventory(), state);
        }
    }

    private boolean moveTopSlotToPlayer(Inventory topInventory, int rawSlot, Player player) {
        ItemStack current = topInventory.getItem(rawSlot);
        if (SfxInventoryPolicy.isEmpty(current)) {
            return false;
        }
        ItemStack moving = current.clone();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(moving);
        int leftoverAmount = leftovers.values().stream()
                .filter(stack -> !SfxInventoryPolicy.isEmpty(stack))
                .mapToInt(ItemStack::getAmount)
                .sum();
        int moved = current.getAmount() - leftoverAmount;
        if (moved <= 0) {
            return false;
        }
        if (leftoverAmount <= 0) {
            topInventory.setItem(rawSlot, null);
        } else {
            ItemStack remaining = current.clone();
            remaining.setAmount(leftoverAmount);
            topInventory.setItem(rawSlot, remaining);
        }
        return true;
    }

    private boolean takeFromSlotToCursor(InventoryClickEvent event, Inventory topInventory, int rawSlot) {
        ItemStack current = topInventory.getItem(rawSlot);
        if (SfxInventoryPolicy.isEmpty(current) || !SfxInventoryPolicy.isEmpty(event.getCursor())) {
            return false;
        }
        int amount;
        if (event.getAction() == InventoryAction.PICKUP_HALF) {
            amount = (current.getAmount() + 1) / 2;
        } else if (event.getAction() == InventoryAction.PICKUP_ONE) {
            amount = 1;
        } else if (event.getAction() == InventoryAction.PICKUP_ALL || event.getAction() == InventoryAction.PICKUP_SOME) {
            amount = current.getAmount();
        } else {
            return false;
        }
        ItemStack cursor = current.clone();
        cursor.setAmount(amount);
        int remainingAmount = current.getAmount() - amount;
        if (remainingAmount <= 0) {
            topInventory.setItem(rawSlot, null);
        } else {
            ItemStack remaining = current.clone();
            remaining.setAmount(remainingAmount);
            topInventory.setItem(rawSlot, remaining);
        }
        event.setCursor(cursor);
        return true;
    }

    private boolean handleInputSlotCursorTransaction(InventoryClickEvent event, Inventory topInventory, int rawSlot, SfxEnergyComponentDefinition definition) {
        InventoryAction action = event.getAction();
        ItemStack current = topInventory.getItem(rawSlot);
        ItemStack cursor = event.getCursor();
        if (SfxInventoryPolicy.isEmpty(cursor)) {
            return takeFromSlotToCursor(event, topInventory, rawSlot);
        }
        if (!isValidInputStack(definition, cursor)) {
            return false;
        }
        if (action == InventoryAction.SWAP_WITH_CURSOR) {
            if (!SfxInventoryPolicy.isEmpty(current) && !isValidInputStack(definition, current)) {
                return false;
            }
            topInventory.setItem(rawSlot, cursor.clone());
            event.setCursor(SfxInventoryPolicy.isEmpty(current) ? null : current.clone());
            return true;
        }
        if (action != InventoryAction.PLACE_ALL && action != InventoryAction.PLACE_ONE && action != InventoryAction.PLACE_SOME) {
            return false;
        }
        if (!SfxInventoryPolicy.isEmpty(current) && !current.isSimilar(cursor)) {
            return false;
        }
        int room = SfxInventoryPolicy.isEmpty(current) ? cursor.getMaxStackSize() : current.getMaxStackSize() - current.getAmount();
        if (room <= 0) {
            return false;
        }
        int requested = action == InventoryAction.PLACE_ONE ? 1 : cursor.getAmount();
        int moved = Math.min(room, requested);
        ItemStack updated = SfxInventoryPolicy.isEmpty(current) ? cursor.clone() : current.clone();
        updated.setAmount((SfxInventoryPolicy.isEmpty(current) ? 0 : current.getAmount()) + moved);
        if (!isValidInputStack(definition, updated)) {
            return false;
        }
        topInventory.setItem(rawSlot, updated);
        int cursorRemaining = cursor.getAmount() - moved;
        if (cursorRemaining <= 0) {
            event.setCursor(null);
        } else {
            ItemStack remainingCursor = cursor.clone();
            remainingCursor.setAmount(cursorRemaining);
            event.setCursor(remainingCursor);
        }
        return true;
    }

    private boolean moveShiftClickedStackToInputs(Inventory topInventory, ItemStack current, SfxEnergyComponentDefinition definition) {
        return SfxInventorySlots.moveStackToSlots(topInventory, INPUT_SLOTS, current, (slot, stack) -> !definition.isCharger() || isValidChargingBenchInput(stack));
    }

    private boolean isValidInputStack(SfxEnergyComponentDefinition definition, ItemStack item) {
        return definition == null || !definition.isCharger() || isValidChargingBenchInput(item);
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
