package cc.theends6.sfx.internal.virtualcontainer;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxVirtualContainerService implements Listener {
    private static final long EXTERNAL_SYNC_INTERVAL = 10L;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final Map<SfxVirtualContainerKey, SfxVirtualContainer> containers = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public SfxVirtualContainerService(JavaPlugin plugin, SfxRuntime runtime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        scheduleExternalSync();
    }

    public Optional<SfxVirtualContainer> ensureRegistered(Location location) {
        Inventory inventory = inventoryAt(location).orElse(null);
        if (inventory == null) {
            return Optional.empty();
        }
        SfxVirtualContainerKey key = keyForInventory(inventory, location).orElseGet(() -> SfxVirtualContainerKey.single(location));
        SfxVirtualContainer container = containers.computeIfAbsent(key, ignored -> new SfxVirtualContainer(key, inventory.getSize()));
        container.cargoAttached(true);
        hydrateIfNeeded(container, inventory);
        return Optional.of(container);
    }

    public Optional<SfxVirtualContainer> findRegistered(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        for (SfxVirtualContainer container : containers.values()) {
            if (container.key().contains(location)) {
                return Optional.of(container);
            }
        }
        return Optional.empty();
    }

    public Collection<SfxVirtualContainer> containers() {
        return List.copyOf(containers.values());
    }

    public void hydrateExternalBeforeLogic() {
        for (SfxVirtualContainer container : containers.values()) {
            if (!container.externalDirty() && !container.viewerActive()) {
                continue;
            }
            inventoryFor(container).ifPresent(inventory -> hydrate(container, inventory));
        }
    }

    public void pushDirtyAfterLogic() {
        for (SfxVirtualContainer container : containers.values()) {
            if (!container.mirrorDirty()) {
                continue;
            }
            if (!container.viewerActive() && !container.externalActive()) {
                continue;
            }
            inventoryFor(container).ifPresent(inventory -> syncToWorld(container, inventory));
        }
    }

    public void flushAllToWorld() {
        for (SfxVirtualContainer container : containers.values()) {
            inventoryFor(container).ifPresent(inventory -> syncToWorld(container, inventory));
        }
    }

    public ItemStack withdrawFirst(SfxVirtualContainer container, java.util.function.Predicate<ItemStack> filter, int maxAmount) {
        if (container == null) {
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

    public List<ItemStack> withdrawBatch(SfxVirtualContainer container, java.util.function.Predicate<ItemStack> filter, int maxItems, int maxDistinctTypes) {
        List<ItemStack> result = new ArrayList<>();
        if (container == null || maxItems <= 0) {
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

    public ItemStack insert(SfxVirtualContainer container, ItemStack input, boolean smartFill) {
        if (container == null || isEmpty(input)) {
            return input;
        }
        ItemStack remaining = input.clone();
        if (smartFill) {
            remaining = fillExisting(container.rawMirror(), remaining);
            if (isEmpty(remaining)) {
                container.mirrorDirty(true);
                return null;
            }
        }
        remaining = fillEmptyOrExisting(container.rawMirror(), remaining, !smartFill);
        if (remaining.getAmount() != input.getAmount()) {
            container.mirrorDirty(true);
        }
        return isEmpty(remaining) ? null : remaining;
    }

    public int capacityFor(SfxVirtualContainer container, ItemStack probe, boolean smartFill) {
        if (container == null || isEmpty(probe)) {
            return 0;
        }
        int capacity = 0;
        for (ItemStack stack : container.rawMirror()) {
            if (isEmpty(stack)) {
                if (!smartFill) {
                    capacity += probe.getMaxStackSize();
                }
                continue;
            }
            if (stack.isSimilar(probe)) {
                capacity += Math.max(0, Math.min(stack.getMaxStackSize(), probe.getMaxStackSize()) - stack.getAmount());
            }
        }
        if (smartFill && capacity <= 0) {
            for (ItemStack stack : container.rawMirror()) {
                if (isEmpty(stack)) {
                    capacity += probe.getMaxStackSize();
                }
            }
        }
        return capacity;
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
        hydrate(container, inventory);
        syncToWorld(container, inventory);
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
        hydrate(container, inventory);
        container.viewerActive(false);
        container.externalActive(true);
        container.externalDirty(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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
    }

    public void shutdown() {
        running = false;
        flushAllToWorld();
        containers.clear();
    }

    private void scheduleExternalSync() {
        runtime.executeGlobalLater(EXTERNAL_SYNC_INTERVAL, () -> {
            if (!running) {
                return;
            }
            long now = Bukkit.getCurrentTick();
            for (SfxVirtualContainer container : containers.values()) {
                if (!container.externalActive() && !container.viewerActive()) {
                    continue;
                }
                inventoryFor(container).ifPresent(inventory -> {
                    if (container.externalDirty() || now - container.lastWorldSyncTick() >= EXTERNAL_SYNC_INTERVAL) {
                        hydrate(container, inventory);
                        if (container.mirrorDirty()) {
                            syncToWorld(container, inventory);
                        }
                    }
                });
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
            container.externalDirty(true);
        });
    }

    private void hydrateIfNeeded(SfxVirtualContainer container, Inventory inventory) {
        if (container.revision() == 0L || container.externalDirty() || container.viewerActive()) {
            hydrate(container, inventory);
        }
    }

    private void hydrate(SfxVirtualContainer container, Inventory inventory) {
        if (container == null || inventory == null) {
            return;
        }
        container.setContents(inventory.getContents());
        container.externalDirty(false);
        container.mirrorDirty(false);
        container.lastWorldSyncTick(Bukkit.getCurrentTick());
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
        container.lastWorldSyncTick(Bukkit.getCurrentTick());
    }

    private Optional<Inventory> inventoryAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        Block block = location.getBlock();
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return Optional.empty();
        }
        return Optional.of(holder.getInventory());
    }

    private Optional<Inventory> inventoryFor(SfxVirtualContainer container) {
        if (container == null) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(container.key().worldId());
        if (world == null) {
            return Optional.empty();
        }
        Block block = world.getBlockAt(container.key().x1(), container.key().y1(), container.key().z1());
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return Optional.empty();
        }
        return Optional.of(holder.getInventory());
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
