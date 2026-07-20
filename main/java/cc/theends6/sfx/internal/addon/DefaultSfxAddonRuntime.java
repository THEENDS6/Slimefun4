package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.addon.SfxAddonRuntime;
import cc.theends6.sfx.api.block.SfxBlockStateService;
import cc.theends6.sfx.api.block.SfxBlockType;
import cc.theends6.sfx.api.container.SfxVirtualFluidContainer;
import cc.theends6.sfx.api.container.SfxVirtualItemContainer;
import cc.theends6.sfx.api.container.SfxTransactionMode;
import cc.theends6.sfx.api.container.SfxTransactionReservation;
import cc.theends6.sfx.api.display.SfxDisplayCategory;
import cc.theends6.sfx.api.display.SfxDisplayType;
import cc.theends6.sfx.api.display.SfxDisplaySessionService;
import cc.theends6.sfx.api.machine.continuous.SfxContinuousMachineContext;
import cc.theends6.sfx.api.machine.continuous.SfxContinuousMachineRuntime;
import cc.theends6.sfx.api.machine.continuous.SfxContinuousMachineState;
import cc.theends6.sfx.api.machine.continuous.SfxContinuousManualMachine;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.power.SfxInventoryPowerRouter;
import cc.theends6.sfx.api.power.SfxPowerInventorySnapshot;
import cc.theends6.sfx.api.power.SfxPowerPort;
import cc.theends6.sfx.api.power.SfxPowerRoute;
import cc.theends6.sfx.api.power.SfxPoweredItem;
import cc.theends6.sfx.api.power.SfxPoweredItemKind;
import cc.theends6.sfx.api.power.SfxPoweredInventoryRuntime;
import cc.theends6.sfx.api.power.SfxPoweredItemRuntime;
import cc.theends6.sfx.api.power.SfxPoweredItemState;
import cc.theends6.sfx.api.power.SfxPoweredItemUseResult;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.api.time.SfxServerActiveClock;
import cc.theends6.sfx.internal.display.DefaultSfxDisplaySessionService;
import cc.theends6.sfx.internal.block.SfxBlockDataService;

public final class DefaultSfxAddonRuntime implements SfxAddonRuntime, AutoCloseable {
    private volatile SfxAddonManager manager;
    private final ContinuousRuntime continuous;
    private final SfxPoweredItemRuntime powered = new PoweredRuntime();
    private final DefaultSfxDisplaySessionService displays;
    private final SfxBlockStateService blockStates;
    private final InventoryPowerRuntime inventoryPower;
    private final SfxItems items;
    private final SfxServerActiveClock activeClock;
    private final NamespacedKey energyKey;
    private final NamespacedKey overclockKey;
    private final NamespacedKey settledTickKey;

    public DefaultSfxAddonRuntime(JavaPlugin plugin, SfxRuntime runtime, SfxItems items,
                                  SfxServerActiveClock activeClock, SfxInventoryPowerRouter powerRouter,
                                  SfxBlockDataService blockData) {
        this.items = items;
        this.activeClock = activeClock;
        this.continuous = new ContinuousRuntime(plugin, runtime,
                plugin.getDataFolder().toPath().resolve("data/addon-continuous-machines.bin"));
        this.energyKey = new NamespacedKey(plugin, "powered_energy");
        this.overclockKey = new NamespacedKey(plugin, "powered_overclock");
        this.settledTickKey = new NamespacedKey(plugin, "powered_settled_tick");
        this.displays = new DefaultSfxDisplaySessionService(plugin, runtime,
                this::displayType, this::displayCategory);
        this.blockStates = new DefaultSfxBlockStateService(blockData, this::blockType);
        this.inventoryPower = new InventoryPowerRuntime(plugin, powerRouter);
        plugin.getServer().getPluginManager().registerEvents(inventoryPower, plugin);
        this.displays.start();
        this.continuous.start();
    }

    public void bind(SfxAddonManager manager) {
        this.manager = manager;
        if (manager == null) displays.clear();
    }

    @Override public Optional<SfxDisplayCategory> displayCategory(String id) { return manager == null ? Optional.empty() : manager.displayCategory(id); }
    @Override public Optional<SfxBlockType<?>> blockType(String id) { return manager == null ? Optional.empty() : manager.blockType(id); }
    @Override public SfxBlockStateService blockStates() { return blockStates; }
    @Override public Optional<SfxDisplayType> displayType(String id) { return manager == null ? Optional.empty() : manager.displayType(id); }
    @Override public SfxDisplaySessionService displays() { return displays; }
    @Override public Optional<SfxVirtualItemContainer> itemContainer(String typeId, Location location) {
        return manager == null ? Optional.empty() : manager.containerType(typeId)
                .filter(type -> type.itemFactory() != null).map(type -> type.itemFactory().apply(location.clone()));
    }
    @Override public Optional<SfxVirtualFluidContainer> fluidContainer(String typeId, Location location) {
        return manager == null ? Optional.empty() : manager.containerType(typeId)
                .filter(type -> type.fluidFactory() != null).map(type -> type.fluidFactory().apply(location.clone()));
    }
    @Override public SfxContinuousMachineRuntime continuousMachines() { return continuous; }
    @Override public SfxPoweredItemRuntime poweredItems() { return powered; }
    @Override public SfxPoweredInventoryRuntime inventoryPower() { return inventoryPower; }

    @Override public void close() {
        HandlerList.unregisterAll(inventoryPower);
        inventoryPower.clear();
        displays.close();
        continuous.close();
    }

    private final class ContinuousRuntime implements SfxContinuousMachineRuntime, AutoCloseable {
        private static final int FILE_MAGIC = 0x53465843;
        private static final int FILE_VERSION = 1;
        private final JavaPlugin plugin;
        private final SfxRuntime runtime;
        private final Path file;
        private final Map<UUID, InputBudget> inputBudgets = new ConcurrentHashMap<>();
        private final Map<UUID, ManagedMachine> managed = new ConcurrentHashMap<>();
        private final Map<UUID, DormantMachine> dormant = new ConcurrentHashMap<>();
        private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
        private volatile ScheduledTask task;

        private ContinuousRuntime(JavaPlugin plugin, SfxRuntime runtime, Path file) {
            this.plugin = plugin;
            this.runtime = runtime;
            this.file = file;
            load();
        }

        private synchronized void start() {
            if (task != null) return;
            task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> {
                long now = activeClock.activeTicks();
                resolveDormant();
                for (ManagedMachine entry : List.copyOf(managed.values())) scheduleTick(entry, now);
                if (now % 1200L < 20L) save();
            }, 20L, 20L);
        }
        @Override public Optional<SfxContinuousMachineState> create(String id, UUID instanceId, Location location, long tick) {
            return machine(id).map(machine -> new SfxContinuousMachineState(instanceId, location,
                    machine.initialVariables(), 0.0D, Math.max(0L, tick), null));
        }

        @Override public Optional<SfxContinuousMachineState> applyInput(String id, SfxContinuousMachineState state,
                                                                        double input, long tick) {
            return applyInput(id, state, null, input, tick);
        }

        @Override public Optional<SfxContinuousMachineState> applyInput(String id, SfxContinuousMachineState state,
                                                                        UUID playerId, double input, long tick) {
            return machine(id).map(machine -> {
                MutableContinuousContext context = context(state, tick);
                double accepted = limitedInput(machine, state.instanceId(), playerId, input, tick);
                if (accepted > 0.0D) machine.playerInput().accept(context, accepted);
                return state(state, context, state.progress(), tick);
            });
        }

        @Override public Optional<SfxContinuousMachineState> tick(String id, SfxContinuousMachineState state, long tick) {
            return machine(id).map(machine -> {
                MutableContinuousContext context = context(state, tick);
                machine.decay().accept(context, context.elapsedTicks());
                double progress = state.progress();
                if (machine.canProgress().test(context)) {
                    double rate = Math.max(0.0D, machine.progressPerTick().applyAsDouble(context));
                    progress += rate * context.elapsedTicks();
                }
                return state(state, context, progress, tick);
            });
        }

        @Override public Optional<SfxContinuousMachineState> createManaged(String id, UUID instanceId,
                                                                            Location location) {
            if (instanceId == null || managed.containsKey(instanceId) || dormant.containsKey(instanceId)) {
                return Optional.empty();
            }
            Optional<SfxContinuousMachineState> created = create(id, instanceId, location, activeClock.activeTicks());
            created.ifPresent(state -> {
                if (managed.putIfAbsent(instanceId, new ManagedMachine(id, state)) != null) {
                    throw new IllegalStateException("Continuous machine instance already exists: " + instanceId);
                }
                save();
            });
            return created;
        }

        @Override public Optional<SfxContinuousMachineState> managedState(UUID instanceId) {
            ManagedMachine entry = instanceId == null ? null : managed.get(instanceId);
            return entry == null ? Optional.empty() : Optional.of(entry.state());
        }

        @Override public List<SfxContinuousMachineState> managedStates(String machineId) {
            if (machineId == null) return List.of();
            return managed.values().stream().filter(entry -> entry.machineId().equals(machineId))
                    .map(ManagedMachine::state).toList();
        }

        @Override public Optional<SfxContinuousMachineState> applyManagedInput(UUID instanceId, UUID playerId,
                                                                                double input) {
            if (instanceId == null) return Optional.empty();
            long now = activeClock.activeTicks();
            AtomicReference<SfxContinuousMachineState> result = new AtomicReference<>();
            managed.computeIfPresent(instanceId, (ignored, live) -> {
                Optional<SfxContinuousMachineState> updated = applyInput(live.machineId(), live.state(),
                        playerId, input, now);
                updated.ifPresent(result::set);
                return updated.map(state -> new ManagedMachine(live.machineId(), state)).orElse(live);
            });
            return Optional.ofNullable(result.get());
        }

        @Override public boolean removeManaged(UUID instanceId) {
            if (instanceId == null) return false;
            boolean removed = managed.remove(instanceId) != null | dormant.remove(instanceId) != null;
            if (!removed) return false;
            inFlight.remove(instanceId);
            save();
            return true;
        }

        private void scheduleTick(ManagedMachine snapshot, long now) {
            UUID id = snapshot.state().instanceId();
            if (!inFlight.add(id)) return;
            runtime.executeAt(snapshot.state().location(), () -> {
                try {
                    ManagedMachine live = managed.get(id);
                    if (live == null || !live.state().location().isChunkLoaded()) return;
                    tick(live.machineId(), live.state(), now).ifPresent(state ->
                            managed.computeIfPresent(id, (ignored, current) ->
                                    current != live ? current : new ManagedMachine(current.machineId(), state)));
                } finally {
                    inFlight.remove(id);
                }
            });
        }

        private synchronized void load() {
            if (!Files.isRegularFile(file)) return;
            try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
                if (input.readInt() != FILE_MAGIC || input.readInt() != FILE_VERSION) {
                    throw new IOException("unsupported continuous machine store format");
                }
                int count = input.readInt();
                for (int index = 0; index < count; index++) {
                    UUID instanceId = new UUID(input.readLong(), input.readLong());
                    String machineId = input.readUTF();
                    UUID worldId = new UUID(input.readLong(), input.readLong());
                    double x = input.readDouble(), y = input.readDouble(), z = input.readDouble();
                    int variables = input.readInt();
                    Map<String, Double> values = new LinkedHashMap<>();
                    for (int variable = 0; variable < variables; variable++) values.put(input.readUTF(), input.readDouble());
                    double progress = input.readDouble();
                    long lastTick = input.readLong();
                    String recipe = input.readBoolean() ? input.readUTF() : null;
                    org.bukkit.World world = plugin.getServer().getWorld(worldId);
                    if (world != null) managed.put(instanceId, new ManagedMachine(machineId,
                            new SfxContinuousMachineState(instanceId, new Location(world, x, y, z), values,
                                    progress, lastTick, recipe)));
                    else dormant.put(instanceId, new DormantMachine(machineId, instanceId, worldId, x, y, z,
                            values, progress, lastTick, recipe));
                }
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().warning("Failed to load managed continuous machines: " + exception.getMessage());
                managed.clear();
                dormant.clear();
            }
        }

        private void resolveDormant() {
            for (DormantMachine entry : List.copyOf(dormant.values())) {
                org.bukkit.World world = plugin.getServer().getWorld(entry.worldId());
                if (world == null) continue;
                ManagedMachine restored = new ManagedMachine(entry.machineId(), new SfxContinuousMachineState(
                        entry.instanceId(), new Location(world, entry.x(), entry.y(), entry.z()), entry.variables(),
                        entry.progress(), entry.lastTick(), entry.lockedRecipeId()));
                if (managed.putIfAbsent(entry.instanceId(), restored) == null) dormant.remove(entry.instanceId(), entry);
            }
        }

        private synchronized void save() {
            try {
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                List<ManagedMachine> snapshot = List.copyOf(managed.values());
                List<DormantMachine> dormantSnapshot = List.copyOf(dormant.values());
                try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporary))) {
                    output.writeInt(FILE_MAGIC); output.writeInt(FILE_VERSION);
                    output.writeInt(snapshot.size() + dormantSnapshot.size());
                    for (ManagedMachine entry : snapshot) write(output, DormantMachine.from(entry));
                    for (DormantMachine entry : dormantSnapshot) write(output, entry);
                }
                try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (IOException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to persist managed continuous machines: " + exception.getMessage());
            }
        }

        @Override public synchronized void close() {
            ScheduledTask current = task;
            task = null;
            if (current != null) current.cancel();
            save();
            managed.clear();
            dormant.clear();
            inFlight.clear();
        }

        private void write(DataOutputStream output, DormantMachine entry) throws IOException {
            output.writeLong(entry.instanceId().getMostSignificantBits());
            output.writeLong(entry.instanceId().getLeastSignificantBits());
            output.writeUTF(entry.machineId());
            output.writeLong(entry.worldId().getMostSignificantBits());
            output.writeLong(entry.worldId().getLeastSignificantBits());
            output.writeDouble(entry.x()); output.writeDouble(entry.y()); output.writeDouble(entry.z());
            output.writeInt(entry.variables().size());
            for (Map.Entry<String, Double> variable : entry.variables().entrySet()) {
                output.writeUTF(variable.getKey()); output.writeDouble(variable.getValue());
            }
            output.writeDouble(entry.progress()); output.writeLong(entry.lastTick());
            output.writeBoolean(entry.lockedRecipeId() != null);
            if (entry.lockedRecipeId() != null) output.writeUTF(entry.lockedRecipeId());
        }

        private Optional<SfxContinuousManualMachine> machine(String id) {
            SfxAddonManager current = manager;
            return current == null ? Optional.empty() : current.continuousMachine(id);
        }

        private MutableContinuousContext context(SfxContinuousMachineState state, long tick) {
            return new MutableContinuousContext(state.instanceId(), state.location(),
                    Math.max(0L, Math.min(72_000L, tick - state.lastTick())),
                    new LinkedHashMap<>(state.variables()), state.lockedRecipeId());
        }
        private SfxContinuousMachineState state(SfxContinuousMachineState previous, MutableContinuousContext context,
                                                double progress, long tick) {
            return new SfxContinuousMachineState(previous.instanceId(), previous.location(), context.variables(),
                    progress, Math.max(previous.lastTick(), tick), previous.lockedRecipeId());
        }

        private double limitedInput(SfxContinuousManualMachine machine, UUID instanceId, UUID playerId,
                                    double input, long tick) {
            double requested = Math.max(0.0D, input);
            if (!Double.isFinite(requested) || requested == 0.0D) return 0.0D;
            if (inputBudgets.size() > 4096) {
                inputBudgets.entrySet().removeIf(entry -> entry.getValue().tick < tick - 20L);
            }
            InputBudget budget = inputBudgets.compute(instanceId, (ignored, current) ->
                    current == null || current.tick != tick ? new InputBudget(tick) : current);
            synchronized (budget) {
                UUID key = playerId == null ? new UUID(0L, 0L) : playerId;
                double playerUsed = budget.byPlayer.getOrDefault(key, 0.0D);
                double accepted = Math.min(requested, Math.min(machine.maxInputPerTick() - budget.total,
                        machine.maxInputPerPlayerPerTick() - playerUsed));
                accepted = Math.max(0.0D, accepted);
                budget.total += accepted;
                budget.byPlayer.put(key, playerUsed + accepted);
                return accepted;
            }
        }
    }

    private record ManagedMachine(String machineId, SfxContinuousMachineState state) {
        private ManagedMachine {
            if (machineId == null || machineId.isBlank() || state == null) throw new IllegalArgumentException();
        }
    }

    private record DormantMachine(String machineId, UUID instanceId, UUID worldId,
                                  double x, double y, double z, Map<String, Double> variables,
                                  double progress, long lastTick, String lockedRecipeId) {
        private DormantMachine { variables = Map.copyOf(variables); }

        private static DormantMachine from(ManagedMachine entry) {
            SfxContinuousMachineState state = entry.state();
            Location location = state.location();
            return new DormantMachine(entry.machineId(), state.instanceId(), location.getWorld().getUID(),
                    location.getX(), location.getY(), location.getZ(), state.variables(), state.progress(),
                    state.lastTick(), state.lockedRecipeId());
        }
    }

    private final class PoweredRuntime implements SfxPoweredItemRuntime {
        @Override public Optional<SfxPoweredItem> definition(String id) {
            SfxAddonManager current = manager;
            return current == null ? Optional.empty() : current.poweredItem(id);
        }
        @Override public Optional<SfxPoweredItemState> charge(String id, SfxPoweredItemState state, double offered) {
            return definition(id).map(item -> {
                double accepted = Math.min(Math.max(0.0D, offered), Math.min(item.maxInputPerTick(), item.capacity() - state.storedEnergy()));
                return new SfxPoweredItemState(state.storedEnergy() + accepted, state.overclocked(), state.lastSettledActiveTick());
            });
        }
        @Override public Optional<SfxPoweredItemUseResult> use(String id, SfxPoweredItemState state, boolean success) {
            return definition(id).map(item -> {
                boolean overclock = state.overclocked() && item.overclock() != null;
                double cost = item.baseUseCost() * (overclock ? item.overclock().energyMultiplier() : 1.0D);
                double effect = overclock ? item.overclock().effectMultiplier() : 1.0D;
                boolean executed = success && state.storedEnergy() + 1.0E-9D >= cost;
                double consumed = executed ? cost : 0.0D;
                return new SfxPoweredItemUseResult(executed, effect, consumed,
                        new SfxPoweredItemState(Math.max(0.0D, state.storedEnergy() - consumed), overclock,
                                state.lastSettledActiveTick()));
            });
        }

        @Override public Optional<SfxPoweredItemState> readState(ItemStack item) {
            if (item == null || item.getType().isAir()) return Optional.empty();
            Optional<String> id = items.readMarker(item).map(marker -> marker.itemId());
            if (id.isEmpty() || definition(id.get()).isEmpty()) return Optional.empty();
            ItemMeta meta = item.getItemMeta();
            Double energy = meta.getPersistentDataContainer().get(energyKey, PersistentDataType.DOUBLE);
            Byte overclock = meta.getPersistentDataContainer().get(overclockKey, PersistentDataType.BYTE);
            Long settled = meta.getPersistentDataContainer().get(settledTickKey, PersistentDataType.LONG);
            SfxPoweredItem definition = definition(id.get()).orElseThrow();
            return Optional.of(new SfxPoweredItemState(
                    Math.min(definition.capacity(), Math.max(0.0D, energy == null ? 0.0D : energy)),
                    overclock != null && overclock != 0,
                    settled == null ? activeClock.activeTicks() : settled));
        }

        @Override public boolean writeState(ItemStack item, SfxPoweredItemState state) {
            if (item == null || state == null) return false;
            Optional<String> id = items.readMarker(item).map(marker -> marker.itemId());
            Optional<SfxPoweredItem> definition = id.flatMap(this::definition);
            if (definition.isEmpty()) return false;
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(energyKey, PersistentDataType.DOUBLE,
                    Math.min(definition.get().capacity(), Math.max(0.0D, state.storedEnergy())));
            meta.getPersistentDataContainer().set(overclockKey, PersistentDataType.BYTE,
                    (byte) (state.overclocked() && definition.get().overclock() != null ? 1 : 0));
            meta.getPersistentDataContainer().set(settledTickKey, PersistentDataType.LONG,
                    Math.max(0L, state.lastSettledActiveTick()));
            item.setItemMeta(meta);
            return true;
        }
    }

    private final class InventoryPowerRuntime implements SfxPoweredInventoryRuntime, Listener {
        private final JavaPlugin plugin;
        private final SfxInventoryPowerRouter router;
        private final Map<UUID, SfxPowerInventorySnapshot> cache = new ConcurrentHashMap<>();

        private InventoryPowerRuntime(JavaPlugin plugin, SfxInventoryPowerRouter router) {
            this.plugin = plugin;
            this.router = router;
        }

        @Override public SfxPowerInventorySnapshot snapshot(Player player) {
            if (player == null) throw new IllegalArgumentException("player must not be null");
            long tick = currentTick();
            SfxPowerInventorySnapshot cached = cache.get(player.getUniqueId());
            if (cached != null && cached.serverTick() == tick) return cached;
            List<SfxPowerPort> sources = new ArrayList<>();
            List<SfxPowerPort> consumers = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                Optional<String> id = stack == null ? Optional.empty()
                        : items.readMarker(stack).map(marker -> marker.itemId());
                Optional<SfxPoweredItem> definition = id.flatMap(powered::definition);
                if (definition.isEmpty()) continue;
                InventoryPort port = new InventoryPort(player, slot, definition.get());
                if (definition.get().maxOutputPerTick() > 0.0D
                        && (definition.get().kind() == SfxPoweredItemKind.POWER_SOURCE
                        || definition.get().kind() == SfxPoweredItemKind.BATTERY)) sources.add(port);
                if (definition.get().maxInputPerTick() > 0.0D
                        && definition.get().kind() != SfxPoweredItemKind.POWER_SOURCE) consumers.add(port);
            }
            SfxPowerInventorySnapshot result = new SfxPowerInventorySnapshot(player.getUniqueId(), tick, sources, consumers);
            cache.put(player.getUniqueId(), result);
            return result;
        }

        @Override public List<SfxPowerRoute> settle(Player player, double transferLimit) {
            SfxPowerInventorySnapshot snapshot = snapshot(player);
            List<SfxPowerPort> generators = snapshot.sources().stream()
                    .filter(port -> ((InventoryPort) port).definition.kind() == SfxPoweredItemKind.POWER_SOURCE).toList();
            List<SfxPowerPort> batteries = snapshot.sources().stream()
                    .filter(port -> ((InventoryPort) port).definition.kind() == SfxPoweredItemKind.BATTERY).toList();
            List<SfxPowerPort> devices = snapshot.consumers().stream()
                    .filter(port -> ((InventoryPort) port).definition.kind() != SfxPoweredItemKind.BATTERY).toList();
            List<SfxPowerRoute> routes = new ArrayList<>(router.route(generators, snapshot.consumers(), transferLimit));
            double moved = routes.stream().mapToDouble(SfxPowerRoute::transferred).sum();
            routes.addAll(router.route(batteries, devices, Math.max(0.0D, transferLimit - moved)));
            invalidate(player.getUniqueId());
            return List.copyOf(routes);
        }

        @Override public void invalidate(UUID playerId) {
            if (playerId != null) cache.remove(playerId);
        }

        private long currentTick() {
            try { return plugin.getServer().getCurrentTick(); }
            catch (UnsupportedOperationException ignored) { return 0L; }
        }

        private void clear() { cache.clear(); }

        @EventHandler(priority = EventPriority.MONITOR) public void onClick(InventoryClickEvent event) {
            if (event.getWhoClicked() instanceof Player player) invalidate(player.getUniqueId());
        }
        @EventHandler(priority = EventPriority.MONITOR) public void onDrag(InventoryDragEvent event) {
            if (event.getWhoClicked() instanceof Player player) invalidate(player.getUniqueId());
        }
        @EventHandler(priority = EventPriority.MONITOR) public void onDrop(PlayerDropItemEvent event) {
            invalidate(event.getPlayer().getUniqueId());
        }
        @EventHandler(priority = EventPriority.MONITOR) public void onPickup(EntityPickupItemEvent event) {
            if (event.getEntity() instanceof Player player) invalidate(player.getUniqueId());
        }
        @EventHandler(priority = EventPriority.MONITOR) public void onSwap(PlayerSwapHandItemsEvent event) {
            invalidate(event.getPlayer().getUniqueId());
        }
    }

    private final class InventoryPort implements SfxPowerPort {
        private final Player player;
        private final int slot;
        private final SfxPoweredItem definition;

        private InventoryPort(Player player, int slot, SfxPoweredItem definition) {
            this.player = player; this.slot = slot; this.definition = definition;
        }
        private ItemStack item() { return player.getInventory().getItem(slot); }
        private SfxPoweredItemState state() {
            return powered.readState(item()).orElse(new SfxPoweredItemState(0.0D, false, activeClock.activeTicks()));
        }
        @Override public String id() { return player.getUniqueId() + ":" + slot + ":" + definition.id(); }
        @Override public int priority() {
            return definition.kind() == SfxPoweredItemKind.POWER_SOURCE ? 0
                    : definition.kind() == SfxPoweredItemKind.BATTERY ? 100 : 200;
        }
        @Override public double available() {
            return Math.min(state().storedEnergy(), definition.maxOutputPerTick());
        }
        @Override public double demand() {
            return Math.min(Math.max(0.0D, definition.capacity() - state().storedEnergy()), definition.maxInputPerTick());
        }
        @Override public double extract(double amount, SfxTransactionMode mode) {
            SfxPoweredItemState state = state();
            double moved = Math.min(Math.max(0.0D, amount), Math.min(state.storedEnergy(), definition.maxOutputPerTick()));
            if (mode == SfxTransactionMode.COMMIT && moved > 0.0D) powered.writeState(item(),
                    new SfxPoweredItemState(state.storedEnergy() - moved, state.overclocked(), activeClock.activeTicks()));
            return moved;
        }
        @Override public double insert(double amount, SfxTransactionMode mode) {
            SfxPoweredItemState state = state();
            double moved = Math.min(Math.max(0.0D, amount),
                    Math.min(definition.capacity() - state.storedEnergy(), definition.maxInputPerTick()));
            if (mode == SfxTransactionMode.COMMIT && moved > 0.0D) powered.writeState(item(),
                    new SfxPoweredItemState(state.storedEnergy() + moved, state.overclocked(), activeClock.activeTicks()));
            return moved;
        }
        @Override public Optional<SfxTransactionReservation> prepareExtract(double amount) {
            SfxPoweredItemState before = state();
            double moved = Math.min(Math.max(0.0D, amount),
                    Math.min(before.storedEnergy(), definition.maxOutputPerTick()));
            if (moved + 1.0E-9D < amount) return Optional.empty();
            SfxPoweredItemState after = new SfxPoweredItemState(before.storedEnergy() - moved,
                    before.overclocked(), activeClock.activeTicks());
            return Optional.of(new PowerStateReservation(this, before, after));
        }
        @Override public Optional<SfxTransactionReservation> prepareInsert(double amount) {
            SfxPoweredItemState before = state();
            double moved = Math.min(Math.max(0.0D, amount),
                    Math.min(definition.capacity() - before.storedEnergy(), definition.maxInputPerTick()));
            if (moved + 1.0E-9D < amount) return Optional.empty();
            SfxPoweredItemState after = new SfxPoweredItemState(before.storedEnergy() + moved,
                    before.overclocked(), activeClock.activeTicks());
            return Optional.of(new PowerStateReservation(this, before, after));
        }
    }

    private final class PowerStateReservation implements SfxTransactionReservation {
        private final InventoryPort port;
        private final SfxPoweredItemState before;
        private final SfxPoweredItemState after;
        private boolean committed;
        private PowerStateReservation(InventoryPort port, SfxPoweredItemState before, SfxPoweredItemState after) {
            this.port = port; this.before = before; this.after = after;
        }
        @Override public synchronized void commit() {
            if (committed) return;
            if (!before.equals(port.state())) throw new IllegalStateException("Powered inventory changed after reservation: " + port.id());
            if (!powered.writeState(port.item(), after)) throw new IllegalStateException("Could not commit powered inventory state: " + port.id());
            committed = true;
        }
        @Override public synchronized void rollback() {
            if (!committed) return;
            if (!powered.writeState(port.item(), before)) throw new IllegalStateException("Could not restore powered inventory state: " + port.id());
            committed = false;
        }
    }

    private static final class MutableContinuousContext implements SfxContinuousMachineContext {
        private final UUID id; private final Location location; private final long elapsed;
        private final Map<String, Double> variables; private final String lockedRecipeId;
        private MutableContinuousContext(UUID id, Location location, long elapsed, Map<String, Double> variables,
                                         String lockedRecipeId) {
            this.id = id; this.location = location; this.elapsed = elapsed; this.variables = variables;
            this.lockedRecipeId = lockedRecipeId;
        }
        @Override public UUID instanceId() { return id; }
        @Override public Location location() { return location.clone(); }
        @Override public long elapsedTicks() { return elapsed; }
        @Override public Map<String, Double> variables() { return variables; }
        @Override public String lockedRecipeId() { return lockedRecipeId; }
    }

    private static final class InputBudget {
        private final long tick;
        private final Map<UUID, Double> byPlayer = new LinkedHashMap<>();
        private double total;
        private InputBudget(long tick) { this.tick = tick; }
    }
}
