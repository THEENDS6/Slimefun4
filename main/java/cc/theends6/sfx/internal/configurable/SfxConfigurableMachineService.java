package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.display.SfxFloatingTextKey;
import cc.theends6.sfx.internal.display.SfxFloatingTextProjection;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxConfigurableMachineService implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final long FLUSH_INTERVAL = 20L;
    private static final int[] ASSEMBLER_HEAD_SLOTS = {20};
    private static final int[] ASSEMBLER_BODY_SLOTS = {24};
    private static final int[] REACTOR_FUEL_SLOTS = {19, 28, 37};
    private static final int[] REACTOR_COOLANT_SLOTS = {25, 34, 43};
    private static final int[] REACTOR_OUTPUT_SLOTS = {40};
    private static final int ENABLE_SLOT = 4;
    private static final int OFFSET_SLOT = 22;
    private static final int COOLDOWN_SLOT = 31;
    private static final int REACTOR_MODE_SLOT = 4;
    private static final int REACTOR_PROGRESS_SLOT = 22;
    private static final int REACTOR_STATUS_SLOT = 49;
    private static final int ASSEMBLER_COOLDOWN_TICKS = 30 * 20;
    private static final int REACTOR_COOLANT_TICKS = 300;
    private static final int HOLOGRAM_VIEW_DISTANCE_SQUARED = 32 * 32;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxFloatingTextDisplayService floatingText;
    private final Map<String, SfxConfigurableMachineDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, SfxConfigurableMachineState> states = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyInstances = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeInstances = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> recentEnergyConsumption = new ConcurrentHashMap<>();
    private final Map<UUID, SfxConfigurableMachineSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, SfxConfigurableMachineSession> sessionsByHost = new ConcurrentHashMap<>();
    private volatile boolean running;
    private volatile long tickCounter;

    public SfxConfigurableMachineService(
            JavaPlugin plugin,
            SfxRuntime runtime,
            SfxItems items,
            SfxLocalization localization,
            SfxBlockDataService blockData,
            SfxFloatingTextDisplayService floatingText
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.floatingText = Objects.requireNonNull(floatingText, "floatingText");
        registerDefinitions();
        bootstrapLoadedStates();
        running = true;
        scheduleTick();
        scheduleFlush();
    }

    private void registerDefinitions() {
        boolean sfxGeneratorBalance = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true);
        int netherStarEnergy = sfxGeneratorBalance ? 2048 : 1024;
        register(SfxConfigurableMachineDefinition.ironGolemAssembler());
        register(SfxConfigurableMachineDefinition.witherAssembler());
        register(SfxConfigurableMachineDefinition.nuclearReactor());
        register(SfxConfigurableMachineDefinition.netherStarReactor(netherStarEnergy));
        register(SfxConfigurableMachineDefinition.reactorAccessPort());
    }

    private void register(SfxConfigurableMachineDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    public boolean supportsType(String typeId) {
        return definitions.containsKey(typeId);
    }

    public boolean isEnergyNode(String typeId) {
        SfxConfigurableMachineDefinition definition = definitions.get(typeId);
        return definition != null && (definition.isConsumer() || definition.isProducer());
    }

    public boolean isConsumer(String typeId) {
        SfxConfigurableMachineDefinition definition = definitions.get(typeId);
        return definition != null && definition.isConsumer();
    }

    public boolean isProducer(String typeId) {
        SfxConfigurableMachineDefinition definition = definitions.get(typeId);
        return definition != null && definition.isProducer();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            if (!definitions.containsKey(marker.itemId())) {
                return;
            }
            UUID instanceId = blockData.findAnchor(event.getBlockPlaced().getLocation())
                    .map(SfxAnchorRecord::instanceId)
                    .orElseGet(() -> blockData.registerSingleBlock(
                            marker.itemId(),
                            event.getBlockPlaced().getLocation(),
                            event.getBlockPlaced().getType(),
                            event.getPlayer().getUniqueId()));
            states.putIfAbsent(instanceId, SfxConfigurableMachineState.empty());
            activeInstances.add(instanceId);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getPlayer().isSneaking()) {
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
        if (instance == null || !definitions.containsKey(instance.typeId())) {
            return;
        }
        event.setCancelled(true);
        runtime.executeForPlayer(event.getPlayer(), () -> openMachine(event.getPlayer(), instance));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxConfigurableMachineHolder holder)) {
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }
        SfxBlockInstanceRecord host = blockData.findInstance(holder.hostInstanceId()).orElse(null);
        SfxConfigurableMachineDefinition definition = host == null ? null : definitions.get(host.typeId());
        if (host == null || definition == null) {
            event.setCancelled(true);
            return;
        }
        boolean topSlot = event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (event.isShiftClick() && !topSlot) {
            int[] targetSlots = editableInputSlots(holder.panelType());
            if (moveShiftClickedStack(event.getView().getTopInventory(), event.getCurrentItem(), targetSlots, definition)) {
                if (event.getCurrentItem() != null && event.getCurrentItem().getAmount() <= 0) {
                    event.setCurrentItem(null);
                }
            }
            event.setCancelled(true);
            runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.hostInstanceId()));
            return;
        }
        if (topSlot) {
            int raw = event.getRawSlot();
            if (isButtonSlot(holder.panelType(), raw)) {
                event.setCancelled(true);
                handleButtonClick(player, holder, host, definition, raw, event.getClick());
                return;
            }
            if (contains(editableOutputSlots(holder.panelType()), raw)) {
                if (!isTakingFromOutput(event)) {
                    event.setCancelled(true);
                    return;
                }
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.hostInstanceId()));
                return;
            }
            if (!contains(editableInputSlots(holder.panelType()), raw)) {
                event.setCancelled(true);
                return;
            }
        }
        runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.hostInstanceId()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxConfigurableMachineHolder holder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (!touchesTop) {
            return;
        }
        int[] allowed = editableInputSlots(holder.panelType());
        boolean onlyEditable = event.getRawSlots().stream()
                .filter(slot -> slot < topSize)
                .allMatch(slot -> contains(allowed, slot));
        if (!onlyEditable) {
            event.setCancelled(true);
            return;
        }
        runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.hostInstanceId()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SfxConfigurableMachineHolder holder)) {
            return;
        }
        SfxConfigurableMachineSession session = sessionsByHost.remove(holder.hostInstanceId());
        if (session == null) {
            return;
        }
        sessionsByViewer.remove(session.viewerId());
        syncSessionState(session);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        SfxConfigurableMachineSession session = sessionsByViewer.remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        sessionsByHost.remove(session.hostInstanceId());
        syncSessionState(session);
    }

    public void shutdown() {
        running = false;
        for (SfxConfigurableMachineSession session : List.copyOf(sessionsByViewer.values())) {
            syncSessionState(session);
            Player player = plugin.getServer().getPlayer(session.viewerId());
            if (player != null) {
                runtime.executeForPlayer(player, player::closeInventory);
            }
        }
        for (SfxBlockInstanceRecord instance : List.copyOf(blockData.anchors()).stream()
                .map(anchor -> blockData.findInstance(anchor.instanceId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(instance -> isProducer(instance.typeId()))
                .toList()) {
            removeReactorHologram(instance.anchorKey());
        }
        flushDirty();
        states.clear();
        dirtyInstances.clear();
        activeInstances.clear();
        recentEnergyConsumption.clear();
        sessionsByViewer.clear();
        sessionsByHost.clear();
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (block == null || instanceId == null || typeId == null || !definitions.containsKey(typeId)) {
            return;
        }
        SfxConfigurableMachineDefinition definition = definitions.get(typeId);
        SfxConfigurableMachineSession session = sessionsByHost.remove(instanceId);
        if (session != null) {
            sessionsByViewer.remove(session.viewerId());
            syncSessionState(session);
            Player viewer = plugin.getServer().getPlayer(session.viewerId());
            if (viewer != null) {
                runtime.executeForPlayer(viewer, viewer::closeInventory);
            }
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxConfigurableMachineState state = states.get(instanceId);
        if (state == null && instance != null) {
            state = currentState(instanceId, instance);
        }
        if (state == null) {
            state = SfxConfigurableMachineState.empty();
        }
        if (definition.kind() != SfxConfigurableMachineKind.ACCESS_PORT) {
            for (int slot = 0; slot < state.inputCapacity(); slot++) {
                dropStack(block, state.input(slot));
            }
            for (int slot = 0; slot < state.outputCapacity(); slot++) {
                dropStack(block, state.output(slot));
            }
        }
        if (definition.kind() == SfxConfigurableMachineKind.REACTOR && instance != null) {
            removeReactorHologram(instance.anchorKey());
        }
        dropPluginBlock(block, typeId);
        states.remove(instanceId);
        dirtyInstances.remove(instanceId);
        activeInstances.remove(instanceId);
        blockData.unregisterAt(block.getLocation());
    }

    public int consumerCapacity(String typeId) {
        SfxConfigurableMachineDefinition definition = definitions.get(typeId);
        return definition == null || !definition.isConsumer() ? 0 : definition.capacity();
    }

    public int consumerStoredEnergy(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !isConsumer(instance.typeId())) {
            return 0;
        }
        return currentState(instanceId, instance).storedEnergy();
    }

    public int chargeConsumer(UUID instanceId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !isConsumer(instance.typeId())) {
            return 0;
        }
        SfxConfigurableMachineDefinition definition = definitions.get(instance.typeId());
        SfxConfigurableMachineState state = currentState(instanceId, instance);
        int accepted = Math.max(0, Math.min(amount, definition.capacity() - state.storedEnergy()));
        if (accepted > 0) {
            state.storedEnergy(state.storedEnergy() + accepted);
            dirtyInstances.add(instanceId);
            activeInstances.add(instanceId);
        }
        return accepted;
    }

    public int requestedEnergyConsumption(List<UUID> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (UUID instanceId : instanceIds) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            if (instance == null || !isConsumer(instance.typeId())) {
                continue;
            }
            SfxConfigurableMachineDefinition definition = definitions.get(instance.typeId());
            SfxConfigurableMachineState state = currentState(instanceId, instance);
            if (!state.enabled() || state.cooldownTicks() > 0 || state.storedEnergy() < definition.energyPerAction()) {
                continue;
            }
            if (hasAssemblerMaterials(state, definition)) {
                total += Math.max(1, definition.energyPerAction() / 20);
            }
        }
        return total;
    }

    public int drainRecentEnergyConsumption(List<UUID> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (UUID id : instanceIds) {
            Integer value = recentEnergyConsumption.remove(id);
            if (value != null && value > 0) {
                total += value;
            }
        }
        return total;
    }

    public int producerCapacity(String typeId) {
        SfxConfigurableMachineDefinition definition = definitions.get(typeId);
        return definition == null || !definition.isProducer() ? 0 : definition.capacity();
    }

    public int producerStoredEnergy(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !isProducer(instance.typeId())) {
            return 0;
        }
        return currentState(instanceId, instance).storedEnergy();
    }

    public int drainProducerEnergy(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !isProducer(instance.typeId())) {
            return 0;
        }
        SfxConfigurableMachineState state = currentState(instanceId, instance);
        int available = state.storedEnergy();
        if (available > 0) {
            state.storedEnergy(0);
            dirtyInstances.add(instanceId);
        }
        return available;
    }

    public int totalStoredEnergy(List<SfxBlockInstanceRecord> instances) {
        int total = 0;
        for (SfxBlockInstanceRecord instance : instances) {
            if (instance == null || !isEnergyNode(instance.typeId())) {
                continue;
            }
            total += currentState(instance.instanceId(), instance).storedEnergy();
        }
        return total;
    }

    public int totalCapacity(List<SfxBlockInstanceRecord> instances) {
        int total = 0;
        for (SfxBlockInstanceRecord instance : instances) {
            SfxConfigurableMachineDefinition definition = instance == null ? null : definitions.get(instance.typeId());
            if (definition != null && (definition.isConsumer() || definition.isProducer())) {
                total += definition.capacity();
            }
        }
        return total;
    }

    private void bootstrapLoadedStates() {
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !definitions.containsKey(instance.typeId())) {
                continue;
            }
            states.put(instance.instanceId(), SfxConfigurableMachineState.decode(instance.stateBlob()));
            activeInstances.add(instance.instanceId());
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

    private void tickMachine(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            activeInstances.remove(instanceId);
            return;
        }
        SfxConfigurableMachineDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            activeInstances.remove(instanceId);
            return;
        }
        if (definition.kind() == SfxConfigurableMachineKind.ACCESS_PORT) {
            activeInstances.remove(instanceId);
            return;
        }
        SfxConfigurableMachineState state = currentState(instanceId, instance);
        SfxConfigurableMachineSession session = sessionsByHost.get(instanceId);
        if (session != null) {
            syncInventoryToState(session.inventory(), state, definition, session.panelType());
        }
        Location location = locationFor(instance);
        boolean changed = switch (definition.kind()) {
            case ASSEMBLER -> tickAssembler(instanceId, definition, state, location);
            case REACTOR -> tickReactor(instanceId, instance, definition, state, location);
            case ACCESS_PORT -> false;
        };
        if (changed) {
            dirtyInstances.add(instanceId);
        }
        if (session != null && tickCounter % 10L == 0L) {
            render(session, instance, definition, state);
        }
        if (session == null && !state.isActive() && !state.hasInventory()) {
            activeInstances.remove(instanceId);
        }
    }

    private boolean tickAssembler(UUID instanceId, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, Location location) {
        boolean changed = false;
        if (state.cooldownTicks() > 0) {
            state.cooldownTicks(state.cooldownTicks() - 1);
            changed = true;
            return changed;
        }
        if (!state.enabled() || location == null || !hasAssemblerMaterials(state, definition) || state.storedEnergy() < definition.energyPerAction()) {
            return changed;
        }
        consumeAssemblerMaterials(state, definition);
        state.storedEnergy(state.storedEnergy() - definition.energyPerAction());
        state.cooldownTicks(ASSEMBLER_COOLDOWN_TICKS);
        recentEnergyConsumption.merge(instanceId, definition.energyPerAction(), Integer::sum);
        Location spawn = location.clone().add(0.5D, definition == null ? 3.0D : state.offsetTenths() / 10.0D, 0.5D);
        if (definition.spawnType() == EntityType.IRON_GOLEM) {
            IronGolem golem = (IronGolem) location.getWorld().spawnEntity(spawn, EntityType.IRON_GOLEM);
            golem.setPlayerCreated(true);
            location.getWorld().playSound(location, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0F, 1.0F);
        } else if (definition.spawnType() == EntityType.WITHER) {
            Wither wither = (Wither) location.getWorld().spawnEntity(spawn, EntityType.WITHER);
            wither.setInvulnerableTicks(220);
        }
        return true;
    }

    private boolean tickReactor(UUID instanceId, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        boolean changed = false;
        if (definition.witherAura()) {
            applyWitherAura(location);
        }
        if (!state.hasActiveFuel()) {
            SfxConfigurableMachineDefinition.ReactorFuel fuel = findFuel(definition, state);
            if (fuel == null || (fuel.output() != null && !canFitOutput(items, state, fuel.output(), 0, 1))) {
                removeReactorHologram(instance.anchorKey());
                return false;
            }
            if (!hasWaterCooling(location) || !consumeCoolantIfNeeded(state, definition)) {
                explodeReactor(instance, location);
                return true;
            }
            consumeInput(state, fuelSlotIndex(state, fuel), fuel.amount());
            state.activeFuelKey(fuel.key());
            state.fuelProgressTicks(0);
            state.fuelTotalTicks(fuel.seconds() * 20);
            changed = true;
        }
        if (!hasWaterCooling(location)) {
            explodeReactor(instance, location);
            return true;
        }
        if (!consumeCoolantIfNeeded(state, definition)) {
            explodeReactor(instance, location);
            return true;
        }
        boolean electricityFocus = state.mode() == 0;
        if (electricityFocus && state.storedEnergy() + definition.energyPerTick() > definition.capacity()) {
            updateReactorHologram(instance.anchorKey(), state);
            return changed;
        }
        state.fuelProgressTicks(state.fuelProgressTicks() + 1);
        if (state.coolantTotalTicks() > 0) {
            state.coolantProgressTicks(state.coolantProgressTicks() + 1);
        }
        if (state.storedEnergy() + definition.energyPerTick() <= definition.capacity()) {
            state.storedEnergy(state.storedEnergy() + definition.energyPerTick());
        }
        if (state.fuelProgressTicks() >= state.fuelTotalTicks()) {
            SfxConfigurableMachineDefinition.ReactorFuel completed = fuelByKey(definition, state.activeFuelKey());
            if (completed != null && completed.output() != null) {
                if (!canFitOutput(items, state, completed.output(), 0, 1)) {
                    state.fuelProgressTicks(state.fuelTotalTicks());
                    updateReactorHologram(instance.anchorKey(), state);
                    return true;
                }
                pushOutput(items, state, completed.output(), 0, 1);
            }
            state.clearFuel();
        }
        updateReactorHologram(instance.anchorKey(), state);
        return true;
    }

    private boolean consumeCoolantIfNeeded(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition definition) {
        if (state.coolantTotalTicks() > 0 && state.coolantProgressTicks() < state.coolantTotalTicks()) {
            return true;
        }
        int slot = findCoolantSlot(state, definition.coolantItemId());
        if (slot < 0) {
            return false;
        }
        consumeInput(state, slot, 1);
        state.coolantProgressTicks(0);
        state.coolantTotalTicks(REACTOR_COOLANT_TICKS);
        return true;
    }

    private void applyWitherAura(Location location) {
        for (org.bukkit.entity.Entity entity : location.getWorld().getNearbyEntities(location, 5.0D, 5.0D, 5.0D,
                candidate -> candidate instanceof LivingEntity && candidate.isValid())) {
            if (entity instanceof LivingEntity living) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1, true, true, true));
            }
        }
    }

    private void explodeReactor(SfxBlockInstanceRecord instance, Location location) {
        removeReactorHologram(instance.anchorKey());
        SfxConfigurableMachineSession session = sessionsByHost.remove(instance.instanceId());
        if (session != null) {
            sessionsByViewer.remove(session.viewerId());
            Player viewer = plugin.getServer().getPlayer(session.viewerId());
            if (viewer != null) {
                runtime.executeForPlayer(viewer, viewer::closeInventory);
            }
        }
        SfxConfigurableMachineState state = states.remove(instance.instanceId());
        if (state != null) {
            for (int slot = 0; slot < state.inputCapacity(); slot++) {
                dropStack(location.getBlock(), state.input(slot));
            }
            for (int slot = 0; slot < state.outputCapacity(); slot++) {
                dropStack(location.getBlock(), state.output(slot));
            }
        }
        dirtyInstances.remove(instance.instanceId());
        activeInstances.remove(instance.instanceId());
        blockData.unregisterAt(location);
        location.getBlock().setType(Material.AIR, false);
        location.getWorld().createExplosion(location.clone().add(0.5D, 0.5D, 0.5D), 6.0F, true, true);
    }

    private void openMachine(Player player, SfxBlockInstanceRecord instance) {
        SfxConfigurableMachineDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        if (definition.kind() == SfxConfigurableMachineKind.ACCESS_PORT) {
            SfxBlockInstanceRecord reactor = reactorBelowAccessPort(instance);
            if (reactor == null) {
                openAccessPortWithoutReactor(player, instance, definition);
                return;
            }
            openPanel(player, instance.instanceId(), reactor.instanceId(), SfxConfigurableMachineHolder.PanelType.ACCESS_PORT);
            return;
        }
        openPanel(player, instance.instanceId(), instance.instanceId(), definition.kind() == SfxConfigurableMachineKind.ASSEMBLER
                ? SfxConfigurableMachineHolder.PanelType.ASSEMBLER
                : SfxConfigurableMachineHolder.PanelType.REACTOR);
    }

    private void openAccessPortWithoutReactor(Player player, SfxBlockInstanceRecord accessPort, SfxConfigurableMachineDefinition definition) {
        Component title = localization.itemName(definition.id(), Component.text(definition.id()));
        Inventory inventory = plugin.getServer().createInventory(new SfxConfigurableMachineHolder(accessPort.instanceId(), accessPort.instanceId(), SfxConfigurableMachineHolder.PanelType.ACCESS_PORT), INVENTORY_SIZE, title);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(REACTOR_STATUS_SLOT, ItemBuilder.of(Material.RED_WOOL)
                .name("<red>No Reactor</red>")
                .lore("<gray>Place this access port 3 blocks above a reactor.</gray>")
                .build());
        player.openInventory(inventory);
    }

    private void openPanel(Player player, UUID panelInstanceId, UUID hostInstanceId, SfxConfigurableMachineHolder.PanelType panelType) {
        SfxConfigurableMachineSession existing = sessionsByHost.get(hostInstanceId);
        if (existing != null && !existing.viewerId().equals(player.getUniqueId())) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.busy", "<red>This machine is already open.</red>")));
            return;
        }
        SfxConfigurableMachineSession previous = sessionsByViewer.remove(player.getUniqueId());
        if (previous != null) {
            sessionsByHost.remove(previous.hostInstanceId());
            syncSessionState(previous);
        }
        SfxBlockInstanceRecord host = blockData.findInstance(hostInstanceId).orElse(null);
        if (host == null) {
            return;
        }
        SfxConfigurableMachineDefinition definition = definitions.get(host.typeId());
        if (definition == null) {
            return;
        }
        SfxConfigurableMachineState state = currentState(hostInstanceId, host);
        SfxBlockInstanceRecord panelInstance = blockData.findInstance(panelInstanceId).orElse(host);
        SfxConfigurableMachineDefinition titleDefinition = panelType == SfxConfigurableMachineHolder.PanelType.ACCESS_PORT
                ? definitions.getOrDefault(panelInstance.typeId(), definition)
                : definition;
        Component title = localization.itemName(titleDefinition.id(), Component.text(titleDefinition.id()));
        Inventory inventory = plugin.getServer().createInventory(new SfxConfigurableMachineHolder(panelInstanceId, hostInstanceId, panelType), INVENTORY_SIZE, title);
        SfxConfigurableMachineSession session = new SfxConfigurableMachineSession(player.getUniqueId(), panelInstanceId, hostInstanceId, panelType, inventory);
        sessionsByViewer.put(player.getUniqueId(), session);
        sessionsByHost.put(hostInstanceId, session);
        activeInstances.add(hostInstanceId);
        render(session, host, definition, state);
        player.openInventory(inventory);
    }

    private void refreshSession(UUID hostInstanceId) {
        SfxConfigurableMachineSession session = sessionsByHost.get(hostInstanceId);
        if (session == null) {
            return;
        }
        SfxBlockInstanceRecord host = blockData.findInstance(hostInstanceId).orElse(null);
        if (host == null) {
            return;
        }
        SfxConfigurableMachineDefinition definition = definitions.get(host.typeId());
        if (definition == null) {
            return;
        }
        SfxConfigurableMachineState state = currentState(hostInstanceId, host);
        syncInventoryToState(session.inventory(), state, definition, session.panelType());
        dirtyInstances.add(hostInstanceId);
        activeInstances.add(hostInstanceId);
        render(session, host, definition, state);
    }

    private void syncSessionState(SfxConfigurableMachineSession session) {
        SfxBlockInstanceRecord host = blockData.findInstance(session.hostInstanceId()).orElse(null);
        if (host == null) {
            return;
        }
        SfxConfigurableMachineDefinition definition = definitions.get(host.typeId());
        if (definition == null) {
            return;
        }
        SfxConfigurableMachineState state = currentState(session.hostInstanceId(), host);
        syncInventoryToState(session.inventory(), state, definition, session.panelType());
        dirtyInstances.add(session.hostInstanceId());
        activeInstances.add(session.hostInstanceId());
    }

    private void syncInventoryToState(Inventory inventory, SfxConfigurableMachineState state, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineHolder.PanelType panelType) {
        if (panelType == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            state.input(0, SfxElectricStack.fromItemStack(items, inventory.getItem(ASSEMBLER_HEAD_SLOTS[0])));
            state.input(1, SfxElectricStack.fromItemStack(items, inventory.getItem(ASSEMBLER_BODY_SLOTS[0])));
            for (int i = 2; i < state.inputCapacity(); i++) {
                state.input(i, null);
            }
            for (int i = 0; i < state.outputCapacity(); i++) {
                state.output(i, null);
            }
            return;
        }
        state.input(0, SfxElectricStack.fromItemStack(items, inventory.getItem(REACTOR_FUEL_SLOTS[0])));
        state.input(1, SfxElectricStack.fromItemStack(items, inventory.getItem(REACTOR_FUEL_SLOTS[1])));
        state.input(2, SfxElectricStack.fromItemStack(items, inventory.getItem(REACTOR_FUEL_SLOTS[2])));
        state.input(3, SfxElectricStack.fromItemStack(items, inventory.getItem(REACTOR_COOLANT_SLOTS[0])));
        state.input(4, SfxElectricStack.fromItemStack(items, inventory.getItem(REACTOR_COOLANT_SLOTS[1])));
        state.input(5, SfxElectricStack.fromItemStack(items, inventory.getItem(REACTOR_COOLANT_SLOTS[2])));
        state.output(0, SfxElectricStack.fromItemStack(items, inventory.getItem(REACTOR_OUTPUT_SLOTS[0])));
        state.output(1, null);
        state.output(2, null);
    }

    private void render(SfxConfigurableMachineSession session, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        if (session.panelType() == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            renderAssembler(session.inventory(), definition, state);
        } else {
            renderReactor(session.inventory(), instance, definition, state, session.panelType());
        }
    }

    private void renderAssembler(Inventory inventory, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        ItemStack head = inventory.getItem(ASSEMBLER_HEAD_SLOTS[0]);
        ItemStack body = inventory.getItem(ASSEMBLER_BODY_SLOTS[0]);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(ASSEMBLER_HEAD_SLOTS[0], head);
        inventory.setItem(ASSEMBLER_BODY_SLOTS[0], body);
        inventory.setItem(ENABLE_SLOT, ItemBuilder.of(state.enabled() ? Material.LIME_WOOL : Material.RED_WOOL)
                .name(state.enabled() ? "<green>Enabled: ✔</green>" : "<red>Enabled: ✘</red>")
                .lore("<gray>Click to toggle this assembler.</gray>")
                .build());
        inventory.setItem(OFFSET_SLOT, ItemBuilder.of(Material.COMPASS)
                .name("<yellow>Offset: " + (state.offsetTenths() / 10.0D) + " Block(s)</yellow>")
                .lore("<gray>Left-click: +0.1</gray>", "<gray>Right-click: -0.1</gray>")
                .build());
        inventory.setItem(COOLDOWN_SLOT, ItemBuilder.of(Material.CLOCK)
                .name("<yellow>Cooldown: 30 Seconds</yellow>")
                .lore("<gray>Remaining: " + formatSeconds(state.cooldownTicks()) + "</gray>",
                        "<gray>Stored: " + state.storedEnergy() + "/" + definition.capacity() + " J</gray>",
                        "<gray>Cost: " + definition.energyPerAction() + " J</gray>")
                .build());
        inventory.setItem(19, ItemBuilder.of(definition.headMaterial()).name("<aqua>Head Slot</aqua>").lore("<gray>Required: " + definition.headAmount() + "</gray>").build());
        inventory.setItem(23, ItemBuilder.of(definition.bodyMaterial()).name("<aqua>Body Slot</aqua>").lore("<gray>Required: " + definition.bodyAmount() + "</gray>").build());
    }

    private void renderReactor(Inventory inventory, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, SfxConfigurableMachineHolder.PanelType panelType) {
        ItemStack[] fuel = slots(inventory, REACTOR_FUEL_SLOTS);
        ItemStack[] coolant = slots(inventory, REACTOR_COOLANT_SLOTS);
        ItemStack output = inventory.getItem(REACTOR_OUTPUT_SLOTS[0]);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        restoreSlots(inventory, REACTOR_FUEL_SLOTS, fuel);
        restoreSlots(inventory, REACTOR_COOLANT_SLOTS, coolant);
        inventory.setItem(REACTOR_OUTPUT_SLOTS[0], output);
        inventory.setItem(REACTOR_MODE_SLOT, ItemBuilder.of(state.mode() == 0 ? Material.REDSTONE : Material.HOPPER)
                .name(state.mode() == 0 ? "<yellow>Focus: Electricity</yellow>" : "<yellow>Focus: Production</yellow>")
                .lore("<gray>Click to switch reactor focus.</gray>")
                .build());
        inventory.setItem(REACTOR_PROGRESS_SLOT, ItemBuilder.of(definition.id().equals("sf:netherstar_reactor") ? Material.NETHER_STAR : Material.GREEN_DYE)
                .name("<yellow>Reactor Progress</yellow>")
                .lore("<gray>Fuel: " + progressText(state.fuelProgressTicks(), state.fuelTotalTicks()) + "</gray>",
                        "<gray>Coolant: " + coolantPercent(state) + "%</gray>",
                        "<gray>Stored: " + state.storedEnergy() + "/" + definition.capacity() + " J</gray>",
                        "<gray>Output: " + definition.energyPerTick() + " J/t</gray>")
                .build());
        SfxBlockInstanceRecord accessPort = accessPortAbove(instance);
        boolean hasPort = accessPort != null;
        inventory.setItem(REACTOR_STATUS_SLOT, ItemBuilder.of(hasPort ? Material.LIME_WOOL : Material.RED_WOOL)
                .name(hasPort ? "<green>Access Port detected</green>" : "<red>No Access Port</red>")
                .lore(hasPort ? new String[] {"<gray>Click to open the access port.</gray>"} : new String[] {"<gray>Place an access port 3 blocks above this reactor.</gray>"})
                .build());
        inventory.setItem(18, ItemBuilder.of(Material.CHEST).name("<aqua>Fuel Slots</aqua>").build());
        inventory.setItem(26, ItemBuilder.of(Material.PACKED_ICE).name("<aqua>Coolant Slots</aqua>").lore("<red>The reactor will explode without coolant.</red>").build());
        inventory.setItem(39, ItemBuilder.of(Material.CHEST).name("<aqua>Byproduct Slot</aqua>").build());
    }

    private ItemStack[] slots(Inventory inventory, int[] slots) {
        ItemStack[] result = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            result[i] = inventory.getItem(slots[i]);
        }
        return result;
    }

    private void restoreSlots(Inventory inventory, int[] slots, ItemStack[] contents) {
        for (int i = 0; i < slots.length; i++) {
            inventory.setItem(slots[i], contents[i]);
        }
    }

    private void handleButtonClick(Player player, SfxConfigurableMachineHolder holder, SfxBlockInstanceRecord host, SfxConfigurableMachineDefinition definition, int slot, ClickType clickType) {
        if (definition.kind() == SfxConfigurableMachineKind.ACCESS_PORT) {
            return;
        }
        SfxConfigurableMachineState state = currentState(host.instanceId(), host);
        if (holder.panelType() == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            if (slot == ENABLE_SLOT) {
                state.enabled(!state.enabled());
            } else if (slot == OFFSET_SLOT) {
                int delta = clickType.isRightClick() ? -1 : 1;
                state.offsetTenths(state.offsetTenths() + delta);
            }
            dirtyInstances.add(host.instanceId());
            activeInstances.add(host.instanceId());
            refreshSession(host.instanceId());
            return;
        }
        if (slot == REACTOR_MODE_SLOT) {
            state.mode(state.mode() == 0 ? 1 : 0);
            dirtyInstances.add(host.instanceId());
            activeInstances.add(host.instanceId());
            refreshSession(host.instanceId());
            return;
        }
        if (slot == REACTOR_STATUS_SLOT) {
            if (holder.panelType() == SfxConfigurableMachineHolder.PanelType.ACCESS_PORT) {
                openPanel(player, host.instanceId(), host.instanceId(), SfxConfigurableMachineHolder.PanelType.REACTOR);
                return;
            }
            SfxBlockInstanceRecord port = accessPortAbove(host);
            if (port != null) {
                openPanel(player, port.instanceId(), host.instanceId(), SfxConfigurableMachineHolder.PanelType.ACCESS_PORT);
            }
        }
    }

    private SfxConfigurableMachineState currentState(UUID instanceId, SfxBlockInstanceRecord instance) {
        return states.computeIfAbsent(instanceId, ignored -> SfxConfigurableMachineState.decode(instance.stateBlob()));
    }

    private void flushDirty() {
        for (UUID instanceId : List.copyOf(dirtyInstances)) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            SfxConfigurableMachineState state = states.get(instanceId);
            if (instance == null || state == null) {
                dirtyInstances.remove(instanceId);
                continue;
            }
            SfxBlockLifecycleState lifecycle = state.isActive() ? SfxBlockLifecycleState.ACTIVE : SfxBlockLifecycleState.IDLE;
            blockData.updateInstanceState(instanceId, state.encode(), lifecycle);
            dirtyInstances.remove(instanceId);
        }
    }

    private boolean hasAssemblerMaterials(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition definition) {
        SfxElectricStack head = state.input(0);
        SfxElectricStack body = state.input(1);
        return head != null && !head.isSfxItem() && head.material() == definition.headMaterial() && head.amount() >= definition.headAmount()
                && body != null && !body.isSfxItem() && body.material() == definition.bodyMaterial() && body.amount() >= definition.bodyAmount();
    }

    private void consumeAssemblerMaterials(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition definition) {
        consumeInput(state, 0, definition.headAmount());
        consumeInput(state, 1, definition.bodyAmount());
    }

    private int fuelSlotIndex(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition.ReactorFuel fuel) {
        for (int slot = 0; slot < 3; slot++) {
            if (fuel.matches(state.input(slot))) {
                return slot;
            }
        }
        return 0;
    }

    private SfxConfigurableMachineDefinition.ReactorFuel findFuel(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        for (SfxConfigurableMachineDefinition.ReactorFuel fuel : definition.fuels()) {
            for (int slot = 0; slot < 3; slot++) {
                if (fuel.matches(state.input(slot))) {
                    return fuel;
                }
            }
        }
        return null;
    }

    private SfxConfigurableMachineDefinition.ReactorFuel fuelByKey(SfxConfigurableMachineDefinition definition, String key) {
        if (key == null) {
            return null;
        }
        for (SfxConfigurableMachineDefinition.ReactorFuel fuel : definition.fuels()) {
            if (key.equals(fuel.key())) {
                return fuel;
            }
        }
        return null;
    }

    private int findCoolantSlot(SfxConfigurableMachineState state, String coolantItemId) {
        for (int slot = 3; slot < 6; slot++) {
            SfxElectricStack stack = state.input(slot);
            if (stack != null && stack.isSfxItem() && coolantItemId.equals(stack.itemId())) {
                return slot;
            }
        }
        return -1;
    }

    private SfxElectricStack consumeInput(SfxConfigurableMachineState state, int slot, int amount) {
        SfxElectricStack input = state.input(slot);
        if (input == null || input.amount() < amount) {
            return null;
        }
        SfxElectricStack consumed = input.copyWithAmount(amount);
        int remaining = input.amount() - amount;
        state.input(slot, remaining <= 0 ? null : input.copyWithAmount(remaining));
        return consumed;
    }

    private boolean canFitOutput(SfxItems items, SfxConfigurableMachineState state, SfxElectricStack output, int startSlot, int slotCount) {
        if (output == null) {
            return true;
        }
        SfxElectricStack[] simulated = new SfxElectricStack[slotCount];
        for (int i = 0; i < slotCount; i++) {
            simulated[i] = state.output(startSlot + i);
        }
        Integer slot = findOutputSlot(items, simulated, output);
        return slot != null;
    }

    private void pushOutput(SfxItems items, SfxConfigurableMachineState state, SfxElectricStack output, int startSlot, int slotCount) {
        SfxElectricStack[] simulated = new SfxElectricStack[slotCount];
        for (int i = 0; i < slotCount; i++) {
            simulated[i] = state.output(startSlot + i);
        }
        Integer found = findOutputSlot(items, simulated, output);
        if (found == null) {
            return;
        }
        int slot = startSlot + found;
        SfxElectricStack current = state.output(slot);
        state.output(slot, current == null ? output : current.copyWithAmount(current.amount() + output.amount()));
    }

    private Integer findOutputSlot(SfxItems items, SfxElectricStack[] outputs, SfxElectricStack output) {
        for (int slot = 0; slot < outputs.length; slot++) {
            SfxElectricStack current = outputs[slot];
            if (current != null && output.canMerge(current, items)) {
                return slot;
            }
        }
        for (int slot = 0; slot < outputs.length; slot++) {
            if (outputs[slot] == null) {
                return slot;
            }
        }
        return null;
    }

    private boolean hasWaterCooling(Location location) {
        Block center = location.getBlock();
        int[][] offsets = {
                {-1, -1}, {-1, 0}, {-1, 1},
                {0, -1},           {0, 1},
                {1, -1},  {1, 0},  {1, 1}
        };
        int[] offset = offsets[ThreadLocalRandom.current().nextInt(offsets.length)];
        Material type = center.getRelative(offset[0], 0, offset[1]).getType();
        return type == Material.WATER || type == Material.BUBBLE_COLUMN;
    }

    private SfxBlockInstanceRecord reactorBelowAccessPort(SfxBlockInstanceRecord accessPort) {
        Location location = locationFor(accessPort);
        if (location == null) {
            return null;
        }
        SfxAnchorRecord anchor = blockData.findAnchor(location.getBlock().getRelative(BlockFace.DOWN, 3).getLocation()).orElse(null);
        if (anchor == null) {
            return null;
        }
        SfxBlockInstanceRecord reactor = blockData.findInstance(anchor.instanceId()).orElse(null);
        return reactor != null && isProducer(reactor.typeId()) ? reactor : null;
    }

    private SfxBlockInstanceRecord accessPortAbove(SfxBlockInstanceRecord reactor) {
        Location location = locationFor(reactor);
        if (location == null) {
            return null;
        }
        SfxAnchorRecord anchor = blockData.findAnchor(location.getBlock().getRelative(BlockFace.UP, 3).getLocation()).orElse(null);
        if (anchor == null) {
            return null;
        }
        SfxBlockInstanceRecord port = blockData.findInstance(anchor.instanceId()).orElse(null);
        return port != null && "sf:reactor_access_port".equals(port.typeId()) ? port : null;
    }

    private void updateReactorHologram(SfxBlockAnchorKey key, SfxConfigurableMachineState state) {
        int percent = coolantPercent(state);
        Component text = Text.mm("<aqua>❄ " + percent + "%</aqua>");
        floatingText.update(new SfxFloatingTextProjection(
                reactorHologramKey(key),
                key.x() + 0.5D,
                key.y() + 1.35D,
                key.z() + 0.5D,
                text,
                HOLOGRAM_VIEW_DISTANCE_SQUARED,
                true));
    }

    private void removeReactorHologram(SfxBlockAnchorKey key) {
        floatingText.remove(reactorHologramKey(key));
    }

    private SfxFloatingTextKey reactorHologramKey(SfxBlockAnchorKey key) {
        return new SfxFloatingTextKey("reactor-coolant", key.worldId(), key.x(), key.y(), key.z());
    }

    private int coolantPercent(SfxConfigurableMachineState state) {
        if (state.coolantTotalTicks() <= 0) {
            return 0;
        }
        int remaining = Math.max(0, state.coolantTotalTicks() - state.coolantProgressTicks());
        return Math.max(0, Math.min(100, (int) Math.round(remaining * 100.0D / state.coolantTotalTicks())));
    }

    private String progressText(int current, int total) {
        if (total <= 0) {
            return "0%";
        }
        int percent = Math.max(0, Math.min(100, (int) Math.round(current * 100.0D / total)));
        return percent + "%";
    }

    private String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f Seconds", Math.max(0, ticks) / 20.0D);
    }

    private Location locationFor(SfxBlockInstanceRecord instance) {
        World world = plugin.getServer().getWorld(instance.anchorKey().worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, instance.anchorKey().x(), instance.anchorKey().y(), instance.anchorKey().z());
    }

    private void fill(Inventory inventory, Material material) {
        ItemStack filler = ItemBuilder.of(material).name(" ").build();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private int[] editableInputSlots(SfxConfigurableMachineHolder.PanelType panelType) {
        if (panelType == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            return new int[] {ASSEMBLER_HEAD_SLOTS[0], ASSEMBLER_BODY_SLOTS[0]};
        }
        return new int[] {19, 28, 37, 25, 34, 43};
    }

    private int[] editableOutputSlots(SfxConfigurableMachineHolder.PanelType panelType) {
        if (panelType == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            return new int[0];
        }
        return REACTOR_OUTPUT_SLOTS;
    }

    private boolean isButtonSlot(SfxConfigurableMachineHolder.PanelType panelType, int slot) {
        if (panelType == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            return slot == ENABLE_SLOT || slot == OFFSET_SLOT || slot == COOLDOWN_SLOT;
        }
        return slot == REACTOR_MODE_SLOT || slot == REACTOR_PROGRESS_SLOT || slot == REACTOR_STATUS_SLOT;
    }

    private boolean moveShiftClickedStack(Inventory inventory, ItemStack current, int[] targetSlots, SfxConfigurableMachineDefinition definition) {
        if (current == null || current.getType().isAir()) {
            return false;
        }
        int original = current.getAmount();
        int remaining = current.getAmount();
        for (int slot : targetSlots) {
            ItemStack existing = inventory.getItem(slot);
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
        for (int slot : targetSlots) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            int moved = Math.min(current.getMaxStackSize(), remaining);
            ItemStack inserted = current.clone();
            inserted.setAmount(moved);
            inventory.setItem(slot, inserted);
            remaining -= moved;
            if (remaining <= 0) {
                current.setAmount(0);
                return true;
            }
        }
        current.setAmount(remaining);
        return remaining < original;
    }

    private boolean isTakingFromOutput(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentItem = current != null && !current.getType().isAir();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        return currentItem && (cursorEmpty || event.isShiftClick());
    }

    private boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    private void dropPluginBlock(Block block, String typeId) {
        Item dropped = block.getWorld().dropItem(block.getLocation().add(0.5D, 0.5D, 0.5D), items.create(typeId));
        dropped.setPickupDelay(0);
    }

    private void dropStack(Block block, SfxElectricStack stack) {
        if (stack == null) {
            return;
        }
        Item dropped = block.getWorld().dropItem(block.getLocation().add(0.5D, 0.5D, 0.5D), stack.toItemStack(items));
        dropped.setPickupDelay(0);
    }
}
