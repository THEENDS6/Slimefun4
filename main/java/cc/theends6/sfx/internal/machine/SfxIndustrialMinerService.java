package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistry;
import cc.theends6.sfx.api.behavior.SfxIndustrialMinerTargetContext;
import cc.theends6.sfx.api.behavior.SfxIndustrialMinerTargetPolicy;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.api.text.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.PistonHead;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxIndustrialMinerService implements Listener {
    private static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS
    );
    private static final BlockFace[] AXES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxBehaviorRegistry behaviors;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxMachineRuntimeEngine machineRuntime;
    private final Map<String, MiningTask> active = new ConcurrentHashMap<>();

    public SfxIndustrialMinerService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items,
                                     SfxBehaviorRegistry behaviors, SfxLocalization localization,
                                     SfxBlockDataService blockData, SfxMachineRuntimeEngine machineRuntime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.behaviors = Objects.requireNonNull(behaviors, "behaviors");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.machineRuntime = machineRuntime == null ? new SfxMachineRuntimeEngine() : machineRuntime;
        registerFrameworkDefinitions();
    }

    private void registerFrameworkDefinitions() {
        for (String id : List.of("sf:industrial_miner", "sf:advanced_industrial_miner")) {
            machineRuntime.registerDefinitionIfAbsent(SfxMachineDefinition.builder(id)
                    .displayName(id)
                    .category(SfxMachineCategory.SPECIAL)
                    .effect(SfxMachineEffect.marker("miner:validate-structure", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                    .effect(SfxMachineEffect.marker("miner:consume-fuel", SfxMachinePhase.BEFORE_INPUT))
                    .effect(SfxMachineEffect.marker("miner:animate-piston", SfxMachinePhase.BEFORE_PROGRESS))
                    .effect(SfxMachineEffect.marker("miner:extract-ore", SfxMachinePhase.ON_COMPLETE))
                    .effect(SfxMachineEffect.marker("miner:commit-output", SfxMachinePhase.AFTER_OUTPUT))
                    .effect(SfxMachineEffect.marker("miner:stop-on-error", SfxMachinePhase.ON_ERROR))
                    .build());
        }
    }

    public SfxMachinePhaseResult frameworkEffect(String effectName, SfxMachinePhaseContext context) {
        if (context == null) return SfxMachinePhaseResult.cont();
        context.put("miner.framework.effect", effectName);
        MiningTask task = context.attachment("miner.task", MiningTask.class).orElse(null);
        if ("miner:validate-structure".equals(effectName)) {
            boolean valid = task != null && isStructureValid(task.structure());
            context.put("miner.structure.valid", valid);
            return valid ? SfxMachinePhaseResult.cont() : SfxMachinePhaseResult.blocked(SfxMachineStatus.BLOCKED, "industrial miner structure is invalid");
        }
        if ("miner:consume-fuel".equals(effectName)) {
            if (task == null) {
                return SfxMachinePhaseResult.blocked(SfxMachineStatus.NO_INPUT, "industrial miner task missing");
            }
            Inventory inventory = chestInventory(task);
            if (inventory == null) {
                return SfxMachinePhaseResult.blocked(SfxMachineStatus.BLOCKED, "industrial miner chest missing");
            }
            if (task.fuelRemaining() <= 0) {
                int gained = consumeFuel(inventory, task.structure().profile());
                task.fuelRemaining(gained);
                context.put("miner.fuel.gained", gained);
            }
            return task.fuelRemaining() > 0 ? SfxMachinePhaseResult.cont() : SfxMachinePhaseResult.blocked(SfxMachineStatus.NO_INPUT, "industrial miner has no fuel");
        }
        if ("miner:animate-piston".equals(effectName)) {
            context.put("miner.animation.controlled-by-framework", Boolean.TRUE);
            return SfxMachinePhaseResult.cont();
        }
        if ("miner:extract-ore".equals(effectName) || "miner:commit-output".equals(effectName)) {
            context.put("miner.output.committed", Boolean.TRUE);
            return SfxMachinePhaseResult.cont();
        }
        if ("miner:stop-on-error".equals(effectName)) {
            context.put("miner.stop-on-error", Boolean.TRUE);
            return SfxMachinePhaseResult.cont();
        }
        context.put("miner.framework.effect.handled", Boolean.TRUE);
        return SfxMachinePhaseResult.cont();
    }

    private SfxBlockInstanceRecord minerInstance(MiningTask task) {
        if (task == null) return null;
        SfxAnchorRecord anchor = blockData.findAnchor(task.structure().blastFurnace().getLocation()).orElse(null);
        return anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
    }

    private Map<String, Object> frameworkAttributes(MiningTask task, SfxBlockInstanceRecord instance) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("miner.task", task);
        attributes.put("miner.instance", instance);
        attributes.put("framework.effect.dispatcher", (SfxMachineEffectDispatcher) this::frameworkEffect);
        return attributes;
    }

    public void shutdown() {
        active.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked.getType() != Material.BLAST_FURNACE) {
            return;
        }
        MinerStructure structure = findStructure(clicked);
        if (structure == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String key = locationKey(structure.blastFurnace().getLocation());
        if (active.containsKey(key)) {
            send(player, "machines.industrial-miner.already-running");
            return;
        }
        MiningTask task = new MiningTask(key, structure, player.getUniqueId());
        SfxBlockInstanceRecord frameworkInstance = minerInstance(task);
        if (frameworkInstance != null) {
            Map<String, Object> framework = frameworkAttributes(task, frameworkInstance);
            if (!SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(frameworkInstance.typeId(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, frameworkInstance.instanceId(), structure.blastFurnace().getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE, framework), framework, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
                send(player, "machines.industrial-miner.structure-changed");
                return;
            }
        }
        active.put(key, task);
        send(player, structure.profile().advanced() ? "machines.industrial-miner.started-advanced" : "machines.industrial-miner.started");
        warmUp(task);
    }

    private MinerStructure findStructure(Block blastFurnace) {
        if (blastFurnace.getType() != Material.BLAST_FURNACE) {
            return null;
        }
        Block chest = blastFurnace.getRelative(BlockFace.UP);
        if (chest.getType() != Material.CHEST || !(chest.getState() instanceof Chest)) {
            return null;
        }
        for (BlockFace side : AXES) {
            BlockFace other = side.getOppositeFace();
            if (chest.getRelative(side).getType() != Material.PISTON || chest.getRelative(other).getType() != Material.PISTON) {
                continue;
            }
            Block leftBase = blastFurnace.getRelative(side);
            Block rightBase = blastFurnace.getRelative(other);
            if (leftBase.getType() == Material.IRON_BLOCK && rightBase.getType() == Material.IRON_BLOCK) {
                return new MinerStructure(blastFurnace, chest, new Block[] { chest.getRelative(side), chest.getRelative(other) }, MinerProfile.normal());
            }
            if (leftBase.getType() == Material.DIAMOND_BLOCK && rightBase.getType() == Material.DIAMOND_BLOCK) {
                return new MinerStructure(blastFurnace, chest, new Block[] { chest.getRelative(side), chest.getRelative(other) }, MinerProfile.advancedProfile());
            }
        }
        return null;
    }

    private void warmUp(MiningTask task) {
        int delay = 0;
        delay = schedulePiston(task, delay + 4, 0, true);
        delay = schedulePiston(task, delay + 10, 0, false);
        delay = schedulePiston(task, delay + 8, 1, true);
        delay = schedulePiston(task, delay + 10, 1, false);
        schedule(task, delay + 1, () -> {
            if (!isActive(task)) {
                return;
            }
            Inventory inventory = chestInventory(task);
            if (inventory == null) {
                stop(task, "machines.industrial-miner.structure-changed");
                return;
            }
            task.fuelRemaining(consumeFuel(inventory, task.structure().profile()));
            if (task.fuelRemaining() <= 0) {
                stop(task, "machines.industrial-miner.no-fuel");
            }
        });
        delay += 1;
        delay = schedulePiston(task, delay + 6, 0, true);
        delay = schedulePiston(task, delay + 9, 0, false);
        delay = schedulePiston(task, delay + 4, 1, true);
        delay = schedulePiston(task, delay + 7, 1, false);
        delay = schedulePiston(task, delay + 3, 0, true);
        delay = schedulePiston(task, delay + 5, 0, false);
        delay = schedulePiston(task, delay + 2, 1, true);
        delay = schedulePiston(task, delay + 4, 1, false);
        delay = schedulePiston(task, delay + 1, 0, true);
        delay = schedulePiston(task, delay + 3, 0, false);
        delay = schedulePiston(task, delay + 1, 1, true);
        delay = schedulePiston(task, delay + 2, 1, false);
        schedule(task, delay + 1, () -> runMiningCycle(task));
    }

    private int schedulePiston(MiningTask task, int delay, int pistonIndex, boolean extended) {
        schedule(task, delay, () -> setPistonState(task, task.structure().pistons()[pistonIndex], extended));
        return delay;
    }

    private void schedule(MiningTask task, long delay, Runnable action) {
        runtime.executeAtLater(task.structure().blastFurnace().getLocation(), delay, () -> {
            if (!isActive(task)) {
                return;
            }
            if (runtime.isGameTickFrozen()) {
                schedule(task, 1L, action);
                return;
            }
            action.run();
        });
    }

    private boolean isActive(MiningTask task) {
        return active.get(task.key()) == task;
    }

    private void runMiningCycle(MiningTask task) {
        if (!isActive(task)) {
            return;
        }
        int delay = 0;
        delay = schedulePiston(task, delay + 1, 0, true);
        delay = schedulePiston(task, delay + 3, 0, false);
        delay = schedulePiston(task, delay + 1, 1, true);
        delay = schedulePiston(task, delay + 3, 1, false);
        schedule(task, delay + 1, () -> mineOneStep(task));
    }

    private void mineOneStep(MiningTask task) {
        SfxBlockInstanceRecord frameworkInstance = minerInstance(task);
        Map<String, Object> framework = frameworkAttributes(task, frameworkInstance);
        SfxMachineTickContext tickContext = new SfxMachineTickContext(0L, 1L, false);
        try {
            if (frameworkInstance != null && !SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(frameworkInstance.typeId(), SfxMachinePhase.BEFORE_OPERATION_RESOLVE, frameworkInstance.instanceId(), task.structure().blastFurnace().getLocation(), tickContext, null, SfxMachineStatus.IDLE, framework), framework, SfxMachinePhase.BEFORE_OPERATION_RESOLVE.name())) {
                stop(task, "machines.industrial-miner.structure-changed");
                return;
            }
            if (!isStructureValid(task.structure())) {
                stop(task, "machines.industrial-miner.structure-changed");
                return;
            }
            Inventory inventory = chestInventory(task);
            if (inventory == null) {
                stop(task, "machines.industrial-miner.structure-changed");
                return;
            }
            if (frameworkInstance != null && !SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(frameworkInstance.typeId(), SfxMachinePhase.BEFORE_INPUT, frameworkInstance.instanceId(), task.structure().blastFurnace().getLocation(), tickContext, null, SfxMachineStatus.IDLE, framework), framework, SfxMachinePhase.BEFORE_INPUT.name())) {
                stop(task, "machines.industrial-miner.no-fuel");
                return;
            }
            if (task.fuelRemaining() <= 0) {
                int gained = consumeFuel(inventory, task.structure().profile());
                if (gained <= 0) {
                    stop(task, "machines.industrial-miner.no-fuel");
                    return;
                }
                task.fuelRemaining(gained);
            }
            Block ore = findNextOre(task);
            if (ore == null) {
                active.remove(task.key());
                Player player = plugin.getServer().getPlayer(task.ownerId());
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.4F, 1F);
                    send(player, "machines.industrial-miner.finished", Map.of("ores", task.oresMined()));
                }
                return;
            }
            Block minedBlock = selectMiningTarget(task, ore);
            Material minedType = minedBlock.getType();
            List<ItemStack> drops = dropsFor(minedBlock, task.structure().profile());
            if (!canInsertAll(inventory, drops)) {
                stop(task, "machines.industrial-miner.chest-full");
                return;
            }
            for (ItemStack drop : drops) {
                cc.theends6.sfx.internal.inventory.SfxInventoryMutationBridge.insertAll(inventory, drop, false, "industrial-miner:ore-drop");
            }
            framework.put("miner.drops", drops.size());
            Block furnace = task.structure().blastFurnace();
            furnace.getWorld().playEffect(furnace.getLocation(), Effect.STEP_SOUND, minedType);
            furnace.getWorld().playSound(furnace.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.BLOCKS, 0.2F, 1.0F);
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, frameworkInstance == null ? "sf:industrial_miner" : frameworkInstance.typeId(), minedBlock, Material.AIR, true, "industrial-miner", "extract-target");
            task.fuelRemaining(task.fuelRemaining() - 1);
            if (ORES.contains(minedType)) {
                task.oresMined(task.oresMined() + 1);
            }
            if (frameworkInstance != null) {
                if (SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(frameworkInstance.typeId(), SfxMachinePhase.ON_COMPLETE, frameworkInstance.instanceId(), task.structure().blastFurnace().getLocation(), tickContext, null, SfxMachineStatus.RUNNING, framework), framework, SfxMachinePhase.ON_COMPLETE.name())) {
                    SfxMachinePipelineGuard.proceed(machineRuntime.runPhase(frameworkInstance.typeId(), SfxMachinePhase.AFTER_OUTPUT, frameworkInstance.instanceId(), task.structure().blastFurnace().getLocation(), tickContext, null, SfxMachineStatus.RUNNING, framework), framework, SfxMachinePhase.AFTER_OUTPUT.name());
                    machineRuntime.runPhase(frameworkInstance.typeId(), SfxMachinePhase.AFTER_TICK, frameworkInstance.instanceId(), task.structure().blastFurnace().getLocation(), tickContext, null, SfxMachineStatus.RUNNING, framework);
                }
            }
            runtime.executeAtLater(task.structure().blastFurnace().getLocation(), Math.max(1L, plugin.getConfig().getLong("legacy.industrial-miner.step-delay-ticks", 4L)), () -> runMiningCycle(task));
        } catch (RuntimeException exception) {
            if (frameworkInstance != null) {
                framework.put("miner.error", exception.getClass().getName());
                machineRuntime.runPhase(frameworkInstance.typeId(), SfxMachinePhase.ON_ERROR, frameworkInstance.instanceId(), task.structure().blastFurnace().getLocation(), tickContext, null, SfxMachineStatus.ERROR, framework);
            }
            plugin.getLogger().warning("Industrial Miner task failed: " + exception.getMessage());
            active.remove(task.key());
        }
    }

    private Inventory chestInventory(MiningTask task) {
        Block chest = task.structure().chest();
        if (chest.getType() == Material.CHEST && chest.getState() instanceof Chest chestState) {
            return chestState.getBlockInventory();
        }
        return null;
    }

    private boolean isStructureValid(MinerStructure structure) {
        MinerStructure found = findStructure(structure.blastFurnace());
        if (found == null) {
            return false;
        }
        return found.profile().advanced() == structure.profile().advanced();
    }

    private Block findNextOre(MiningTask task) {
        MinerStructure structure = task.structure();
        int range = structure.profile().range();
        Block origin = structure.blastFurnace();
        World world = origin.getWorld();
        int minY = world.getMinHeight();
        int width = range * 2 + 1;
        int maxSteps = width * width * Math.max(1, origin.getY() - minY);
        for (int i = 0; i < maxSteps; i++) {
            int cursor = task.cursor();
            task.cursor(cursor + 1);
            int column = cursor % (width * width);
            int dy = cursor / (width * width);
            int dx = (column % width) - range;
            int dz = (column / width) - range;
            int y = origin.getY() - 1 - dy;
            if (y < minY) {
                task.cursor(0);
                continue;
            }
            Block candidate = world.getBlockAt(origin.getX() + dx, y, origin.getZ() + dz);
            if (ORES.contains(candidate.getType()) && blockData.findAnchor(candidate.getLocation()).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private Block selectMiningTarget(MiningTask task, Block ore) {
        List<Block> adjacentCandidates = safeAdjacentTargets(ore);
        SfxIndustrialMinerTargetContext context = new SfxIndustrialMinerTargetContext(
                task.structure().profile().advanced(), ore, adjacentCandidates);
        Block target = ore;
        for (SfxIndustrialMinerTargetPolicy policy : behaviors.industrialMinerTargetPolicies()) {
            try {
                Block selected = policy.selectTarget(context, target);
                if (selected != null && (selected.equals(ore) || adjacentCandidates.contains(selected))) {
                    target = selected;
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Industrial Miner target policy failed: " + exception.getMessage());
            }
        }
        return target;
    }

    private List<Block> safeAdjacentTargets(Block ore) {
        List<Block> candidates = new ArrayList<>(6);
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            Block candidate = ore.getRelative(face);
            Material type = candidate.getType();
            if (!type.isBlock() || !type.isSolid() || type.getHardness() < 0.0F
                    || candidate.getState() instanceof TileState
                    || blockData.findAnchor(candidate.getLocation()).isPresent()) {
                continue;
            }
            candidates.add(candidate);
        }
        return List.copyOf(candidates);
    }

    private List<ItemStack> dropsFor(Block ore, MinerProfile profile) {
        if (profile.advanced()) {
            return List.of(new ItemStack(ore.getType()));
        }
        Collection<ItemStack> drops = ore.getDrops(new ItemStack(Material.IRON_PICKAXE));
        return drops.isEmpty() ? List.of(new ItemStack(ore.getType())) : new ArrayList<>(drops);
    }

    private int consumeFuel(Inventory inventory, MinerProfile profile) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            for (Fuel fuel : profile.fuels()) {
                if (!fuel.matches(stack, items)) {
                    continue;
                }
                int next = stack.getAmount() - 1;
                inventory.setItem(slot, next <= 0 ? null : withAmount(stack, next));
                if (fuel.returnItem() != null) {
                    cc.theends6.sfx.internal.inventory.SfxInventoryMutationBridge.insertAll(inventory, new ItemStack(fuel.returnItem()), false, "industrial-miner:fuel-return");
                }
                return fuel.ores();
            }
        }
        return 0;
    }

    private boolean canInsertAll(Inventory inventory, List<ItemStack> drops) {
        Inventory clone = Bukkit.createInventory(null, inventory.getSize());
        clone.setContents(inventory.getContents());
        for (ItemStack drop : drops) {
            if (!cc.theends6.sfx.internal.inventory.SfxInventoryMutationBridge.insertAll(clone, drop.clone(), false, "industrial-miner:simulate").success()) {
                return false;
            }
        }
        return true;
    }

    private ItemStack withAmount(ItemStack stack, int amount) {
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }

    private void setPistonState(MiningTask task, Block block, boolean extended) {
        if (!isActive(task)) {
            return;
        }
        SfxBlockInstanceRecord frameworkInstance = minerInstance(task);
        if (frameworkInstance != null) {
            Map<String, Object> framework = frameworkAttributes(task, frameworkInstance);
            framework.put("miner.piston.extended", extended);
            machineRuntime.runPhase(frameworkInstance.typeId(), SfxMachinePhase.BEFORE_PROGRESS, frameworkInstance.instanceId(), task.structure().blastFurnace().getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE, framework);
        }
        try {
            Location particleLoc = task.structure().blastFurnace().getLocation().clone();
            block.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 20, 0.7D, 0.7D, 0.7D, 0.0D);
            if (block.getType() == Material.MOVING_PISTON) {
                cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, frameworkInstance == null ? "sf:industrial_miner" : frameworkInstance.typeId(), block.getRelative(BlockFace.UP), Material.AIR, false, "industrial-miner", "clear-above");
                return;
            }
            if (block.getType() != Material.PISTON || !(block.getBlockData() instanceof Piston piston)) {
                stop(task, "machines.industrial-miner.structure-changed");
                return;
            }
            Block above = block.getRelative(BlockFace.UP);
            if (!above.isEmpty() && above.getType() != Material.PISTON_HEAD) {
                stop(task, "machines.industrial-miner.piston-no-space");
                return;
            }
            if (piston.getFacing() != BlockFace.UP) {
                stop(task, "machines.industrial-miner.piston-wrong-direction");
                return;
            }
            piston.setExtended(extended);
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(machineRuntime, frameworkInstance == null ? "sf:industrial_miner" : frameworkInstance.typeId(), block, piston, false, "industrial-miner", "restore-piston-facing");
            if (extended) {
                PistonHead head = (PistonHead) Material.PISTON_HEAD.createBlockData();
                head.setFacing(BlockFace.UP);
                cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(machineRuntime, frameworkInstance == null ? "sf:industrial_miner" : frameworkInstance.typeId(), above, head, false, "industrial-miner", "restore-piston-head-facing");
            } else if (above.getType() == Material.PISTON_HEAD) {
                cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(machineRuntime, frameworkInstance == null ? "sf:industrial_miner" : frameworkInstance.typeId(), above, Material.AIR, false, "industrial-miner", "clear-above");
            }
            block.getWorld().playSound(block.getLocation(), extended ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.1F, 1.0F);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Industrial Miner piston animation failed: " + exception.getMessage());
            stop(task, "machines.industrial-miner.structure-changed");
        }
    }

    private void stop(MiningTask task, String key) {
        active.remove(task.key());
        Player player = plugin.getServer().getPlayer(task.ownerId());
        if (player != null && player.isOnline()) {
            send(player, key);
        }
    }

    private void send(Player player, String key) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key)));
    }

    private void send(Player player, String key, Map<String, ?> placeholders) {
        player.sendMessage(Text.prefixed(plugin, localization.text(key, placeholders)));
    }

    private String locationKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private record MinerStructure(Block blastFurnace, Block chest, Block[] pistons, MinerProfile profile) {
    }

    private record Fuel(int ores, Material material, String itemId, Material returnItem) {
        static Fuel material(int ores, Material material) {
            return new Fuel(ores, material, null, null);
        }

        static Fuel material(int ores, Material material, Material returnItem) {
            return new Fuel(ores, material, null, returnItem);
        }

        static Fuel itemId(int ores, String itemId, Material returnItem) {
            return new Fuel(ores, null, itemId, returnItem);
        }

        boolean matches(ItemStack stack, SfxItems items) {
            if (stack == null || stack.getType().isAir()) {
                return false;
            }
            if (material != null && stack.getType() == material) {
                return true;
            }
            if (itemId == null) {
                return false;
            }
            return items.readMarker(stack).map(SfxItemMarker::itemId).map(id -> id.equals(itemId)).orElse(false);
        }
    }

    private record MinerProfile(boolean advanced, int range, List<Fuel> fuels) {
        static MinerProfile normal() {
            List<Fuel> fuels = new ArrayList<>();
            fuels.add(Fuel.material(4, Material.COAL));
            fuels.add(Fuel.material(4, Material.CHARCOAL));
            fuels.add(Fuel.material(40, Material.COAL_BLOCK));
            fuels.add(Fuel.material(10, Material.DRIED_KELP_BLOCK));
            fuels.add(Fuel.material(4, Material.BLAZE_ROD));
            for (Material material : Material.values()) {
                String name = material.name();
                if (name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) {
                    fuels.add(Fuel.material(1, material));
                }
            }
            return new MinerProfile(false, 3, List.copyOf(fuels));
        }

        static MinerProfile advancedProfile() {
            return new MinerProfile(true, 5, List.of(
                    Fuel.material(48, Material.LAVA_BUCKET, Material.BUCKET),
                    Fuel.itemId(64, "sf:bucket_of_oil", Material.BUCKET),
                    Fuel.itemId(64, "sf:oil_bucket", Material.BUCKET),
                    Fuel.itemId(128, "sf:bucket_of_fuel", Material.BUCKET),
                    Fuel.itemId(128, "sf:fuel_bucket", Material.BUCKET)
            ));
        }
    }

    private static final class MiningTask {
        private final String key;
        private final MinerStructure structure;
        private final UUID ownerId;
        private int fuelRemaining;
        private int cursor;
        private int oresMined;

        private MiningTask(String key, MinerStructure structure, UUID ownerId) {
            this.key = key;
            this.structure = structure;
            this.ownerId = ownerId;
        }

        String key() { return key; }
        MinerStructure structure() { return structure; }
        UUID ownerId() { return ownerId; }
        int fuelRemaining() { return fuelRemaining; }
        void fuelRemaining(int fuelRemaining) { this.fuelRemaining = fuelRemaining; }
        int cursor() { return cursor; }
        void cursor(int cursor) { this.cursor = cursor; }
        int oresMined() { return oresMined; }
        void oresMined(int oresMined) { this.oresMined = oresMined; }
    }
}
