package cc.theends6.sfx.internal.virtualcontainer;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.inventory.SfxInventoryAccessState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxVirtualContainerService implements Listener {
    public record SlotTake(int slot, ItemStack template, int amount) {
    }

    public record PlannedStack(ItemStack stack, List<SlotTake> takes) {
        public boolean isEmpty() {
            return stack == null || stack.getType().isAir() || stack.getAmount() <= 0 || takes == null || takes.isEmpty();
        }
    }

    public record IngredientRequest(Predicate<ItemStack> matcher, int amount) {
        public IngredientRequest {
            amount = Math.max(1, amount);
        }
    }

    public enum CraftingTransactionStatus {
        SUCCESS,
        NO_CONTAINER,
        MISSING_INPUT,
        OUTPUT_FULL,
        BUSY
    }

    public record CraftingTransactionResult(CraftingTransactionStatus status) {
        public boolean success() {
            return status == CraftingTransactionStatus.SUCCESS;
        }
    }

    private static final long EXTERNAL_SYNC_INTERVAL = 10L;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final Map<SfxVirtualContainerKey, SfxVirtualContainer> containers = new ConcurrentHashMap<>();
    private final Map<SfxBlockAnchorKey, SfxVirtualContainerKey> locationIndex = new ConcurrentHashMap<>();
    private final AtomicLong localTickClock = new AtomicLong();
    private volatile long registryRevision;
    private volatile boolean running = true;

    public SfxVirtualContainerService(JavaPlugin plugin, SfxRuntime runtime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        scheduleExternalSync();
    }

    public Optional<SfxVirtualContainer> ensureRegistered(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        if (!owns(location)) {
            return runtime.supplyAt(location, () -> ensureRegistered(location));
        }
        Optional<SfxVirtualContainer> registered = findRegistered(location);
        if (registered.isPresent()) {
            SfxVirtualContainer container = registered.get();
            container.cargoAttached(true);
            return registered;
        }
        Inventory inventory = inventoryAt(location).orElse(null);
        if (inventory == null) {
            return Optional.empty();
        }
        SfxVirtualContainerKey key = keyForInventory(inventory, location).orElseGet(() -> SfxVirtualContainerKey.single(location));
        SfxVirtualContainer container = containers.computeIfAbsent(key, ignored -> new SfxVirtualContainer(key, inventory.getSize()));
        registerIndex(container.key());
        registryRevision++;
        container.cargoAttached(true);
        reconcileBeforeAccess(container, inventory);
        return Optional.of(container);
    }


    public CompletableFuture<Optional<SfxVirtualContainer>> ensureRegisteredAsync(Location location) {
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (owns(location)) {
            return CompletableFuture.completedFuture(ensureRegistered(location));
        }
        return runtime.supplyAtAsync(location, () -> ensureRegistered(location));
    }

    public Optional<SfxVirtualContainer> findRegistered(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxVirtualContainerKey indexed = locationIndex.get(key);
        if (indexed == null) {
            return Optional.empty();
        }
        SfxVirtualContainer container = containers.get(indexed);
        if (container == null) {
            locationIndex.remove(key, indexed);
            return Optional.empty();
        }
        return Optional.of(container);
    }

    public Collection<SfxVirtualContainer> containers() {
        return List.copyOf(containers.values());
    }

    public long registryRevision() {
        return registryRevision;
    }

    public void hydrateExternalBeforeLogic() {
        for (SfxVirtualContainer container : containers.values()) {
            if (!container.externalDirty() && !container.mirrorDirty() && container.revision() != 0L) {
                continue;
            }
            runAtContainer(container, () -> inventoryFor(container).ifPresent(inventory -> reconcileBeforeAccess(container, inventory)));
        }
    }

    public void pushDirtyAfterLogic() {
        for (SfxVirtualContainer container : containers.values()) {
            if (!container.mirrorDirty()) {
                continue;
            }
            // Keep the real vanilla container close to the SFX mirror after every cargo tick.
            // This is deliberately conservative: it prevents hoppers/viewers from reading a
            // stale world inventory and duplicating items that only existed in the mirror.
            runAtContainer(container, () -> inventoryFor(container).ifPresent(inventory -> syncToWorld(container, inventory)));
        }
    }

    public void flushAllToWorld() {
        for (SfxVirtualContainer container : containers.values()) {
            runAtContainer(container, () -> inventoryFor(container).ifPresent(inventory -> syncToWorld(container, inventory)));
        }
    }


    public CraftingTransactionResult checkCraftingTransaction(Location location, List<IngredientRequest> ingredients, List<ItemStack> outputs) {
        if (location != null && location.getWorld() != null && !owns(location)) {
            return runtime.supplyAt(location, () -> checkCraftingTransaction(location, ingredients, outputs));
        }
        SfxVirtualContainer container = ensureRegistered(location).orElse(null);
        if (container == null) {
            return new CraftingTransactionResult(CraftingTransactionStatus.NO_CONTAINER);
        }
        Inventory inventory = inventoryFor(container).orElse(null);
        if (inventory == null) {
            return new CraftingTransactionResult(CraftingTransactionStatus.NO_CONTAINER);
        }
        if (!reconcileBeforeTransaction(container, inventory)) {
            return new CraftingTransactionResult(CraftingTransactionStatus.BUSY);
        }
        ItemStack[] simulated = container.snapshot();
        CraftingTransactionStatus status = simulateCrafting(simulated, ingredients, outputs);
        return new CraftingTransactionResult(status);
    }


    public CompletableFuture<CraftingTransactionResult> checkCraftingTransactionAsync(Location location, List<IngredientRequest> ingredients, List<ItemStack> outputs) {
        if (location != null && location.getWorld() != null && !owns(location)) {
            return runtime.supplyAtAsync(location, () -> checkCraftingTransaction(location, ingredients, outputs));
        }
        return CompletableFuture.completedFuture(checkCraftingTransaction(location, ingredients, outputs));
    }

    public CompletableFuture<CraftingTransactionResult> commitCraftingTransactionAsync(Location location, List<IngredientRequest> ingredients, List<ItemStack> outputs) {
        if (location != null && location.getWorld() != null && !owns(location)) {
            return runtime.supplyAtAsync(location, () -> commitCraftingTransaction(location, ingredients, outputs));
        }
        return CompletableFuture.completedFuture(commitCraftingTransaction(location, ingredients, outputs));
    }

    public CraftingTransactionResult commitCraftingTransaction(Location location, List<IngredientRequest> ingredients, List<ItemStack> outputs) {
        if (location != null && location.getWorld() != null && !owns(location)) {
            return runtime.supplyAt(location, () -> commitCraftingTransaction(location, ingredients, outputs));
        }
        SfxVirtualContainer container = ensureRegistered(location).orElse(null);
        if (container == null) {
            return new CraftingTransactionResult(CraftingTransactionStatus.NO_CONTAINER);
        }
        Inventory inventory = inventoryFor(container).orElse(null);
        if (inventory == null) {
            return new CraftingTransactionResult(CraftingTransactionStatus.NO_CONTAINER);
        }
        if (!reconcileBeforeTransaction(container, inventory)) {
            return new CraftingTransactionResult(CraftingTransactionStatus.BUSY);
        }
        ItemStack[] before = container.snapshot();
        ItemStack[] after = cloneContents(before);
        CraftingTransactionStatus status = simulateCrafting(after, ingredients, outputs);
        if (status != CraftingTransactionStatus.SUCCESS) {
            return new CraftingTransactionResult(status);
        }
        if (!commitSlotDifferences(container, before, after)) {
            return new CraftingTransactionResult(CraftingTransactionStatus.BUSY);
        }
        syncToWorld(container, inventory);
        return new CraftingTransactionResult(CraftingTransactionStatus.SUCCESS);
    }

    private boolean reconcileBeforeTransaction(SfxVirtualContainer container, Inventory inventory) {
        if (container == null || inventory == null) {
            return false;
        }
        if (container.externalDirty() && container.externalFinalizationPending()) {
            // Vanilla inventory mutations may be finalized after the event callback.
            // Defer this machine transaction by one tick so the mirror can hydrate from the final state.
            return false;
        }
        if (container.externalDirty()) {
            hydrate(container, inventory);
        } else if (container.mirrorDirty()) {
            syncToWorld(container, inventory);
        } else if (container.viewerActive() || container.externalActive() || container.revision() == 0L) {
            hydrate(container, inventory);
        }
        return true;
    }

    private CraftingTransactionStatus simulateCrafting(ItemStack[] simulated, List<IngredientRequest> ingredients, List<ItemStack> outputs) {
        if (simulated == null || simulated.length == 0) {
            return CraftingTransactionStatus.NO_CONTAINER;
        }
        for (IngredientRequest request : ingredients == null ? List.<IngredientRequest>of() : ingredients) {
            if (!consumeIngredient(simulated, request)) {
                return CraftingTransactionStatus.MISSING_INPUT;
            }
        }
        for (ItemStack output : outputs == null ? List.<ItemStack>of() : outputs) {
            if (!placeStack(simulated, output)) {
                return CraftingTransactionStatus.OUTPUT_FULL;
            }
        }
        return CraftingTransactionStatus.SUCCESS;
    }

    private boolean consumeIngredient(ItemStack[] contents, IngredientRequest request) {
        if (request == null || request.matcher() == null || request.amount() <= 0) {
            return true;
        }
        int remaining = request.amount();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (isEmpty(stack) || !request.matcher().test(stack)) {
                continue;
            }
            int taken = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - taken);
            remaining -= taken;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        return remaining <= 0;
    }

    private boolean placeStack(ItemStack[] contents, ItemStack output) {
        if (isEmpty(output)) {
            return true;
        }
        ItemStack remaining = output.clone();
        // Match normal inventory insertion semantics: top up existing stacks first, then use empty slots.
        for (ItemStack stack : contents) {
            if (isEmpty(stack) || !stack.isSimilar(remaining)) {
                continue;
            }
            int room = Math.max(0, Math.min(stack.getMaxStackSize(), remaining.getMaxStackSize()) - stack.getAmount());
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining.getAmount());
            stack.setAmount(stack.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                return true;
            }
        }
        for (int i = 0; i < contents.length && remaining.getAmount() > 0; i++) {
            if (!isEmpty(contents[i])) {
                continue;
            }
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            contents[i] = placed;
            remaining.setAmount(remaining.getAmount() - moved);
        }
        return remaining.getAmount() <= 0;
    }

    private boolean commitSlotDifferences(SfxVirtualContainer container, ItemStack[] before, ItemStack[] after) {
        if (container == null || before == null || after == null || before.length != after.length) {
            return false;
        }
        ItemStack[] mirror = container.rawMirror();
        if (mirror.length != before.length) {
            return false;
        }
        for (int i = 0; i < before.length; i++) {
            if (!sameStack(mirror[i], before[i])) {
                return false;
            }
        }
        boolean changed = false;
        for (int i = 0; i < after.length; i++) {
            if (sameStack(before[i], after[i])) {
                continue;
            }
            mirror[i] = after[i] == null ? null : after[i].clone();
            changed = true;
        }
        if (changed) {
            container.mirrorDirty(true);
        }
        return true;
    }

    private boolean sameStack(ItemStack a, ItemStack b) {
        if (isEmpty(a) || isEmpty(b)) {
            return isEmpty(a) && isEmpty(b);
        }
        return a.getAmount() == b.getAmount() && a.isSimilar(b);
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        if (contents == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }


    private SfxInventoryAccessState reconcileForMemoryAccess(SfxVirtualContainer container) {
        if (container == null) {
            return SfxInventoryAccessState.UNAVAILABLE;
        }
        if (container.externalFinalizationPending()) {
            // A player/hopper/container event marked this inventory dirty, but Bukkit may not
            // have finalized the vanilla inventory mutation yet. Never let cargo or machine
            // logic read the old mirror during this one-tick window.
            runAtContainerLater(container, 1L, () -> inventoryFor(container).ifPresent(inventory -> reconcileBeforeAccess(container, inventory)));
            return SfxInventoryAccessState.BUSY_EXTERNAL_FINALIZATION;
        }
        if (!container.externalDirty()
                && !container.mirrorDirty()
                && !container.externalActive()
                && !container.viewerActive()
                && container.revision() != 0L) {
            return SfxInventoryAccessState.READY;
        }
        Location location = primaryLocation(container);
        if (location == null) {
            return SfxInventoryAccessState.UNAVAILABLE;
        }
        if (!owns(location)) {
            runtime.executeAt(location, () -> reconcileForMemoryAccess(container));
            return SfxInventoryAccessState.BUSY_WRONG_REGION;
        }
        Optional<Inventory> inventory = inventoryFor(container);
        if (inventory.isEmpty()) {
            return SfxInventoryAccessState.UNAVAILABLE;
        }
        reconcileBeforeAccess(container, inventory.get());
        return container.externalFinalizationPending() ? SfxInventoryAccessState.BUSY_EXTERNAL_FINALIZATION : SfxInventoryAccessState.READY;
    }

    private boolean isMemoryReady(SfxVirtualContainer container) {
        return reconcileForMemoryAccess(container) == SfxInventoryAccessState.READY;
    }

    private void pushIfDirty(SfxVirtualContainer container) {
        if (container == null || !container.mirrorDirty()) {
            return;
        }
        Location location = primaryLocation(container);
        if (location == null) {
            return;
        }
        if (!owns(location)) {
            runtime.executeAt(location, () -> pushIfDirty(container));
            return;
        }
        inventoryFor(container).ifPresent(inventory -> syncToWorld(container, inventory));
    }


    public synchronized PlannedStack planFirst(SfxVirtualContainer container, java.util.function.Predicate<ItemStack> filter, int maxAmount) {
        if (!isMemoryReady(container)) {
            return new PlannedStack(null, List.of());
        }
        ItemStack[] mirror = container.rawMirror();
        for (int i = 0; i < mirror.length; i++) {
            ItemStack stack = mirror[i];
            if (isEmpty(stack) || (filter != null && !filter.test(stack))) {
                continue;
            }
            int amount = Math.max(1, Math.min(Math.min(maxAmount, stack.getMaxStackSize()), stack.getAmount()));
            ItemStack planned = stack.clone();
            planned.setAmount(amount);
            ItemStack template = stack.clone();
            template.setAmount(1);
            return new PlannedStack(planned, List.of(new SlotTake(i, template, amount)));
        }
        return new PlannedStack(null, List.of());
    }

    public synchronized List<PlannedStack> planBatch(SfxVirtualContainer container, java.util.function.Predicate<ItemStack> filter, int maxItems, int maxDistinctTypes, boolean allowMultipleSlots) {
        List<PlannedStack> result = new ArrayList<>();
        if (maxItems <= 0 || !isMemoryReady(container)) {
            return result;
        }
        int remaining = Math.min(128, maxItems);
        Map<String, ItemStack> grouped = new LinkedHashMap<>();
        Map<String, List<SlotTake>> takes = new LinkedHashMap<>();
        ItemStack[] mirror = container.rawMirror();
        for (int i = 0; i < mirror.length && remaining > 0; i++) {
            ItemStack stack = mirror[i];
            if (isEmpty(stack) || (filter != null && !filter.test(stack))) {
                continue;
            }
            String key = itemKey(stack);
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
            takes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new SlotTake(i, template, amount));
            remaining -= amount;
            if (!allowMultipleSlots) {
                break;
            }
        }
        for (Map.Entry<String, ItemStack> entry : grouped.entrySet()) {
            result.add(new PlannedStack(entry.getValue(), List.copyOf(takes.getOrDefault(entry.getKey(), List.of()))));
        }
        return result;
    }

    public synchronized boolean canRemovePlanned(SfxVirtualContainer container, List<SlotTake> takes) {
        if (takes == null || takes.isEmpty() || !isMemoryReady(container)) {
            return false;
        }
        ItemStack[] mirror = container.rawMirror();
        for (SlotTake take : takes) {
            if (take == null || take.amount() <= 0 || take.slot() < 0 || take.slot() >= mirror.length) {
                return false;
            }
            ItemStack stack = mirror[take.slot()];
            if (isEmpty(stack) || !stack.isSimilar(take.template()) || stack.getAmount() < take.amount()) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean removePlanned(SfxVirtualContainer container, List<SlotTake> takes) {
        if (!canRemovePlanned(container, takes)) {
            return false;
        }
        ItemStack[] mirror = container.rawMirror();
        for (SlotTake take : takes) {
            ItemStack stack = mirror[take.slot()];
            stack.setAmount(stack.getAmount() - take.amount());
            if (stack.getAmount() <= 0) {
                mirror[take.slot()] = null;
            }
        }
        container.mirrorDirty(true);
        return true;
    }

    public synchronized ItemStack peekFirst(SfxVirtualContainer container, java.util.function.Predicate<ItemStack> filter, int maxAmount) {
        if (!isMemoryReady(container)) {
            return null;
        }
        ItemStack[] mirror = container.rawMirror();
        for (ItemStack stack : mirror) {
            if (isEmpty(stack) || (filter != null && !filter.test(stack))) {
                continue;
            }
            int amount = Math.max(1, Math.min(Math.min(maxAmount, stack.getMaxStackSize()), stack.getAmount()));
            ItemStack taken = stack.clone();
            taken.setAmount(amount);
            return taken;
        }
        return null;
    }

    public synchronized List<ItemStack> peekBatch(SfxVirtualContainer container, java.util.function.Predicate<ItemStack> filter, int maxItems, int maxDistinctTypes, boolean allowMultipleSlots) {
        List<ItemStack> result = new ArrayList<>();
        if (maxItems <= 0 || !isMemoryReady(container)) {
            return result;
        }
        int remaining = Math.min(128, maxItems);
        Map<String, ItemStack> grouped = new LinkedHashMap<>();
        ItemStack[] mirror = container.rawMirror();
        for (ItemStack stack : mirror) {
            if (remaining <= 0) {
                break;
            }
            if (isEmpty(stack) || (filter != null && !filter.test(stack))) {
                continue;
            }
            String key = itemKey(stack);
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
            remaining -= amount;
            if (!allowMultipleSlots) {
                break;
            }
        }
        result.addAll(grouped.values());
        return result;
    }

    public synchronized int removeSimilar(SfxVirtualContainer container, ItemStack template, int amount) {
        if (isEmpty(template) || amount <= 0 || !isMemoryReady(container)) {
            return 0;
        }
        int remaining = amount;
        ItemStack[] mirror = container.rawMirror();
        for (int i = 0; i < mirror.length && remaining > 0; i++) {
            ItemStack stack = mirror[i];
            if (isEmpty(stack) || !stack.isSimilar(template)) {
                continue;
            }
            int moved = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - moved);
            remaining -= moved;
            if (stack.getAmount() <= 0) {
                mirror[i] = null;
            }
        }
        int removed = amount - remaining;
        if (removed > 0) {
            container.mirrorDirty(true);
        }
        return removed;
    }

    public synchronized ItemStack withdrawFirst(SfxVirtualContainer container, java.util.function.Predicate<ItemStack> filter, int maxAmount) {
        if (!isMemoryReady(container)) {
            return null;
        }
        ItemStack[] mirror = container.rawMirror();
        for (int i = 0; i < mirror.length; i++) {
            ItemStack stack = mirror[i];
            if (isEmpty(stack) || (filter != null && !filter.test(stack))) {
                continue;
            }
            int amount = Math.max(1, Math.min(Math.min(maxAmount, stack.getMaxStackSize()), stack.getAmount()));
            ItemStack taken = stack.clone();
            taken.setAmount(amount);
            stack.setAmount(stack.getAmount() - amount);
            if (stack.getAmount() <= 0) {
                mirror[i] = null;
            }
            container.mirrorDirty(true);
            return taken;
        }
        return null;
    }

    public synchronized List<ItemStack> withdrawBatch(SfxVirtualContainer container, java.util.function.Predicate<ItemStack> filter, int maxItems, int maxDistinctTypes) {
        List<ItemStack> result = new ArrayList<>();
        if (maxItems <= 0 || !isMemoryReady(container)) {
            return result;
        }
        int remaining = Math.min(128, maxItems);
        Map<String, ItemStack> grouped = new LinkedHashMap<>();
        ItemStack[] mirror = container.rawMirror();
        for (int i = 0; i < mirror.length && remaining > 0; i++) {
            ItemStack stack = mirror[i];
            if (isEmpty(stack) || (filter != null && !filter.test(stack))) {
                continue;
            }
            String key = itemKey(stack);
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
            stack.setAmount(stack.getAmount() - amount);
            remaining -= amount;
            if (stack.getAmount() <= 0) {
                mirror[i] = null;
            }
        }
        if (!grouped.isEmpty()) {
            result.addAll(grouped.values());
            container.mirrorDirty(true);
        }
        return result;
    }

    public synchronized ItemStack insert(SfxVirtualContainer container, ItemStack input, boolean smartFill) {
        if (isEmpty(input)) {
            return null;
        }
        if (!isMemoryReady(container)) {
            return input;
        }
        int originalAmount = input.getAmount();
        ItemStack remaining = input.clone();
        ItemStack[] mirror = container.rawMirror();
        if (smartFill) {
            boolean hasSimilar = hasSimilar(mirror, remaining);
            if (hasSimilar) {
                remaining = fillExisting(mirror, remaining);
            } else {
                remaining = fillEmptyOnly(mirror, remaining);
            }
        } else {
            remaining = fillEmptyOrExisting(mirror, remaining, true);
        }
        int remainingAmount = isEmpty(remaining) ? 0 : remaining.getAmount();
        if (remainingAmount != originalAmount) {
            container.mirrorDirty(true);
        }
        return isEmpty(remaining) ? null : remaining;
    }

    /**
     * Inserts into at most one inventory slot. This is used by the classic/basic Cargo Input Node
     * to preserve the single-slot output semantics instead of spreading one transfer across several slots.
     */
    public synchronized ItemStack insertSingleSlot(SfxVirtualContainer container, ItemStack input, boolean smartFill) {
        if (isEmpty(input)) {
            return null;
        }
        if (!isMemoryReady(container)) {
            return input;
        }
        int originalAmount = input.getAmount();
        ItemStack remaining = input.clone();
        ItemStack[] mirror = container.rawMirror();
        if (smartFill) {
            boolean hasSimilar = hasSimilar(mirror, remaining);
            remaining = hasSimilar ? fillOneExistingSlot(mirror, remaining) : fillOneEmptySlot(mirror, remaining);
        } else {
            remaining = fillOneEmptyOrExistingSlot(mirror, remaining);
        }
        int remainingAmount = isEmpty(remaining) ? 0 : remaining.getAmount();
        if (remainingAmount != originalAmount) {
            container.mirrorDirty(true);
        }
        return isEmpty(remaining) ? null : remaining;
    }

    public synchronized int capacityFor(SfxVirtualContainer container, ItemStack probe, boolean smartFill) {
        if (isEmpty(probe) || !isMemoryReady(container)) {
            return 0;
        }
        ItemStack[] mirror = container.rawMirror();
        if (smartFill) {
            int existingCapacity = existingCapacity(mirror, probe);
            if (hasSimilar(mirror, probe)) {
                return existingCapacity;
            }
            return emptyCapacity(mirror, probe);
        }
        int capacity = 0;
        for (ItemStack stack : mirror) {
            if (isEmpty(stack)) {
                capacity += probe.getMaxStackSize();
                continue;
            }
            if (stack.isSimilar(probe)) {
                capacity += Math.max(0, Math.min(stack.getMaxStackSize(), probe.getMaxStackSize()) - stack.getAmount());
            }
        }
        return capacity;
    }

    /**
     * Capacity in the first slot that would be used by {@link #insertSingleSlot}.
     */
    public synchronized int capacityForSingleSlot(SfxVirtualContainer container, ItemStack probe, boolean smartFill) {
        if (isEmpty(probe) || !isMemoryReady(container)) {
            return 0;
        }
        ItemStack[] mirror = container.rawMirror();
        if (smartFill) {
            boolean hasSimilar = hasSimilar(mirror, probe);
            if (hasSimilar) {
                for (ItemStack stack : mirror) {
                    if (!isEmpty(stack) && stack.isSimilar(probe)) {
                        int capacity = Math.max(0, Math.min(stack.getMaxStackSize(), probe.getMaxStackSize()) - stack.getAmount());
                        if (capacity > 0) {
                            return capacity;
                        }
                    }
                }
                return 0;
            }
            return firstEmptyCapacity(mirror, probe);
        }
        for (ItemStack stack : mirror) {
            if (isEmpty(stack)) {
                return probe.getMaxStackSize();
            }
            if (stack.isSimilar(probe)) {
                int capacity = Math.max(0, Math.min(stack.getMaxStackSize(), probe.getMaxStackSize()) - stack.getAmount());
                if (capacity > 0) {
                    return capacity;
                }
            }
        }
        return 0;
    }

    private boolean hasSimilar(ItemStack[] mirror, ItemStack probe) {
        for (ItemStack stack : mirror) {
            if (!isEmpty(stack) && stack.isSimilar(probe)) {
                return true;
            }
        }
        return false;
    }

    private int existingCapacity(ItemStack[] mirror, ItemStack probe) {
        int capacity = 0;
        for (ItemStack stack : mirror) {
            if (!isEmpty(stack) && stack.isSimilar(probe)) {
                capacity += Math.max(0, Math.min(stack.getMaxStackSize(), probe.getMaxStackSize()) - stack.getAmount());
            }
        }
        return capacity;
    }

    private int emptyCapacity(ItemStack[] mirror, ItemStack probe) {
        int capacity = 0;
        for (ItemStack stack : mirror) {
            if (isEmpty(stack)) {
                capacity += probe.getMaxStackSize();
            }
        }
        return capacity;
    }

    private int firstEmptyCapacity(ItemStack[] mirror, ItemStack probe) {
        for (ItemStack stack : mirror) {
            if (isEmpty(stack)) {
                return probe.getMaxStackSize();
            }
        }
        return 0;
    }

    private ItemStack fillExisting(ItemStack[] mirror, ItemStack input) {
        ItemStack remaining = input.clone();
        for (ItemStack stack : mirror) {
            if (isEmpty(stack) || !stack.isSimilar(remaining)) {
                continue;
            }
            int limit = Math.min(stack.getMaxStackSize(), remaining.getMaxStackSize());
            int moved = Math.min(remaining.getAmount(), Math.max(0, limit - stack.getAmount()));
            if (moved <= 0) {
                continue;
            }
            stack.setAmount(stack.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }
        return remaining;
    }

    private ItemStack fillEmptyOnly(ItemStack[] mirror, ItemStack input) {
        ItemStack remaining = input.clone();
        for (int i = 0; i < mirror.length; i++) {
            if (!isEmpty(mirror[i])) {
                continue;
            }
            int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            mirror[i] = placed;
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }
        return remaining;
    }

    private ItemStack fillOneExistingSlot(ItemStack[] mirror, ItemStack input) {
        ItemStack remaining = input.clone();
        for (ItemStack stack : mirror) {
            if (isEmpty(stack) || !stack.isSimilar(remaining)) {
                continue;
            }
            int limit = Math.min(stack.getMaxStackSize(), remaining.getMaxStackSize());
            int moved = Math.min(remaining.getAmount(), Math.max(0, limit - stack.getAmount()));
            if (moved <= 0) {
                continue;
            }
            stack.setAmount(stack.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            return remaining.getAmount() <= 0 ? null : remaining;
        }
        return remaining;
    }

    private ItemStack fillOneEmptySlot(ItemStack[] mirror, ItemStack input) {
        ItemStack remaining = input.clone();
        for (int i = 0; i < mirror.length; i++) {
            if (!isEmpty(mirror[i])) {
                continue;
            }
            int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            mirror[i] = placed;
            remaining.setAmount(remaining.getAmount() - moved);
            return remaining.getAmount() <= 0 ? null : remaining;
        }
        return remaining;
    }

    private ItemStack fillOneEmptyOrExistingSlot(ItemStack[] mirror, ItemStack input) {
        ItemStack remaining = input.clone();
        for (int i = 0; i < mirror.length; i++) {
            ItemStack stack = mirror[i];
            if (isEmpty(stack)) {
                int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.clone();
                placed.setAmount(moved);
                mirror[i] = placed;
                remaining.setAmount(remaining.getAmount() - moved);
                return remaining.getAmount() <= 0 ? null : remaining;
            }
            if (stack.isSimilar(remaining)) {
                int limit = Math.min(stack.getMaxStackSize(), remaining.getMaxStackSize());
                int moved = Math.min(remaining.getAmount(), Math.max(0, limit - stack.getAmount()));
                if (moved <= 0) {
                    continue;
                }
                stack.setAmount(stack.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
                return remaining.getAmount() <= 0 ? null : remaining;
            }
        }
        return remaining;
    }

    private ItemStack fillEmptyOrExisting(ItemStack[] mirror, ItemStack input, boolean existingAllowed) {
        ItemStack remaining = input.clone();
        for (int i = 0; i < mirror.length; i++) {
            ItemStack stack = mirror[i];
            if (isEmpty(stack)) {
                int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.clone();
                placed.setAmount(moved);
                mirror[i] = placed;
                remaining.setAmount(remaining.getAmount() - moved);
            } else if (existingAllowed && stack.isSimilar(remaining)) {
                int limit = Math.min(stack.getMaxStackSize(), remaining.getMaxStackSize());
                int moved = Math.min(remaining.getAmount(), Math.max(0, limit - stack.getAmount()));
                stack.setAmount(stack.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
            }
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }
        return remaining;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        Location location = holderLocation(inventory.getHolder());
        if (location == null) {
            return;
        }
        Optional<SfxVirtualContainer> optional = findRegistered(location);
        if (optional.isEmpty()) {
            return;
        }
        SfxVirtualContainer container = optional.get();
        container.viewerActive(true);
        reconcileForViewerOpen(container, inventory);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        Location location = holderLocation(inventory.getHolder());
        if (location == null) {
            return;
        }
        Optional<SfxVirtualContainer> optional = findRegistered(location);
        if (optional.isEmpty()) {
            return;
        }
        SfxVirtualContainer container = optional.get();
        if (container.externalDirty() || container.externalActive()) {
            hydrate(container, inventory);
        } else if (container.mirrorDirty()) {
            syncToWorld(container, inventory);
        } else {
            hydrate(container, inventory);
        }
        container.viewerActive(false);
        container.externalActive(false);
        container.externalDirty(false);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        markExternal(event.getInventory());
        if (event.getClickedInventory() != null && event.getClickedInventory() != event.getInventory()) {
            markExternal(event.getClickedInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        markExternal(event.getInventory());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        markExternal(event.getSource());
        markExternal(event.getDestination());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Optional<SfxVirtualContainer> optional = findRegistered(event.getBlock().getLocation());
        if (optional.isEmpty()) {
            return;
        }
        SfxVirtualContainer container = optional.get();
        inventoryFor(container).ifPresent(inventory -> syncToWorld(container, inventory));
        containers.remove(container.key());
        unregisterIndex(container.key());
        registryRevision++;
    }

    public void shutdown() {
        running = false;
        flushAllToWorld();
        containers.clear();
        locationIndex.clear();
        registryRevision++;
    }

    private long advanceLocalTick(long delta) {
        return localTickClock.addAndGet(Math.max(1L, delta));
    }

    private long currentLocalTick() {
        return localTickClock.get();
    }

    private void scheduleExternalSync() {
        runtime.executeGlobalLater(EXTERNAL_SYNC_INTERVAL, () -> {
            if (!running) {
                return;
            }
            long now = advanceLocalTick(EXTERNAL_SYNC_INTERVAL);
            for (SfxVirtualContainer container : containers.values()) {
                if (!container.externalActive() && !container.viewerActive()) {
                    continue;
                }
                runAtContainer(container, () -> inventoryFor(container).ifPresent(inventory -> {
                    if (container.externalDirty() || container.mirrorDirty()) {
                        reconcileBeforeAccess(container, inventory);
                    } else if (now - container.lastWorldSyncTick() >= EXTERNAL_SYNC_INTERVAL && container.viewerActive()) {
                        // When a player is viewing the vanilla inventory, import their manual edits periodically.
                        hydrate(container, inventory);
                    }
                }));
            }
            scheduleExternalSync();
        });
    }

    private void markExternal(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        Location location = holderLocation(inventory.getHolder());
        Optional<SfxVirtualContainer> optional = location == null
                ? keyForInventory(inventory, null).map(containers::get)
                : findRegistered(location);
        optional.ifPresent(container -> {
            container.externalActive(true);
            // The vanilla inventory mutation may be finalized after the event callback.
            // Mark dirty immediately and hydrate on the next tick to import the final player/hopper state.
            container.externalDirty(true);
            container.externalFinalizationPending(true);
            runAtContainerLater(container, 1L, () -> inventoryFor(container).ifPresent(target -> {
                container.externalFinalizationPending(false);
                if (container.externalDirty()) {
                    hydrate(container, target);
                }
            }));
        });
    }

    private void reconcileForViewerOpen(SfxVirtualContainer container, Inventory inventory) {
        if (container == null || inventory == null) {
            return;
        }
        if (container.externalDirty() || container.externalActive()) {
            hydrate(container, inventory);
            return;
        }
        if (container.mirrorDirty()) {
            syncToWorld(container, inventory);
            return;
        }
        if (container.revision() == 0L) {
            hydrate(container, inventory);
            return;
        }
        // Existing mirror is authoritative in cold mode. Push once so the vanilla UI shows it.
        syncToWorld(container, inventory);
    }

    private void reconcileBeforeAccess(SfxVirtualContainer container, Inventory inventory) {
        if (container == null || inventory == null) {
            return;
        }
        if (container.externalDirty()) {
            hydrate(container, inventory);
            return;
        }
        if (container.mirrorDirty()) {
            syncToWorld(container, inventory);
            return;
        }
        if (container.revision() == 0L) {
            hydrate(container, inventory);
        }
    }

    private void hydrate(SfxVirtualContainer container, Inventory inventory) {
        if (container == null || inventory == null) {
            return;
        }
        container.setContents(inventory.getContents());
        container.externalDirty(false);
        container.externalFinalizationPending(false);
        container.mirrorDirty(false);
        container.lastWorldSyncTick(currentLocalTick());
    }

    private void syncToWorld(SfxVirtualContainer container, Inventory inventory) {
        if (container == null || inventory == null) {
            return;
        }
        ItemStack[] snapshot = container.snapshot();
        if (snapshot.length != inventory.getSize()) {
            ItemStack[] resized = new ItemStack[inventory.getSize()];
            System.arraycopy(snapshot, 0, resized, 0, Math.min(snapshot.length, resized.length));
            snapshot = resized;
        }
        inventory.setContents(snapshot);
        container.mirrorDirty(false);
        container.externalDirty(false);
        container.externalFinalizationPending(false);
        container.lastWorldSyncTick(currentLocalTick());
    }

    private Optional<Inventory> inventoryAt(Location location) {
        if (location == null || location.getWorld() == null || !owns(location)) {
            return Optional.empty();
        }
        Block block = location.getBlock();
        if (!isLikelyInventoryBlock(block.getType())) {
            return Optional.empty();
        }
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return Optional.empty();
        }
        return Optional.of(holder.getInventory());
    }

    private Optional<Inventory> inventoryFor(SfxVirtualContainer container) {
        Location location = primaryLocation(container);
        if (location == null || !owns(location)) {
            return Optional.empty();
        }
        Block block = location.getBlock();
        if (!isLikelyInventoryBlock(block.getType())) {
            return Optional.empty();
        }
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return Optional.empty();
        }
        return Optional.of(holder.getInventory());
    }


    private void registerIndex(SfxVirtualContainerKey key) {
        if (key == null) {
            return;
        }
        locationIndex.put(new SfxBlockAnchorKey(key.worldId(), key.x1(), key.y1(), key.z1()), key);
        locationIndex.put(new SfxBlockAnchorKey(key.worldId(), key.x2(), key.y2(), key.z2()), key);
    }

    private void unregisterIndex(SfxVirtualContainerKey key) {
        if (key == null) {
            return;
        }
        locationIndex.remove(new SfxBlockAnchorKey(key.worldId(), key.x1(), key.y1(), key.z1()), key);
        locationIndex.remove(new SfxBlockAnchorKey(key.worldId(), key.x2(), key.y2(), key.z2()), key);
    }

    private boolean isLikelyInventoryBlock(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        if (name.endsWith("SHULKER_BOX")) {
            return true;
        }
        return switch (material) {
            case CHEST,
                    TRAPPED_CHEST,
                    BARREL,
                    HOPPER,
                    DROPPER,
                    DISPENSER,
                    FURNACE,
                    BLAST_FURNACE,
                    SMOKER,
                    BREWING_STAND,
                    CHISELED_BOOKSHELF,
                    DECORATED_POT -> true;
            default -> false;
        };
    }

    private Optional<SfxVirtualContainerKey> keyForInventory(Inventory inventory, Location fallback) {
        if (inventory == null) {
            return Optional.empty();
        }
        if (inventory instanceof DoubleChestInventory doubleChest) {
            Location left = holderLocation(doubleChest.getLeftSide().getHolder());
            Location right = holderLocation(doubleChest.getRightSide().getHolder());
            if (left != null && right != null && left.getWorld() != null && left.getWorld().equals(right.getWorld())) {
                return Optional.of(new SfxVirtualContainerKey(
                        left.getWorld().getUID(),
                        left.getBlockX(), left.getBlockY(), left.getBlockZ(),
                        right.getBlockX(), right.getBlockY(), right.getBlockZ()));
            }
        }
        return fallback == null ? Optional.empty() : Optional.of(SfxVirtualContainerKey.single(fallback));
    }

    private void runAtContainer(SfxVirtualContainer container, Runnable task) {
        Location location = primaryLocation(container);
        if (location == null) {
            return;
        }
        if (owns(location)) {
            task.run();
        } else {
            runtime.executeAt(location, task);
        }
    }

    private void runAtContainerLater(SfxVirtualContainer container, long delayTicks, Runnable task) {
        Location location = primaryLocation(container);
        if (location == null) {
            return;
        }
        runtime.executeAtLater(location, Math.max(1L, delayTicks), task);
    }

    private Location primaryLocation(SfxVirtualContainer container) {
        if (container == null) {
            return null;
        }
        World world = Bukkit.getWorld(container.key().worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, container.key().x1(), container.key().y1(), container.key().z1());
    }

    private boolean owns(Location location) {
        return location != null && location.getWorld() != null && runtime.isOwnedByCurrentRegion(location);
    }

    private Location holderLocation(InventoryHolder holder) {
        if (holder == null) {
            return null;
        }
        if (holder instanceof Chest chest) {
            return chest.getLocation();
        }
        try {
            java.lang.reflect.Method method = holder.getClass().getMethod("getLocation");
            Object value = method.invoke(holder);
            return value instanceof Location location ? location : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    private String itemKey(ItemStack stack) {
        if (stack == null) {
            return "air";
        }
        int metaHash = stack.hasItemMeta() && stack.getItemMeta() != null ? stack.getItemMeta().hashCode() : 0;
        return stack.getType().name() + ":" + metaHash;
    }
}
