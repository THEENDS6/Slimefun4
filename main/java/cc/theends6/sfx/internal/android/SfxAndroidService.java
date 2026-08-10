package cc.theends6.sfx.internal.android;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxAndroidWoodcutterContext;
import cc.theends6.sfx.api.behavior.SfxAndroidWoodcutterPolicy;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleRouter;
import cc.theends6.sfx.internal.entity.SfxEntityKillAttribution;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.api.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.machine.SfxMachineEffectDispatcher;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseContext;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseResult;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachineLegacyHookBridge;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import cc.theends6.sfx.api.machine.runtime.SfxMachineTickContext;
import cc.theends6.sfx.internal.ui.SfxMachineMenuTransactions;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.api.text.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.SoundGroup;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class SfxAndroidService implements Listener {
    private static final String INTERFACE_FUEL = "sf:android_interface_fuel";
    private static final String INTERFACE_ITEMS = "sf:android_interface_items";
    private static final int[] OUTPUT_SLOTS = {20, 21, 22, 29, 30, 31};
    private static final int FUEL_SLOT = 43;
    private static final int[] SCRIPT_SLOTS = {
            1, 2, 3, 4, 5, 6, 7, 8, 9,
            10, 11, 12, 13, 14, 15, 16, 17, 18,
            19, 20, 21, 22, 23, 24, 25, 26, 27,
            28, 29, 30, 31, 32, 33, 34, 35, 36,
            37, 38, 39, 40, 41, 42, 43, 44, 45,
            46, 47, 48, 49, 50, 51, 52
    };
    private static final String HEAD_SCRIPT_START = "4ae29422db4047efdb9bac2cdae5a0719eb772fccc88a66d912320b343c341";
    private static final String HEAD_SCRIPT_REPEAT = "bc8def67a12622ead1decd3d89364257b531896d87e469813131ca235b5c7";
    private static final String HEAD_SCRIPT_NEW = "171d8979c1878a05987a7faf21b56d1b744f9d068c74cffcde1ea1edad5852";
    private static final String HEAD_SCRIPT_PAUSE = "16139fd1c5654e56e9e4e2c8be7eb2bd5b499d633616663feee99b74352ad64";
    private static final String HEAD_MEMORY_CORE = "d78f2b7e5e75639ea7fb796c35d364c4df28b4243e66b76277aadcd6261337";

    private final JavaPlugin plugin;
    final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxItemRegistry itemRegistry;
    private final SfxLocalization localization;
    final SfxBlockDataService blockData;
    final SfxMachineRuntimeEngine machineRuntime;
    private final SqliteSfxAndroidScriptRepository scripts;
    final Map<UUID, SfxAndroidState> states = new ConcurrentHashMap<>();
    final Set<UUID> activeAndroids = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ImportSession> pendingImports = new ConcurrentHashMap<>();
    private final Map<UUID, UploadSession> pendingUploads = new ConcurrentHashMap<>();
    private final Map<UUID, EditScriptSession> pendingScriptEdits = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> mainViewers = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> fuelSlotDirtyViewers = new ConcurrentHashMap<>();
    private final AtomicLong androidTick = new AtomicLong();
    private volatile boolean running;
    private long tickInterval;
    int maxActivePerRegion;
    private volatile SfxBlockLifecycleRouter blockLifecycleRouter;
    private boolean minerCanBreakSfxBlocks;

    public SfxAndroidService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxItemRegistry itemRegistry, SfxLocalization localization, SfxBlockDataService blockData, SqliteSfxAndroidScriptRepository scripts, SfxMachineRuntimeEngine machineRuntime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.itemRegistry = Objects.requireNonNull(itemRegistry, "itemRegistry");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.machineRuntime = Objects.requireNonNull(machineRuntime, "machineRuntime");
    }

    public void start() {
        try {
            scripts.initialize();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize Android script database", exception);
        }
        tickInterval = Math.max(1L, plugin.getConfig().getLong("androids.tick-interval-ticks", 10L));
        maxActivePerRegion = Math.max(1, plugin.getConfig().getInt("androids.max-active-per-region-per-tick", 64));
        minerCanBreakSfxBlocks = plugin.getConfig().getBoolean("androids.miner.allow-break-sfx-blocks", false);
        running = true;
        rebuildIndex();
        scheduleTick();
        scheduleFuelTick();
        scheduleOpenMainInventoryRefresh();
    }

    public void bindBlockLifecycleRouter(SfxBlockLifecycleRouter blockLifecycleRouter) {
        this.blockLifecycleRouter = Objects.requireNonNull(blockLifecycleRouter, "blockLifecycleRouter");
    }

    public void shutdown() {
        running = false;
        pendingImports.clear();
        pendingUploads.clear();
        pendingScriptEdits.clear();
        mainViewers.clear();
        fuelSlotDirtyViewers.clear();
        flushAllStates();
        states.clear();
        activeAndroids.clear();
        scripts.close();
    }

    public boolean supportsType(String typeId) {
        return SfxAndroidType.isAndroidItem(typeId) || INTERFACE_FUEL.equals(typeId) || INTERFACE_ITEMS.equals(typeId);
    }


    public SfxMachinePhaseResult frameworkEffect(String effectName, SfxMachinePhaseContext context) {
        if (context == null) {
            return SfxMachinePhaseResult.cont();
        }
        context.put("android.framework.effect", effectName);
        context.put("android.framework.effect.handled", Boolean.TRUE);
        SfxAndroidInstruction instruction = context.attachment("android.instruction", SfxAndroidInstruction.class).orElse(null);
        if (instruction != null) {
            context.put("android.framework.instruction", instruction.name());
        }
        SfxAndroidState state = context.attachment("android.state", SfxAndroidState.class).orElse(null);
        if (state != null) {
            context.put("android.framework.runtime-state", state.runtimeState().name());
        }
        return SfxMachinePhaseResult.cont();
    }

    Map<String, Object> androidFrameworkAttributes(SfxBlockInstanceRecord instance, SfxAndroidType type, SfxAndroidState state, Block block, SfxAndroidInstruction instruction, boolean moveAccepted, long tickId) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("android.instance", instance);
        attributes.put("android.type", type);
        attributes.put("android.state", state);
        attributes.put("android.block", block);
        attributes.put("android.instruction", instruction);
        attributes.put("android.moveAccepted", moveAccepted);
        attributes.put("android.tickId", tickId);
        attributes.put("android.service", this);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkEffect);
        return attributes;
    }

    public void handlePlaced(UUID instanceId, String typeId, Player player, Block block) {
        handlePlaced(instanceId, typeId, SfxAndroidBlockAppearance.facingFromPlayer(player), block);
    }

    public void handlePlaced(UUID instanceId, String typeId, BlockFace rotation, Block block) {
        if (instanceId == null || typeId == null || block == null) {
            return;
        }
        SfxAndroidType type = SfxAndroidType.fromItemId(typeId);
        if (type == null) {
            return;
        }
        BlockFace placedRotation = rotation == null ? BlockFace.NORTH : rotation;
        SfxAndroidState state = SfxAndroidState.createDefault(placedRotation);
        states.put(instanceId, state);
        persist(instanceId, state);
        SfxAndroidBlockAppearance.apply(machineRuntime, itemRegistry, block, typeId, placedRotation);
        runtime.executeAtLater(block.getLocation(), 1L, () -> SfxAndroidBlockAppearance.apply(machineRuntime, itemRegistry, block, typeId, placedRotation));
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (block == null || instanceId == null || typeId == null) {
            return;
        }
        if (SfxAndroidType.isAndroidItem(typeId)) {
            SfxAndroidState state = stateFor(instanceId, typeId, block.getLocation());
            for (ItemStack stack : state.outputs()) {
                SfxBlockDrops.dropItem(block, stack);
            }
            SfxBlockDrops.dropItem(block, state.fuelSlot());
            SfxBlockDrops.dropPluginBlock(block, items, typeId);
            states.remove(instanceId);
            activeAndroids.remove(instanceId);
            blockData.unregisterAt(block.getLocation());
            return;
        }
        if (INTERFACE_FUEL.equals(typeId) || INTERFACE_ITEMS.equals(typeId)) {
            SfxAndroidBlockAppearance.dropInventory(block);
            SfxBlockDrops.dropPluginBlock(block, items, typeId);
            blockData.unregisterAt(block.getLocation());
        }
    }

    private void rebuildIndex() {
        for (SfxAnchorRecord anchor : blockData.allAnchorsSnapshot()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
                continue;
            }
            SfxAndroidState state = SfxAndroidState.decode(instance.stateBlob(), BlockFace.NORTH);
            states.put(instance.instanceId(), state);
            if (!state.paused() && state.runtimeState() == SfxAndroidRuntimeState.ACTIVE) {
                activeAndroids.add(instance.instanceId());
            }
        }
    }

    private void scheduleTick() {
        runtime.executeGlobalLater(tickInterval, () -> {
            if (!running) {
                return;
            }
            try {
                if (!runtime.isGameTickFrozen()) {
                    tickAndroids(androidTick.incrementAndGet());
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Android scheduler failed: " + throwable.getMessage());
            } finally {
                scheduleTick();
            }
        });
    }

    private void scheduleOpenMainInventoryRefresh() {
        runtime.executeGlobalLater(1L, () -> {
            if (!running) {
                return;
            }
            try {
                refreshOpenMainInventories();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Android inventory refresh failed: " + throwable.getMessage());
            } finally {
                scheduleOpenMainInventoryRefresh();
            }
        });
    }

    private void scheduleFuelTick() {
        runtime.executeGlobalLater(20L, () -> {
            if (!running) {
                return;
            }
            try {
                if (!runtime.isGameTickFrozen()) {
                    burnActiveFuelSeconds();
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Android fuel scheduler failed: " + throwable.getMessage());
            } finally {
                scheduleFuelTick();
            }
        });
    }

    private void burnActiveFuelSeconds() {
        for (UUID instanceId : List.copyOf(activeAndroids)) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
                activeAndroids.remove(instanceId);
                states.remove(instanceId);
                continue;
            }
            Location location = toLocation(instance.anchorKey());
            SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), location);
            if (state.paused() || state.runtimeState() == SfxAndroidRuntimeState.PAUSED) {
                activeAndroids.remove(instanceId);
                continue;
            }
            SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
            if (type == null) {
                continue;
            }
            boolean changed = false;
            if (state.fuelTicks() <= 0) {
                changed = consumeFuelItem(state, type);
            }
            if (state.fuelTicks() > 0) {
                state.consumeFuelTick();
                changed = true;
                if (state.runtimeState() == SfxAndroidRuntimeState.DORMANT_NO_FUEL) {
                    state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
                }
            }
            if (changed) {
                persist(instance.instanceId(), state, false);
            }
        }
    }

    private void refreshOpenMainInventories() {
        for (UUID instanceId : List.copyOf(mainViewers.keySet())) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
                mainViewers.remove(instanceId);
                continue;
            }
            SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
            refreshOpenMainInventory(instanceId, state, true);
        }
    }

    private void tickAndroids(long tickId) {
        SfxAndroidScheduler.tickAndroids(this, tickId);
    }

    void tickRegionBatch(List<SfxBlockInstanceRecord> instances, long tickId) {
        SfxAndroidBatchTickController.tickRegionBatch(this, instances, tickId);
    }

    boolean executeInstruction(SfxBlockInstanceRecord instance, SfxAndroidType type, SfxAndroidState state, SfxAndroidInstruction instruction, Block block, boolean moveAccepted, long tickId) {
        BlockFace face = actionFacing(state);
        return switch (instruction) {
            case WAIT -> {
                state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
                yield true;
            }
            case TURN_LEFT -> {
                state.rotation(SfxAndroidBlockAppearance.turnLeft(state.rotation()));
                SfxAndroidBlockAppearance.applyRotation(machineRuntime, instance.typeId(), block, state.rotation());
                state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
                yield true;
            }
            case TURN_RIGHT -> {
                state.rotation(SfxAndroidBlockAppearance.turnRight(state.rotation()));
                SfxAndroidBlockAppearance.applyRotation(machineRuntime, instance.typeId(), block, state.rotation());
                state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
                yield true;
            }
            case GO_FORWARD, GO_UP, GO_DOWN -> moveAccepted && moveAndroid(instance, state, block, targetBlock(block, face, instruction));
            case DIG_FORWARD, DIG_UP, DIG_DOWN -> dig(state, block, targetBlock(block, face, instruction), false, instance.typeId(), instance.instanceId(), instance.ownerId());
            case MOVE_AND_DIG_FORWARD, MOVE_AND_DIG_UP, MOVE_AND_DIG_DOWN -> {
                Block target = targetBlock(block, face, instruction);
                boolean dug = dig(state, block, target, false, instance.typeId(), instance.instanceId(), instance.ownerId());
                yield dug && moveAccepted && moveAndroid(instance, state, block, target);
            }
            case FARM_FORWARD, FARM_DOWN, FARM_EXOTIC_FORWARD, FARM_EXOTIC_DOWN -> farm(state, targetBlock(block, face, instruction), type, instance.instanceId(), instance.ownerId());
            case CHOP_TREE -> chopTree(state, targetBlock(block, face, instruction), instance.instanceId(), instance.ownerId());
            case CATCH_FISH -> catchFish(state, block, type);
            case ATTACK_MOBS_ANIMALS -> attack(block, state, type, living -> true, instance.instanceId(), instance.ownerId());
            case ATTACK_MOBS -> attack(block, state, type, living -> living instanceof Monster, instance.instanceId(), instance.ownerId());
            case ATTACK_ANIMALS -> attack(block, state, type, living -> living instanceof Animals, instance.instanceId(), instance.ownerId());
            case ATTACK_ANIMALS_ADULT -> attack(block, state, type, living -> living instanceof Animals animal && animal.isAdult(), instance.instanceId(), instance.ownerId());
            case INTERFACE_ITEMS -> depositItems(state, block, face);
            case INTERFACE_FUEL -> pullFuel(state, block, face, type);
        };
    }

    UUID currentInstanceId(SfxBlockInstanceRecord fallback, Location currentLocation) {
        SfxAnchorRecord anchor = blockData.findAnchor(currentLocation).orElse(null);
        return anchor == null ? fallback.instanceId() : anchor.instanceId();
    }

    boolean ensureFuel(SfxBlockInstanceRecord instance, SfxAndroidState state, SfxAndroidType type, Block block) {
        if (state.fuelTicks() > 0) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            return true;
        }
        if (!consumeFuelItem(state, type)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_NO_FUEL);
            state.incrementNoEffectTicks();
            persist(instance.instanceId(), state, false);
            return false;
        }
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        persist(instance.instanceId(), state, false);
        return true;
    }

    private boolean consumeFuelItem(SfxAndroidState state, SfxAndroidType type) {
        ItemStack fuel = state.fuelSlot();
        int fuelTicks = fuelValue(fuel, type);
        if (fuelTicks <= 0) {
            return false;
        }
        fuel.setAmount(fuel.getAmount() - 1);
        state.fuelSlot(fuel.getAmount() <= 0 ? null : fuel);
        state.fuelTicks(fuelTicks);
        return true;
    }

    int fuelValue(ItemStack item, SfxAndroidType type) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return 0;
        }
        int base = switch (item.getType()) {
            case COAL_BLOCK -> 80;
            case BLAZE_ROD -> 45;
            case DRIED_KELP_BLOCK -> 70;
            case COAL, CHARCOAL -> 8;
            case BAMBOO, OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, JUNGLE_PLANKS, ACACIA_PLANKS, DARK_OAK_PLANKS, MANGROVE_PLANKS, CHERRY_PLANKS, CRIMSON_PLANKS, WARPED_PLANKS -> 1;
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG, MANGROVE_LOG, CHERRY_LOG, CRIMSON_STEM, WARPED_STEM -> 2;
            case LAVA_BUCKET -> 100;
            default -> 0;
        };
        if ("sf:oil_bucket".equals(sfxItemId(item))) {
            base = 200;
        } else if ("sf:fuel_bucket".equals(sfxItemId(item))) {
            base = 500;
        } else if ("sf:uranium".equals(sfxItemId(item))) {
            base = 2500;
        } else if ("sf:neptunium".equals(sfxItemId(item))) {
            base = 1200;
        } else if ("sf:boosted_uranium".equals(sfxItemId(item))) {
            base = 3000;
        }
        if (base <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.round(base * type.fuelEfficiency()));
    }

    private String sfxItemId(ItemStack item) {
        return items.readMarker(item).map(SfxItemMarker::itemId).orElse(null);
    }

    boolean isMoveInstruction(SfxAndroidInstruction instruction) {
        return instruction == SfxAndroidInstruction.GO_FORWARD
                || instruction == SfxAndroidInstruction.GO_UP
                || instruction == SfxAndroidInstruction.GO_DOWN
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_FORWARD
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_UP
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_DOWN;
    }

    boolean isMoveAndDigInstruction(SfxAndroidInstruction instruction) {
        return instruction == SfxAndroidInstruction.MOVE_AND_DIG_FORWARD
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_UP
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_DOWN;
    }

    boolean canClearTargetForMoveAndDig(SfxAndroidState state, Block target) {
        SfxAnchorRecord anchor = target == null ? null : blockData.findAnchor(target.getLocation()).orElse(null);
        if (anchor != null) {
            return minerCanBreakSfxBlocks && blockLifecycleRouter != null;
        }
        if (target == null
                || target.getType().isAir()
                || isUnbreakable(target.getType())) {
            return false;
        }
        List<ItemStack> drops = collectMineDrops(target);
        return !drops.isEmpty() && SfxAndroidOutputBuffer.canFitAllOutputs(state, drops);
    }


    boolean shouldSkipForBackoff(SfxAndroidState state, long tickId) {
        if (state == null || state.noEffectTicks() <= 0) {
            return false;
        }
        long threshold = Math.max(1L, Math.round(200.0D / Math.max(1L, tickInterval)));
        int stage = (int) Math.min(4L, state.noEffectTicks() / threshold);
        int divisor = switch (stage) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 8;
            default -> 16;
        };
        return divisor > 1 && Math.floorMod(tickId, divisor) != 0;
    }

    BlockFace actionFacing(SfxAndroidState state) {
        return state == null ? BlockFace.NORTH : state.rotation();
    }

    Block targetBlock(Block block, BlockFace face, SfxAndroidInstruction instruction) {
        return switch (instruction) {
            case GO_UP, DIG_UP, MOVE_AND_DIG_UP -> block.getRelative(BlockFace.UP);
            case GO_DOWN, DIG_DOWN, MOVE_AND_DIG_DOWN -> block.getRelative(BlockFace.DOWN);
            case FARM_DOWN, FARM_EXOTIC_DOWN -> block.getRelative(BlockFace.DOWN);
            default -> block.getRelative(face);
        };
    }

    private boolean moveAndroid(SfxBlockInstanceRecord instance, SfxAndroidState state, Block from, Block to) {
        if (to == null || !to.getType().isAir() || from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
        if (type == null) {
            return false;
        }
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, instance.typeId(), from, Material.AIR, false, "android", "moveAndroid:clear-from");
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, instance.typeId(), to, Material.PLAYER_HEAD, false, "android", "moveAndroid:place-to");
        SfxAndroidBlockAppearance.apply(machineRuntime, itemRegistry, to, instance.typeId(), state.rotation());
        blockData.unregisterAt(from.getLocation());
        UUID newId = blockData.registerSingleBlock(instance.typeId(), to.getLocation(), Material.PLAYER_HEAD, instance.ownerId());
        states.remove(instance.instanceId());
        activeAndroids.remove(instance.instanceId());
        states.put(newId, state);
        activeAndroids.add(newId);
        persist(newId, state);
        return true;
    }

    private boolean dig(SfxAndroidState state, Block androidBlock, Block target, boolean moveAfter, String typeId, UUID instanceId, UUID ownerId) {
        if (target == null || target.getType().isAir() || isUnbreakable(target.getType())) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        if (!androidCanModify(instanceId, ownerId, target)) {
            
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        SfxAnchorRecord anchor = blockData.findAnchor(target.getLocation()).orElse(null);
        if (anchor != null) {
            SfxBlockLifecycleRouter router = blockLifecycleRouter;
            SfxBlockInstanceRecord anchoredInstance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (!minerCanBreakSfxBlocks || router == null || anchoredInstance == null) {
                state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
                return false;
            }
            playBlockBreakEffect(target);
            router.destroyAnchoredBlock(target, anchoredInstance.instanceId(), anchoredInstance.typeId());
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, typeId, target, Material.AIR, false, "android", "dig-sfx");
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            return true;
        }
        List<ItemStack> drops = collectMineDrops(target);
        if (drops.isEmpty()) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        if (!SfxAndroidOutputBuffer.pushAllOutputsAtomically(state, drops)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL);
            return false;
        }
        playBlockBreakEffect(target);
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, typeId, target, Material.AIR, true, "android", "dig");
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        return true;
    }

    private List<ItemStack> collectMineDrops(Block target) {
        List<ItemStack> drops = new ArrayList<>(target.getDrops(new ItemStack(Material.DIAMOND_PICKAXE)));
        if (target.getState() instanceof Container container && !target.getType().name().endsWith("SHULKER_BOX")) {
            for (ItemStack content : container.getInventory().getContents()) {
                if (content != null && !content.getType().isAir() && content.getAmount() > 0) {
                    drops.add(content.clone());
                }
            }
        }
        return drops;
    }

    private void playBlockBreakEffect(Block block) {
        if (block == null || block.getType().isAir()) {
            return;
        }
        BlockData data = block.getBlockData();
        SoundGroup soundGroup = data.getSoundGroup();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().playSound(center, soundGroup.getBreakSound(), soundGroup.getVolume(), soundGroup.getPitch());
        block.getWorld().spawnParticle(Particle.BLOCK, center, 24, 0.25, 0.25, 0.25, data);
    }

    private UUID currentInstanceIdByBlock(Block block) {
        SfxAnchorRecord anchor = blockData.findAnchor(block.getLocation()).orElse(null);
        return anchor == null ? null : anchor.instanceId();
    }


    private boolean isUnbreakable(Material material) {
        return material == Material.BEDROCK || material == Material.BARRIER || material == Material.COMMAND_BLOCK || material == Material.CHAIN_COMMAND_BLOCK || material == Material.REPEATING_COMMAND_BLOCK || material == Material.STRUCTURE_BLOCK || material == Material.JIGSAW;
    }

    private boolean farm(SfxAndroidState state, Block target, SfxAndroidType type, UUID instanceId, UUID ownerId) {
        if (target == null || !(target.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        if (!androidCanModify(instanceId, ownerId, target)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        ItemStack drop = cropDrop(target.getType());
        if (drop == null || !state.pushOutput(drop)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL);
            return false;
        }
        ageable.setAge(0);
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(machineRuntime, "sf:android", target, ageable, true, "android", "farm:reset-crop-age");
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.7f, 1.0f);
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        return true;
    }

    private ItemStack cropDrop(Material material) {
        return switch (material) {
            case WHEAT -> new ItemStack(Material.WHEAT);
            case CARROTS -> new ItemStack(Material.CARROT);
            case POTATOES -> new ItemStack(Material.POTATO);
            case BEETROOTS -> new ItemStack(Material.BEETROOT);
            case COCOA -> new ItemStack(Material.COCOA_BEANS);
            case NETHER_WART -> new ItemStack(Material.NETHER_WART);
            case SWEET_BERRY_BUSH -> new ItemStack(Material.SWEET_BERRIES);
            default -> null;
        };
    }

    private boolean chopTree(SfxAndroidState state, Block target, UUID instanceId, UUID ownerId) {
        if (target == null || !Tag.LOGS.isTagged(target.getType())) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        if (!androidCanModify(instanceId, ownerId, target)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        List<Block> logs = connectedLogs(target, 160);
        if (logs.isEmpty()) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        List<Block> bottomLogs = bottomLayerLogs(target, logs, actionFacing(state));
        boolean onlyBottomLayerLogsRemain = onlyBottomLayerLogsRemain(logs, bottomLogs);
        if (batchReplantBottomLayer(logs, bottomLogs, onlyBottomLayerLogsRemain)) {
            return chopAndReplantBottomLayer(state, bottomLogs, instanceId, ownerId);
        }
        Block log = nextLogToChop(target, logs, bottomLogs, actionFacing(state));
        if (log == null) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        if (!androidCanModify(instanceId, ownerId, log)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        Material logType = log.getType();
        ItemStack drop = new ItemStack(logType);
        if (!state.pushOutput(drop)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL);
            return false;
        }
        playBlockBreakEffect(log);
        if (isBottomLayerLog(log, bottomLogs)) {
            replantAfterChop(log, logType);
        } else {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, "sf:android", log, Material.AIR, true, "android", "chopTree");
        }
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        return true;
    }

    private boolean batchReplantBottomLayer(List<Block> logs, List<Block> bottomLogs, boolean onlyBottomLayerLogsRemain) {
        SfxApi api = sfxApi();
        if (api == null) {
            return false;
        }
        SfxAndroidWoodcutterContext context = new SfxAndroidWoodcutterContext(logs.size(), bottomLogs.size(), onlyBottomLayerLogsRemain);
        boolean decision = false;
        for (SfxAndroidWoodcutterPolicy policy : api.behaviors().androidWoodcutterPolicies()) {
            decision = policy.batchReplantBottomLayer(context, decision);
        }
        return decision;
    }

    private SfxApi sfxApi() {
        return plugin instanceof SlimeFunXPlugin sfx ? sfx.api() : null;
    }

    private cc.theends6.sfx.api.permission.SfxWorldPermissionService permissions() {
        SfxApi api = sfxApi();
        return api == null ? null : api.permissions();
    }

    private cc.theends6.sfx.api.permission.SfxActionActor androidActor(UUID instanceId, UUID ownerId) {
        
        
        Player owner = ownerId == null ? null : plugin.getServer().getPlayer(ownerId);
        return cc.theends6.sfx.api.permission.SfxActionActor.machine(instanceId, ownerId, owner);
    }

    private boolean androidCanModify(UUID instanceId, UUID ownerId, Block target) {
        cc.theends6.sfx.api.permission.SfxWorldPermissionService perms = permissions();
        if (perms == null) {
            return true;
        }
        return perms.canBreak(androidActor(instanceId, ownerId), target);
    }

    private boolean androidCanDamage(UUID instanceId, UUID ownerId, org.bukkit.entity.Entity target) {
        cc.theends6.sfx.api.permission.SfxWorldPermissionService perms = permissions();
        if (perms == null) {
            return true;
        }
        return perms.canDamage(androidActor(instanceId, ownerId), target);
    }

    private Block nextLogToChop(Block root, List<Block> logs, List<Block> bottomLogs, BlockFace face) {
        if (logs.isEmpty()) {
            return null;
        }
        List<Block> upperLogs = new ArrayList<>();
        for (Block log : logs) {
            if (!isBottomLayerLog(log, bottomLogs)) {
                upperLogs.add(log);
            }
        }
        if (!upperLogs.isEmpty()) {
            return upperLogs.stream()
                    .max(Comparator
                            .comparingInt(Block::getY)
                            .thenComparingInt(block -> manhattan(block, root))
                            .thenComparingInt(block -> stableForwardCoordinate(block, root, face))
                            .thenComparingInt(block -> stableSideCoordinate(block, root, face)))
                    .orElse(upperLogs.get(0));
        }
        List<Block> sortedBottom = new ArrayList<>(bottomLogs);
        sortedBottom.sort(bottomLayerComparator(root, face));
        return sortedBottom.isEmpty() ? root : sortedBottom.get(0);
    }

    private List<Block> bottomLayerLogs(Block root, List<Block> logs, BlockFace face) {
        List<Block> result = new ArrayList<>();
        int bottomY = root.getY();
        for (Block log : logs) {
            if (log.getY() == bottomY && Tag.LOGS.isTagged(log.getType()) && saplingForLog(log.getType()) != null) {
                result.add(log);
            }
        }
        result.sort(bottomLayerComparator(root, face));
        return result;
    }

    private Comparator<Block> bottomLayerComparator(Block root, BlockFace face) {
        return Comparator
                .comparingInt((Block block) -> sameBlock(block, root) ? 1 : 0)
                .thenComparingInt(block -> stableForwardCoordinate(block, root, face))
                .thenComparingInt(block -> stableSideCoordinate(block, root, face))
                .thenComparingInt(Block::getX)
                .thenComparingInt(Block::getZ);
    }

    private int stableForwardCoordinate(Block block, Block root, BlockFace face) {
        int dx = block.getX() - root.getX();
        int dz = block.getZ() - root.getZ();
        BlockFace normalized = normalizeHorizontal(face);
        return dx * normalized.getModX() + dz * normalized.getModZ();
    }

    private int stableSideCoordinate(Block block, Block root, BlockFace face) {
        int dx = block.getX() - root.getX();
        int dz = block.getZ() - root.getZ();
        BlockFace right = SfxAndroidBlockAppearance.turnRight(normalizeHorizontal(face));
        return dx * right.getModX() + dz * right.getModZ();
    }

    private BlockFace normalizeHorizontal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.EAST || face == BlockFace.SOUTH || face == BlockFace.WEST ? face : BlockFace.NORTH;
    }

    private boolean isBottomLayerLog(Block block, List<Block> bottomLogs) {
        for (Block candidate : bottomLogs) {
            if (sameBlock(block, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean onlyBottomLayerLogsRemain(List<Block> logs, List<Block> bottomLogs) {
        if (bottomLogs.isEmpty()) {
            return false;
        }
        for (Block log : logs) {
            if (!isBottomLayerLog(log, bottomLogs)) {
                return false;
            }
        }
        return true;
    }

    private boolean chopAndReplantBottomLayer(
            SfxAndroidState state,
            List<Block> bottomLogs,
            UUID instanceId,
            UUID ownerId
    ) {
        List<ItemStack> drops = new ArrayList<>();
        for (Block block : bottomLogs) {
            if (Tag.LOGS.isTagged(block.getType())) {
                if (!androidCanModify(instanceId, ownerId, block)) {
                    state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
                    return false;
                }
                drops.add(new ItemStack(block.getType()));
            }
        }
        if (drops.isEmpty()) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        if (!SfxAndroidOutputBuffer.pushAllOutputsAtomically(state, drops)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL);
            return false;
        }
        for (Block block : bottomLogs) {
            Material logType = block.getType();
            if (Tag.LOGS.isTagged(logType)) {
                playBlockBreakEffect(block);
                replantAfterChop(block, logType);
            }
        }
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        return true;
    }

    private List<Block> connectedLogs(Block root, int maxReach) {
        List<Block> result = new ArrayList<>();
        Set<LocationKey> visited = new HashSet<>();
        List<Block> queue = new ArrayList<>();
        queue.add(root);
        visited.add(LocationKey.of(root.getLocation()));
        for (int index = 0; index < queue.size() && result.size() < maxReach; index++) {
            Block current = queue.get(index);
            if (!Tag.LOGS.isTagged(current.getType())) {
                continue;
            }
            result.add(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block relative = current.getRelative(dx, dy, dz);
                        LocationKey key = LocationKey.of(relative.getLocation());
                        if (visited.add(key) && Tag.LOGS.isTagged(relative.getType())) {
                            queue.add(relative);
                        }
                    }
                }
            }
        }
        return result;
    }

    private int manhattan(Block a, Block b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private boolean sameBlock(Block a, Block b) {
        return a != null && b != null && a.getWorld().equals(b.getWorld()) && a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    private void replantAfterChop(Block block, Material logType) {
        Material sapling = saplingForLog(logType);
        if (sapling == null) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, "sf:android", block, Material.AIR, true, "android", "replantAfterChop:clear");
            return;
        }
        replantSaplingAt(block, sapling);
    }

    private void replantSaplingAt(Block block, Material sapling) {
        Material soil = block.getRelative(BlockFace.DOWN).getType();
        if (canPlantSaplingOn(sapling, soil)) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, "sf:android", block, sapling, true, "android", "replantSaplingAt");
        } else {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, "sf:android", block, Material.AIR, true, "android", "replantAfterChop:clear");
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.25, 0.5), new ItemStack(sapling));
        }
    }

    private Material saplingForLog(Material logType) {
        return switch (logType) {
            case OAK_LOG, OAK_WOOD, STRIPPED_OAK_LOG, STRIPPED_OAK_WOOD -> Material.OAK_SAPLING;
            case SPRUCE_LOG, SPRUCE_WOOD, STRIPPED_SPRUCE_LOG, STRIPPED_SPRUCE_WOOD -> Material.SPRUCE_SAPLING;
            case BIRCH_LOG, BIRCH_WOOD, STRIPPED_BIRCH_LOG, STRIPPED_BIRCH_WOOD -> Material.BIRCH_SAPLING;
            case JUNGLE_LOG, JUNGLE_WOOD, STRIPPED_JUNGLE_LOG, STRIPPED_JUNGLE_WOOD -> Material.JUNGLE_SAPLING;
            case ACACIA_LOG, ACACIA_WOOD, STRIPPED_ACACIA_LOG, STRIPPED_ACACIA_WOOD -> Material.ACACIA_SAPLING;
            case DARK_OAK_LOG, DARK_OAK_WOOD, STRIPPED_DARK_OAK_LOG, STRIPPED_DARK_OAK_WOOD -> Material.DARK_OAK_SAPLING;
            case MANGROVE_LOG, MANGROVE_WOOD, STRIPPED_MANGROVE_LOG, STRIPPED_MANGROVE_WOOD -> Material.MANGROVE_PROPAGULE;
            case CHERRY_LOG, CHERRY_WOOD, STRIPPED_CHERRY_LOG, STRIPPED_CHERRY_WOOD -> Material.CHERRY_SAPLING;
            case PALE_OAK_LOG, PALE_OAK_WOOD, STRIPPED_PALE_OAK_LOG, STRIPPED_PALE_OAK_WOOD -> Material.PALE_OAK_SAPLING;
            case CRIMSON_STEM, CRIMSON_HYPHAE, STRIPPED_CRIMSON_STEM, STRIPPED_CRIMSON_HYPHAE -> Material.CRIMSON_FUNGUS;
            case WARPED_STEM, WARPED_HYPHAE, STRIPPED_WARPED_STEM, STRIPPED_WARPED_HYPHAE -> Material.WARPED_FUNGUS;
            default -> null;
        };
    }

    private boolean canPlantSaplingOn(Material sapling, Material soil) {
        return switch (sapling) {
            case CRIMSON_FUNGUS -> soil == Material.CRIMSON_NYLIUM;
            case WARPED_FUNGUS -> soil == Material.WARPED_NYLIUM;
            case MANGROVE_PROPAGULE -> isMangroveSoil(soil);
            default -> isDirtLike(soil);
        };
    }

    private boolean isDirtLike(Material material) {
        return switch (material) {
            case DIRT, GRASS_BLOCK, PODZOL, COARSE_DIRT, ROOTED_DIRT, FARMLAND, MYCELIUM, MOSS_BLOCK -> true;
            default -> false;
        };
    }

    private boolean isMangroveSoil(Material material) {
        return isDirtLike(material) || material == Material.MUD || material == Material.MUDDY_MANGROVE_ROOTS;
    }

    private boolean catchFish(SfxAndroidState state, Block block, SfxAndroidType type) {
        if (block.getRelative(BlockFace.DOWN).getType() != Material.WATER) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        int chance = 10 * type.tier();
        block.getWorld().playSound(block.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.6f, 1.0f);
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= chance) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            return true;
        }
        ItemStack drop = classicFishingLoot();
        if (!state.pushOutput(drop)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL);
            return false;
        }
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        return true;
    }

    private ItemStack classicFishingLoot() {
        int totalWeight = 0;
        List<WeightedMaterial> loot = new ArrayList<>();
        for (Material fish : Tag.ITEMS_FISHES.getValues()) {
            loot.add(new WeightedMaterial(fish, 25));
            totalWeight += 25;
        }
        totalWeight += addLoot(loot, Material.BONE, 10);
        totalWeight += addLoot(loot, Material.STRING, 10);
        totalWeight += addLoot(loot, Material.INK_SAC, 8);
        totalWeight += addLoot(loot, Material.KELP, 6);
        totalWeight += addLoot(loot, Material.STICK, 5);
        totalWeight += addLoot(loot, Material.ROTTEN_FLESH, 3);
        totalWeight += addLoot(loot, Material.LEATHER, 2);
        totalWeight += addLoot(loot, Material.BAMBOO, 3);
        totalWeight += addLoot(loot, Material.SADDLE, 1);
        totalWeight += addLoot(loot, Material.NAME_TAG, 1);
        totalWeight += addLoot(loot, Material.NAUTILUS_SHELL, 1);
        int roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1, totalWeight));
        int cursor = 0;
        for (WeightedMaterial entry : loot) {
            cursor += entry.weight();
            if (roll < cursor) {
                return new ItemStack(entry.material());
            }
        }
        return new ItemStack(Material.COD);
    }

    private int addLoot(List<WeightedMaterial> loot, Material material, int weight) {
        loot.add(new WeightedMaterial(material, weight));
        return weight;
    }

    private boolean attack(Block block, SfxAndroidState state, SfxAndroidType type, java.util.function.Predicate<LivingEntity> filter, UUID instanceId, UUID ownerId) {
        double damage = type.tier() >= 3 ? 20D : 4D * type.tier();
        double radius = 4D + type.tier();
        Location origin = block.getLocation().add(0.5, 0.5, 0.5);
        BlockFace face = actionFacing(state);
        List<Entity> targets = block.getWorld().getNearbyEntities(origin, radius, radius, radius).stream()
                .filter(entity -> entity instanceof LivingEntity living
                        && !(entity instanceof ArmorStand)
                        && !(entity instanceof Player)
                        && entity.isValid()
                        && filter.test(living)
                        && isInFront(origin, living.getLocation(), face))
                .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)))
                .toList();
        if (targets.isEmpty()) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return true;
        }
        LivingEntity target = (LivingEntity) targets.get(0);
        if (!androidCanDamage(instanceId, ownerId, target)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return true;
        }
        SfxEntityKillAttribution.damageAsAndroid(plugin, target, damage);
        if (target.isDead()) {
            collectNearbyDrops(state, target.getLocation());
        }
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        return true;
    }

    private boolean isInFront(Location origin, Location target, BlockFace face) {
        double dx = target.getX() - origin.getX();
        double dz = target.getZ() - origin.getZ();
        return switch (face) {
            case NORTH -> dz <= 0 && Math.abs(dz) >= Math.abs(dx) * 0.35;
            case SOUTH -> dz >= 0 && Math.abs(dz) >= Math.abs(dx) * 0.35;
            case EAST -> dx >= 0 && Math.abs(dx) >= Math.abs(dz) * 0.35;
            case WEST -> dx <= 0 && Math.abs(dx) >= Math.abs(dz) * 0.35;
            default -> true;
        };
    }


    private void collectNearbyDrops(SfxAndroidState state, Location location) {
        for (Entity entity : location.getWorld().getNearbyEntities(location, 0.75, 0.75, 0.75)) {
            if (entity instanceof Item item) {
                if (state.pushOutput(item.getItemStack())) {
                    item.remove();
                }
            } else if (entity instanceof ExperienceOrb orb) {
                orb.remove();
            }
        }
    }

    private boolean depositItems(SfxAndroidState state, Block androidBlock, BlockFace preferredFace) {
        Block target = findFrontInterface(androidBlock, preferredFace, INTERFACE_ITEMS);
        if (target == null || !(target.getState() instanceof Dispenser dispenser)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_WAITING_EXTERNAL_CHANGE);
            return true;
        }
        SfxAndroidOutputBuffer.DepositResult result = SfxAndroidOutputBuffer.depositOutputs(state, dispenser.getInventory());
        if (result.moved()) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        } else {
            state.runtimeState(result.hadOutput() ? SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL : SfxAndroidRuntimeState.DORMANT_WAITING_EXTERNAL_CHANGE);
        }
        return true;
    }

    private boolean pullFuel(SfxAndroidState state, Block androidBlock, BlockFace preferredFace, SfxAndroidType type) {
        Block target = findFrontInterface(androidBlock, preferredFace, INTERFACE_FUEL);
        if (target == null || !(target.getState() instanceof Dispenser dispenser)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_WAITING_EXTERNAL_CHANGE);
            return true;
        }
        Inventory inventory = dispenser.getInventory();
        ItemStack current = state.fuelSlot();
        if (current != null && fuelValue(current, type) <= 0) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_WAITING_EXTERNAL_CHANGE);
            return true;
        }
        ItemStack template = current == null ? firstFuelTemplate(inventory, type) : current.clone();
        if (template == null || fuelValue(template, type) <= 0) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_NO_FUEL);
            return true;
        }
        int currentAmount = current == null ? 0 : current.getAmount();
        int maxAmount = Math.max(1, template.getMaxStackSize());
        int needed = maxAmount - currentAmount;
        if (needed <= 0) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            return true;
        }
        int moved = 0;
        for (int i = 0; i < inventory.getSize() && needed > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0 || fuelValue(stack, type) <= 0 || !stack.isSimilar(template)) {
                continue;
            }
            int transfer = Math.min(needed, stack.getAmount());
            stack.setAmount(stack.getAmount() - transfer);
            inventory.setItem(i, stack.getAmount() <= 0 ? null : stack);
            currentAmount += transfer;
            needed -= transfer;
            moved += transfer;
        }
        if (moved > 0) {
            ItemStack filled = template.clone();
            filled.setAmount(currentAmount);
            state.fuelSlot(filled);
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        } else {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_NO_FUEL);
        }
        return true;
    }

    private ItemStack firstFuelTemplate(Inventory inventory, SfxAndroidType type) {
        if (inventory == null) {
            return null;
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (fuelValue(stack, type) > 0) {
                ItemStack template = stack.clone();
                template.setAmount(1);
                return template;
            }
        }
        return null;
    }

    private Block findFrontInterface(Block androidBlock, BlockFace face, String interfaceTypeId) {
        if (androidBlock == null || face == null || interfaceTypeId == null) {
            return null;
        }
        Block candidate = androidBlock.getRelative(face);
        SfxBlockInstanceRecord instance = blockData.findAnchor(candidate.getLocation())
                .flatMap(anchor -> blockData.findInstance(anchor.instanceId()))
                .orElse(null);
        if (instance != null && interfaceTypeId.equals(instance.typeId()) && candidate.getState() instanceof Dispenser) {
            return candidate;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        SfxBlockInstanceRecord instance = blockData.findAnchor(block.getLocation()).flatMap(anchor -> blockData.findInstance(anchor.instanceId())).orElse(null);
        if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
            return;
        }
        event.setCancelled(true);
        SfxMachineLegacyHookBridge.interact(machineRuntime, instance.typeId(), instance.instanceId(), block.getLocation(), "android", "SfxAndroidService.onInteract");
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(instance.ownerId()) && !player.hasPermission("sfx.android.bypass")) {
            player.sendMessage(msg("android.messages.no_access"));
            return;
        }
        openMain(player, instance.instanceId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        SfxBlockInstanceRecord instance = blockData.findAnchor(event.getBlock().getLocation()).flatMap(anchor -> blockData.findInstance(anchor.instanceId())).orElse(null);
        if (instance != null && (INTERFACE_FUEL.equals(instance.typeId()) || INTERFACE_ITEMS.equals(instance.typeId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SfxAndroidMenuHolder holder)) {
            return;
        }
        if (!holder.viewerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (SfxMachineMenuTransactions.cancelUnsupportedManagedClick(event)) {
            return;
        }
        SfxBlockInstanceRecord clickInstance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (clickInstance != null) {
            SfxMachineLegacyHookBridge.menuClick(machineRuntime, clickInstance.typeId(), clickInstance.instanceId(), toLocation(clickInstance.anchorKey()), "android", "SfxAndroidService.onMenuClick");
        }
        int raw = event.getRawSlot();
        if (holder.menuType() == SfxAndroidMenuHolder.MenuType.MAIN) {
            handleMainInventoryClick(event, player, holder, top, raw);
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }
        handleMenuButton(player, holder, raw, event.getClick());
    }

    private void handleMainInventoryClick(InventoryClickEvent event, Player player, SfxAndroidMenuHolder holder, Inventory top, int raw) {
        boolean topClick = event.getClickedInventory() != null && event.getClickedInventory().equals(top);
        if (!topClick) {
            if (event.getClick().isShiftClick()) {
                event.setCancelled(true);
                shiftFuelIntoAndroid(event, holder, top);
            }
            return;
        }
        if (raw == FUEL_SLOT) {
            handleFuelSlotClick(event, player, holder, top);
            return;
        }
        if (isOutputSlot(raw)) {
            event.setCancelled(true);
            handleOutputSlotClick(event, player, holder, top, raw);
            return;
        }
        event.setCancelled(true);
        handleMainButton(player, holder, top, raw);
    }

    private void handleFuelSlotClick(InventoryClickEvent event, Player player, SfxAndroidMenuHolder holder, Inventory top) {
        event.setCancelled(true);
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        SfxAndroidType type = instance == null ? null : SfxAndroidType.fromItemId(instance.typeId());
        java.util.function.Predicate<ItemStack> fuelValidator = stack -> type != null && fuelValue(stack, type) > 0;
        boolean changed;
        if (event.isShiftClick()) {
            changed = SfxMachineMenuTransactions.moveTopSlotToPlayer(top, FUEL_SLOT, player);
        } else if (SfxMachineMenuTransactions.handleManagedHotbarOrOffhand(event, top, FUEL_SLOT, player, true, false, fuelValidator)) {
            changed = true;
        } else if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            changed = SfxMachineMenuTransactions.dropFromTopSlot(event, top, FUEL_SLOT, player);
        } else if (SfxMachineMenuTransactions.handleManagedDoubleClick(event, top, player, slot -> slot == FUEL_SLOT)) {
            changed = true;
        } else {
            changed = SfxMachineMenuTransactions.handleInputSlotCursorTransaction(event, top, FUEL_SLOT, fuelValidator);
        }
        if (changed) {
            syncMainInventoryLater(player, holder.instanceId(), top);
        }
    }

    private void shiftFuelIntoAndroid(InventoryClickEvent event, SfxAndroidMenuHolder holder, Inventory top) {
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        SfxAndroidType type = instance == null ? null : SfxAndroidType.fromItemId(instance.typeId());
        ItemStack source = event.getCurrentItem();
        if (type == null || fuelValue(source, type) <= 0) {
            return;
        }
        ItemStack target = top.getItem(FUEL_SLOT);
        if (target != null && !target.getType().isAir() && !target.isSimilar(source)) {
            return;
        }
        int max = Math.min(source.getMaxStackSize(), top.getMaxStackSize());
        int current = target == null || target.getType().isAir() ? 0 : target.getAmount();
        int movable = Math.min(source.getAmount(), max - current);
        if (movable <= 0) {
            return;
        }
        if (target == null || target.getType().isAir()) {
            ItemStack moved = source.clone();
            moved.setAmount(movable);
            top.setItem(FUEL_SLOT, moved);
        } else {
            target.setAmount(current + movable);
            top.setItem(FUEL_SLOT, target);
        }
        source.setAmount(source.getAmount() - movable);
        event.setCurrentItem(source.getAmount() <= 0 ? null : source);
        syncMainInventoryLater((Player) event.getWhoClicked(), holder.instanceId(), top);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SfxAndroidMenuHolder holder) {
            if (holder.menuType() != SfxAndroidMenuHolder.MenuType.MAIN) {
                event.setCancelled(true);
                return;
            }
            int topSize = top.getSize();
            boolean illegal = event.getRawSlots().stream().anyMatch(slot -> slot < topSize && slot != FUEL_SLOT);
            if (illegal) {
                event.setCancelled(true);
                return;
            }
            if (event.getRawSlots().contains(FUEL_SLOT) && event.getWhoClicked() instanceof Player player) {
                syncMainInventoryLater(player, holder.instanceId(), top);
            }
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SfxAndroidMenuHolder holder) || holder.menuType() != SfxAndroidMenuHolder.MenuType.MAIN) {
            return;
        }
        removeMainViewer(holder.instanceId(), player.getUniqueId());
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance != null) {
            SfxMachineLegacyHookBridge.menuClose(machineRuntime, instance.typeId(), instance.instanceId(), toLocation(instance.anchorKey()), "android", "SfxAndroidService.onMenuClose");
        }
        if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
            return;
        }
        if (!consumeFuelSlotDirty(holder.instanceId(), player.getUniqueId())) {
            return;
        }
        SfxAndroidState state = syncFuelSlotToState(instance, inventory);
        if (!state.paused() && state.runtimeState() == SfxAndroidRuntimeState.DORMANT_NO_FUEL && fuelValue(state.fuelSlot(), SfxAndroidType.fromItemId(instance.typeId())) > 0) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            activeAndroids.add(instance.instanceId());
        }
        persist(instance.instanceId(), state, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(AsyncChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        ImportSession importSession = pendingImports.remove(playerId);
        UploadSession uploadSession = pendingUploads.remove(playerId);
        EditScriptSession editSession = pendingScriptEdits.remove(playerId);
        if (importSession == null && uploadSession == null && editSession == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        runtime.executeForPlayer(event.getPlayer(), () -> {
            if (importSession != null) {
                handleImportChat(event.getPlayer(), importSession, message);
            } else if (uploadSession != null) {
                handleUploadNameChat(event.getPlayer(), uploadSession, message);
            } else if (editSession != null) {
                handleEditScriptChat(event.getPlayer(), editSession, message);
            }
        });
    }

    private void handleImportChat(Player player, ImportSession session, String message) {
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(msg("android.messages.import_cancelled"));
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(session.instanceId()).orElse(null);
        if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
            player.sendMessage(msg("android.messages.missing_android"));
            return;
        }
        SfxAndroidType currentType = SfxAndroidType.fromItemId(instance.typeId());
        try {
            List<SfxAndroidInstruction> body;
            if (message.toUpperCase(Locale.ROOT).startsWith(SfxAndroidScriptCodec.PREFIX) || looksLikeShortCode(message)) {
                SfxAndroidScriptCodec.DecodedScript decoded = SfxAndroidScriptCodec.importCode(message);
                if (decoded.type().function() != currentType.function() && decoded.type() != currentType) {
                    throw new IllegalArgumentException("Script code is for " + decoded.type().key() + ", not " + currentType.key());
                }
                body = SfxAndroidScriptCodec.canonicalize(currentType, decoded.body());
            } else {
                body = SfxAndroidScriptCodec.parseReadableScript(currentType, message);
            }
            SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
            state.setBody(body);
            state.index(0);
            state.runtimeState(state.paused() ? SfxAndroidRuntimeState.PAUSED : SfxAndroidRuntimeState.ACTIVE);
            if (!state.paused()) {
                activeAndroids.add(instance.instanceId());
            }
            persist(instance.instanceId(), state);
            player.sendMessage(msg("android.messages.imported"));
            openScript(player, instance.instanceId(), 0);
        } catch (RuntimeException exception) {
            player.sendMessage(msg("android.messages.invalid_script", Map.of("reason", exception.getMessage())));
        }
    }

    private boolean looksLikeShortCode(String input) {
        return input != null && input.matches("[0-9A-Za-z]{5,64}");
    }

    private void openMain(Player player, UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxMachineLegacyHookBridge.menuOpen(machineRuntime, instance.typeId(), instance.instanceId(), toLocation(instance.anchorKey()), "android", "SfxAndroidService.openMain");
        SfxAndroidState state = stateFor(instanceId, instance.typeId(), toLocation(instance.anchorKey()));
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.MAIN, 0, -1, false), 54, tr("android.menu.main.title"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fillClassicFrame(inventory);
        inventory.setItem(15, headIcon(HEAD_SCRIPT_START, "android.menu.main.start"));
        inventory.setItem(16, headIcon(HEAD_MEMORY_CORE, "android.menu.main.memory_core"));
        inventory.setItem(17, headIcon(HEAD_SCRIPT_PAUSE, "android.menu.main.pause"));
        inventory.setItem(34, fuelInfoIcon(state));
        ItemStack[] outputs = state.outputs();
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            inventory.setItem(OUTPUT_SLOTS[i], outputs[i]);
        }
        inventory.setItem(FUEL_SLOT, state.fuelSlot());
        clearFuelSlotDirty(instanceId, player.getUniqueId());
        player.openInventory(inventory);
        mainViewers.computeIfAbsent(instanceId, ignored -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
        scheduleMainViewerRefresh(player, instanceId);
    }

    private void scheduleMainViewerRefresh(Player player, UUID instanceId) {
        if (player == null || instanceId == null) {
            return;
        }
        runtime.executeForPlayerLater(player, 1L, () -> {
            if (!running || !player.isOnline()) {
                return;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof SfxAndroidMenuHolder holder)
                    || holder.menuType() != SfxAndroidMenuHolder.MenuType.MAIN
                    || !holder.instanceId().equals(instanceId)
                    || !holder.viewerId().equals(player.getUniqueId())) {
                removeMainViewer(instanceId, player.getUniqueId());
                clearFuelSlotDirty(instanceId, player.getUniqueId());
                return;
            }
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
                removeMainViewer(instanceId, player.getUniqueId());
                clearFuelSlotDirty(instanceId, player.getUniqueId());
                player.closeInventory();
                return;
            }
            SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
            refreshMainStatus(top, state, !isFuelSlotDirty(instanceId, player.getUniqueId()));
            player.updateInventory();
            scheduleMainViewerRefresh(player, instanceId);
        });
    }

    private void removeMainViewer(UUID instanceId, UUID viewerId) {
        Set<UUID> viewers = mainViewers.get(instanceId);
        if (viewers == null) {
            return;
        }
        viewers.remove(viewerId);
        if (viewers.isEmpty()) {
            mainViewers.remove(instanceId);
        }
    }

    private SfxAndroidMenuHolder newHolder(Player player, UUID instanceId, SfxAndroidMenuHolder.MenuType type, int page, int editIndex, boolean adding) {
        return new SfxAndroidMenuHolder(player.getUniqueId(), instanceId, type, page, editIndex, adding);
    }

    private SfxAndroidState syncFuelSlotToState(SfxBlockInstanceRecord instance, Inventory inventory) {
        SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
        state.fuelSlot(inventory.getItem(FUEL_SLOT));
        return state;
    }

    private void handleOutputSlotClick(InventoryClickEvent event, Player player, SfxAndroidMenuHolder holder, Inventory top, int raw) {
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
            return;
        }
        int outputIndex = indexOf(OUTPUT_SLOTS, raw);
        if (outputIndex < 0) {
            return;
        }
        SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
        ItemStack stored = state.outputs()[outputIndex];
        if (stored == null || stored.getType().isAir() || stored.getAmount() <= 0) {
            refreshMainStatus(top, state, !isFuelSlotDirty(holder.instanceId(), player.getUniqueId()));
            return;
        }
        int moved;
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            int amount = event.getClick() == ClickType.DROP ? 1 : stored.getAmount();
            moved = dropStackFromOutput(player, stored, amount);
        } else if (event.getClick().isShiftClick()) {
            moved = moveStackToPlayerInventory(player, stored);
        } else if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            moved = moveStackToCarrier(event, player, stored);
        } else {
            moved = moveStackToCursor(event, stored);
        }
        if (moved <= 0) {
            return;
        }
        ItemStack remaining = stored.clone();
        remaining.setAmount(stored.getAmount() - moved);
        state.output(outputIndex, remaining.getAmount() <= 0 ? null : remaining);
        if (!state.paused() && state.runtimeState() == SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL && state.hasFreeOutputSpace()) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            activeAndroids.add(instance.instanceId());
        }
        persist(instance.instanceId(), state, true);
        refreshMainStatus(top, state, !isFuelSlotDirty(holder.instanceId(), player.getUniqueId()));
        player.updateInventory();
    }

    private int moveStackToCarrier(InventoryClickEvent event, Player player, ItemStack stored) {
        if (stored == null || stored.getType().isAir()) {
            return 0;
        }
        ItemStack carrier = event.getClick() == ClickType.SWAP_OFFHAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItem(event.getHotbarButton());
        if (carrier != null && !carrier.getType().isAir()) {
            return 0;
        }
        ItemStack moved = stored.clone();
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            player.getInventory().setItemInOffHand(moved);
        } else if (event.getHotbarButton() >= 0) {
            player.getInventory().setItem(event.getHotbarButton(), moved);
        } else {
            return 0;
        }
        return moved.getAmount();
    }

    private int moveStackToPlayerInventory(Player player, ItemStack stored) {
        ItemStack moving = stored.clone();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(moving);
        int leftoverAmount = 0;
        for (ItemStack leftover : leftovers.values()) {
            if (leftover != null) {
                leftoverAmount += leftover.getAmount();
            }
        }
        return stored.getAmount() - leftoverAmount;
    }

    private int dropStackFromOutput(Player player, ItemStack stored, int requestedAmount) {
        if (player == null || stored == null || stored.getType().isAir() || requestedAmount <= 0) {
            return 0;
        }
        int amount = Math.min(requestedAmount, stored.getAmount());
        if (amount <= 0) {
            return 0;
        }
        ItemStack dropped = stored.clone();
        dropped.setAmount(amount);
        Vector direction = player.getLocation().getDirection().normalize();
        Location dropLocation = player.getEyeLocation().add(direction.clone().multiply(0.35D));
        Item entity = player.getWorld().dropItem(dropLocation, dropped);
        entity.setVelocity(direction.multiply(0.35D).add(new Vector(0.0D, 0.1D, 0.0D)));
        return amount;
    }

    private int moveStackToCursor(InventoryClickEvent event, ItemStack stored) {
        ItemStack cursor = event.getCursor();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir() || cursor.getAmount() <= 0;
        if (cursorEmpty) {
            int amount = event.getClick() == ClickType.RIGHT ? Math.max(1, (stored.getAmount() + 1) / 2) : stored.getAmount();
            ItemStack moved = stored.clone();
            moved.setAmount(amount);
            event.setCursor(moved);
            return amount;
        }
        if (!cursor.isSimilar(stored) || cursor.getAmount() >= cursor.getMaxStackSize()) {
            return 0;
        }
        int amount = event.getClick() == ClickType.RIGHT ? 1 : Math.min(stored.getAmount(), cursor.getMaxStackSize() - cursor.getAmount());
        amount = Math.min(amount, stored.getAmount());
        if (amount <= 0) {
            return 0;
        }
        ItemStack updated = cursor.clone();
        updated.setAmount(cursor.getAmount() + amount);
        event.setCursor(updated);
        return amount;
    }

    private void syncMainInventoryLater(Player player, UUID instanceId, Inventory inventory) {
        markFuelSlotDirty(instanceId, player.getUniqueId());
        runtime.executeForPlayerLater(player, 1L, () -> {
            if (!isFuelSlotDirty(instanceId, player.getUniqueId())) {
                return;
            }
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
                clearFuelSlotDirty(instanceId, player.getUniqueId());
                return;
            }
            SfxAndroidState state = syncFuelSlotToState(instance, inventory);
            clearFuelSlotDirty(instanceId, player.getUniqueId());
            if (!state.paused() && state.runtimeState() == SfxAndroidRuntimeState.DORMANT_NO_FUEL && fuelValue(state.fuelSlot(), SfxAndroidType.fromItemId(instance.typeId())) > 0) {
                state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
                activeAndroids.add(instance.instanceId());
            }
            persist(instance.instanceId(), state, false);
        });
    }

    private void refreshMainFuelInfo(Inventory inventory, SfxAndroidState state, UUID instanceId, UUID viewerId) {
        if (inventory == null || state == null || inventory.getHolder() == null) {
            return;
        }
        inventory.setItem(34, fuelInfoIcon(state));
        if (!isFuelSlotDirty(instanceId, viewerId)) {
            inventory.setItem(FUEL_SLOT, state.fuelSlot());
        }
    }

    private void refreshMainStatus(Inventory inventory, SfxAndroidState state, boolean refreshFuelSlot) {
        if (inventory == null || state == null || inventory.getHolder() == null) {
            return;
        }
        inventory.setItem(34, fuelInfoIcon(state));
        if (refreshFuelSlot) {
            inventory.setItem(FUEL_SLOT, state.fuelSlot());
        }
        ItemStack[] outputs = state.outputs();
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            inventory.setItem(OUTPUT_SLOTS[i], outputs[i]);
        }
    }

    private void refreshMainStatus(Inventory inventory, SfxAndroidState state) {
        refreshMainStatus(inventory, state, true);
    }

    private void markFuelSlotDirty(UUID instanceId, UUID viewerId) {
        if (instanceId == null || viewerId == null) {
            return;
        }
        fuelSlotDirtyViewers.computeIfAbsent(instanceId, ignored -> ConcurrentHashMap.newKeySet()).add(viewerId);
    }

    private boolean isFuelSlotDirty(UUID instanceId, UUID viewerId) {
        Set<UUID> viewers = fuelSlotDirtyViewers.get(instanceId);
        return viewers != null && viewers.contains(viewerId);
    }

    private boolean consumeFuelSlotDirty(UUID instanceId, UUID viewerId) {
        Set<UUID> viewers = fuelSlotDirtyViewers.get(instanceId);
        if (viewers == null) {
            return false;
        }
        boolean removed = viewers.remove(viewerId);
        if (viewers.isEmpty()) {
            fuelSlotDirtyViewers.remove(instanceId);
        }
        return removed;
    }

    private void clearFuelSlotDirty(UUID instanceId, UUID viewerId) {
        consumeFuelSlotDirty(instanceId, viewerId);
    }

    private void handleMainButton(Player player, SfxAndroidMenuHolder holder, Inventory top, int slot) {
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxAndroidState state = consumeFuelSlotDirty(holder.instanceId(), player.getUniqueId())
                ? syncFuelSlotToState(instance, top)
                : stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
        if (slot == 15) {
            boolean kickstart = state.paused()
                    || state.runtimeState() != SfxAndroidRuntimeState.ACTIVE
                    || shouldSkipForBackoff(state, androidTick.get() + 1L);
            state.paused(false);
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            state.resetNoEffectTicks();
            state.sleepingUntilTick(0L);
            activeAndroids.add(instance.instanceId());
            persist(instance.instanceId(), state);
            refreshMainStatus(top, state);
            if (kickstart) {
                runAndroidImmediately(instance.instanceId());
            }
            player.sendMessage(msg("android.messages.started"));
        } else if (slot == 17) {
            state.paused(true);
            state.runtimeState(SfxAndroidRuntimeState.PAUSED);
            activeAndroids.remove(instance.instanceId());
            persist(instance.instanceId(), state);
            refreshMainStatus(top, state);
            player.sendMessage(msg("android.messages.paused"));
        } else if (slot == 16) {
            state.paused(true);
            state.runtimeState(SfxAndroidRuntimeState.PAUSED);
            activeAndroids.remove(instance.instanceId());
            persist(instance.instanceId(), state);
            refreshMainStatus(top, state);
            openEditor(player, instance.instanceId());
        }
    }

    private void runAndroidImmediately(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
            return;
        }
        Location location = toLocation(instance.anchorKey());
        if (location == null) {
            return;
        }
        runtime.executeAt(location, () -> {
            SfxBlockInstanceRecord current = blockData.findInstance(instanceId).orElse(null);
            if (current == null || !SfxAndroidType.isAndroidItem(current.typeId())) {
                activeAndroids.remove(instanceId);
                states.remove(instanceId);
                return;
            }
            Location currentLocation = toLocation(current.anchorKey());
            if (currentLocation == null) {
                return;
            }
            SfxAndroidState state = stateFor(current.instanceId(), current.typeId(), currentLocation);
            if (state.paused() || state.runtimeState() == SfxAndroidRuntimeState.PAUSED) {
                return;
            }
            state.resetNoEffectTicks();
            state.sleepingUntilTick(0L);
            tickRegionBatch(List.of(current), androidTick.incrementAndGet());
        });
    }
    private void pauseAndroidForScriptEditing(UUID instanceId) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
            return;
        }
        SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
        if (!state.paused() || state.runtimeState() != SfxAndroidRuntimeState.PAUSED) {
            state.paused(true);
            state.runtimeState(SfxAndroidRuntimeState.PAUSED);
            activeAndroids.remove(instance.instanceId());
            persist(instance.instanceId(), state);
        }
    }


    private void openEditor(Player player, UUID instanceId) {
        pauseAndroidForScriptEditing(instanceId);
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.EDITOR, 0, -1, false), 27, tr("android.menu.editor.title"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(10, headIcon(HEAD_SCRIPT_NEW, "android.menu.editor.edit"));
        inventory.setItem(11, icon(Material.MAP, "android.menu.editor.export"));
        inventory.setItem(12, icon(Material.PAPER, "android.menu.editor.import"));
        inventory.setItem(14, icon(Material.ENDER_CHEST, "android.menu.editor.download"));
        inventory.setItem(15, icon(Material.HOPPER, "android.menu.editor.upload"));
        inventory.setItem(16, icon(Material.BARRIER, "android.menu.editor.back"));
        player.openInventory(inventory);
    }

    private void handleMenuButton(Player player, SfxAndroidMenuHolder holder, int slot, ClickType click) {
        switch (holder.menuType()) {
            case EDITOR -> handleEditorButton(player, holder, slot);
            case SCRIPT -> handleScriptButton(player, holder, slot, click);
            case INSTRUCTIONS -> handleInstructionButton(player, holder, slot);
            case DOWNLOADER -> handleDownloaderButton(player, holder, slot, click);
            case UPLOAD_VISIBILITY -> handleUploadVisibility(player, holder, slot);
            default -> {
            }
        }
    }

    private void handleEditorButton(Player player, SfxAndroidMenuHolder holder, int slot) {
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        if (slot == 10) {
            openScript(player, instance.instanceId(), 0);
        } else if (slot == 11) {
            exportScript(player, instance);
        } else if (slot == 12) {
            pendingImports.put(player.getUniqueId(), new ImportSession(instance.instanceId()));
            player.closeInventory();
            player.sendMessage(msg("android.messages.import_prompt"));
        } else if (slot == 14) {
            openDownloader(player, instance.instanceId(), 0);
        } else if (slot == 15) {
            openUploadVisibility(player, instance.instanceId());
        } else if (slot == 16) {
            openMain(player, instance.instanceId());
        }
    }

    private void exportScript(Player player, SfxBlockInstanceRecord instance) {
        SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
        SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
        String code = SfxAndroidScriptCodec.exportCode(type, state.body(), true);
        Component clickable = tr("android.messages.copy_script").clickEvent(ClickEvent.copyToClipboard(code));
        player.sendMessage(msg("android.messages.export").append(clickable));
        player.sendMessage(Component.text(code, NamedTextColor.GRAY));
    }

    private void openUploadVisibility(Player player, UUID instanceId) {
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.UPLOAD_VISIBILITY, 0, -1, false), 27, tr("android.menu.upload.title"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(11, icon(Material.LIME_DYE, "android.menu.upload.public"));
        inventory.setItem(15, icon(Material.IRON_DOOR, "android.menu.upload.private"));
        inventory.setItem(22, icon(Material.BARRIER, "android.menu.common.back"));
        player.openInventory(inventory);
    }

    private void handleUploadVisibility(Player player, SfxAndroidMenuHolder holder, int slot) {
        SfxAndroidScriptVisibility visibility = switch (slot) {
            case 11 -> SfxAndroidScriptVisibility.PUBLIC;
            case 15 -> SfxAndroidScriptVisibility.PRIVATE;
            default -> null;
        };
        if (visibility == null) {
            if (slot == 22) {
                openEditor(player, holder.instanceId());
            }
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
        SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
        pendingUploads.put(player.getUniqueId(), new UploadSession(instance.instanceId(), type, List.copyOf(state.body()), visibility));
        player.closeInventory();
        player.sendMessage(msg("android.messages.upload_name_prompt"));
    }

    private void openScript(Player player, UUID instanceId, int page) {
        pauseAndroidForScriptEditing(instanceId);
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxAndroidState state = stateFor(instanceId, instance.typeId(), toLocation(instance.anchorKey()));
        List<SfxAndroidInstruction> body = state.body();
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.SCRIPT, 0, -1, false), 54, tr("android.menu.script.title"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(0, headIcon(HEAD_SCRIPT_START, "android.menu.script.start"));
        for (int i = 0; i < Math.min(body.size(), SCRIPT_SLOTS.length); i++) {
            SfxAndroidInstruction instruction = body.get(i);
            inventory.setItem(SCRIPT_SLOTS[i], instructionIcon(instruction, i + 1, true));
        }
        if (body.size() < SCRIPT_SLOTS.length) {
            inventory.setItem(SCRIPT_SLOTS[body.size()], headIcon(HEAD_SCRIPT_NEW, "android.menu.script.add"));
        }
        inventory.setItem(53, headIcon(HEAD_SCRIPT_REPEAT, "android.menu.script.repeat"));
        player.openInventory(inventory);
    }

    private void handleScriptButton(Player player, SfxAndroidMenuHolder holder, int slot, ClickType click) {
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        int slotIndex = indexOf(SCRIPT_SLOTS, slot);
        if (slotIndex < 0) {
            return;
        }
        SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
        List<SfxAndroidInstruction> body = new ArrayList<>(state.body());
        if (slotIndex >= body.size()) {
            openInstructions(player, instance.instanceId(), body.size(), true);
            return;
        }
        if (click.isRightClick()) {
            if (click.isShiftClick()) {
                if (body.size() < SfxAndroidState.MAX_BODY_LENGTH) {
                    body.add(slotIndex + 1, body.get(slotIndex));
                }
            } else if (body.size() > 1) {
                body.remove(slotIndex);
            }
            state.setBody(body);
            state.index(0);
            persist(instance.instanceId(), state);
            openScript(player, instance.instanceId(), 0);
        } else {
            openInstructions(player, instance.instanceId(), slotIndex, false);
        }
    }

    private void openInstructions(Player player, UUID instanceId, int editIndex, boolean adding) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.INSTRUCTIONS, 0, editIndex, adding), 54, tr("android.menu.instructions.title"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        List<SfxAndroidInstruction> instructions = SfxAndroidInstruction.validForType(type);
        for (int i = 0; i < Math.min(45, instructions.size()); i++) {
            SfxAndroidInstruction instruction = instructions.get(i);
            inventory.setItem(i, instructionIcon(instruction, -1, false));
        }
        inventory.setItem(53, icon(Material.BARRIER, "android.menu.common.back"));
        player.openInventory(inventory);
    }

    private void handleInstructionButton(Player player, SfxAndroidMenuHolder holder, int slot) {
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        if (slot == 53) {
            openScript(player, instance.instanceId(), 0);
            return;
        }
        SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
        List<SfxAndroidInstruction> instructions = SfxAndroidInstruction.validForType(type);
        if (slot < 0 || slot >= instructions.size()) {
            return;
        }
        SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
        List<SfxAndroidInstruction> body = new ArrayList<>(state.body());
        if (holder.adding()) {
            if (body.size() < SfxAndroidState.MAX_BODY_LENGTH) {
                body.add(instructions.get(slot));
            }
        } else if (holder.editIndex() >= 0 && holder.editIndex() < body.size()) {
            body.set(holder.editIndex(), instructions.get(slot));
        }
        state.setBody(body);
        state.index(0);
        persist(instance.instanceId(), state);
        openScript(player, instance.instanceId(), 0);
    }

    private void openDownloader(Player player, UUID instanceId, int page) {
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.DOWNLOADER, page, -1, false), 54, tr("android.menu.downloader.title"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        try {
            List<SfxAndroidScriptRecord> records = scripts.listVisible(type, player.getUniqueId(), page * 45, 45);
            for (int i = 0; i < records.size(); i++) {
                SfxAndroidScriptRecord record = records.get(i);
                inventory.setItem(i, downloaderEntryIcon(record, player));
            }
        } catch (SQLException exception) {
            inventory.setItem(22, icon(Material.BARRIER, "android.menu.downloader.error", Map.of("reason", exception.getMessage())));
        }
        inventory.setItem(45, icon(Material.ARROW, "android.menu.common.previous"));
        inventory.setItem(49, icon(Material.BARRIER, "android.menu.common.back"));
        inventory.setItem(53, icon(Material.ARROW, "android.menu.common.next"));
        player.openInventory(inventory);
    }

    private void handleDownloaderButton(Player player, SfxAndroidMenuHolder holder, int slot, ClickType click) {
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        if (slot == 49) {
            openEditor(player, instance.instanceId());
            return;
        }
        if (slot == 45 && holder.page() > 0) {
            openDownloader(player, instance.instanceId(), holder.page() - 1);
            return;
        }
        if (slot == 53) {
            openDownloader(player, instance.instanceId(), holder.page() + 1);
            return;
        }
        if (slot < 0 || slot >= 45) {
            return;
        }
        SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
        try {
            List<SfxAndroidScriptRecord> records = scripts.listVisible(type, player.getUniqueId(), holder.page() * 45, 45);
            if (slot >= records.size()) {
                return;
            }
            SfxAndroidScriptRecord record = records.get(slot);
            if (click.isShiftClick() && click.isLeftClick()) {
                scripts.vote(record.id(), player.getUniqueId(), 1);
                openDownloader(player, instance.instanceId(), holder.page());
                return;
            }
            if (click.isShiftClick() && click.isRightClick()) {
                scripts.vote(record.id(), player.getUniqueId(), -1);
                openDownloader(player, instance.instanceId(), holder.page());
                return;
            }
            if (click.isRightClick()) {
                boolean force = player.hasPermission("sfx.android.script.edit.any") || player.hasPermission("sfx.android.script.delete.any");
                if (record.authorId().equals(player.getUniqueId()) || force) {
                    pendingScriptEdits.put(player.getUniqueId(), new EditScriptSession(instance.instanceId(), holder.page(), record.id(), force));
                    player.closeInventory();
                    player.sendMessage(msg("android.messages.edit_script_prompt"));
                }
                return;
            }
            SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
            state.setBody(SfxAndroidScriptCodec.canonicalize(type, record.body()));
            state.index(0);
            persist(instance.instanceId(), state);
            scripts.incrementDownloads(record.id());
            player.sendMessage(msg("android.messages.downloaded", Map.of("id", record.id())));
            openScript(player, instance.instanceId(), 0);
        } catch (SQLException exception) {
            player.sendMessage(msg("android.messages.script_library_error", Map.of("reason", exception.getMessage())));
        }
    }


    private void handleUploadNameChat(Player player, UploadSession session, String message) {
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(msg("android.messages.upload_cancelled"));
            openEditor(player, session.instanceId());
            return;
        }
        String name = sanitizeScriptName(message);
        if (name == null) {
            pendingUploads.put(player.getUniqueId(), session);
            player.sendMessage(msg("android.messages.invalid_script_name"));
            return;
        }
        try {
            long id = scripts.upload(session.type(), player.getUniqueId(), player.getName(), name, session.body(), session.visibility());
            player.sendMessage(msg("android.messages.uploaded", Map.of("id", id)));
        } catch (SQLException exception) {
            player.sendMessage(msg("android.messages.upload_failed", Map.of("reason", exception.getMessage())));
        }
        openEditor(player, session.instanceId());
    }

    private void handleEditScriptChat(Player player, EditScriptSession session, String message) {
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(msg("android.messages.edit_cancelled"));
            openDownloader(player, session.instanceId(), session.page());
            return;
        }
        try {
            if (message.equalsIgnoreCase("delete")) {
                boolean deleted = scripts.softDelete(session.scriptId(), player.getUniqueId(), session.force());
                if (deleted) {
                    player.sendMessage(msg("android.messages.deleted", Map.of("id", session.scriptId())));
                } else {
                    player.sendMessage(msg("android.messages.edit_denied"));
                }
                openDownloader(player, session.instanceId(), session.page());
                return;
            }
            String name = sanitizeScriptName(message);
            if (name == null) {
                pendingScriptEdits.put(player.getUniqueId(), session);
                player.sendMessage(msg("android.messages.invalid_script_name"));
                return;
            }
            boolean renamed = scripts.rename(session.scriptId(), player.getUniqueId(), session.force(), name);
            if (renamed) {
                player.sendMessage(msg("android.messages.renamed", Map.of("id", session.scriptId())));
            } else {
                player.sendMessage(msg("android.messages.edit_denied"));
            }
        } catch (SQLException exception) {
            player.sendMessage(msg("android.messages.script_library_error", Map.of("reason", exception.getMessage())));
        }
        openDownloader(player, session.instanceId(), session.page());
    }

    private String sanitizeScriptName(String input) {
        if (input == null) {
            return null;
        }
        String name = input.strip().replace('\n', ' ').replace('\r', ' ');
        if (name.isBlank() || name.length() > 32) {
            return null;
        }
        return name;
    }

    private ItemStack downloaderEntryIcon(SfxAndroidScriptRecord record, Player viewer) {
        List<Component> lore = new ArrayList<>();
        lore.add(Text.renderFlexible(localization.text("android.menu.downloader.entry.author", Map.of("author", record.authorName()))));
        lore.add(Text.renderFlexible(localization.text("android.menu.downloader.entry.length", Map.of("length", record.body().size() + 2))));
        lore.add(Text.renderFlexible(localization.text("android.menu.downloader.entry.visibility", Map.of("visibility", record.visibility().name()))));
        lore.add(Text.renderFlexible(localization.text("android.menu.downloader.entry.downloads", Map.of("downloads", record.downloads(), "positive", record.positiveVotes(), "negative", record.negativeVotes()))));
        lore.add(tr("android.menu.downloader.entry.actions"));
        if (record.authorId().equals(viewer.getUniqueId()) || viewer.hasPermission("sfx.android.script.edit.any") || viewer.hasPermission("sfx.android.script.delete.any")) {
            lore.add(tr("android.menu.downloader.entry.modify"));
        }
        ItemStack item = ItemBuilder.of(Material.BOOK).name("<green>#" + record.id() + " " + record.name() + "</green>").build();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillClassicFrame(Inventory inventory) {
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        int[] border = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 18, 24, 25, 26, 27, 33, 35, 36, 42, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int slot : border) {
            inventory.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE));
        }
        int[] outputBorder = {10, 11, 12, 13, 14, 19, 23, 28, 32, 37, 38, 39, 40, 41};
        for (int slot : outputBorder) {
            inventory.setItem(slot, pane(Material.ORANGE_STAINED_GLASS_PANE));
        }
        for (int slot : OUTPUT_SLOTS) {
            inventory.setItem(slot, null);
        }
        inventory.setItem(FUEL_SLOT, null);
    }

    private void fill(Inventory inventory, Material material) {
        ItemStack pane = pane(material);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
    }

    private ItemStack pane(Material material) {
        return ItemBuilder.of(material).name(" ").build();
    }

    private Component tr(String path) {
        return localization.component(path);
    }

    private Component msg(String path) {
        return Text.prefixed(plugin, localization.text(path));
    }

    private Component msg(String path, Map<String, ?> placeholders) {
        return Text.prefixed(plugin, localization.text(path, placeholders));
    }

    private ItemStack icon(Material material, String key) {
        return localizedItem(material, null, key, Map.of());
    }

    private ItemStack icon(Material material, String key, Map<String, ?> placeholders) {
        return localizedItem(material, null, key, placeholders);
    }

    private ItemStack headIcon(String texture, String key) {
        return localizedItem(Material.PLAYER_HEAD, texture, key, Map.of());
    }

    private ItemStack instructionIcon(SfxAndroidInstruction instruction, int displayIndex, boolean editableLore) {
        String key = "android.instructions." + instruction.name().toLowerCase(Locale.ROOT);
        List<Component> lore = localizedLore(key + ".lore", Map.of());
        if (editableLore) {
            lore = new ArrayList<>(lore);
            lore.add(tr("android.menu.script.edit_hint"));
        }
        ItemStack item = localizedItem(Material.PLAYER_HEAD, instruction.texture(), key, Map.of());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayIndex > 0) {
                meta.displayName(Component.text(displayIndex + ". ", NamedTextColor.YELLOW).append(tr(key + ".name")));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack fuelInfoIcon(SfxAndroidState state) {
        String fuelTime = formatFuelTime(state.fuelTicks());
        ItemStack item = localizedItem(Material.COAL, null, "android.menu.main.fuel_info", Map.of());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(localizedLore("android.menu.main.fuel_info.lore", Map.of("fuel", fuelTime, "state", state.runtimeState().name())));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatFuelTime(int fuelTicks) {
        if (fuelTicks <= 0) {
            return "0s";
        }
        int seconds = fuelTicks;
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }

    private ItemStack localizedItem(Material material, String texture, String key, Map<String, ?> placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (texture != null) {
                HeadTextures.apply(meta, texture);
            }
            meta.displayName(localization.component(key + ".name", placeholders));
            List<Component> lore = localizedLore(key + ".lore", placeholders);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Component> localizedLore(String path, Map<String, ?> placeholders) {
        List<String> values = localization.list(path);
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Component> components = new ArrayList<>();
        for (String line : values) {
            String rendered = line;
            for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                rendered = rendered.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
            components.add(Text.renderFlexible(rendered));
        }
        return components;
    }

    private boolean isOutputSlot(int raw) {
        return indexOf(OUTPUT_SLOTS, raw) >= 0;
    }

    private int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    void persist(UUID instanceId, SfxAndroidState state) {
        persist(instanceId, state, true);
    }

    void persist(UUID instanceId, SfxAndroidState state, boolean fullRefresh) {
        if (instanceId == null || state == null) {
            return;
        }
        blockData.updateInstanceState(instanceId, state.encode(), state.runtimeState() == SfxAndroidRuntimeState.ACTIVE ? SfxBlockLifecycleState.ACTIVE : SfxBlockLifecycleState.IDLE);
        refreshOpenMainInventory(instanceId, state, fullRefresh);
    }

    private void refreshOpenMainInventory(UUID instanceId, SfxAndroidState state, boolean fullRefresh) {
        Set<UUID> viewers = mainViewers.get(instanceId);
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        for (UUID viewerId : List.copyOf(viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                viewers.remove(viewerId);
                continue;
            }
            runtime.executeForPlayer(viewer, () -> {
                Inventory top = viewer.getOpenInventory().getTopInventory();
                if (!(top.getHolder() instanceof SfxAndroidMenuHolder holder)
                        || holder.menuType() != SfxAndroidMenuHolder.MenuType.MAIN
                        || !holder.instanceId().equals(instanceId)
                        || !holder.viewerId().equals(viewerId)) {
                    removeMainViewer(instanceId, viewerId);
                    clearFuelSlotDirty(instanceId, viewerId);
                    return;
                }
                boolean fuelDirty = isFuelSlotDirty(instanceId, viewerId);
                if (fullRefresh) {
                    refreshMainStatus(top, state, !fuelDirty);
                } else {
                    refreshMainFuelInfo(top, state, instanceId, viewerId);
                }
                viewer.updateInventory();
            });
        }
    }

    private void flushAllStates() {
        for (Map.Entry<UUID, SfxAndroidState> entry : states.entrySet()) {
            persist(entry.getKey(), entry.getValue());
        }
    }

    SfxAndroidState stateFor(UUID instanceId, String typeId, Location location) {
        return states.computeIfAbsent(instanceId, id -> {
            SfxBlockInstanceRecord instance = blockData.findInstance(id).orElse(null);
            return SfxAndroidState.decode(instance == null ? new byte[0] : instance.stateBlob(), BlockFace.NORTH);
        });
    }

    Location toLocation(cc.theends6.sfx.api.block.SfxBlockAnchorKey key) {
        if (key == null) {
            return null;
        }
        World world = Bukkit.getWorld(key.worldId());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }


}
