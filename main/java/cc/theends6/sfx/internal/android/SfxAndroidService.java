package cc.theends6.sfx.internal.android;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.Text;
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
import org.bukkit.block.Dispenser;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Animals;
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
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxItemRegistry itemRegistry;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SqliteSfxAndroidScriptRepository scripts;
    private final Map<UUID, SfxAndroidState> states = new ConcurrentHashMap<>();
    private final Set<UUID> activeAndroids = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ImportSession> pendingImports = new ConcurrentHashMap<>();
    private final Map<UUID, UploadSession> pendingUploads = new ConcurrentHashMap<>();
    private final Map<UUID, EditScriptSession> pendingScriptEdits = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> mainViewers = new ConcurrentHashMap<>();
    private final AtomicLong androidTick = new AtomicLong();
    private volatile boolean running;
    private long tickInterval;
    private int maxActivePerRegion;

    public SfxAndroidService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxItemRegistry itemRegistry, SfxLocalization localization, SfxBlockDataService blockData, SqliteSfxAndroidScriptRepository scripts) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.itemRegistry = Objects.requireNonNull(itemRegistry, "itemRegistry");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
    }

    public void start() {
        try {
            scripts.initialize();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize Android script database", exception);
        }
        tickInterval = Math.max(1L, plugin.getConfig().getLong("androids.tick-interval-ticks", 10L));
        maxActivePerRegion = Math.max(1, plugin.getConfig().getInt("androids.max-active-per-region-per-tick", 64));
        running = true;
        rebuildIndex();
        scheduleTick();
    }

    public void shutdown() {
        running = false;
        pendingImports.clear();
        pendingUploads.clear();
        pendingScriptEdits.clear();
        mainViewers.clear();
        flushAllStates();
        states.clear();
        activeAndroids.clear();
        scripts.close();
    }

    public boolean supportsType(String typeId) {
        return SfxAndroidType.isAndroidItem(typeId) || INTERFACE_FUEL.equals(typeId) || INTERFACE_ITEMS.equals(typeId);
    }

    public void handlePlaced(UUID instanceId, String typeId, Player player, Block block) {
        if (instanceId == null || typeId == null || block == null) {
            return;
        }
        SfxAndroidType type = SfxAndroidType.fromItemId(typeId);
        if (type == null) {
            return;
        }
        BlockFace rotation = facingFromPlayer(player);
        SfxAndroidState state = SfxAndroidState.createDefault(rotation);
        states.put(instanceId, state);
        persist(instanceId, state);
        applyAndroidBlockAppearance(block, typeId, rotation);
        runtime.executeAtLater(block.getLocation(), 1L, () -> applyAndroidBlockAppearance(block, typeId, rotation));
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
            dropInventory(block);
            SfxBlockDrops.dropPluginBlock(block, items, typeId);
            blockData.unregisterAt(block.getLocation());
        }
    }

    private void rebuildIndex() {
        for (SfxAnchorRecord anchor : blockData.anchors()) {
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
                tickAndroids(androidTick.incrementAndGet());
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Android scheduler failed: " + throwable.getMessage());
            } finally {
                scheduleTick();
            }
        });
    }

    private void tickAndroids(long tickId) {
        List<SfxBlockInstanceRecord> active = new ArrayList<>();
        for (UUID instanceId : List.copyOf(activeAndroids)) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
                activeAndroids.remove(instanceId);
                states.remove(instanceId);
                continue;
            }
            SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
            if (state.paused() || state.runtimeState() == SfxAndroidRuntimeState.PAUSED) {
                activeAndroids.remove(instanceId);
                continue;
            }
            if (shouldSkipForBackoff(state, tickId)) {
                continue;
            }
            active.add(instance);
        }
        Map<String, List<SfxBlockInstanceRecord>> groups = new HashMap<>();
        for (SfxBlockInstanceRecord instance : active) {
            String key = instance.anchorKey().worldId() + ":" + (instance.anchorKey().x() >> 4) + ":" + (instance.anchorKey().z() >> 4);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(instance);
        }
        for (List<SfxBlockInstanceRecord> group : groups.values()) {
            if (group.isEmpty()) {
                continue;
            }
            group.sort(Comparator.comparing(SfxBlockInstanceRecord::instanceId));
            SfxBlockInstanceRecord first = group.get(0);
            Location location = toLocation(first.anchorKey());
            if (location == null) {
                continue;
            }
            List<SfxBlockInstanceRecord> snapshot = group.size() > maxActivePerRegion ? group.subList(0, maxActivePerRegion) : group;
            runtime.executeAt(location, () -> tickRegionBatch(List.copyOf(snapshot), tickId));
        }
    }

    private void tickRegionBatch(List<SfxBlockInstanceRecord> instances, long tickId) {
        Map<UUID, MoveIntent> moveIntents = new HashMap<>();
        Set<UUID> runnable = new HashSet<>();
        Set<LocationKey> occupiedBefore = new HashSet<>();
        Map<UUID, SfxAndroidState> batchStates = new HashMap<>();
        for (SfxBlockInstanceRecord instance : instances) {
            Location from = toLocation(instance.anchorKey());
            if (from == null) {
                continue;
            }
            occupiedBefore.add(LocationKey.of(from));
            SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), from);
            batchStates.put(instance.instanceId(), state);
            SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
            if (type == null || state.paused() || state.runtimeState() == SfxAndroidRuntimeState.PAUSED) {
                continue;
            }
            SfxAndroidInstruction instruction = state.currentInstruction();
            if (!instruction.validFor(type)) {
                state.runtimeState(SfxAndroidRuntimeState.DORMANT_SCRIPT_INVALID);
                activeAndroids.remove(instance.instanceId());
                persist(instance.instanceId(), state);
                continue;
            }
            boolean fuelInterfaceBootstrap = instruction == SfxAndroidInstruction.INTERFACE_FUEL
                    && state.fuelTicks() <= 0
                    && fuelValue(state.fuelSlot(), type) <= 0;
            if (!fuelInterfaceBootstrap && !ensureFuel(instance, state, type, from.getBlock())) {
                continue;
            }
            if (fuelInterfaceBootstrap) {
                state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            }
            runnable.add(instance.instanceId());
            BlockFace face = actionFacing(state);
            Block target = targetBlock(from.getBlock(), face, instruction);
            if (isMoveInstruction(instruction)) {
                boolean clearsTarget = isMoveAndDigInstruction(instruction) && canClearTargetForMoveAndDig(state, target);
                moveIntents.put(instance.instanceId(), new MoveIntent(instance, from, target.getLocation(), instruction, clearsTarget));
            }
        }
        Map<LocationKey, List<MoveIntent>> byTarget = new HashMap<>();
        Set<LocationKey> leaving = new HashSet<>();
        for (MoveIntent intent : moveIntents.values()) {
            byTarget.computeIfAbsent(LocationKey.of(intent.to), ignored -> new ArrayList<>()).add(intent);
            leaving.add(LocationKey.of(intent.from));
        }
        Set<UUID> acceptedMoves = new HashSet<>();
        for (List<MoveIntent> contenders : byTarget.values()) {
            contenders.sort(Comparator.comparing(intent -> intent.instance.instanceId()));
            MoveIntent winner = contenders.get(0);
            Block target = winner.to.getBlock();
            LocationKey targetKey = LocationKey.of(winner.to);
            boolean targetFree = target.getType().isAir()
                    || (occupiedBefore.contains(targetKey) && leaving.contains(targetKey))
                    || winner.clearsTargetBeforeMove();
            if (targetFree) {
                acceptedMoves.add(winner.instance.instanceId());
            }
        }
        for (SfxBlockInstanceRecord instance : instances) {
            Location location = toLocation(instance.anchorKey());
            if (location == null) {
                continue;
            }
            SfxAndroidState state = batchStates.getOrDefault(instance.instanceId(), stateFor(instance.instanceId(), instance.typeId(), location));
            SfxAndroidType type = SfxAndroidType.fromItemId(instance.typeId());
            if (type == null || !runnable.contains(instance.instanceId()) || state.paused() || state.runtimeState() == SfxAndroidRuntimeState.PAUSED || state.runtimeState() == SfxAndroidRuntimeState.DORMANT_SCRIPT_INVALID) {
                continue;
            }
            SfxAndroidInstruction instruction = state.currentInstruction();
            boolean consumedFuelForInstruction = state.fuelTicks() > 0;
            boolean success = executeInstruction(instance, type, state, instruction, location.getBlock(), acceptedMoves.contains(instance.instanceId()), tickId);
            if (success) {
                state.advance();
            }
            if (state.runtimeState() == SfxAndroidRuntimeState.ACTIVE) {
                state.resetNoEffectTicks();
            } else if (!state.paused() && state.runtimeState() != SfxAndroidRuntimeState.PAUSED && state.runtimeState() != SfxAndroidRuntimeState.DORMANT_SCRIPT_INVALID) {
                state.incrementNoEffectTicks();
            }
            if (consumedFuelForInstruction) {
                state.consumeFuelTick();
            }
            persist(currentInstanceId(instance, location), state);
        }
    }

    private boolean executeInstruction(SfxBlockInstanceRecord instance, SfxAndroidType type, SfxAndroidState state, SfxAndroidInstruction instruction, Block block, boolean moveAccepted, long tickId) {
        BlockFace face = actionFacing(state);
        return switch (instruction) {
            case WAIT -> {
                state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
                yield true;
            }
            case TURN_LEFT -> {
                state.rotation(turnLeft(state.rotation()));
                applyRotation(block, state.rotation());
                state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
                yield true;
            }
            case TURN_RIGHT -> {
                state.rotation(turnRight(state.rotation()));
                applyRotation(block, state.rotation());
                state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
                yield true;
            }
            case GO_FORWARD, GO_UP, GO_DOWN -> moveAccepted && moveAndroid(instance, state, block, targetBlock(block, face, instruction));
            case DIG_FORWARD, DIG_UP, DIG_DOWN -> dig(state, block, targetBlock(block, face, instruction), false, instance.typeId());
            case MOVE_AND_DIG_FORWARD, MOVE_AND_DIG_UP, MOVE_AND_DIG_DOWN -> {
                Block target = targetBlock(block, face, instruction);
                boolean dug = dig(state, block, target, false, instance.typeId());
                yield dug && moveAccepted && moveAndroid(instance, state, block, target);
            }
            case FARM_FORWARD, FARM_DOWN, FARM_EXOTIC_FORWARD, FARM_EXOTIC_DOWN -> farm(state, targetBlock(block, face, instruction), type);
            case CHOP_TREE -> chopTree(state, targetBlock(block, face, instruction));
            case CATCH_FISH -> catchFish(state, block, type);
            case ATTACK_MOBS_ANIMALS -> attack(block, state, type, living -> living instanceof Monster || living instanceof Animals);
            case ATTACK_MOBS -> attack(block, state, type, living -> living instanceof Monster);
            case ATTACK_ANIMALS -> attack(block, state, type, living -> living instanceof Animals);
            case ATTACK_ANIMALS_ADULT -> attack(block, state, type, living -> living instanceof Animals animal && animal.isAdult());
            case INTERFACE_ITEMS -> depositItems(state, block, face);
            case INTERFACE_FUEL -> pullFuel(state, block, face, type);
        };
    }

    private UUID currentInstanceId(SfxBlockInstanceRecord fallback, Location currentLocation) {
        SfxAnchorRecord anchor = blockData.findAnchor(currentLocation).orElse(null);
        return anchor == null ? fallback.instanceId() : anchor.instanceId();
    }

    private boolean ensureFuel(SfxBlockInstanceRecord instance, SfxAndroidState state, SfxAndroidType type, Block block) {
        if (state.fuelTicks() > 0) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            return true;
        }
        ItemStack fuel = state.fuelSlot();
        int fuelTicks = fuelValue(fuel, type);
        if (fuelTicks <= 0) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_NO_FUEL);
            state.incrementNoEffectTicks();
            persist(instance.instanceId(), state);
            return false;
        }
        fuel.setAmount(fuel.getAmount() - 1);
        state.fuelSlot(fuel.getAmount() <= 0 ? null : fuel);
        state.fuelTicks(fuelTicks);
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        persist(instance.instanceId(), state);
        return true;
    }

    private int fuelValue(ItemStack item, SfxAndroidType type) {
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

    private boolean isMoveInstruction(SfxAndroidInstruction instruction) {
        return instruction == SfxAndroidInstruction.GO_FORWARD
                || instruction == SfxAndroidInstruction.GO_UP
                || instruction == SfxAndroidInstruction.GO_DOWN
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_FORWARD
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_UP
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_DOWN;
    }

    private boolean isMoveAndDigInstruction(SfxAndroidInstruction instruction) {
        return instruction == SfxAndroidInstruction.MOVE_AND_DIG_FORWARD
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_UP
                || instruction == SfxAndroidInstruction.MOVE_AND_DIG_DOWN;
    }

    private boolean canClearTargetForMoveAndDig(SfxAndroidState state, Block target) {
        return target != null
                && !target.getType().isAir()
                && !isUnbreakable(target.getType())
                && blockData.findAnchor(target.getLocation()).isEmpty()
                && state.hasFreeOutputSpace()
                && !target.getDrops(new ItemStack(Material.DIAMOND_PICKAXE)).isEmpty();
    }


    private boolean shouldSkipForBackoff(SfxAndroidState state, long tickId) {
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

    private BlockFace actionFacing(SfxAndroidState state) {
        BlockFace visual = state == null ? BlockFace.NORTH : state.rotation();
        return switch (visual) {
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }

    private Block targetBlock(Block block, BlockFace face, SfxAndroidInstruction instruction) {
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
        from.setType(Material.AIR, false);
        to.setType(Material.PLAYER_HEAD, false);
        applyAndroidBlockAppearance(to, instance.typeId(), state.rotation());
        blockData.unregisterAt(from.getLocation());
        UUID newId = blockData.registerSingleBlock(instance.typeId(), to.getLocation(), Material.PLAYER_HEAD, instance.ownerId());
        states.remove(instance.instanceId());
        activeAndroids.remove(instance.instanceId());
        states.put(newId, state);
        activeAndroids.add(newId);
        persist(newId, state);
        return true;
    }

    private boolean dig(SfxAndroidState state, Block androidBlock, Block target, boolean moveAfter, String typeId) {
        if (target == null || target.getType().isAir() || isUnbreakable(target.getType()) || blockData.findAnchor(target.getLocation()).isPresent()) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        List<ItemStack> drops = new ArrayList<>(target.getDrops(new ItemStack(Material.DIAMOND_PICKAXE)));
        if (drops.isEmpty()) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        for (ItemStack drop : drops) {
            if (!state.pushOutput(drop)) {
                state.runtimeState(SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL);
                return false;
            }
        }
        playBlockBreakEffect(target);
        target.setType(Material.AIR, true);
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        return true;
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

    private boolean farm(SfxAndroidState state, Block target, SfxAndroidType type) {
        if (target == null || !(target.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        ItemStack drop = cropDrop(target.getType());
        if (drop == null || !state.pushOutput(drop)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL);
            return false;
        }
        ageable.setAge(0);
        target.setBlockData(ageable, true);
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

    private boolean chopTree(SfxAndroidState state, Block target) {
        if (target == null || !Tag.LOGS.isTagged(target.getType())) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        ItemStack drop = new ItemStack(target.getType());
        if (!state.pushOutput(drop)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL);
            return false;
        }
        playBlockBreakEffect(target);
        target.setType(Material.AIR, true);
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        return true;
    }

    private boolean catchFish(SfxAndroidState state, Block block, SfxAndroidType type) {
        if (block.getRelative(BlockFace.DOWN).getType() != Material.WATER) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
            return false;
        }
        int chance = 10 * type.tier();
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= chance) {
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.6f, 1.0f);
            return true;
        }
        Material[] loot = {Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH, Material.BONE, Material.STRING, Material.INK_SAC, Material.KELP, Material.STICK, Material.ROTTEN_FLESH, Material.LEATHER, Material.BAMBOO, Material.NAUTILUS_SHELL};
        ItemStack drop = new ItemStack(loot[java.util.concurrent.ThreadLocalRandom.current().nextInt(loot.length)]);
        if (!state.pushOutput(drop)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL);
            return false;
        }
        state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        return true;
    }

    private boolean attack(Block block, SfxAndroidState state, SfxAndroidType type, java.util.function.Predicate<LivingEntity> filter) {
        double damage = type.tier() >= 3 ? 20D : 4D * type.tier();
        double radius = 4D + type.tier();
        Location origin = block.getLocation().add(0.5, 0.5, 0.5);
        BlockFace face = actionFacing(state);
        boolean attacked = false;
        for (Entity entity : block.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living instanceof Player || !filter.test(living) || !isInFront(origin, living.getLocation(), face)) {
                continue;
            }
            living.damage(damage);
            attacked = true;
            if (living.isDead()) {
                collectNearbyDrops(state, living.getLocation());
            }
        }
        if (!attacked) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_BLOCKED);
        } else {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        }
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
        Block target = findAdjacentInterface(androidBlock, state, preferredFace, INTERFACE_ITEMS);
        if (target == null || !(target.getState() instanceof Dispenser dispenser)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_WAITING_EXTERNAL_CHANGE);
            return true;
        }
        Inventory inventory = dispenser.getInventory();
        boolean moved = false;
        boolean hadOutput = false;
        ItemStack[] outputs = state.outputs();
        for (int i = 0; i < outputs.length; i++) {
            ItemStack stack = outputs[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            hadOutput = true;
            Map<Integer, ItemStack> left = inventory.addItem(stack.clone());
            if (left.isEmpty()) {
                state.output(i, null);
                moved = true;
            } else {
                ItemStack remaining = left.values().iterator().next();
                if (remaining.getAmount() != stack.getAmount()) {
                    moved = true;
                }
                state.output(i, remaining);
            }
        }
        dispenser.update(true, false);
        if (moved) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
        } else {
            state.runtimeState(hadOutput ? SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL : SfxAndroidRuntimeState.DORMANT_WAITING_EXTERNAL_CHANGE);
        }
        return true;
    }

    private boolean pullFuel(SfxAndroidState state, Block androidBlock, BlockFace preferredFace, SfxAndroidType type) {
        Block target = findAdjacentInterface(androidBlock, state, preferredFace, INTERFACE_FUEL);
        if (target == null || !(target.getState() instanceof Dispenser dispenser)) {
            state.runtimeState(SfxAndroidRuntimeState.DORMANT_WAITING_EXTERNAL_CHANGE);
            return true;
        }
        if (state.fuelSlot() != null && fuelValue(state.fuelSlot(), type) > 0) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            return true;
        }
        Inventory inventory = dispenser.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (fuelValue(stack, type) <= 0) {
                continue;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            stack.setAmount(stack.getAmount() - 1);
            inventory.setItem(i, stack.getAmount() <= 0 ? null : stack);
            dispenser.update(true, false);
            state.fuelSlot(one);
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            return true;
        }
        state.runtimeState(SfxAndroidRuntimeState.DORMANT_NO_FUEL);
        return true;
    }

    private Block findAdjacentInterface(Block androidBlock, SfxAndroidState state, BlockFace preferredFace, String interfaceTypeId) {
        if (androidBlock == null || interfaceTypeId == null) {
            return null;
        }
        List<BlockFace> faces = new ArrayList<>();
        addFace(faces, preferredFace);
        addFace(faces, state == null ? null : state.rotation());
        addFace(faces, preferredFace == null ? null : preferredFace.getOppositeFace());
        addFace(faces, BlockFace.NORTH);
        addFace(faces, BlockFace.EAST);
        addFace(faces, BlockFace.SOUTH);
        addFace(faces, BlockFace.WEST);
        addFace(faces, BlockFace.UP);
        addFace(faces, BlockFace.DOWN);
        for (BlockFace face : faces) {
            Block candidate = androidBlock.getRelative(face);
            SfxBlockInstanceRecord instance = blockData.findAnchor(candidate.getLocation())
                    .flatMap(anchor -> blockData.findInstance(anchor.instanceId()))
                    .orElse(null);
            if (instance != null && interfaceTypeId.equals(instance.typeId()) && candidate.getState() instanceof Dispenser) {
                return candidate;
            }
        }
        return null;
    }

    private void addFace(List<BlockFace> faces, BlockFace face) {
        if (face != null && !faces.contains(face)) {
            faces.add(face);
        }
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
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(instance.ownerId()) && !player.hasPermission("sfx.android.bypass")) {
            player.sendMessage(msg("android.messages.no_access", "<red>This Android belongs to another player.</red>"));
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
            return;
        }
        if (isOutputSlot(raw)) {
            if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick().isShiftClick()) {
                return;
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        handleMainButton(player, holder, top, raw);
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
        Set<UUID> viewers = mainViewers.get(holder.instanceId());
        if (viewers != null) {
            viewers.remove(player.getUniqueId());
            if (viewers.isEmpty()) {
                mainViewers.remove(holder.instanceId());
            }
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
            return;
        }
        SfxAndroidState state = syncMainInventoryToState(instance, inventory);
        if (!state.paused() && (state.runtimeState() == SfxAndroidRuntimeState.DORMANT_NO_FUEL || state.runtimeState() == SfxAndroidRuntimeState.DORMANT_OUTPUT_FULL)) {
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            activeAndroids.add(instance.instanceId());
        }
        persist(instance.instanceId(), state);
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
            player.sendMessage(msg("android.messages.import_cancelled", "<yellow>Android script import cancelled.</yellow>"));
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(session.instanceId()).orElse(null);
        if (instance == null || !SfxAndroidType.isAndroidItem(instance.typeId())) {
            player.sendMessage(msg("android.messages.missing_android", "<red>That Android no longer exists.</red>"));
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
            player.sendMessage(msg("android.messages.imported", "<green>Imported Android script.</green>"));
            openScript(player, instance.instanceId(), 0);
        } catch (RuntimeException exception) {
            player.sendMessage(msg("android.messages.invalid_script", "<red>Invalid Android script: {reason}</red>", Map.of("reason", exception.getMessage())));
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
        SfxAndroidState state = stateFor(instanceId, instance.typeId(), toLocation(instance.anchorKey()));
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.MAIN, 0, -1, false), 54, tr("android.menu.main.title", "Programmable Android"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fillClassicFrame(inventory);
        inventory.setItem(15, headIcon(HEAD_SCRIPT_START, "android.menu.main.start", "<green>Start/Continue</green>", "<gray>Start or resume this Android.</gray>"));
        inventory.setItem(16, headIcon(HEAD_MEMORY_CORE, "android.menu.main.memory_core", "<aqua>Memory Core</aqua>", "", "<dark_gray>⇨ <gray>Click to open the Script Editor</gray>"));
        inventory.setItem(17, headIcon(HEAD_SCRIPT_PAUSE, "android.menu.main.pause", "<dark_red>Pause</dark_red>", "<gray>Stop execution without clearing state.</gray>"));
        inventory.setItem(34, fuelInfoIcon(state));
        ItemStack[] outputs = state.outputs();
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            inventory.setItem(OUTPUT_SLOTS[i], outputs[i]);
        }
        inventory.setItem(FUEL_SLOT, state.fuelSlot());
        mainViewers.computeIfAbsent(instanceId, ignored -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
        player.openInventory(inventory);
    }

    private SfxAndroidMenuHolder newHolder(Player player, UUID instanceId, SfxAndroidMenuHolder.MenuType type, int page, int editIndex, boolean adding) {
        return new SfxAndroidMenuHolder(player.getUniqueId(), instanceId, type, page, editIndex, adding);
    }

    private SfxAndroidState syncMainInventoryToState(SfxBlockInstanceRecord instance, Inventory inventory) {
        SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            state.output(i, inventory.getItem(OUTPUT_SLOTS[i]));
        }
        state.fuelSlot(inventory.getItem(FUEL_SLOT));
        return state;
    }

    private void refreshMainStatus(Inventory inventory, SfxAndroidState state) {
        if (inventory == null || state == null || inventory.getHolder() == null) {
            return;
        }
        inventory.setItem(34, fuelInfoIcon(state));
        inventory.setItem(FUEL_SLOT, state.fuelSlot());
        ItemStack[] outputs = state.outputs();
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            inventory.setItem(OUTPUT_SLOTS[i], outputs[i]);
        }
    }

    private void handleMainButton(Player player, SfxAndroidMenuHolder holder, Inventory top, int slot) {
        SfxBlockInstanceRecord instance = blockData.findInstance(holder.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxAndroidState state = syncMainInventoryToState(instance, top);
        if (slot == 15) {
            state.paused(false);
            state.runtimeState(SfxAndroidRuntimeState.ACTIVE);
            activeAndroids.add(instance.instanceId());
            persist(instance.instanceId(), state);
            refreshMainStatus(top, state);
            player.sendMessage(msg("android.messages.started", "<green>Android started.</green>"));
        } else if (slot == 17) {
            state.paused(true);
            state.runtimeState(SfxAndroidRuntimeState.PAUSED);
            activeAndroids.remove(instance.instanceId());
            persist(instance.instanceId(), state);
            refreshMainStatus(top, state);
            player.sendMessage(msg("android.messages.paused", "<yellow>Android paused.</yellow>"));
        } else if (slot == 16) {
            state.paused(true);
            state.runtimeState(SfxAndroidRuntimeState.PAUSED);
            activeAndroids.remove(instance.instanceId());
            persist(instance.instanceId(), state);
            refreshMainStatus(top, state);
            openEditor(player, instance.instanceId());
        }
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
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.EDITOR, 0, -1, false), 27, tr("android.menu.editor.title", "<dark_gray>Android Memory Core</dark_gray>"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(10, headIcon(HEAD_SCRIPT_NEW, "android.menu.editor.edit", "<green>Edit Script</green>", "<gray>Edit the current script graphically.</gray>"));
        inventory.setItem(11, icon(Material.MAP, "android.menu.editor.export", "<aqua>Export Script</aqua>", "<gray>Send a click-to-copy short code.</gray>"));
        inventory.setItem(12, icon(Material.PAPER, "android.menu.editor.import", "<yellow>Import Script</yellow>", "<gray>Paste a short code or readable script in chat.</gray>"));
        inventory.setItem(14, icon(Material.ENDER_CHEST, "android.menu.editor.download", "<gold>Download Scripts</gold>", "<gray>Browse public/private uploaded scripts.</gray>"));
        inventory.setItem(15, icon(Material.HOPPER, "android.menu.editor.upload", "<blue>Upload Script</blue>", "<gray>Share this script with this server.</gray>"));
        inventory.setItem(16, icon(Material.BARRIER, "android.menu.editor.back", "<red>Back</red>", "<gray>Return to the Android.</gray>"));
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
            player.sendMessage(msg("android.messages.import_prompt", "<yellow>Paste an Android script code in chat. Type <white>cancel</white> to abort.</yellow>"));
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
        Component clickable = tr("android.messages.copy_script", "<aqua>[Click to copy Android script]</aqua>").clickEvent(ClickEvent.copyToClipboard(code));
        player.sendMessage(msg("android.messages.export", "<green>Android script export:</green> ").append(clickable));
        player.sendMessage(Component.text(code, NamedTextColor.GRAY));
    }

    private void openUploadVisibility(Player player, UUID instanceId) {
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.UPLOAD_VISIBILITY, 0, -1, false), 27, tr("android.menu.upload.title", "<dark_gray>Upload Android Script</dark_gray>"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(11, icon(Material.LIME_DYE, "android.menu.upload.public", "<green>Public</green>", "<gray>Visible to everyone.</gray>"));
        inventory.setItem(15, icon(Material.IRON_DOOR, "android.menu.upload.private", "<yellow>Private</yellow>", "<gray>Only you can see it.</gray>"));
        inventory.setItem(22, icon(Material.BARRIER, "android.menu.common.back", "<red>Back</red>"));
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
        player.sendMessage(msg("android.messages.upload_name_prompt", "<yellow>Type a script name in chat. Type <white>cancel</white> to abort.</yellow>"));
    }

    private void openScript(Player player, UUID instanceId, int page) {
        pauseAndroidForScriptEditing(instanceId);
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        if (instance == null) {
            return;
        }
        SfxAndroidState state = stateFor(instanceId, instance.typeId(), toLocation(instance.anchorKey()));
        List<SfxAndroidInstruction> body = state.body();
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.SCRIPT, 0, -1, false), 54, tr("android.menu.script.title", "<dark_gray>Edit Android Script</dark_gray>"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(0, headIcon(HEAD_SCRIPT_START, "android.menu.script.start", "<green>START</green>"));
        for (int i = 0; i < Math.min(body.size(), SCRIPT_SLOTS.length); i++) {
            SfxAndroidInstruction instruction = body.get(i);
            inventory.setItem(SCRIPT_SLOTS[i], instructionIcon(instruction, i + 1, true));
        }
        if (body.size() < SCRIPT_SLOTS.length) {
            inventory.setItem(SCRIPT_SLOTS[body.size()], headIcon(HEAD_SCRIPT_NEW, "android.menu.script.add", "<green>Add new Command</green>"));
        }
        inventory.setItem(53, headIcon(HEAD_SCRIPT_REPEAT, "android.menu.script.repeat", "<red>REPEAT</red>"));
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
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.INSTRUCTIONS, 0, editIndex, adding), 54, tr("android.menu.instructions.title", "<dark_gray>Select Instruction</dark_gray>"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        List<SfxAndroidInstruction> instructions = SfxAndroidInstruction.validForType(type);
        for (int i = 0; i < Math.min(45, instructions.size()); i++) {
            SfxAndroidInstruction instruction = instructions.get(i);
            inventory.setItem(i, instructionIcon(instruction, -1, false));
        }
        inventory.setItem(53, icon(Material.BARRIER, "android.menu.common.back", "<red>Back</red>"));
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
        Inventory inventory = Bukkit.createInventory(newHolder(player, instanceId, SfxAndroidMenuHolder.MenuType.DOWNLOADER, page, -1, false), 54, tr("android.menu.downloader.title", "<dark_gray>Android Scripts</dark_gray>"));
        ((SfxAndroidMenuHolder) inventory.getHolder()).bind(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        try {
            List<SfxAndroidScriptRecord> records = scripts.listVisible(type, player.getUniqueId(), page * 45, 45);
            for (int i = 0; i < records.size(); i++) {
                SfxAndroidScriptRecord record = records.get(i);
                inventory.setItem(i, downloaderEntryIcon(record, player));
            }
        } catch (SQLException exception) {
            inventory.setItem(22, icon(Material.BARRIER, "<red>Failed to read scripts</red>", "<gray>" + exception.getMessage() + "</gray>"));
        }
        inventory.setItem(45, icon(Material.ARROW, "android.menu.common.previous", "<yellow>Previous Page</yellow>"));
        inventory.setItem(49, icon(Material.BARRIER, "android.menu.common.back", "<red>Back</red>"));
        inventory.setItem(53, icon(Material.ARROW, "android.menu.common.next", "<yellow>Next Page</yellow>"));
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
                    player.sendMessage(msg("android.messages.edit_script_prompt", "<yellow>Type a new script name, or type <white>delete</white> to delete it. Type <white>cancel</white> to abort.</yellow>"));
                }
                return;
            }
            SfxAndroidState state = stateFor(instance.instanceId(), instance.typeId(), toLocation(instance.anchorKey()));
            state.setBody(SfxAndroidScriptCodec.canonicalize(type, record.body()));
            state.index(0);
            persist(instance.instanceId(), state);
            scripts.incrementDownloads(record.id());
            player.sendMessage(msg("android.messages.downloaded", "<green>Downloaded Android script #{id}.</green>", Map.of("id", record.id())));
            openScript(player, instance.instanceId(), 0);
        } catch (SQLException exception) {
            player.sendMessage(msg("android.messages.script_library_error", "<red>Script library error: {reason}</red>", Map.of("reason", exception.getMessage())));
        }
    }


    private void handleUploadNameChat(Player player, UploadSession session, String message) {
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(msg("android.messages.upload_cancelled", "<yellow>Android script upload cancelled.</yellow>"));
            openEditor(player, session.instanceId());
            return;
        }
        String name = sanitizeScriptName(message);
        if (name == null) {
            pendingUploads.put(player.getUniqueId(), session);
            player.sendMessage(msg("android.messages.invalid_script_name", "<red>Invalid script name. Use 1-32 visible characters.</red>"));
            return;
        }
        try {
            long id = scripts.upload(session.type(), player.getUniqueId(), player.getName(), name, session.body(), session.visibility());
            player.sendMessage(msg("android.messages.uploaded", "<green>Uploaded Android script #{id}.</green>", Map.of("id", id)));
        } catch (SQLException exception) {
            player.sendMessage(msg("android.messages.upload_failed", "<red>Failed to upload script: {reason}</red>", Map.of("reason", exception.getMessage())));
        }
        openEditor(player, session.instanceId());
    }

    private void handleEditScriptChat(Player player, EditScriptSession session, String message) {
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(msg("android.messages.edit_cancelled", "<yellow>Android script edit cancelled.</yellow>"));
            openDownloader(player, session.instanceId(), session.page());
            return;
        }
        try {
            if (message.equalsIgnoreCase("delete")) {
                boolean deleted = scripts.softDelete(session.scriptId(), player.getUniqueId(), session.force());
                if (deleted) {
                    player.sendMessage(msg("android.messages.deleted", "<yellow>Deleted Android script #{id}.</yellow>", Map.of("id", session.scriptId())));
                } else {
                    player.sendMessage(msg("android.messages.edit_denied", "<red>You cannot modify that script.</red>"));
                }
                openDownloader(player, session.instanceId(), session.page());
                return;
            }
            String name = sanitizeScriptName(message);
            if (name == null) {
                pendingScriptEdits.put(player.getUniqueId(), session);
                player.sendMessage(msg("android.messages.invalid_script_name", "<red>Invalid script name. Use 1-32 visible characters.</red>"));
                return;
            }
            boolean renamed = scripts.rename(session.scriptId(), player.getUniqueId(), session.force(), name);
            if (renamed) {
                player.sendMessage(msg("android.messages.renamed", "<green>Renamed Android script #{id}.</green>", Map.of("id", session.scriptId())));
            } else {
                player.sendMessage(msg("android.messages.edit_denied", "<red>You cannot modify that script.</red>"));
            }
        } catch (SQLException exception) {
            player.sendMessage(msg("android.messages.script_library_error", "<red>Script library error: {reason}</red>", Map.of("reason", exception.getMessage())));
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
        lore.add(Text.renderFlexible(localization.text("android.menu.downloader.entry.author", "<gray>Author: <white>{author}</white></gray>", Map.of("author", record.authorName()))));
        lore.add(Text.renderFlexible(localization.text("android.menu.downloader.entry.length", "<gray>Length: <white>{length}/54</white></gray>", Map.of("length", record.body().size() + 2))));
        lore.add(Text.renderFlexible(localization.text("android.menu.downloader.entry.visibility", "<gray>Visibility: <white>{visibility}</white></gray>", Map.of("visibility", record.visibility().name()))));
        lore.add(Text.renderFlexible(localization.text("android.menu.downloader.entry.downloads", "<gray>Downloads: <white>{downloads}</white> | +{positive} / -{negative}</gray>", Map.of("downloads", record.downloads(), "positive", record.positiveVotes(), "negative", record.negativeVotes()))));
        lore.add(tr("android.menu.downloader.entry.actions", "<gray>Left: download | Shift-left: upvote | Shift-right: downvote</gray>"));
        if (record.authorId().equals(viewer.getUniqueId()) || viewer.hasPermission("sfx.android.script.edit.any") || viewer.hasPermission("sfx.android.script.delete.any")) {
            lore.add(tr("android.menu.downloader.entry.modify", "<yellow>Right-click: rename, or type delete to remove</yellow>"));
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

    private Component tr(String path, String fallback) {
        return localization.component(path, fallback);
    }

    private Component msg(String path, String fallback) {
        return Text.prefixed(plugin, localization.text(path, fallback));
    }

    private Component msg(String path, String fallback, Map<String, ?> placeholders) {
        return Text.prefixed(plugin, localization.text(path, fallback, placeholders));
    }

    private ItemStack icon(Material material, String keyOrName, String... loreOrFallback) {
        if (keyOrName != null && keyOrName.startsWith("android.")) {
            String fallbackName = loreOrFallback.length == 0 ? keyOrName : loreOrFallback[0];
            String[] fallbackLore = loreOrFallback.length <= 1 ? new String[0] : java.util.Arrays.copyOfRange(loreOrFallback, 1, loreOrFallback.length);
            return localizedItem(material, null, keyOrName, fallbackName, fallbackLore);
        }
        return ItemBuilder.of(material).name(keyOrName).lore(loreOrFallback).build();
    }

    private ItemStack headIcon(String texture, String key, String fallbackName, String... fallbackLore) {
        return localizedItem(Material.PLAYER_HEAD, texture, key, fallbackName, fallbackLore);
    }

    private ItemStack instructionIcon(SfxAndroidInstruction instruction, int displayIndex, boolean editableLore) {
        String key = "android.instructions." + instruction.name().toLowerCase(Locale.ROOT);
        String fallbackName = "<yellow>" + instruction.displayName() + "</yellow>";
        List<Component> lore = localizedLore(key + ".lore", instruction.description());
        if (editableLore) {
            lore = new ArrayList<>(lore);
            lore.add(tr("android.menu.script.edit_hint", "<gray>Left: edit | Right: delete | Shift-right: duplicate</gray>"));
        }
        ItemStack item = localizedItem(Material.PLAYER_HEAD, instruction.texture(), key, fallbackName);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayIndex > 0) {
                meta.displayName(Component.text(displayIndex + ". ", NamedTextColor.YELLOW).append(tr(key + ".name", fallbackName)));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack fuelInfoIcon(SfxAndroidState state) {
        ItemStack item = localizedItem(Material.COAL, null, "android.menu.main.fuel_info", "<yellow>Fuel Input</yellow>",
                "<gray>Put compatible fuel in slot 43.</gray>",
                "<gray>Current fuel ticks: <white>{fuel}</white></gray>",
                "<gray>State: <white>{state}</white></gray>");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = localizedLore("android.menu.main.fuel_info.lore",
                    "<gray>Put compatible fuel in slot 43.</gray>",
                    "<gray>Current fuel ticks: <white>{fuel}</white></gray>",
                    "<gray>State: <white>{state}</white></gray>");
            List<Component> rendered = new ArrayList<>();
            for (Component component : lore) {
                String legacy = Text.toLegacy(component).replace("{fuel}", Long.toString(state.fuelTicks())).replace("{state}", state.runtimeState().name());
                rendered.add(Text.legacy(legacy));
            }
            meta.lore(rendered);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack localizedItem(Material material, String texture, String key, String fallbackName, String... fallbackLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (texture != null) {
                HeadTextures.apply(meta, texture);
            }
            meta.displayName(tr(key + ".name", fallbackName));
            meta.lore(localizedLore(key + ".lore", fallbackLore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Component> localizedLore(String path, String... fallbackLore) {
        List<String> values = localization.list(path);
        if (values.isEmpty()) {
            values = fallbackLore == null ? List.of() : java.util.Arrays.asList(fallbackLore);
        }
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Component> components = new ArrayList<>();
        for (String line : values) {
            components.add(Text.renderFlexible(line));
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

    private void persist(UUID instanceId, SfxAndroidState state) {
        if (instanceId == null || state == null) {
            return;
        }
        blockData.updateInstanceState(instanceId, state.encode(), state.runtimeState() == SfxAndroidRuntimeState.ACTIVE ? SfxBlockLifecycleState.ACTIVE : SfxBlockLifecycleState.IDLE);
        refreshOpenMainInventory(instanceId, state);
    }

    private void refreshOpenMainInventory(UUID instanceId, SfxAndroidState state) {
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
                        || !holder.instanceId().equals(instanceId)) {
                    Set<UUID> current = mainViewers.get(instanceId);
                    if (current != null) {
                        current.remove(viewerId);
                        if (current.isEmpty()) {
                            mainViewers.remove(instanceId);
                        }
                    }
                    return;
                }
                refreshMainStatus(top, state);
            });
        }
    }

    private void flushAllStates() {
        for (Map.Entry<UUID, SfxAndroidState> entry : states.entrySet()) {
            persist(entry.getKey(), entry.getValue());
        }
    }

    private SfxAndroidState stateFor(UUID instanceId, String typeId, Location location) {
        return states.computeIfAbsent(instanceId, id -> {
            SfxBlockInstanceRecord instance = blockData.findInstance(id).orElse(null);
            return SfxAndroidState.decode(instance == null ? new byte[0] : instance.stateBlob(), BlockFace.NORTH);
        });
    }

    private Location toLocation(cc.theends6.sfx.internal.block.SfxBlockAnchorKey key) {
        if (key == null) {
            return null;
        }
        World world = Bukkit.getWorld(key.worldId());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }

    private void applyAndroidBlockAppearance(Block block, String typeId, BlockFace rotation) {
        if (block == null || typeId == null) {
            return;
        }
        block.setType(Material.PLAYER_HEAD, false);
        applyRotation(block, rotation);
        SfxItemDefinition definition = itemRegistry.item(typeId).orElse(null);
        if (definition != null && block.getState() instanceof Skull skull) {
            HeadTextures.apply(skull, definition.headTextureHash());
        }
    }

    private void applyRotation(Block block, BlockFace rotation) {
        if (block.getBlockData() instanceof Rotatable rotatable) {
            rotatable.setRotation(rotation);
            block.setBlockData(rotatable, false);
        }
    }

    private BlockFace facingFromPlayer(Player player) {
        if (player == null) {
            return BlockFace.NORTH;
        }
        return player.getFacing().getOppositeFace();
    }

    private BlockFace turnLeft(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.NORTH;
        };
    }

    private BlockFace turnRight(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.NORTH;
        };
    }

    private void dropInventory(Block block) {
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return;
        }
        Inventory inventory = holder.getInventory();
        for (ItemStack stack : inventory.getContents()) {
            SfxBlockDrops.dropItem(block, stack);
        }
        inventory.clear();
    }

    private record MoveIntent(SfxBlockInstanceRecord instance, Location from, Location to, SfxAndroidInstruction instruction, boolean clearsTargetBeforeMove) {
    }

    private record LocationKey(UUID worldId, int x, int y, int z) {
        static LocationKey of(Location location) {
            return new LocationKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    private record ImportSession(UUID instanceId) {
    }

    private record UploadSession(UUID instanceId, SfxAndroidType type, List<SfxAndroidInstruction> body, SfxAndroidScriptVisibility visibility) {
        private UploadSession {
            body = List.copyOf(body);
        }
    }

    private record EditScriptSession(UUID instanceId, int page, long scriptId, boolean force) {
    }
}
