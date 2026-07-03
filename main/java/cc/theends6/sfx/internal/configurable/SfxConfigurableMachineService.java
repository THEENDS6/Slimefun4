package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxAnchoredInteraction;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayMode;
import cc.theends6.sfx.internal.display.SfxFloatingTextKey;
import cc.theends6.sfx.internal.display.SfxFloatingTextProjection;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import cc.theends6.sfx.internal.machine.SfxMachineLegacyHookBridge;
import cc.theends6.sfx.internal.ui.SfxMachineMenuTransactions;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachineExecution;
import cc.theends6.sfx.internal.machine.SfxMachineEffectDispatcher;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseContext;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseResult;
import cc.theends6.sfx.internal.machine.SfxMachinePipelineGuard;
import cc.theends6.sfx.internal.machine.SfxMachineState;
import cc.theends6.sfx.internal.machine.SfxMachineTickSettings;
import cc.theends6.sfx.internal.ui.SfxInventoryPainter;
import cc.theends6.sfx.internal.ui.SfxUiItems;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxEventGuards;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxInventorySlots;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import cc.theends6.sfx.internal.ui.SfxMachineStatusIconRenderer;
import cc.theends6.sfx.internal.ui.SfxMachineStatusKey;
import cc.theends6.sfx.internal.ui.SfxMachineStatusView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
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
    private static final long FLUSH_INTERVAL = 20L;
    private static final int[] ASSEMBLER_HEAD_SLOTS = {19, 28};
    private static final int[] ASSEMBLER_BODY_SLOTS = {25, 34};
    private static final int[] ASSEMBLER_HEAD_STATE_SLOTS = {0, 1};
    private static final int[] ASSEMBLER_BODY_STATE_SLOTS = {2, 3};
    private static final int ENABLE_SLOT = 13;
    private static final int OFFSET_SLOT = 31;
    private static final int ASSEMBLER_STATUS_SLOT = 22;
    private static final int ASSEMBLER_WORK_TICKS = 30 * 20;
    private static final int REACTOR_COOLANT_TICKS = 300;
    private static final int HOLOGRAM_VIEW_DISTANCE_SQUARED = 32 * 32;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxFloatingTextDisplayService floatingText;
    private final Map<String, SfxConfigurableMachineDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, SfxConfigurableMachineState> states = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyInstances = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeInstances = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> recentEnergyConsumption = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLogicTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> recentGeneratedEnergy = new ConcurrentHashMap<>();
    private final Set<UUID> autoPausedProducers = ConcurrentHashMap.newKeySet();
    private final SfxMachineStatusIconRenderer statusIcons;
    private final SfxMachineTickSettings tickSettings;
    private final SfxMachineRuntimeEngine machineRuntime;
    private final Map<UUID, SfxConfigurableMachineSession> sessionsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, SfxConfigurableMachineSession> sessionsByHost = new ConcurrentHashMap<>();
    private volatile boolean running;
    volatile long tickCounter;

    public SfxConfigurableMachineService(
            JavaPlugin plugin,
            SfxRuntime runtime,
            SfxItems items,
            SfxLocalization localization,
            SfxBlockDataService blockData,
            SfxFloatingTextDisplayService floatingText,
            SfxMachineRuntimeEngine sharedMachineRuntime
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.floatingText = Objects.requireNonNull(floatingText, "floatingText");
        this.statusIcons = new SfxMachineStatusIconRenderer(localization);
        this.tickSettings = SfxMachineTickSettings.from(plugin.getConfig());
        this.machineRuntime = sharedMachineRuntime == null ? new SfxMachineRuntimeEngine() : sharedMachineRuntime;
        registerDefinitions();
        registerFrameworkEffects();
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

    private void registerDefinitions() {
        Map<String, SfxConfigurableMachineDefinition> yamlDefinitions = SfxConfigurableMachineDefinitions.load(plugin);
        if (yamlDefinitions.isEmpty()) {
            throw new IllegalStateException("No compiled configurable machine definitions were loaded; Java fallback definitions are disabled.");
        }
        yamlDefinitions.values().forEach(this::register);
        machineRuntime.registerDefinitions(SfxConfigurableMachineFrameworkBridge.definitions(definitions));
    }

    private void register(SfxConfigurableMachineDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    private void registerFrameworkEffects() {
        for (String effectName : List.of(
                "reactor:consume-coolant",
                "reactor:emit-energy",
                "reactor:meltdown-on-error",
                "assembler:validate-offset",
                "assembler:spawn-entity",
                "proxy:resolve-host"
        )) {
            machineRuntime.registerEffectHook(effectName, context -> frameworkConfigurableEffect(effectName, context));
        }
    }

    private SfxMachinePhaseResult frameworkConfigurableEffect(String effectName, SfxMachinePhaseContext phaseContext) {
        SfxConfigurableMachineDefinition definition = phaseContext.attachment("configurable.definition", SfxConfigurableMachineDefinition.class).orElse(null);
        SfxConfigurableMachineState state = phaseContext.attachment("configurable.state", SfxConfigurableMachineState.class).orElse(null);
        SfxBlockInstanceRecord instance = phaseContext.attachment("configurable.instance", SfxBlockInstanceRecord.class).orElse(null);
        if (definition == null || state == null || instance == null) {
            return SfxMachinePhaseResult.cont();
        }
        phaseContext.put("configurable.framework.effect", effectName);
        return switch (effectName) {
            case "assembler:validate-offset" -> frameworkAssemblerValidate(phaseContext, instance, definition, state);
            case "assembler:spawn-entity" -> frameworkAssemblerSpawn(phaseContext, definition, state);
            case "reactor:consume-coolant" -> frameworkReactorProgress(phaseContext, instance, definition, state);
            case "reactor:emit-energy" -> frameworkReactorEmit(phaseContext, definition, state);
            case "reactor:meltdown-on-error" -> {
                phaseContext.put("configurable.reactor.meltdown.checked", Boolean.TRUE);
                yield SfxMachinePhaseResult.cont();
            }
            case "proxy:resolve-host" -> {
                phaseContext.put("configurable.proxy.host-resolve.requested", Boolean.TRUE);
                phaseContext.put("configurable.proxy.host.instanceId", instance.instanceId());
                yield SfxMachinePhaseResult.cont();
            }
            default -> SfxMachinePhaseResult.cont();
        };
    }

    private SfxMachinePhaseResult frameworkAssemblerValidate(SfxMachinePhaseContext phaseContext, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        if (definition.kind() != SfxConfigurableMachineKind.ASSEMBLER) {
            return SfxMachinePhaseResult.cont();
        }
        Location location = phaseContext.location();
        SfxMachineTickContext context = phaseContext.tickContext();
        if (location == null || context == null) {
            return SfxMachinePhaseResult.blocked(cc.theends6.sfx.internal.machine.SfxMachineStatus.BLOCKED, "missing location/context");
        }
        Location spawn = location.clone().add(0.5D, state.offsetTenths() / 10.0D, 0.5D);
        phaseContext.put("configurable.assembler.spawn", spawn);
        boolean changed = tickAssemblerProgress(phaseContext.instanceId(), definition, state, location, context, phaseContext.attachments());
        phaseContext.put("configurable.changed", changed);
        return SfxMachinePhaseResult.complete(SfxConfigurableMachineFrameworkBridge.statusFor(state, definition), "assembler progress executed through framework effect");
    }

    private SfxMachinePhaseResult frameworkAssemblerSpawn(SfxMachinePhaseContext phaseContext, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        if (definition.kind() != SfxConfigurableMachineKind.ASSEMBLER) {
            return SfxMachinePhaseResult.cont();
        }
        Location location = phaseContext.location();
        if (location == null) {
            return SfxMachinePhaseResult.cont();
        }
        boolean spawned = spawnAssemblerOutput(definition, state, location, phaseContext.attachments());
        if (spawned) {
            phaseContext.put("configurable.changed", Boolean.TRUE);
            return SfxMachinePhaseResult.complete(SfxConfigurableMachineFrameworkBridge.statusFor(state, definition), "assembler completion executed through framework effect");
        }
        return SfxMachinePhaseResult.cont();
    }

    private SfxMachinePhaseResult frameworkReactorProgress(SfxMachinePhaseContext phaseContext, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        if (definition.kind() != SfxConfigurableMachineKind.REACTOR) {
            return SfxMachinePhaseResult.cont();
        }
        Location location = phaseContext.location();
        SfxMachineTickContext context = phaseContext.tickContext();
        if (location == null || context == null) {
            return SfxMachinePhaseResult.blocked(cc.theends6.sfx.internal.machine.SfxMachineStatus.BLOCKED, "missing location/context");
        }
        boolean changed = tickProductionFocusReactor(phaseContext.instanceId(), instance, definition, state, location, context, phaseContext.attachments());
        phaseContext.put("configurable.changed", changed);
        return SfxMachinePhaseResult.complete(SfxConfigurableMachineFrameworkBridge.statusFor(state, definition), "reactor progress executed through framework effect");
    }

    private SfxMachinePhaseResult frameworkReactorEmit(SfxMachinePhaseContext phaseContext, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        if (definition.kind() != SfxConfigurableMachineKind.REACTOR) {
            return SfxMachinePhaseResult.cont();
        }
        Integer generated = phaseContext.attachment("configurable.reactor.generated", Integer.class).orElse(0);
        phaseContext.put("configurable.reactor.emitted", generated);
        return generated > 0
                ? SfxMachinePhaseResult.complete(SfxConfigurableMachineFrameworkBridge.statusFor(state, definition), "reactor emission recorded through framework effect")
                : SfxMachinePhaseResult.cont();
    }

    private Map<String, Object> configurableFrameworkAttributes(SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, SfxConfigurableMachineSession session) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("configurable.instance", instance);
        attributes.put("configurable.definition", definition);
        attributes.put("configurable.state", state);
        if (session != null) attributes.put("configurable.session", session);
        attributes.put("configurable.service", this);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkConfigurableEffect);
        return attributes;
    }

    private boolean runFrameworkConfigurableTick(UUID instanceId, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, SfxConfigurableMachineSession session, Location location, SfxMachineTickContext context, Map<String, Object> attributes) {
        SfxMachinePhaseResult before = machineRuntime.runPhase(definition.id(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, instanceId, location, context, null, SfxConfigurableMachineFrameworkBridge.statusFor(state, definition), attributes);
        if (!SfxMachinePipelineGuard.proceed(before, attributes, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
            return false;
        }
        if (definition.kind() == SfxConfigurableMachineKind.REACTOR) {
            SfxMachinePhaseResult afterProgress = machineRuntime.runPhase(definition.id(), SfxMachinePhase.AFTER_PROGRESS, instanceId, location, context, null, SfxConfigurableMachineFrameworkBridge.statusFor(state, definition), attributes);
            if (!SfxMachinePipelineGuard.proceed(afterProgress, attributes, SfxMachinePhase.AFTER_PROGRESS.name())) {
                return false;
            }
        }
        if (definition.kind() == SfxConfigurableMachineKind.ASSEMBLER && Boolean.TRUE.equals(attributes.get("configurable.assembler.readyToSpawn"))) {
            SfxMachinePhaseResult complete = machineRuntime.runPhase(definition.id(), SfxMachinePhase.ON_COMPLETE, instanceId, location, context, null, SfxConfigurableMachineFrameworkBridge.statusFor(state, definition), attributes);
            if (!SfxMachinePipelineGuard.proceed(complete, attributes, SfxMachinePhase.ON_COMPLETE.name())) {
                return false;
            }
        }
        Object changed = attributes.get("configurable.changed");
        return changed instanceof Boolean value && value;
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
            SfxMachineLegacyHookBridge.place(machineRuntime, marker.itemId(), instanceId, event.getBlockPlaced().getLocation(), "configurable", "SfxConfigurableMachineService.onPlace");
            activeInstances.add(instanceId);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        SfxAnchoredInteraction interaction = SfxAnchoredInteraction.resolve(event, blockData);
        if (interaction == null || !definitions.containsKey(interaction.instance().typeId())) {
            return;
        }
        SfxBlockInstanceRecord instance = interaction.instance();
        SfxMachineLegacyHookBridge.interact(machineRuntime, instance.typeId(), instance.instanceId(), interaction.block().getLocation(), "configurable", "SfxConfigurableMachineService.onInteract");
        if (SfxInteractionRules.prefersBlockPlacement(items, event)) {
            return;
        }
        SfxEventGuards.denyBlockAndItemUse(event);
        runtime.executeForPlayer(event.getPlayer(), () -> openMachine(event.getPlayer(), instance));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof SfxConfigurableMachineHolder holder)) {
            return;
        }
        if (SfxMachineMenuTransactions.isCreativeCloneClick(player, event)) {
            return;
        }
        SfxBlockInstanceRecord host = blockData.findInstance(holder.hostInstanceId()).orElse(null);
        SfxConfigurableMachineDefinition definition = host == null ? null : definitions.get(host.typeId());
        if (host != null && definition != null) {
            SfxMachineLegacyHookBridge.menuClick(machineRuntime, definition.id(), host.instanceId(), locationFor(host), "configurable", "SfxConfigurableMachineService.onInventoryClick");
        }
        if (host == null || definition == null) {
            event.setCancelled(true);
            return;
        }
        boolean topSlot = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (topSlot) {
            int raw = event.getRawSlot();
            boolean managedInput = contains(editableInputSlots(holder.panelType(), definition), raw);
            boolean managedOutput = contains(editableOutputSlots(holder.panelType(), definition), raw);
            if (SfxMachineMenuTransactions.handleManagedHotbarOrOffhand(event, event.getView().getTopInventory(), raw, player, managedInput, managedOutput, stack -> isValidInputItem(holder.panelType(), raw, stack, definition))) {
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.hostInstanceId()));
                return;
            }
            if ((managedInput || managedOutput) && SfxMachineMenuTransactions.handleManagedDoubleClick(event, event.getView().getTopInventory(), player, slot -> contains(managedOutput ? editableOutputSlots(holder.panelType(), definition) : editableInputSlots(holder.panelType(), definition), slot))) {
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.hostInstanceId()));
                return;
            }
        }
        if (topSlot && SfxMachineMenuTransactions.cancelUnsupportedManagedClick(event)) {
            return;
        }
        if (event.isShiftClick() && !topSlot) {
            int[] targetSlots = editableInputSlots(holder.panelType(), definition);
            if (moveShiftClickedStack(event.getView().getTopInventory(), event.getCurrentItem(), targetSlots, definition, holder.panelType())) {
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
            if (isButtonSlot(holder.panelType(), definition, raw)) {
                event.setCancelled(true);
                handleButtonClick(player, holder, host, definition, raw, event.getClick());
                return;
            }
            if (contains(editableOutputSlots(holder.panelType(), definition), raw)) {
                if (!SfxMachineMenuTransactions.isTakingFromOutput(event)) {
                    event.setCancelled(true);
                    return;
                }
                if (SfxMachineMenuTransactions.dropFromTopSlot(event, event.getView().getTopInventory(), raw, player)) {
                    event.setCancelled(true);
                }
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.hostInstanceId()));
                return;
            }
            if (!contains(editableInputSlots(holder.panelType(), definition), raw)) {
                event.setCancelled(true);
                return;
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isValidInputItem(holder.panelType(), raw, cursor, definition)) {
                event.setCancelled(true);
                return;
            }
            if (SfxMachineMenuTransactions.dropFromTopSlot(event, event.getView().getTopInventory(), raw, player)) {
                event.setCancelled(true);
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.hostInstanceId()));
                return;
            }
            if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                SfxMachineMenuTransactions.moveTopSlotToPlayer(event.getView().getTopInventory(), raw, player);
                event.setCancelled(true);
                runtime.executeForPlayerLater(player, 1L, () -> refreshSession(holder.hostInstanceId()));
                return;
            }
        }
        if (topSlot && SfxMachineMenuTransactions.cancelUnsupportedManagedClick(event)) {
            return;
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
        SfxBlockInstanceRecord host = blockData.findInstance(holder.hostInstanceId()).orElse(null);
        SfxConfigurableMachineDefinition definition = host == null ? null : definitions.get(host.typeId());
        if (host != null && definition != null) {
            SfxMachineLegacyHookBridge.menuClick(machineRuntime, definition.id(), host.instanceId(), locationFor(host), "configurable", "SfxConfigurableMachineService.onInventoryClick");
        }
        if (host == null || definition == null) {
            event.setCancelled(true);
            return;
        }
        int[] allowed = editableInputSlots(holder.panelType(), definition);
        boolean valid = true;
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            int slot = entry.getKey();
            if (slot >= topSize) {
                continue;
            }
            if (!contains(allowed, slot) || !isValidInputItem(holder.panelType(), slot, entry.getValue(), definition)) {
                valid = false;
                break;
            }
        }
        if (!valid) {
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
        SfxBlockInstanceRecord closeHost = blockData.findInstance(holder.hostInstanceId()).orElse(null);
        SfxConfigurableMachineDefinition closeDefinition = closeHost == null ? null : definitions.get(closeHost.typeId());
        if (closeHost != null && closeDefinition != null) {
            SfxMachineLegacyHookBridge.menuClose(machineRuntime, closeDefinition.id(), closeHost.instanceId(), locationFor(closeHost), "configurable", "SfxConfigurableMachineService.onInventoryClose");
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
        lastLogicTicks.clear();
        recentEnergyConsumption.clear();
        recentGeneratedEnergy.clear();
        autoPausedProducers.clear();
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
        lastLogicTicks.remove(instanceId);
        recentEnergyConsumption.remove(instanceId);
        recentGeneratedEnergy.remove(instanceId);
        autoPausedProducers.remove(instanceId);
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
            if (!state.enabled() || state.storedEnergy() < definition.energyPerAction()) {
                continue;
            }
            if (isAssemblerWorking(state) || hasAssemblerMaterials(state, definition)) {
                total += definition.energyPerAction();
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

    public int drainRecentGeneratedEnergy(List<UUID> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (UUID id : instanceIds) {
            Integer value = recentGeneratedEnergy.remove(id);
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

    public int chargeProducer(UUID instanceId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !isProducer(instance.typeId())) {
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


    public boolean isProducerAutoPaused(UUID instanceId) {
        return autoPausedProducers.contains(instanceId);
    }

    public void setProducerAutoPaused(UUID instanceId, boolean paused) {
        if (instanceId == null) {
            return;
        }
        if (paused) {
            autoPausedProducers.add(instanceId);
        } else {
            autoPausedProducers.remove(instanceId);
        }
        activeInstances.add(instanceId);
    }

    public boolean canAutoPauseProducer(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !isProducer(instance.typeId())) {
            return false;
        }
        SfxConfigurableMachineDefinition definition = definitions.get(instance.typeId());
        SfxConfigurableMachineState state = currentState(instanceId, instance);
        return definition != null && definition.kind() == SfxConfigurableMachineKind.REACTOR && state.mode() == 0;
    }

    public int producerPotentialGeneration(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !isProducer(instance.typeId())) {
            return 0;
        }
        SfxConfigurableMachineDefinition definition = definitions.get(instance.typeId());
        SfxConfigurableMachineState state = currentState(instanceId, instance);
        if (definition == null || definition.kind() != SfxConfigurableMachineKind.REACTOR || state.mode() != 0) {
            return 0;
        }
        Location location = locationFor(instance);
        if (location == null || !hasWaterCooling(location)) {
            return 0;
        }
        if (state.hasActiveFuel()) {
            return definition.energyPerTick();
        }
        SfxConfigurableMachineDefinition.ReactorFuel fuel = findFuel(definition, state);
        if (fuel == null || (fuel.output() != null && !canFitOutput(items, state, fuel.output(), 0, 1))) {
            return 0;
        }
        return definition.energyPerTick();
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
                    lastLogicTicks.remove(instanceId);
                    continue;
                }
                SfxConfigurableMachineDefinition loopDefinition = definitions.get(instance.typeId());
                if (loopDefinition == null) {
                    activeInstances.remove(instanceId);
                    lastLogicTicks.remove(instanceId);
                    continue;
                }
                if (loopDefinition.kind() == SfxConfigurableMachineKind.ACCESS_PORT) {
                    activeInstances.remove(instanceId);
                    lastLogicTicks.remove(instanceId);
                    continue;
                }
                if (loopDefinition.kind() == SfxConfigurableMachineKind.REACTOR) {
                    SfxConfigurableMachineState loopState = currentState(instanceId, instance);
                    if (loopState.mode() == 0) {
                        
                        continue;
                    }
                }
                Location location = locationFor(instance);
                if (location == null) {
                    continue;
                }
                boolean hasViewers = sessionsByHost.containsKey(instanceId);
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

    private void tickMachine(UUID instanceId, SfxMachineTickContext context) {
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
        if (location == null || !isInstanceChunkLoaded(instance)) {
            activeInstances.add(instanceId);
            return;
        }
        Map<String, Object> frameworkAttributes = configurableFrameworkAttributes(instance, definition, state, session);
        SfxMachineState frameworkState = new SfxMachineState();
        try (SfxMachineExecution machineExecution = machineRuntime.beginTick(instanceId, definition.id(), location, context, frameworkState, frameworkAttributes)) {
        boolean changed = runFrameworkConfigurableTick(instanceId, instance, definition, state, session, location, context, frameworkAttributes);
        if (changed) {
            if (blockData.findInstance(instanceId).isEmpty()) {
                return;
            }
            dirtyInstances.add(instanceId);
        }
        if (session != null && sessionsByHost.get(instanceId) == session) {
            render(session, instance, definition, state);
        }
        machineExecution.status(SfxConfigurableMachineFrameworkBridge.statusFor(state, definition));
        if (session == null && !state.isActive() && !state.hasInventory()) {
            activeInstances.remove(instanceId);
        }
        }
    }

    private boolean tickProductionFocusReactor(UUID instanceId, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, Location location, SfxMachineTickContext context, Map<String, Object> frameworkAttributes) {
        if (definition.kind() != SfxConfigurableMachineKind.REACTOR || state.mode() == 0 || location == null) {
            return false;
        }
        boolean changed = false;
        int loops = Math.max(1, Math.min(100, context.elapsedTicksInt()));
        for (int i = 0; i < loops; i++) {
            ReactorTickResult result = tickReactor(instanceId, instance, definition, state, location, null);
            changed |= result.changed();
            if (blockData.findInstance(instanceId).isEmpty()) {
                return changed;
            }
        }
        return changed;
    }

    private boolean tickAssemblerProgress(UUID instanceId, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, Location location, SfxMachineTickContext context, Map<String, Object> frameworkAttributes) {
        if (location == null) {
            return false;
        }
        if (!state.enabled()) {
            return false;
        }
        boolean changed = false;
        if (!isAssemblerWorking(state)) {
            if (!hasAssemblerMaterials(state, definition)) {
                return false;
            }
            consumeAssemblerMaterials(state, definition);
            state.activeFuelKey("assembler");
            state.fuelProgressTicks(0);
            state.fuelTotalTicks(ASSEMBLER_WORK_TICKS);
            changed = true;
        }
        int elapsed = Math.max(1, context.elapsedTicksInt());
        int remaining = Math.max(0, state.fuelTotalTicks() - state.fuelProgressTicks());
        int progressTicks = Math.min(elapsed, Math.max(1, remaining));
        if (definition.energyPerAction() > 0) {
            progressTicks = Math.min(progressTicks, state.storedEnergy() / definition.energyPerAction());
        }
        if (progressTicks <= 0) {
            return changed;
        }
        int consumed = definition.energyPerAction() * progressTicks;
        state.storedEnergy(state.storedEnergy() - consumed);
        if (consumed > 0) {
            recentEnergyConsumption.merge(instanceId, consumed, Integer::sum);
        }
        state.fuelProgressTicks(state.fuelProgressTicks() + progressTicks);
        changed = true;
        if (state.fuelProgressTicks() >= state.fuelTotalTicks()) {
            Location spawn = location.clone().add(0.5D, state.offsetTenths() / 10.0D, 0.5D);
            if (frameworkAttributes != null) {
                frameworkAttributes.put("configurable.assembler.readyToSpawn", Boolean.TRUE);
                frameworkAttributes.put("configurable.assembler.spawn", spawn);
            }
        }
        return changed;
    }

    private boolean spawnAssemblerOutput(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, Location location, Map<String, Object> frameworkAttributes) {
        if (definition == null || state == null || location == null || location.getWorld() == null) {
            return false;
        }
        if (frameworkAttributes == null || !Boolean.TRUE.equals(frameworkAttributes.get("configurable.assembler.readyToSpawn"))) {
            return false;
        }
        Location spawn = frameworkAttributes.get("configurable.assembler.spawn") instanceof Location loc
                ? loc.clone()
                : location.clone().add(0.5D, state.offsetTenths() / 10.0D, 0.5D);
        if (definition.spawnType() == EntityType.IRON_GOLEM) {
            IronGolem golem = (IronGolem) location.getWorld().spawnEntity(spawn, EntityType.IRON_GOLEM);
            golem.setPlayerCreated(true);
            location.getWorld().playSound(location, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0F, 1.0F);
        } else if (definition.spawnType() == EntityType.WITHER) {
            Wither wither = (Wither) location.getWorld().spawnEntity(spawn, EntityType.WITHER);
            wither.setInvulnerableTicks(220);
        } else {
            return false;
        }
        state.clearFuel();
        frameworkAttributes.put("configurable.assembler.readyToSpawn", Boolean.FALSE);
        frameworkAttributes.put("configurable.assembler.spawned", Boolean.TRUE);
        return true;
    }

    public int generateProducerEnergy(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !isProducer(instance.typeId())) {
            return 0;
        }
        SfxConfigurableMachineDefinition definition = definitions.get(instance.typeId());
        SfxConfigurableMachineState state = currentState(instanceId, instance);
        if (definition == null || definition.kind() != SfxConfigurableMachineKind.REACTOR || state.mode() != 0) {
            return 0;
        }
        Location location = locationFor(instance);
        if (location == null) {
            return 0;
        }
        if (!runtime.isOwnedByCurrentRegion(location)) {
            return runtime.supplyAt(location, () -> generateProducerEnergy(instanceId));
        }
        SfxConfigurableMachineSession session = sessionsByHost.get(instanceId);
        boolean hasViewers = session != null;
        long lastTick = lastLogicTicks.getOrDefault(instanceId, 0L);
        int interval = tickSettings.intervalFor(hasViewers);
        if (lastTick > 0L && tickCounter - lastTick < interval) {
            return 0;
        }
        long elapsedTicks = lastTick <= 0L ? 1L : Math.max(1L, tickCounter - lastTick);
        lastLogicTicks.put(instanceId, tickCounter);
        if (session != null) {
            syncInventoryToState(session.inventory(), state, definition, session.panelType());
        }
        if (autoPausedProducers.contains(instanceId) && canAutoPauseProducer(instanceId)) {
            return 0;
        }
        int totalGenerated = 0;
        boolean changed = false;
        int loops = Math.max(1, Math.min(100, (int) elapsedTicks));
        for (int i = 0; i < loops; i++) {
            ReactorTickResult result = tickReactor(instanceId, instance, definition, state, location, null);
            totalGenerated += result.generatedEnergy();
            changed |= result.changed();
            if (blockData.findInstance(instanceId).isEmpty()) {
                return totalGenerated;
            }
        }
        if (changed) {
            if (blockData.findInstance(instanceId).isPresent()) {
                dirtyInstances.add(instanceId);
            }
        }
        if (session != null && sessionsByHost.get(instanceId) == session && blockData.findInstance(instanceId).isPresent()) {
            render(session, instance, definition, state);
        }
        return totalGenerated;
    }

    private ReactorTickResult tickReactor(UUID instanceId, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, Location location, Map<String, Object> frameworkAttributes) {
        return SfxConfigurableReactorController.tickReactor(this, instanceId, instance, definition, state, location, frameworkAttributes);
    }

    boolean isReactorOutputBlocked(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        if (!state.hasActiveFuel() || state.fuelTotalTicks() <= 0 || state.fuelProgressTicks() < state.fuelTotalTicks()) {
            return false;
        }
        SfxConfigurableMachineDefinition.ReactorFuel activeFuel = fuelByKey(definition, state.activeFuelKey());
        return activeFuel != null && activeFuel.output() != null && !canFitOutput(items, state, activeFuel.output(), 0, 1);
    }

    boolean consumeCoolantIfNeeded(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition definition) {
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

    void applyWitherAura(Location location) {
        for (org.bukkit.entity.Entity entity : location.getWorld().getNearbyEntities(location, 5.0D, 5.0D, 5.0D,
                candidate -> candidate instanceof LivingEntity && candidate.isValid())) {
            if (entity instanceof LivingEntity living) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1, true, true, true), false);
            }
        }
    }

    void meltDownReactor(SfxBlockInstanceRecord instance, Location location) {
        removeReactorHologram(instance.anchorKey());
        SfxConfigurableMachineSession session = sessionsByHost.remove(instance.instanceId());
        if (session != null) {
            sessionsByViewer.remove(session.viewerId());
            Player viewer = plugin.getServer().getPlayer(session.viewerId());
            if (viewer != null) {
                runtime.executeForPlayer(viewer, viewer::closeInventory);
            }
        }
        states.remove(instance.instanceId());
        dirtyInstances.remove(instance.instanceId());
        activeInstances.remove(instance.instanceId());
        recentEnergyConsumption.remove(instance.instanceId());
        recentGeneratedEnergy.remove(instance.instanceId());
        autoPausedProducers.remove(instance.instanceId());
        blockData.unregisterAt(location);
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, instance.typeId(), location.getBlock(), Material.OBSIDIAN, false, "configurable", "meltDownReactor");
    }

    private void openMachine(Player player, SfxBlockInstanceRecord instance) {
        SfxMachineLegacyHookBridge.menuOpen(machineRuntime, instance.typeId(), instance.instanceId(), locationFor(instance), "configurable", "SfxConfigurableMachineService.openMachine");
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
        Component title = localization.itemName(definition.id());
        SfxConfigurableMachineUiPanel panel = panelFor(definition, SfxConfigurableMachineHolder.PanelType.ACCESS_PORT);
        Inventory inventory = plugin.getServer().createInventory(new SfxConfigurableMachineHolder(accessPort.instanceId(), accessPort.instanceId(), SfxConfigurableMachineHolder.PanelType.ACCESS_PORT), panel.inventorySize(), title);
        fillPanel(inventory, panel);
        setItemSource(inventory, panel, "reactor.access-port", ItemBuilder.of(Material.RED_WOOL)
                .name(localization.text("configurable-ui.reactor.access-port.missing.name"))
                .lore(localization.text("configurable-ui.reactor.access-port.missing.lore"))
                .build());
        player.openInventory(inventory);
    }

    private void openPanel(Player player, UUID panelInstanceId, UUID hostInstanceId, SfxConfigurableMachineHolder.PanelType panelType) {
        SfxConfigurableMachineSession existing = sessionsByHost.get(hostInstanceId);
        if (existing != null && !existing.viewerId().equals(player.getUniqueId())) {
            player.sendMessage(Text.prefixed(plugin, localization.text("machines.busy")));
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
        SfxConfigurableMachineUiPanel panel = panelFor(definition, panelType);
        Component title = localization.itemName(titleDefinition.id());
        Inventory inventory = plugin.getServer().createInventory(new SfxConfigurableMachineHolder(panelInstanceId, hostInstanceId, panelType), panel.inventorySize(), title);
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
            state.input(1, SfxElectricStack.fromItemStack(items, inventory.getItem(ASSEMBLER_HEAD_SLOTS[1])));
            state.input(2, SfxElectricStack.fromItemStack(items, inventory.getItem(ASSEMBLER_BODY_SLOTS[0])));
            state.input(3, SfxElectricStack.fromItemStack(items, inventory.getItem(ASSEMBLER_BODY_SLOTS[1])));
            for (int i = 4; i < state.inputCapacity(); i++) {
                state.input(i, null);
            }
            for (int i = 0; i < state.outputCapacity(); i++) {
                state.output(i, null);
            }
            return;
        }
        SfxConfigurableMachineUiPanel panel = panelFor(definition, panelType);
        int[] inputSlots = panel.inputSlots();
        for (int i = 0; i < state.inputCapacity(); i++) {
            state.input(i, i < inputSlots.length ? SfxElectricStack.fromItemStack(items, inventory.getItem(inputSlots[i])) : null);
        }
        int[] outputSlots = panel.outputSlots();
        for (int i = 0; i < state.outputCapacity(); i++) {
            state.output(i, i < outputSlots.length ? SfxElectricStack.fromItemStack(items, inventory.getItem(outputSlots[i])) : null);
        }
    }

    private void render(SfxConfigurableMachineSession session, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        if (session.panelType() == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            renderAssembler(session.inventory(), definition, state);
        } else {
            renderReactor(session.inventory(), instance, definition, state, session.panelType());
        }
    }

    private void renderAssembler(Inventory inventory, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        inventory.clear();
        setSlots(inventory, Material.GRAY_STAINED_GLASS_PANE, 0, 2, 3, 4, 5, 6, 8, 12, 14, 21, 23, 30, 32, 39, 40, 41, 50);
        Material headPane = definition.spawnType() == EntityType.IRON_GOLEM ? Material.ORANGE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        Material bodyPane = definition.spawnType() == EntityType.IRON_GOLEM ? Material.WHITE_STAINED_GLASS_PANE : Material.BROWN_STAINED_GLASS_PANE;
        setSlots(inventory, headPane, 9, 10, 11, 18, 20, 27, 29, 36, 37, 38);
        setSlots(inventory, bodyPane, 15, 16, 17, 24, 26, 33, 35, 42, 43, 44);
        inventory.setItem(ASSEMBLER_HEAD_SLOTS[0], toItemStack(state.input(0)));
        inventory.setItem(ASSEMBLER_HEAD_SLOTS[1], toItemStack(state.input(1)));
        inventory.setItem(ASSEMBLER_BODY_SLOTS[0], toItemStack(state.input(2)));
        inventory.setItem(ASSEMBLER_BODY_SLOTS[1], toItemStack(state.input(3)));
        inventory.setItem(1, ItemBuilder.of(definition.headMaterial())
                .name(localization.text("configurable-ui.assembler.head-slot.name"))
                .lore(localization.text("configurable-ui.assembler.required.lore", Map.of("amount", definition.headAmount())))
                .build());
        inventory.setItem(7, ItemBuilder.of(definition.bodyMaterial())
                .name(localization.text("configurable-ui.assembler.body-slot.name"))
                .lore(localization.text("configurable-ui.assembler.required.lore", Map.of("amount", definition.bodyAmount())))
                .build());
        inventory.setItem(ENABLE_SLOT, ItemBuilder.of(state.enabled() ? Material.REDSTONE_TORCH : Material.GUNPOWDER)
                .name(state.enabled()
                        ? localization.text("configurable-ui.assembler.enabled.name")
                        : localization.text("configurable-ui.assembler.disabled.name"))
                .lore(localization.text("configurable-ui.assembler.toggle.lore"))
                .build());
        inventory.setItem(OFFSET_SLOT, ItemBuilder.of(Material.PISTON)
                .name(localization.text("configurable-ui.assembler.offset.name", Map.of("offset", state.offsetTenths() / 10.0D)))
                .lore(localization.text("configurable-ui.assembler.offset.left"),
                        localization.text("configurable-ui.assembler.offset.right"),
                        localization.text("configurable-ui.assembler.offset.range"))
                .build());
        inventory.setItem(ASSEMBLER_STATUS_SLOT, assemblerStatusItem(definition, state));
    }

    private void renderReactor(Inventory inventory, SfxBlockInstanceRecord instance, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state, SfxConfigurableMachineHolder.PanelType panelType) {
        SfxConfigurableMachineUiPanel panel = panelFor(definition, panelType);
        fillPanel(inventory, panel);
        int[] inputSlots = panel.inputSlots();
        for (int i = 0; i < inputSlots.length; i++) {
            inventory.setItem(inputSlots[i], toItemStack(state.input(i)));
        }
        int[] outputSlots = panel.outputSlots();
        for (int i = 0; i < outputSlots.length; i++) {
            inventory.setItem(outputSlots[i], toItemStack(state.output(i)));
        }

        setItemSource(inventory, panel, "reactor.mode", namedIcon(
                state.mode() == 0 ? items.create(definition.id()) : items.create("sf:plutonium"),
                state.mode() == 0
                        ? localization.text("configurable-ui.reactor.focus-electricity.name")
                        : localization.text("configurable-ui.reactor.focus-production.name"),
                localization.text("configurable-ui.reactor.focus.lore")));
        setItemSource(inventory, panel, "reactor.progress", reactorProgressItem(definition, state));
        SfxBlockInstanceRecord accessPort = accessPortAbove(instance);
        boolean hasPort = accessPort != null;
        setItemSource(inventory, panel, "reactor.access-port", ItemBuilder.of(hasPort ? Material.LIME_WOOL : Material.RED_WOOL)
                .name(hasPort
                        ? localization.text("configurable-ui.reactor.access-port.detected.name")
                        : localization.text("configurable-ui.reactor.access-port.missing.name"))
                .lore(hasPort
                        ? localization.text("configurable-ui.reactor.access-port.detected.lore")
                        : localization.text("configurable-ui.reactor.access-port.missing.lore"))
                .build());
        setItemSource(inventory, panel, "reactor.fuel-slots", namedIcon(
                representativeFuelIcon(definition),
                localization.text("configurable-ui.reactor.fuel-slots.name"),
                localization.text("configurable-ui.reactor.fuel-slots.lore")));
        setItemSource(inventory, panel, "reactor.coolant-slots", namedIcon(
                items.create(definition.coolantItemId()),
                localization.text("configurable-ui.reactor.coolant-slots.name"),
                localization.text("configurable-ui.reactor.coolant-slots.lore")));
    }

    private void fillPanel(Inventory inventory, SfxConfigurableMachineUiPanel panel) {
        inventory.clear();
        for (SfxConfigurableMachineUiSlot slot : panel.slots().values()) {
            if (slot.item() != null) {
                inventory.setItem(slot.slot(), slot.item().toItemStack(localization));
            }
        }
    }

    private void setSlots(Inventory inventory, Material material, int... slots) {
        SfxInventoryPainter.setSlots(inventory, material, slots);
    }

    private void setItemSource(Inventory inventory, SfxConfigurableMachineUiPanel panel, String itemSource, ItemStack stack) {
        int slot = panel.slotByItemSource(itemSource);
        if (slot >= 0) {
            inventory.setItem(slot, stack);
        }
    }

    private ItemStack representativeFuelIcon(SfxConfigurableMachineDefinition definition) {
        if (definition.id().equals("sf:netherstar_reactor")) {
            return new ItemStack(Material.NETHER_STAR);
        }
        return items.create("sf:uranium");
    }

    private ItemStack namedIcon(ItemStack base, String name, String... lore) {
        return SfxUiItems.named(
                base == null || base.getType().isAir() ? new ItemStack(Material.STONE) : base,
                Text.mm(name),
                lore == null ? List.of() : Text.lore(lore));
    }

    private ItemStack assemblerStatusItem(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        boolean working = isAssemblerWorking(state);
        boolean paused = working && !state.enabled();
        boolean hasMaterials = hasAssemblerMaterials(state, definition);
        boolean noPower = state.enabled() && (working || hasMaterials) && state.storedEnergy() < definition.energyPerAction();
        SfxMachineStatusKey statusKey = paused
                ? SfxMachineStatusKey.PAUSED
                : working
                        ? SfxMachineStatusKey.WORKING
                        : noPower
                                ? SfxMachineStatusKey.NO_POWER
                                : state.enabled() ? SfxMachineStatusKey.IDLE : SfxMachineStatusKey.DISABLED;

        SfxMachineStatusView.Builder view = SfxMachineStatusView.builder(statusKey)
                .material(working && !paused ? definition.headMaterial() : null)
                .energy(state.storedEnergy(), definition.capacity())
                .consumption(definition.energyPerAction())
                .extraLore(localization.component("configurable-ui.assembler.work-time", Map.of("time", statusIcons.formatTimeLeft(ASSEMBLER_WORK_TICKS))))
                .extraLore(localization.component("configurable-ui.assembler.offset.status", Map.of("offset", state.offsetTenths() / 10.0D)));
        if (working) {
            int remainingTicks = Math.max(0, state.fuelTotalTicks() - state.fuelProgressTicks());
            view.progress(state.fuelProgressTicks(), state.fuelTotalTicks(), remainingTicks, !paused)
                    .includeDefaultStatusLore(false)
                .statusLore(paused
                            ? localization.component("configurable-ui.assembler.paused.lore")
                            : localization.component("configurable-ui.assembler.working.lore"));
            if (paused) {
                view.name(localization.component("configurable-ui.assembler.paused.name"));
            }
        } else if (!state.enabled()) {
            view.includeDefaultStatusLore(false)
                .statusLore(localization.component("configurable-ui.assembler.disabled.lore"));
        }
        return statusIcons.render(view.build());
    }

    private ItemStack reactorProgressItem(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        boolean active = state.hasActiveFuel();
        boolean outputBlocked = isReactorOutputBlocked(definition, state);
        SfxMachineStatusKey statusKey = outputBlocked
                ? SfxMachineStatusKey.BLOCKED_OUTPUT
                : active ? SfxMachineStatusKey.WORKING : SfxMachineStatusKey.IDLE;
        ItemStack activeIcon = definition.id().equals("sf:netherstar_reactor") ? new ItemStack(Material.NETHER_STAR) : items.create("sf:lava_crystal");
        SfxConfigurableMachineDefinition.ReactorFuel activeFuel = fuelByKey(definition, state.activeFuelKey());
        String byproduct = activeFuel == null || activeFuel.output() == null
                ? localization.text("configurable-ui.none")
                : stackDisplayName(activeFuel.output());
        int fuelRemainingPercent = fuelRemainingPercent(state);
        SfxMachineStatusView.Builder view = SfxMachineStatusView.builder(statusKey)
                .icon(active && !outputBlocked ? activeIcon : null)
                .name(active && !outputBlocked ? localization.component("configurable-ui.reactor.progress.name") : null)
                .energy(state.storedEnergy(), definition.capacity())
                .generation(definition.energyPerTick())
                .extraLore(localization.component("configurable-ui.reactor.progress.nuclear-fuel", Map.of("percent", fuelRemainingPercent)))
                .extraLore(localization.component("configurable-ui.reactor.progress.coolant", Map.of("percent", coolantPercent(state))))
                .extraLore(localization.component("configurable-ui.reactor.progress.mode", Map.of("mode", state.mode() == 0 ? localization.text("configurable-ui.reactor.mode-electricity") : localization.text("configurable-ui.reactor.mode-production"))))
                .extraLore(localization.component("configurable-ui.reactor.progress.byproduct", Map.of("item", byproduct)))
                .extraLore(localization.component("configurable-ui.reactor.progress.ticks", Map.of("current", state.fuelProgressTicks(), "total", state.fuelTotalTicks())))
                .extraLore(localization.component("configurable-ui.reactor.progress.coolant-ticks", Map.of("current", state.coolantProgressTicks(), "total", state.coolantTotalTicks())));
        if (outputBlocked) {
            view.progress(state.fuelProgressTicks(), state.fuelTotalTicks(), 0, false);
        } else if (active) {
            view.progress(state.fuelProgressTicks(), state.fuelTotalTicks(), Math.max(0, state.fuelTotalTicks() - state.fuelProgressTicks()), true)
                    .includeDefaultStatusLore(false)
                .statusLore(localization.component("configurable-ui.reactor.progress.working"));
        } else {
            view.includeDefaultStatusLore(false)
                .statusLore(localization.component("configurable-ui.reactor.progress.idle"));
        }
        return statusIcons.render(view.build());
    }

    private String stackDisplayName(SfxElectricStack stack) {
        if (stack == null) {
            return localization.text("configurable-ui.none");
        }
        if (stack.isSfxItem()) {
            return plainText(localization.itemName(stack.itemId()));
        }
        return plainText(Component.translatable(stack.material().translationKey()));
    }

    private String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private ItemStack toItemStack(SfxElectricStack stack) {
        return stack == null ? null : stack.toItemStack(items);
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
        SfxConfigurableMachineUiPanel panel = panelFor(definition, holder.panelType());
        SfxConfigurableMachineUiSlot uiSlot = panel.slot(slot);
        if (uiSlot != null && uiSlot.actionIs("reactor-toggle-mode")) {
            state.mode(state.mode() == 0 ? 1 : 0);
            autoPausedProducers.remove(host.instanceId());
            dirtyInstances.add(host.instanceId());
            activeInstances.add(host.instanceId());
            refreshSession(host.instanceId());
            return;
        }
        if (uiSlot != null && uiSlot.actionIs("reactor-access-port")) {
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

    private boolean isAssemblerWorking(SfxConfigurableMachineState state) {
        return "assembler".equals(state.activeFuelKey()) && state.fuelTotalTicks() > 0;
    }

    private boolean hasAssemblerMaterials(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition definition) {
        return totalMaterialAmount(state, ASSEMBLER_HEAD_STATE_SLOTS, definition.headMaterial()) >= definition.headAmount()
                && totalMaterialAmount(state, ASSEMBLER_BODY_STATE_SLOTS, definition.bodyMaterial()) >= definition.bodyAmount();
    }

    private int totalMaterialAmount(SfxConfigurableMachineState state, int[] slots, Material material) {
        int total = 0;
        for (int slot : slots) {
            SfxElectricStack stack = state.input(slot);
            if (stack != null && !stack.isSfxItem() && stack.material() == material) {
                total += stack.amount();
            }
        }
        return total;
    }

    private void consumeAssemblerMaterials(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition definition) {
        consumeMaterialFromInputs(state, ASSEMBLER_HEAD_STATE_SLOTS, definition.headMaterial(), definition.headAmount());
        consumeMaterialFromInputs(state, ASSEMBLER_BODY_STATE_SLOTS, definition.bodyMaterial(), definition.bodyAmount());
    }

    private void consumeMaterialFromInputs(SfxConfigurableMachineState state, int[] slots, Material material, int amount) {
        int remaining = amount;
        for (int slot : slots) {
            if (remaining <= 0) {
                return;
            }
            SfxElectricStack stack = state.input(slot);
            if (stack == null || stack.isSfxItem() || stack.material() != material) {
                continue;
            }
            int consumed = Math.min(remaining, stack.amount());
            remaining -= consumed;
            int left = stack.amount() - consumed;
            state.input(slot, left <= 0 ? null : stack.copyWithAmount(left));
        }
    }

    int fuelSlotIndex(SfxConfigurableMachineState state, SfxConfigurableMachineDefinition.ReactorFuel fuel) {
        for (int slot = 0; slot < 3; slot++) {
            if (fuel.matches(state.input(slot))) {
                return slot;
            }
        }
        return 0;
    }

    SfxConfigurableMachineDefinition.ReactorFuel findFuel(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineState state) {
        for (SfxConfigurableMachineDefinition.ReactorFuel fuel : definition.fuels()) {
            for (int slot = 0; slot < 3; slot++) {
                if (fuel.matches(state.input(slot))) {
                    return fuel;
                }
            }
        }
        return null;
    }

    SfxConfigurableMachineDefinition.ReactorFuel fuelByKey(SfxConfigurableMachineDefinition definition, String key) {
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

    SfxElectricStack consumeInput(SfxConfigurableMachineState state, int slot, int amount) {
        SfxElectricStack input = state.input(slot);
        if (input == null || input.amount() < amount) {
            return null;
        }
        SfxElectricStack consumed = input.copyWithAmount(amount);
        int remaining = input.amount() - amount;
        state.input(slot, remaining <= 0 ? null : input.copyWithAmount(remaining));
        return consumed;
    }

    boolean canFitOutput(SfxItems items, SfxConfigurableMachineState state, SfxElectricStack output, int startSlot, int slotCount) {
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

    void pushOutput(SfxItems items, SfxConfigurableMachineState state, SfxElectricStack output, int startSlot, int slotCount) {
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

    boolean hasWaterCooling(Location location) {
        Block center = location.getBlock();
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    Material type = center.getRelative(x, y, z).getType();
                    if (type != Material.WATER && type != Material.BUBBLE_COLUMN) {
                        return false;
                    }
                }
            }
        }
        return true;
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

    void updateReactorHologram(SfxBlockAnchorKey key, SfxConfigurableMachineState state) {
        int percent = coolantPercent(state);
        Component text = Text.mm("<aqua>❄ " + percent + "%</aqua>");
        floatingText.update(new SfxFloatingTextProjection(
                reactorHologramKey(key),
                key.x() + 0.5D,
                key.y() + 1.35D,
                key.z() + 0.5D,
                text,
                HOLOGRAM_VIEW_DISTANCE_SQUARED,
                false,
                SfxFloatingTextDisplayMode.ARMOR_STAND));
    }

    void removeReactorHologram(SfxBlockAnchorKey key) {
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

    private int fuelRemainingPercent(SfxConfigurableMachineState state) {
        if (state.fuelTotalTicks() <= 0) {
            return 0;
        }
        int remaining = Math.max(0, state.fuelTotalTicks() - state.fuelProgressTicks());
        return Math.max(0, Math.min(100, (int) Math.round(remaining * 100.0D / state.fuelTotalTicks())));
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


    private boolean isInstanceChunkLoaded(SfxBlockInstanceRecord instance) {
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

    private Location locationFor(SfxBlockInstanceRecord instance) {
        World world = plugin.getServer().getWorld(instance.anchorKey().worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, instance.anchorKey().x(), instance.anchorKey().y(), instance.anchorKey().z());
    }

    private void fill(Inventory inventory, Material material) {
        SfxInventoryPainter.fill(inventory, SfxUiItems.blankPane(material));
    }

    private SfxConfigurableMachineUiPanel panelFor(SfxConfigurableMachineDefinition definition, SfxConfigurableMachineHolder.PanelType panelType) {
        SfxConfigurableMachineUiPanel panel = definition.ui().panel(panelType);
        if (panel == null) {
            throw new IllegalStateException("Missing compiled configurable UI panel " + panelType + " for " + definition.id());
        }
        return panel;
    }

    private int[] editableInputSlots(SfxConfigurableMachineHolder.PanelType panelType, SfxConfigurableMachineDefinition definition) {
        if (panelType == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            return new int[] {ASSEMBLER_HEAD_SLOTS[0], ASSEMBLER_HEAD_SLOTS[1], ASSEMBLER_BODY_SLOTS[0], ASSEMBLER_BODY_SLOTS[1]};
        }
        return panelFor(definition, panelType).inputSlots();
    }

    private int[] editableOutputSlots(SfxConfigurableMachineHolder.PanelType panelType, SfxConfigurableMachineDefinition definition) {
        if (panelType == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            return new int[0];
        }
        return panelFor(definition, panelType).outputSlots();
    }

    private boolean isButtonSlot(SfxConfigurableMachineHolder.PanelType panelType, SfxConfigurableMachineDefinition definition, int slot) {
        if (panelType == SfxConfigurableMachineHolder.PanelType.ASSEMBLER) {
            return slot == ENABLE_SLOT || slot == OFFSET_SLOT || slot == ASSEMBLER_STATUS_SLOT;
        }
        return panelFor(definition, panelType).isActionSlot(slot);
    }

    private boolean moveShiftClickedStack(Inventory inventory, ItemStack current, int[] targetSlots, SfxConfigurableMachineDefinition definition, SfxConfigurableMachineHolder.PanelType panelType) {
        return SfxInventorySlots.moveStackToSlots(inventory, targetSlots, current, (slot, stack) -> isValidInputItem(panelType, slot, stack, definition));
    }

    private boolean isValidInputItem(SfxConfigurableMachineHolder.PanelType panelType, int slot, ItemStack item, SfxConfigurableMachineDefinition definition) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        SfxElectricStack stack = SfxElectricStack.fromItemStack(items, item);
        if (definition.kind() == SfxConfigurableMachineKind.ASSEMBLER) {
            if (contains(ASSEMBLER_HEAD_SLOTS, slot)) {
                return stack != null && !stack.isSfxItem() && stack.material() == definition.headMaterial();
            }
            if (contains(ASSEMBLER_BODY_SLOTS, slot)) {
                return stack != null && !stack.isSfxItem() && stack.material() == definition.bodyMaterial();
            }
            return false;
        }
        SfxConfigurableMachineUiSlot uiSlot = panelFor(definition, panelType).slot(slot);
        if (uiSlot != null && uiSlot.accepts("reactor-fuel")) {
            for (SfxConfigurableMachineDefinition.ReactorFuel fuel : definition.fuels()) {
                if (fuel.matches(stack)) {
                    return true;
                }
            }
            return false;
        }
        if (uiSlot != null && uiSlot.accepts("reactor-coolant")) {
            return stack != null && stack.isSfxItem() && definition.coolantItemId().equals(stack.itemId());
        }
        return false;
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
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
    }

    private void dropStack(Block block, SfxElectricStack stack) {
        SfxBlockDrops.dropStack(block, items, stack);
    }
}
