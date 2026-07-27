package cc.theends6.sfx.internal.altar;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipe;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxAnchoredInteraction;
import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.api.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.machine.SfxMachineEffectDispatcher;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseContext;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseResult;
import cc.theends6.sfx.internal.machine.SfxMachinePipelineGuard;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import cc.theends6.sfx.api.machine.runtime.SfxMachineTickContext;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.api.text.Text;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;









public final class SfxAncientAltarService implements Listener {
    public static final String ANCIENT_ALTAR = "sf:ancient_altar";
    public static final String ANCIENT_PEDESTAL = "sf:ancient_pedestal";

    private static final String RECIPE_TYPE = "sf:ancient_altar";
    private static final int STEP_COUNT = 36;
    private static final long STEP_DELAY_TICKS = 8L;
    private static final int VIEW_DISTANCE_SQUARED = 48 * 48;
    private static final int ITEM_STACK_METADATA_INDEX = 8;
    private static final int ENTITY_METADATA_CUSTOM_NAME = 2;
    private static final int ENTITY_METADATA_CUSTOM_NAME_VISIBLE = 3;
    private static final int ENTITY_METADATA_SILENT = 4;
    private static final int ENTITY_METADATA_NO_GRAVITY = 5;
    private static final byte ENTITY_FLAGS = 0;
    private static final int[] RECIPE_RING = {0, 1, 2, 5, 8, 7, 6, 3};
    private static final int[][] PEDESTAL_OFFSETS = {
            {2, -2}, {3, 0}, {2, 2}, {0, 3}, {-2, 2}, {-3, 0}, {-2, -2}, {0, -3}
    };

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxItemRegistry itemRegistry;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxMachineRuntimeEngine machineRuntime;
    private final NamespacedKey spawnerTypeKey;
    private final AtomicInteger virtualEntityIds = new AtomicInteger(4_000_000);
    private final Map<SfxBlockAnchorKey, PedestalState> pedestalStates = new ConcurrentHashMap<>();
    private final Map<SfxBlockAnchorKey, VirtualItemProjection> virtualItems = new ConcurrentHashMap<>();
    private final Map<UUID, RitualSession> sessions = new ConcurrentHashMap<>();
    private final Map<SfxBlockAnchorKey, UUID> sessionsByBlock = new ConcurrentHashMap<>();
    private final Object recipeLock = new Object();
    private volatile List<AltarRecipe> recipes = List.of();
    private volatile boolean shutdown;

    public SfxAncientAltarService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxItemRegistry itemRegistry, SfxLocalization localization, SfxBlockDataService blockData, SfxMachineRuntimeEngine machineRuntime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.itemRegistry = Objects.requireNonNull(itemRegistry, "itemRegistry");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.machineRuntime = Objects.requireNonNull(machineRuntime, "machineRuntime");
        this.spawnerTypeKey = new NamespacedKey(plugin, "spawner_type");
        reloadRecipes();
    }

    public void start() {
        shutdown = false;
        reloadRecipes();
        rebuildIndex();
        scheduleViewerRefresh();
    }

    public void shutdown() {
        shutdown = true;
        for (RitualSession session : List.copyOf(sessions.values())) {
            abortSession(session, true);
        }
        for (SfxBlockAnchorKey key : List.copyOf(virtualItems.keySet())) {
            removeVirtualItem(key);
        }
        virtualItems.clear();
        pedestalStates.clear();
        sessions.clear();
        sessionsByBlock.clear();
    }

    public boolean supportsType(String typeId) {
        return ANCIENT_ALTAR.equals(typeId) || ANCIENT_PEDESTAL.equals(typeId);
    }


    public SfxMachinePhaseResult frameworkEffect(String effectName, SfxMachinePhaseContext context) {
        if (context == null) {
            return SfxMachinePhaseResult.cont();
        }
        context.put("altar.framework.effect", effectName);
        context.put("altar.framework.effect.handled", Boolean.TRUE);
        if ("altar:validate-structure".equals(effectName)) {
            RitualSession session = context.attachment("altar.session", RitualSession.class).orElse(null);
            if (session != null) {
                context.put("altar.framework.structure-valid", structureStillValid(session));
            }
        }
        return SfxMachinePhaseResult.cont();
    }

    private Map<String, Object> altarFrameworkAttributes(SfxBlockInstanceRecord altarInstance, Player player, RitualSession session) {
        Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put("altar.instance", altarInstance);
        if (player != null) {
            attributes.put("altar.player", player);
        }
        if (session != null) {
            attributes.put("altar.session", session);
        }
        attributes.put("altar.service", this);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkEffect);
        return attributes;
    }

    public void handlePlaced(UUID instanceId, String typeId) {
        if (instanceId == null || typeId == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        if (ANCIENT_PEDESTAL.equals(typeId)) {
            PedestalState state = PedestalState.decode(instance.stateBlob());
            SfxBlockAnchorKey key = instance.anchorKey();
            if (state.hasItem()) {
                pedestalStates.put(key, state);
                updateVirtualItem(key, pedestalDisplayLocation(key), state.item());
            } else {
                pedestalStates.remove(key);
                removeVirtualItem(key);
            }
        }
    }

    public void rebuildIndex() {
        pedestalStates.clear();
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            handlePlaced(instance.instanceId(), instance.typeId());
        }
    }

    public void reloadRecipes() {
        synchronized (recipeLock) {
            List<AltarRecipe> loaded = new ArrayList<>();
            for (SfxItemDefinition definition : itemRegistry.items()) {
                for (SfxRecipe recipe : definition.recipes()) {
                    if (!RECIPE_TYPE.equals(recipe.recipeType())) {
                        continue;
                    }
                    List<SfxRecipeSlot> matrix = recipe.matrix();
                    List<SfxRecipeSlot> ring = new ArrayList<>(8);
                    for (int slot : RECIPE_RING) {
                        ring.add(matrix.get(slot));
                    }
                    loaded.add(new AltarRecipe(recipe.id(), definition.id(), recipe.outputAmount(), matrix.get(4), List.copyOf(ring)));
                }
            }
            this.recipes = List.copyOf(loaded);
        }
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (block == null || typeId == null) {
            return;
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(block.getLocation());
        UUID sessionId = sessionsByBlock.get(key);
        if (sessionId != null) {
            RitualSession session = sessions.get(sessionId);
            if (session != null) {
                abortSession(session, true);
            }
        } else if (ANCIENT_PEDESTAL.equals(typeId)) {
            PedestalState state = pedestalStates.remove(key);
            if (state != null && state.hasItem()) {
                dropItem(block.getLocation().add(0.5D, 1.0D, 0.5D), state.item());
            }
            removeVirtualItem(key);
        }
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
        blockData.unregisterAt(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction().isLeftClick()) {
            return;
        }
        SfxAnchoredInteraction interaction = SfxAnchoredInteraction.resolve(event, blockData);
        if (interaction == null || !supportsType(interaction.instance().typeId())) {
            return;
        }
        if (SfxInteractionRules.prefersBlockPlacement(items, event)) {
            return;
        }
        deny(event);
        if (ANCIENT_PEDESTAL.equals(interaction.instance().typeId())) {
            handlePedestalUse(event.getPlayer(), interaction.block(), interaction.instance());
        } else if (ANCIENT_ALTAR.equals(interaction.instance().typeId())) {
            handleAltarUse(event.getPlayer(), interaction.block(), interaction.instance());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        SfxAnchorRecord anchor = blockData.findAnchor(event.getBlock().getLocation()).orElse(null);
        if (anchor == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null || !supportsType(instance.typeId())) {
            return;
        }
        event.setDropItems(false);
        destroyAnchoredBlock(event.getBlock(), instance.instanceId(), instance.typeId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceAbovePedestal(BlockPlaceEvent event) {
        Block below = event.getBlockPlaced().getRelative(0, -1, 0);
        SfxBlockInstanceRecord instance = instanceAt(below.getLocation());
        if (instance != null && ANCIENT_PEDESTAL.equals(instance.typeId())) {
            event.setCancelled(true);
            sendMessage(event.getPlayer(), "machines.ancient-pedestal.obstructed");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshViewerLater(event.getPlayer(), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        for (VirtualItemProjection projection : virtualItems.values()) {
            projection.viewers().remove(viewerId);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        refreshViewerLater(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        refreshViewerLater(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        refreshViewerLater(event.getPlayer(), 20L);
    }

    private void handlePedestalUse(Player player, Block block, SfxBlockInstanceRecord instance) {
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(block.getLocation());
        if (sessionsByBlock.containsKey(key)) {
            sendMessage(player, "machines.ancient-altar.in-use");
            return;
        }
        PedestalState existing = pedestalStates.get(key);
        if (existing != null && existing.hasItem()) {
            pedestalStates.remove(key);
            removeVirtualItem(key);
            persistPedestalState(instance.instanceId(), PedestalState.empty(), SfxBlockLifecycleState.IDLE);
            giveOrDrop(player, existing.item());
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 1.0F, 1.0F);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || hand.getAmount() <= 0) {
            sendMessage(player, "machines.ancient-pedestal.hold-item");
            return;
        }
        ItemStack stored = hand.clone();
        stored.setAmount(1);
        pedestalStates.put(key, new PedestalState(stored));
        persistPedestalState(instance.instanceId(), new PedestalState(stored), SfxBlockLifecycleState.IDLE);
        updateVirtualItem(key, pedestalDisplayLocation(key), stored);
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5F, 0.5F);
    }

    private void handleAltarUse(Player player, Block altarBlock, SfxBlockInstanceRecord altarInstance) {
        SfxBlockAnchorKey altarKey = SfxBlockAnchorKey.fromLocation(altarBlock.getLocation());
        if (sessionsByBlock.containsKey(altarKey)) {
            sendMessage(player, "machines.ancient-altar.in-use");
            return;
        }
        Map<String, Object> startFramework = altarFrameworkAttributes(altarInstance, player, null);
        if (!SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(altarInstance.typeId(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, altarInstance.instanceId(), altarBlock.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE, startFramework), startFramework, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
            return;
        }
        List<SfxBlockAnchorKey> pedestalKeys = pedestalKeys(altarBlock.getLocation());
        List<SfxBlockInstanceRecord> pedestals = new ArrayList<>(8);
        for (SfxBlockAnchorKey key : pedestalKeys) {
            SfxBlockInstanceRecord pedestal = instanceAt(key);
            if (pedestal == null || !ANCIENT_PEDESTAL.equals(pedestal.typeId())) {
                sendMessage(player, "machines.ancient-altar.not-enough-pedestals",
                        Map.of("pedestals", countPedestals(pedestalKeys)));
                return;
            }
            pedestals.add(pedestal);
        }

        ItemStack catalyst = player.getInventory().getItemInMainHand();
        if (catalyst == null || catalyst.getType().isAir()) {
            sendMessage(player, "machines.ancient-altar.unknown-catalyst");
            return;
        }

        List<PedestalState> inputs = pedestalKeys.stream()
                .map(key -> pedestalStates.getOrDefault(key, PedestalState.empty()))
                .toList();
        MatchedRecipe match = findMatchingRecipe(catalyst, inputs);
        if (match == null) {
            sendMessage(player, "machines.ancient-altar.unknown-recipe");
            altarBlock.getWorld().playSound(altarBlock.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.BLOCKS, 1.0F, 1.0F);
            return;
        }

        ItemStack catalystInput = catalyst.clone();
        catalystInput.setAmount(match.recipe().catalyst().amount());
        catalyst.setAmount(catalyst.getAmount() - match.recipe().catalyst().amount());
        player.getInventory().setItemInMainHand(catalyst.getAmount() <= 0 ? null : catalyst);
        ItemStack output = createOutput(match.recipe(), catalystInput);
        RitualSession session = new RitualSession(
                UUID.randomUUID(),
                altarKey,
                altarBlock.getLocation().add(0.5D, 0.5D, 0.5D),
                pedestalKeys,
                pedestals.stream().map(SfxBlockInstanceRecord::instanceId).toList(),
                match.recipe(),
                output,
                frozenInputs(catalystInput, inputs),
                match.offset());
        registerSession(session);
        blockData.updateInstanceState(altarInstance.instanceId(), altarInstance.stateBlob(), SfxBlockLifecycleState.ACTIVE);
        for (SfxBlockInstanceRecord pedestal : pedestals) {
            blockData.updateInstanceState(pedestal.instanceId(), pedestal.stateBlob(), SfxBlockLifecycleState.ACTIVE);
        }
        altarBlock.getWorld().playSound(altarBlock.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.BLOCKS, 1.0F, 1.0F);
        scheduleFirstTick(session.id());
    }

    private void registerSession(RitualSession session) {
        sessions.put(session.id(), session);
        sessionsByBlock.put(session.altarKey(), session.id());
        for (SfxBlockAnchorKey key : session.pedestalKeys()) {
            sessionsByBlock.put(key, session.id());
        }
    }

    private void unregisterSession(RitualSession session) {
        sessions.remove(session.id());
        sessionsByBlock.remove(session.altarKey(), session.id());
        for (SfxBlockAnchorKey key : session.pedestalKeys()) {
            sessionsByBlock.remove(key, session.id());
        }
    }

    private List<ItemStack> frozenInputs(ItemStack catalyst, List<PedestalState> pedestalInputs) {
        List<ItemStack> frozen = new ArrayList<>();
        frozen.add(catalyst.clone());
        for (PedestalState state : pedestalInputs) {
            if (state != null && state.hasItem()) {
                frozen.add(state.item());
            }
        }
        return List.copyOf(frozen);
    }

    private void scheduleFirstTick(UUID sessionId) {
        RitualSession session = sessions.get(sessionId);
        if (session == null || shutdown) {
            return;
        }
        runtime.executeAtLater(session.altarLocation(), 10L, () -> tickRitual(sessionId));
    }

    private void scheduleTick(UUID sessionId) {
        RitualSession session = sessions.get(sessionId);
        if (session == null || shutdown) {
            return;
        }
        runtime.executeAtLater(session.altarLocation(), STEP_DELAY_TICKS, () -> tickRitual(sessionId));
    }

    private void tickRitual(UUID sessionId) {
        RitualSession session = sessions.get(sessionId);
        if (session == null || shutdown) {
            return;
        }
        if (!structureStillValid(session)) {
            abortSession(session, true);
            return;
        }

        World world = session.altarLocation().getWorld();
        if (world == null) {
            abortSession(session, true);
            return;
        }
        int step = session.step();
        SfxBlockInstanceRecord frameworkAltar = instanceAt(session.altarKey());
        Map<String, Object> framework = frameworkAltar == null ? null : altarFrameworkAttributes(frameworkAltar, null, session);
        if (frameworkAltar != null) {
            if (!SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(frameworkAltar.typeId(), SfxMachinePhase.BEFORE_PROGRESS, frameworkAltar.instanceId(), session.altarLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.RUNNING, framework), framework, SfxMachinePhase.BEFORE_PROGRESS.name())) {
                abortSession(session, true);
                return;
            }
        }
        if (step == STEP_COUNT) {
            completeSession(session);
            return;
        }

        playRitualEffects(session);

        if (step > 0 && step % 4 == 0 && step / 4 <= 8) {
            int index = step / 4 - 1;
            SfxBlockAnchorKey pedestalKey = rotatedPedestalKey(session, index);
            session.consumedPedestals().add(pedestalKey);
            removeVirtualItem(pedestalKey);
            Location consumedLocation = pedestalDisplayLocation(pedestalKey);
            if (consumedLocation != null) {
                world.spawnParticle(Particle.ENCHANT, consumedLocation, 16, 0.3D, 0.2D, 0.3D, 0.0D);
                world.spawnParticle(Particle.ENCHANTED_HIT, consumedLocation, 8, 0.3D, 0.2D, 0.3D, 0.0D);
                world.playSound(consumedLocation, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0F, 2.0F);
            }
        }

        session.advanceStep();
        if (frameworkAltar != null) {
            if (!SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(frameworkAltar.typeId(), SfxMachinePhase.AFTER_PROGRESS, frameworkAltar.instanceId(), session.altarLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.RUNNING, framework), framework, SfxMachinePhase.AFTER_PROGRESS.name())) {
                abortSession(session, true);
                return;
            }
        }
        scheduleTick(sessionId);
    }

    private boolean structureStillValid(RitualSession session) {
        SfxBlockInstanceRecord altar = instanceAt(session.altarKey());
        if (altar == null || !ANCIENT_ALTAR.equals(altar.typeId())) {
            return false;
        }
        for (SfxBlockAnchorKey key : session.pedestalKeys()) {
            SfxBlockInstanceRecord pedestal = instanceAt(key);
            if (pedestal == null || !ANCIENT_PEDESTAL.equals(pedestal.typeId())) {
                return false;
            }
        }
        return true;
    }

    private void completeSession(RitualSession session) {
        SfxBlockInstanceRecord altarInstance = instanceAt(session.altarKey());
        if (altarInstance != null) {
            Map<String, Object> completeFramework = altarFrameworkAttributes(altarInstance, null, session);
            if (SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(altarInstance.typeId(), SfxMachinePhase.ON_COMPLETE, altarInstance.instanceId(), session.altarLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.RUNNING, completeFramework), completeFramework, SfxMachinePhase.ON_COMPLETE.name())) {
                SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(altarInstance.typeId(), SfxMachinePhase.AFTER_OUTPUT, altarInstance.instanceId(), session.altarLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.RUNNING, completeFramework), completeFramework, SfxMachinePhase.AFTER_OUTPUT.name());
                machineRuntime.runPhase(altarInstance.typeId(), SfxMachinePhase.AFTER_TICK, altarInstance.instanceId(), session.altarLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.RUNNING, completeFramework);
            }
        }
        unregisterSession(session);
        clearPedestalStates(session, false);
        setLifecycleIdle(session);
        for (SfxBlockAnchorKey key : session.pedestalKeys()) {
            removeVirtualItem(key);
        }
        Location outputLocation = session.altarLocation().clone().add(0.0D, 0.3D, 0.0D);
        dropItem(outputLocation, session.output());
        World world = session.altarLocation().getWorld();
        if (world != null) {
            world.playSound(outputLocation, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.BLOCKS, 1.0F, 1.0F);
            world.playEffect(outputLocation, org.bukkit.Effect.STEP_SOUND, Material.EMERALD_BLOCK);
        }
    }

    private void abortSession(RitualSession session, boolean dropInputs) {
        SfxBlockInstanceRecord altarInstance = instanceAt(session.altarKey());
        if (altarInstance != null) {
            machineRuntime.runPhase(altarInstance.typeId(), SfxMachinePhase.AFTER_OUTPUT, altarInstance.instanceId(), session.altarLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.RUNNING, altarFrameworkAttributes(altarInstance, null, session));
        }
        unregisterSession(session);
        clearPedestalStates(session, false);
        setLifecycleIdle(session);
        for (SfxBlockAnchorKey key : session.pedestalKeys()) {
            removeVirtualItem(key);
        }
        if (dropInputs) {
            Location dropLocation = session.altarLocation().clone().add(0.0D, 0.9D, 0.0D);
            for (ItemStack stack : session.frozenInputs()) {
                dropItem(dropLocation, stack);
            }
            World world = session.altarLocation().getWorld();
            if (world != null) {
                world.playSound(dropLocation, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    private void clearPedestalStates(RitualSession session, boolean dropContents) {
        for (SfxBlockAnchorKey key : session.pedestalKeys()) {
            PedestalState state = pedestalStates.remove(key);
            SfxBlockInstanceRecord pedestal = instanceAt(key);
            if (pedestal != null) {
                persistPedestalState(pedestal.instanceId(), PedestalState.empty(), SfxBlockLifecycleState.IDLE);
            }
            if (dropContents && state != null && state.hasItem()) {
                Location location = pedestalDisplayLocation(key);
                if (location != null) {
                    dropItem(location, state.item());
                }
            }
        }
    }

    private void setLifecycleIdle(RitualSession session) {
        SfxBlockInstanceRecord altar = instanceAt(session.altarKey());
        if (altar != null) {
            blockData.updateInstanceState(altar.instanceId(), altar.stateBlob(), SfxBlockLifecycleState.IDLE);
        }
        for (SfxBlockAnchorKey key : session.pedestalKeys()) {
            SfxBlockInstanceRecord pedestal = instanceAt(key);
            if (pedestal != null) {
                blockData.updateInstanceState(pedestal.instanceId(), pedestal.stateBlob(), SfxBlockLifecycleState.IDLE);
            }
        }
    }

    private void playRitualEffects(RitualSession session) {
        World world = session.altarLocation().getWorld();
        if (world == null) {
            return;
        }
        Location altar = session.altarLocation().clone().add(0.0D, 0.8D, 0.0D);
        world.spawnParticle(Particle.WITCH, altar, 16, 1.2D, 0.0D, 1.2D, 0.0D);
        world.spawnParticle(Particle.FIREWORK, altar, 8, 0.2D, 0.0D, 0.2D, 0.0D);
        for (SfxBlockAnchorKey key : session.consumedPedestals()) {
            Location pedestal = pedestalDisplayLocation(key);
            if (pedestal != null) {
                world.spawnParticle(Particle.ENCHANT, pedestal, 16, 0.3D, 0.2D, 0.3D, 0.0D);
                world.spawnParticle(Particle.ENCHANTED_HIT, pedestal, 8, 0.3D, 0.2D, 0.3D, 0.0D);
            }
        }
    }

    private SfxBlockAnchorKey rotatedPedestalKey(RitualSession session, int recipeRingIndex) {
        int physicalIndex = Math.floorMod(session.matchOffset() + recipeRingIndex, session.pedestalKeys().size());
        return session.pedestalKeys().get(physicalIndex);
    }

    private MatchedRecipe findMatchingRecipe(ItemStack catalyst, List<PedestalState> pedestalInputs) {
        for (AltarRecipe recipe : recipes) {
            if (!items.matches(catalyst, recipe.catalyst())) {
                continue;
            }
            for (int offset = 0; offset < 8; offset++) {
                if (matchesPedestals(recipe, pedestalInputs, offset)) {
                    return new MatchedRecipe(recipe, offset);
                }
            }
        }
        return null;
    }

    private boolean matchesPedestals(AltarRecipe recipe, List<PedestalState> pedestalInputs, int offset) {
        for (int i = 0; i < 8; i++) {
            SfxRecipeSlot slot = recipe.ring().get(i);
            PedestalState state = pedestalInputs.get(Math.floorMod(offset + i, 8));
            ItemStack stack = state == null ? null : state.item();
            if (!items.matches(stack, slot)) {
                return false;
            }
        }
        return true;
    }

    private ItemStack createOutput(AltarRecipe recipe, ItemStack catalyst) {
        if ("sf:reinforced_spawner".equals(recipe.outputItemId())) {
            return createReinforcedSpawner(catalyst, recipe.outputAmount());
        }
        return items.create(recipe.outputItemId(), recipe.outputAmount());
    }

    private ItemStack createReinforcedSpawner(ItemStack catalyst, int amount) {
        ItemStack stack = items.create("sf:reinforced_spawner", amount);
        EntityType entityType = readSpawnerType(catalyst).orElse(EntityType.PIG);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.getPersistentDataContainer().set(spawnerTypeKey, PersistentDataType.STRING, entityType.name());
        meta.lore(List.of(Text.renderFlexible(localization.text("items.sf.reinforced_spawner.type-line", Map.of("type", prettyEnumName(entityType.name()))))));
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(entityType);
            blockStateMeta.setBlockState(spawner);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private Optional<EntityType> readSpawnerType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String raw = meta.getPersistentDataContainer().get(spawnerTypeKey, PersistentDataType.STRING);
        if (raw != null) {
            try {
                return Optional.of(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                
            }
        }
        if (meta.lore() != null) {
            for (Component line : meta.lore()) {
                String legacy = Text.toLegacy(line);
                int index = legacy.toLowerCase(Locale.ROOT).indexOf("type:");
                if (index < 0) {
                    continue;
                }
                String candidate = legacy.substring(index + 5).replaceAll("[&§][0-9A-FK-ORa-fk-or]", "").trim()
                        .toUpperCase(Locale.ROOT).replace(' ', '_');
                try {
                    return Optional.of(EntityType.valueOf(candidate));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private String prettyEnumName(String raw) {
        String[] words = raw.toLowerCase(Locale.ROOT).split("_");
        List<String> pretty = new ArrayList<>(words.length);
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            pretty.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", pretty);
    }

    private List<SfxBlockAnchorKey> pedestalKeys(Location altarLocation) {
        List<SfxBlockAnchorKey> keys = new ArrayList<>(8);
        for (int[] offset : PEDESTAL_OFFSETS) {
            Location location = altarLocation.clone().add(offset[0], 0, offset[1]);
            keys.add(SfxBlockAnchorKey.fromLocation(location));
        }
        return keys;
    }

    private SfxBlockInstanceRecord instanceAt(Location location) {
        SfxAnchorRecord anchor = blockData.findAnchor(location).orElse(null);
        return anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
    }

    private SfxBlockInstanceRecord instanceAt(SfxBlockAnchorKey key) {
        SfxAnchorRecord anchor = blockData.findAnchor(key).orElse(null);
        return anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
    }

    private Location pedestalDisplayLocation(SfxBlockAnchorKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return null;
        }
        return new Location(world, key.x() + 0.5D, key.y() + 1.2D, key.z() + 0.5D);
    }

    private void persistPedestalState(UUID instanceId, PedestalState state, SfxBlockLifecycleState lifecycleState) {
        blockData.updateInstanceState(instanceId, state.encode(), lifecycleState);
    }

    private void deny(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
        leftovers.values().forEach(leftover -> dropItem(player.getLocation(), leftover));
    }

    private void dropItem(Location location, ItemStack stack) {
        if (location == null || location.getWorld() == null || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        location.getWorld().dropItemNaturally(location, stack.clone()).setPickupDelay(0);
    }

    private void updateVirtualItem(SfxBlockAnchorKey key, Location location, ItemStack item) {
        if (key == null || location == null || item == null || item.getType().isAir()) {
            return;
        }
        VirtualItemProjection projection = virtualItems.computeIfAbsent(key, ignored -> new VirtualItemProjection(virtualEntityIds.incrementAndGet(), UUID.randomUUID()));
        projection.location(location);
        projection.item(item.clone());
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            runtime.executeForPlayer(player, () -> updateVirtualItemForPlayer(player, key, projection));
        }
    }

    private void removeVirtualItem(SfxBlockAnchorKey key) {
        VirtualItemProjection projection = virtualItems.remove(key);
        if (projection == null) {
            return;
        }
        int[] entityIds = {projection.entityId()};
        for (UUID viewerId : new HashSet<>(projection.viewers())) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                runtime.executeForPlayer(viewer, () -> PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(entityIds)));
            }
        }
        projection.viewers().clear();
    }

    private void updateVirtualItemForPlayer(Player player, SfxBlockAnchorKey key, VirtualItemProjection projection) {
        if (player == null || !player.isOnline() || projection.location() == null || projection.item() == null) {
            return;
        }
        Location location = projection.location();
        boolean visible = player.getWorld().getUID().equals(key.worldId()) && player.getLocation().distanceSquared(location) <= VIEW_DISTANCE_SQUARED;
        if (!visible) {
            if (projection.viewers().remove(player.getUniqueId())) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(new int[] {projection.entityId()}));
            }
            return;
        }
        if (projection.viewers().add(player.getUniqueId())) {
            WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                    projection.entityId(),
                    Optional.of(projection.uuid()),
                    EntityTypes.ITEM,
                    new Vector3d(location.getX(), location.getY(), location.getZ()),
                    0,
                    0,
                    0,
                    0,
                    Optional.of(new Vector3d(0.0D, 0.0D, 0.0D)));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawn);
        }
        sendVirtualItemMetadata(player, projection);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sendVirtualItemMetadata(Player player, VirtualItemProjection projection) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, ENTITY_FLAGS));
        metadata.add(new EntityData(ENTITY_METADATA_CUSTOM_NAME, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(displayName(projection.item()))));
        metadata.add(new EntityData(ENTITY_METADATA_CUSTOM_NAME_VISIBLE, EntityDataTypes.BOOLEAN, true));
        metadata.add(new EntityData(ENTITY_METADATA_SILENT, EntityDataTypes.BOOLEAN, true));
        metadata.add(new EntityData(ENTITY_METADATA_NO_GRAVITY, EntityDataTypes.BOOLEAN, true));
        metadata.add(new EntityData(ITEM_STACK_METADATA_INDEX, EntityDataTypes.ITEMSTACK, toPacketItemStack(projection.item())));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(projection.entityId(), metadata));
    }

    private Object toPacketItemStack(ItemStack item) {
        try {
            Class<?> converter = Class.forName("io.github.retrooper.packetevents.util.SpigotConversionUtil");
            Method method = converter.getMethod("fromBukkitItemStack", ItemStack.class);
            return method.invoke(null, item.clone());
        } catch (ReflectiveOperationException ignored) {
            return item.clone();
        }
    }

    private void refreshViewerLater(Player player, long delayTicks) {
        runtime.executeForPlayerLater(player, Math.max(1L, delayTicks), () -> refreshViewer(player));
    }

    private void refreshViewer(Player player) {
        if (player == null) {
            return;
        }
        for (Map.Entry<SfxBlockAnchorKey, VirtualItemProjection> entry : virtualItems.entrySet()) {
            updateVirtualItemForPlayer(player, entry.getKey(), entry.getValue());
        }
    }

    private void scheduleViewerRefresh() {
        runtime.executeGlobalLater(20L, () -> {
            if (shutdown) {
                return;
            }
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                runtime.executeForPlayer(player, () -> refreshViewer(player));
            }
            scheduleViewerRefresh();
        });
    }

    private void sendMessage(Player player, String path) {
        if (player != null) {
            player.sendMessage(Text.prefixed(plugin, localization.text(path)));
        }
    }

    private void sendMessage(Player player, String path, Map<String, ?> placeholders) {
        if (player != null) {
            player.sendMessage(Text.prefixed(plugin, localization.text(path, placeholders)));
        }
    }

    private int countPedestals(List<SfxBlockAnchorKey> pedestalKeys) {
        int count = 0;
        for (SfxBlockAnchorKey key : pedestalKeys) {
            SfxBlockInstanceRecord pedestal = instanceAt(key);
            if (pedestal != null && ANCIENT_PEDESTAL.equals(pedestal.typeId())) {
                count++;
            }
        }
        return count;
    }

    private Component displayName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Component.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return meta.displayName();
        }
        return Component.text(prettyEnumName(item.getType().name()));
    }

    private record AltarRecipe(String id, String outputItemId, int outputAmount, SfxRecipeSlot catalyst, List<SfxRecipeSlot> ring) {
    }

    private record MatchedRecipe(AltarRecipe recipe, int offset) {
    }

    private static final class PedestalState {
        private final ItemStack item;

        private PedestalState(ItemStack item) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                this.item = null;
            } else {
                ItemStack clone = item.clone();
                clone.setAmount(1);
                this.item = clone;
            }
        }

        static PedestalState empty() {
            return new PedestalState(null);
        }

        static PedestalState decode(byte[] blob) {
            if (blob == null || blob.length == 0) {
                return empty();
            }
            try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(blob))) {
                int version = input.readInt();
                if (version != 1 || !input.readBoolean()) {
                    return empty();
                }
                Object raw = input.readObject();
                return raw instanceof ItemStack stack ? new PedestalState(stack) : empty();
            } catch (IOException | ClassNotFoundException exception) {
                return empty();
            }
        }

        byte[] encode() {
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(buffer)) {
                    output.writeInt(1);
                    output.writeBoolean(hasItem());
                    if (hasItem()) {
                        output.writeObject(item.clone());
                    }
                }
                return buffer.toByteArray();
            } catch (IOException exception) {
                return new byte[0];
            }
        }

        boolean hasItem() {
            return item != null && !item.getType().isAir() && item.getAmount() > 0;
        }

        ItemStack item() {
            return item == null ? null : item.clone();
        }
    }

    private static final class RitualSession {
        private final UUID id;
        private final SfxBlockAnchorKey altarKey;
        private final Location altarLocation;
        private final List<SfxBlockAnchorKey> pedestalKeys;
        private final List<UUID> pedestalInstanceIds;
        private final AltarRecipe recipe;
        private final ItemStack output;
        private final List<ItemStack> frozenInputs;
        private final int matchOffset;
        private final Set<SfxBlockAnchorKey> consumedPedestals = ConcurrentHashMap.newKeySet();
        private int step;

        private RitualSession(UUID id, SfxBlockAnchorKey altarKey, Location altarLocation, List<SfxBlockAnchorKey> pedestalKeys, List<UUID> pedestalInstanceIds, AltarRecipe recipe, ItemStack output, List<ItemStack> frozenInputs, int matchOffset) {
            this.id = id;
            this.altarKey = altarKey;
            this.altarLocation = altarLocation;
            this.pedestalKeys = List.copyOf(pedestalKeys);
            this.pedestalInstanceIds = List.copyOf(pedestalInstanceIds);
            this.recipe = recipe;
            this.output = output.clone();
            this.frozenInputs = frozenInputs.stream().map(ItemStack::clone).toList();
            this.matchOffset = matchOffset;
        }

        UUID id() {
            return id;
        }

        SfxBlockAnchorKey altarKey() {
            return altarKey;
        }

        Location altarLocation() {
            return altarLocation.clone();
        }

        List<SfxBlockAnchorKey> pedestalKeys() {
            return pedestalKeys;
        }

        List<UUID> pedestalInstanceIds() {
            return pedestalInstanceIds;
        }

        AltarRecipe recipe() {
            return recipe;
        }

        ItemStack output() {
            return output.clone();
        }

        List<ItemStack> frozenInputs() {
            return frozenInputs.stream().map(ItemStack::clone).toList();
        }

        int matchOffset() {
            return matchOffset;
        }

        Set<SfxBlockAnchorKey> consumedPedestals() {
            return consumedPedestals;
        }

        int step() {
            return step;
        }

        void advanceStep() {
            step += 1;
        }
    }

    private static final class VirtualItemProjection {
        private final int entityId;
        private final UUID uuid;
        private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
        private volatile Location location;
        private volatile ItemStack item;

        private VirtualItemProjection(int entityId, UUID uuid) {
            this.entityId = entityId;
            this.uuid = uuid;
        }

        int entityId() {
            return entityId;
        }

        UUID uuid() {
            return uuid;
        }

        Set<UUID> viewers() {
            return viewers;
        }

        Location location() {
            return location == null ? null : location.clone();
        }

        void location(Location location) {
            this.location = location == null ? null : location.clone();
        }

        ItemStack item() {
            return item == null ? null : item.clone();
        }

        void item(ItemStack item) {
            this.item = item == null ? null : item.clone();
        }
    }
}
