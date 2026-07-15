package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.machine.manual.SfxManualMachineDefinition;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineRecipe;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOperation;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOutput;

import static cc.theends6.sfx.internal.bootstrap.BaseContentBootstrap.*;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxBasicMachineBlockListener;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.api.text.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ManualMachineService {
    private static final BlockFace[] OUTPUT_SEARCH_ORDER = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };
    private static final long COMPRESSOR_CONTRACT_TICKS = 20L;
    private static final long COMPRESSOR_EXTEND_TICKS = 40L;
    private static final long COMPRESSOR_COMPLETE_TICKS = 60L;
    private static final long ARMOR_FORGE_WORK_TICKS = 20L;
    private static final long ARMOR_FORGE_COMPLETE_TICKS = 60L;
    private static final int MATCH_CACHE_LIMIT = 512;

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final DefaultManualMachineRegistry registry;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBasicMachineBlockListener basicBlockMachines;
    private final Map<MatchCacheKey, List<SfxManualMachineRecipe>> matchCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MatchCacheKey, List<SfxManualMachineRecipe>> eldest) {
            return size() > MATCH_CACHE_LIMIT;
        }
    };

    public ManualMachineService(JavaPlugin plugin, SfxRuntime runtime, DefaultManualMachineRegistry registry, SfxItems items, SfxLocalization localization, SfxBasicMachineBlockListener basicBlockMachines) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.basicBlockMachines = Objects.requireNonNull(basicBlockMachines, "basicBlockMachines");
    }

    public boolean tryInteract(Player player, Block clickedBlock) {
        if (clickedBlock == null) {
            return false;
        }
        SfxManualMachineDefinition matched = null;
        for (SfxManualMachineDefinition definition : registry.machines()) {
            if (definition.matches(clickedBlock)) {
                matched = definition;
                break;
            }
        }
        if (matched == null) {
            return false;
        }
        SfxManualMachineDefinition definition = matched;
        runtime.executeAt(clickedBlock.getLocation(), () -> runMachine(player, clickedBlock, definition));
        return true;
    }

    private void runMachine(Player player, Block clickedBlock, SfxManualMachineDefinition definition) {
        if (!definition.matches(clickedBlock)) {
            message(player, localization.text("machines.structure-changed"));
            return;
        }

        if (requiresIgnitionFire(definition) && !hasIgnitionFire(definition, clickedBlock)) {
            message(player, localization.text("machines.smeltery-not-lit"));
            return;
        }

        if (definition.operation() == SfxManualMachineOperation.HAND_INPUT) {
            runHandMachine(player, clickedBlock, definition);
            return;
        }

        Dispenser dispenser = resolveInputDispenser(definition, clickedBlock);
        if (dispenser == null) {
            message(player, localization.text("machines.missing-dispenser"));
            return;
        }

        Inventory input = dispenser.getInventory();
        Inventory output = resolveOutputInventory(definition, clickedBlock, dispenser, input);
        Collection<SfxManualMachineRecipe> recipes = registry.recipesFor(definition.id());
        if (recipes.isEmpty()) {
            message(player, localization.text("machines.no-recipes"));
            return;
        }

        if (isInventoryEmpty(input)) {
            message(player, emptyMessage(definition));
            return;
        }

        boolean matchedInput = false;
        boolean outputBlocked = false;
        List<SfxManualMachineRecipe> singleCraftable = new ArrayList<>();
        List<ShapedMatchPlan> shapedCraftable = new ArrayList<>();
        ItemStack[] inputContents = input.getContents();
        ManualRecipeHash orderedHash = ManualRecipeHash.orderedInput(inputContents, items);
        List<SfxManualMachineRecipe> orderedCandidates = cachedCandidates(definition.id(), MatchKind.ORDERED, orderedHash,
                registry.orderedCandidates(definition.id(), orderedHash));
        for (SfxManualMachineRecipe recipe : orderedCandidates) {
            if (recipe.operation() == SfxManualMachineOperation.SHAPED_3X3) {
                ShapedMatchPlan plan = planShaped(input, recipe);
                if (plan == null) {
                    continue;
                }
                matchedInput = true;
                OutputPlan outputPlan = planOutputAfterConsume(input, output, plan.inputAfterConsume(), plan.recipe().fixedOutputs());
                if (outputPlan != null) {
                    shapedCraftable.add(plan.withOutputPlan(outputPlan));
                } else {
                    outputBlocked = true;
                }
            }
        }
        for (SfxManualMachineRecipe recipe : recipes) {
            if (recipe.operation() == SfxManualMachineOperation.SINGLE_INPUT) {
                MatchResult result = matchSingle(input, output, recipe);
                if (result == MatchResult.INPUT_MATCH_AND_FITS) {
                    singleCraftable.add(recipe);
                    matchedInput = true;
                } else if (result == MatchResult.INPUT_MATCH_BUT_FULL) {
                    matchedInput = true;
                    outputBlocked = true;
                }
            }
        }

        if (!shapedCraftable.isEmpty()) {
            craftShaped(clickedBlock, input, output, shapedCraftable.getFirst(), definition);
            return;
        }
        if (!singleCraftable.isEmpty()) {
            craftSingle(clickedBlock, input, output, singleCraftable.get(ThreadLocalRandom.current().nextInt(singleCraftable.size())), definition);
            return;
        }

        ShapelessMatchPlan shapeless = findShapelessMatch(definition.id(), inputContents, recipes);
        if (shapeless != null) {
            if (!canFitAfterShapelessConsume(input, output, shapeless)) {
                message(player, localization.text("machines.output-full"));
                return;
            }
            List<SfxManualMachineOutput> outputs = selectedOutputs(shapeless.recipe());
            applyShapelessConsumption(input, shapeless);
            if (isDelayedCompletionMachine(definition)) startDelayedCompletion(clickedBlock, definition, outputs);
            else {
                addOutputs(output, outputs);
                success(clickedBlock, definition);
            }
            return;
        }

        if (outputBlocked && matchedInput) {
            message(player, localization.text("machines.output-full"));
            return;
        }

        message(player, noMatchMessage(definition));
    }

    private ShapelessMatchPlan findShapelessMatch(String machineId, ItemStack[] contents, Collection<SfxManualMachineRecipe> allRecipes) {
        ManualRecipeHash hash = ManualRecipeHash.unorderedInput(contents, items);
        List<SfxManualMachineRecipe> indexed = cachedCandidates(machineId, MatchKind.UNORDERED, hash,
                registry.unorderedCandidates(machineId, hash));
        ShapelessMatchPlan best = bestShapeless(contents, indexed);
        if (best != null) return best;
        
        return bestShapeless(contents, allRecipes);
    }

    private ShapelessMatchPlan bestShapeless(ItemStack[] contents, Collection<SfxManualMachineRecipe> recipes) {
        ShapelessMatchPlan best = null;
        for (SfxManualMachineRecipe recipe : recipes) {
            if (recipe.operation() != SfxManualMachineOperation.SHAPELESS_INPUT) continue;
            ShapelessMatchPlan plan = planShapeless(contents, recipe);
            if (plan != null && (best == null || recipe.priority() > best.recipe().priority())) best = plan;
        }
        return best;
    }

    private List<SfxManualMachineRecipe> cachedCandidates(String machineId, MatchKind kind, ManualRecipeHash hash,
                                                        List<SfxManualMachineRecipe> indexed) {
        MatchCacheKey key = new MatchCacheKey(registry.revision(), machineId, kind, hash);
        synchronized (matchCache) {
            List<SfxManualMachineRecipe> cached = matchCache.get(key);
            if (cached != null) return cached;
            List<SfxManualMachineRecipe> candidates = List.copyOf(indexed);
            matchCache.put(key, candidates);
            return candidates;
        }
    }

    private boolean requiresIgnitionFire(SfxManualMachineDefinition definition) {
        return SMELTERY.equals(definition.id()) || MAKESHIFT_SMELTERY.equals(definition.id());
    }

    private boolean hasIgnitionFire(SfxManualMachineDefinition definition, Block clickedBlock) {
        Block fireBlock = definition.centerBlock(clickedBlock).getRelative(BlockFace.DOWN);
        Material type = fireBlock.getType();
        return type == Material.FIRE || type == Material.SOUL_FIRE;
    }

    private Dispenser resolveInputDispenser(SfxManualMachineDefinition definition, Block clickedBlock) {
        if (MAGIC_WORKBENCH.equals(definition.id())) {
            Block center = definition.centerBlock(clickedBlock);
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
                BlockState adjacent = center.getRelative(face).getState();
                if (adjacent instanceof Dispenser dispenser) {
                    return dispenser;
                }
            }
            return null;
        }
        Block machineInventoryBlock = definition.inventoryBlock(clickedBlock);
        BlockState state = machineInventoryBlock.getState();
        return state instanceof Dispenser dispenser ? dispenser : null;
    }

    private Inventory resolveOutputInventory(SfxManualMachineDefinition definition, Block clickedBlock, Dispenser input, Inventory inputInventory) {
        Block center = definition.centerBlock(clickedBlock);
        Block inventoryBlock = MAGIC_WORKBENCH.equals(definition.id()) ? input.getBlock() : definition.inventoryBlock(clickedBlock);
        Inventory found = adjacentInventory(center, inventoryBlock, input.getBlock());
        return found == null ? inputInventory : found;
    }

    private Inventory resolveHandOutputInventory(SfxManualMachineDefinition definition, Block clickedBlock) {
        Block center = definition.centerBlock(clickedBlock);
        Block inventoryBlock = definition.inventoryBlock(clickedBlock);
        return adjacentInventory(center, inventoryBlock, null);
    }

    private Inventory adjacentInventory(Block center, Block inventoryBlock, Block inputBlock) {
        for (Block origin : List.of(inputBlock, inventoryBlock, center)) {
            if (origin == null) {
                continue;
            }
            Optional<Inventory> outputChest = basicBlockMachines.findAnyOutputChestFor(origin);
            if (outputChest.isPresent()) {
                return outputChest.get();
            }
        }
        return null;
    }

    private String emptyMessage(SfxManualMachineDefinition definition) {
        if (definition.operation() == SfxManualMachineOperation.SHAPED_3X3) {
            return localization.text("machines.inventory-empty");
        }
        if (definition.operation() == SfxManualMachineOperation.HAND_INPUT) {
            return localization.text("machines.empty");
        }
        return localization.text("machines.empty");
    }

    private String noMatchMessage(SfxManualMachineDefinition definition) {
        if (definition.operation() == SfxManualMachineOperation.SHAPED_3X3) {
            return localization.text("machines.pattern-not-found");
        }
        if (definition.operation() == SfxManualMachineOperation.HAND_INPUT) {
            return localization.text("machines.unknown-material");
        }
        return localization.text("machines.unknown-material");
    }

    private ShapedMatchPlan planShaped(Inventory input, SfxManualMachineRecipe recipe) {
        ItemStack[] inputCopy = cloneContents(input);
        int[] consumed = new int[inputCopy.length];
        for (int i = 0; i < 9; i++) {
            ItemStack current = inputCopy[i];
            SfxRecipeSlot required = recipe.input().get(i);
            if (!matchesSlot(current, required, true)) {
                return null;
            }
            if (required != null && !required.isEmpty()) {
                consumed[i] = required.amount();
                consume(current, required.amount());
                if (current != null && current.getAmount() <= 0) {
                    inputCopy[i] = null;
                }
            }
        }
        return new ShapedMatchPlan(recipe, consumed, inputCopy);
    }

    private void craftShaped(Block clickedBlock, Inventory input, Inventory output, ShapedMatchPlan plan, SfxManualMachineDefinition definition) {
        if (plan.outputPlan() == null) {
            return;
        }
        applySlotConsumption(input, plan.consumed());
        if (ARMOR_FORGE.equals(definition.id())) {
            startArmorForgeCompletion(clickedBlock, definition, plan.recipe().fixedOutputs());
            return;
        }
        applyOutputPlan(output, plan.outputPlan());
        if (isDelayedCompletionMachine(definition)) {
            startDelayedCompletion(clickedBlock, definition, plan.recipe().fixedOutputs());
        } else {
            success(clickedBlock, definition);
        }
    }

    private MatchResult matchSingle(Inventory input, Inventory output, SfxManualMachineRecipe recipe) {
        SfxRecipeSlot required = recipe.input().get(0);
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack current = input.getItem(i);
            if (matchesSlot(current, required, false)) {
                boolean fits = recipe.hasRandomOutputs()
                        ? canFitRandomAfterConsume(input, output, i, required.amount(), recipe)
                        : canFitAfterConsume(input, output, i, required.amount(), recipe.fixedOutputs());
                return fits ? MatchResult.INPUT_MATCH_AND_FITS : MatchResult.INPUT_MATCH_BUT_FULL;
            }
        }
        return MatchResult.NO_INPUT_MATCH;
    }

    private void craftSingle(Block clickedBlock, Inventory input, Inventory output, SfxManualMachineRecipe recipe, SfxManualMachineDefinition definition) {
        SfxRecipeSlot required = recipe.input().get(0);
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack current = input.getItem(i);
            if (!matchesSlot(current, required, false)) {
                continue;
            }
            boolean fits = recipe.hasRandomOutputs()
                    ? canFitRandomAfterConsume(input, output, i, required.amount(), recipe)
                    : canFitAfterConsume(input, output, i, required.amount(), recipe.fixedOutputs());
            if (!fits) {
                return;
            }
            List<SfxManualMachineOutput> outputs = selectedOutputs(recipe);
            consume(input, i, required.amount());
            if (isDelayedCompletionMachine(definition)) {
                startDelayedCompletion(clickedBlock, definition, outputs);
            } else {
                addOutputs(output, outputs);
                success(clickedBlock, definition);
            }
            return;
        }
    }

    private void runHandMachine(Player player, Block clickedBlock, SfxManualMachineDefinition definition) {
        Collection<SfxManualMachineRecipe> recipes = registry.recipesFor(definition.id());
        if (recipes.isEmpty()) {
            message(player, localization.text("machines.no-recipes"));
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || held.getAmount() <= 0) {
            message(player, emptyMessage(definition));
            return;
        }

        List<SfxManualMachineRecipe> craftable = new ArrayList<>();
        for (SfxManualMachineRecipe recipe : recipes) {
            SfxRecipeSlot required = recipe.input().get(0);
            if (matchesSlot(held, required, false)) {
                craftable.add(recipe);
            }
        }

        if (craftable.isEmpty()) {
            message(player, noMatchMessage(definition));
            return;
        }

        SfxManualMachineRecipe recipe = craftable.get(ThreadLocalRandom.current().nextInt(craftable.size()));
        List<SfxManualMachineOutput> outputs = selectedOutputs(recipe);
        Inventory output = resolveHandOutputInventory(definition, clickedBlock);
        if (output != null && !canFitAll(cloneContents(output), outputs)) {
            message(player, localization.text("machines.output-full"));
            return;
        }

        SfxRecipeSlot required = recipe.input().get(0);
        if (player.getGameMode() != GameMode.CREATIVE) {
            consume(held, required.amount());
        }
        if (output != null) {
            addOutputs(output, outputs);
        } else {
            dropOutputs(clickedBlock, outputs);
        }
        success(clickedBlock, definition);
    }

    private List<SfxManualMachineOutput> selectedOutputs(SfxManualMachineRecipe recipe) {
        if (!recipe.hasRandomOutputs()) {
            return recipe.fixedOutputs();
        }
        List<SfxManualMachineOutput> selected = new ArrayList<>(recipe.fixedOutputs());
        List<SfxManualMachineOutput> randomOutputs = recipe.randomOutputs();
        selected.add(randomOutputs.get(ThreadLocalRandom.current().nextInt(randomOutputs.size())));
        return selected;
    }

    private boolean isDelayedCompletionMachine(SfxManualMachineDefinition definition) {
        return MANUAL_COMPRESSOR.equals(definition.id()) || PRESSURE_CHAMBER.equals(definition.id());
    }

    private void startDelayedCompletion(Block clickedBlock, SfxManualMachineDefinition definition, List<SfxManualMachineOutput> outputs) {
        if (PRESSURE_CHAMBER.equals(definition.id())) {
            startPressureChamberCompletion(clickedBlock, definition, outputs);
            return;
        }
        Location origin = clickedBlock.getLocation().clone();
        compressorTick(origin, 0);
        runDelayedAt(origin, COMPRESSOR_CONTRACT_TICKS, () -> compressorTick(origin, 1));
        runDelayedAt(origin, COMPRESSOR_EXTEND_TICKS, () -> compressorTick(origin, 2));
        runDelayedAt(origin, COMPRESSOR_COMPLETE_TICKS, () -> completeDelayedOperation(origin, definition, outputs));
    }

    private void startArmorForgeCompletion(Block clickedBlock, SfxManualMachineDefinition definition, List<SfxManualMachineOutput> outputs) {
        Location origin = clickedBlock.getLocation().clone();
        armorForgeTick(origin, false);
        runDelayedAt(origin, ARMOR_FORGE_WORK_TICKS, () -> armorForgeTick(origin, false));
        runDelayedAt(origin, ARMOR_FORGE_WORK_TICKS * 2, () -> armorForgeTick(origin, false));
        runDelayedAt(origin, ARMOR_FORGE_COMPLETE_TICKS, () -> completeArmorForgeOperation(origin, definition, outputs));
    }

    private void startPressureChamberCompletion(Block clickedBlock, SfxManualMachineDefinition definition, List<SfxManualMachineOutput> outputs) {
        Location origin = clickedBlock.getLocation().clone();
        pressureChamberTick(origin, false);
        runDelayedAt(origin, 20L, () -> pressureChamberTick(origin, false));
        runDelayedAt(origin, 40L, () -> pressureChamberTick(origin, false));
        runDelayedAt(origin, 60L, () -> completePressureChamberOperation(origin, definition, outputs));
    }

    private void compressorTick(Location origin, int step) {
        if (step == 1) {
            playBlockSound(origin, Sound.BLOCK_PISTON_CONTRACT, 1.0f, 1.0f);
        } else {
            playBlockSound(origin, Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.0f);
        }
    }

    private void armorForgeTick(Location origin, boolean finished) {
        if (finished) {
            playBlockSound(origin, Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
        } else {
            playBlockSound(origin, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.0f);
        }
    }

    private void pressureChamberTick(Location origin, boolean finished) {
        World world = origin.getWorld();
        if (world != null) {
            Location smoke = origin.clone().add(0.5, 1.5, 0.5);
            world.spawnParticle(Particle.SMOKE, smoke, 12, 0.0, 0.0, 0.0, 0.02);
        }
        if (finished) {
            playBlockSound(origin, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.0f);
        } else {
            playBlockSound(origin, Sound.ENTITY_TNT_PRIMED, 0.8f, 1.0f);
        }
    }

    private void completeDelayedOperation(Location origin, SfxManualMachineDefinition definition, List<SfxManualMachineOutput> outputs) {
        Block clickedBlock = origin.getBlock();
        Inventory output = null;
        if (definition.matches(clickedBlock)) {
            Dispenser dispenser = resolveInputDispenser(definition, clickedBlock);
            if (dispenser != null) {
                output = resolveOutputInventory(definition, clickedBlock, dispenser, dispenser.getInventory());
            }
        }

        if (output != null && canFitAll(cloneContents(output), outputs)) {
            addOutputs(output, outputs);
        } else {
            dropOutputs(origin, outputs);
        }
        playBlockSound(origin, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.0f);
    }

    private void completeArmorForgeOperation(Location origin, SfxManualMachineDefinition definition, List<SfxManualMachineOutput> outputs) {
        Block clickedBlock = origin.getBlock();
        Inventory output = null;
        if (definition.matches(clickedBlock)) {
            Dispenser dispenser = resolveInputDispenser(definition, clickedBlock);
            if (dispenser != null) {
                output = resolveOutputInventory(definition, clickedBlock, dispenser, dispenser.getInventory());
            }
        }

        if (output != null) {
            addOutputsOrDrop(origin, output, outputs);
        } else {
            dropOutputs(origin, outputs);
        }
        armorForgeTick(origin, true);
    }

    private void completePressureChamberOperation(Location origin, SfxManualMachineDefinition definition, List<SfxManualMachineOutput> outputs) {
        Block clickedBlock = origin.getBlock();
        Inventory output = null;
        if (definition.matches(clickedBlock)) {
            Dispenser dispenser = resolveInputDispenser(definition, clickedBlock);
            if (dispenser != null) {
                output = resolveOutputInventory(definition, clickedBlock, dispenser, dispenser.getInventory());
            }
        }

        if (output != null && canFitAll(cloneContents(output), outputs)) {
            addOutputs(output, outputs);
        } else {
            dropOutputs(origin, outputs);
        }
        pressureChamberTick(origin, true);
    }

    private void runDelayedAt(Location location, long delayTicks, Runnable task) {
        plugin.getServer().getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
    }

    private void addOutputs(Inventory inventory, List<SfxManualMachineOutput> outputs) {
        for (SfxManualMachineOutput output : outputs) {
            cc.theends6.sfx.internal.inventory.SfxInventoryMutationBridge.insertAll(inventory, output.create(items), false, "manual-machine:output");
        }
    }

    private void addOutputsOrDrop(Location origin, Inventory inventory, List<SfxManualMachineOutput> outputs) {
        Location dropLocation = origin.clone().add(0.5, 0.8, 0.5);
        for (SfxManualMachineOutput output : outputs) {
            ItemStack stack = output.create(items);
            cc.theends6.sfx.internal.inventory.SfxInventoryMutationBridge.insertAllOrDrop(inventory, stack, false, dropLocation, "manual-machine:output-or-drop");
        }
    }

    private void dropOutputs(Block clickedBlock, List<SfxManualMachineOutput> outputs) {
        dropOutputs(clickedBlock.getLocation(), outputs);
    }

    private void dropOutputs(Location origin, List<SfxManualMachineOutput> outputs) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        var location = origin.clone().add(0.5, 0.8, 0.5);
        for (SfxManualMachineOutput output : outputs) {
            world.dropItemNaturally(location, output.create(items));
        }
    }

    private boolean matchesSlot(ItemStack current, SfxRecipeSlot required, boolean emptyMustMatch) {
        if (required == null || required.isEmpty()) {
            return !emptyMustMatch || current == null || current.getType().isAir() || current.getAmount() <= 0;
        }
        return items.matches(current, required);
    }

    private ShapelessMatchPlan planShapeless(Inventory input, SfxManualMachineRecipe recipe) {
        return planShapeless(input.getContents(), recipe);
    }

    private ShapelessMatchPlan planShapeless(ItemStack[] contents, SfxManualMachineRecipe recipe) {
        ItemStack[] inputCopy = cloneContents(contents);
        int[] consumed = new int[inputCopy.length];

        for (SfxRecipeSlot required : recipe.input()) {
            int remaining = required.amount();
            for (int i = 0; i < inputCopy.length && remaining > 0; i++) {
                ItemStack current = inputCopy[i];
                if (!matchesSlotIdentity(current, required)) {
                    continue;
                }
                int taken = Math.min(remaining, current.getAmount());
                consume(current, taken);
                if (current.getAmount() <= 0) {
                    inputCopy[i] = null;
                }
                consumed[i] += taken;
                remaining -= taken;
            }
            if (remaining > 0) {
                return null;
            }
        }

        return new ShapelessMatchPlan(recipe, consumed, inputCopy);
    }

    private enum MatchKind { ORDERED, UNORDERED }

    private record MatchCacheKey(long revision, String machineId, MatchKind kind, ManualRecipeHash hash) {}

    private boolean canFitAfterShapelessConsume(Inventory input, Inventory output, ShapelessMatchPlan plan) {
        ItemStack[] contents = outputContentsAfterInputConsumption(input, output, plan.inputAfterConsume());
        return plan.recipe().hasRandomOutputs()
                ? canFitRandomRecipe(contents, plan.recipe())
                : canFitAll(contents, plan.recipe().fixedOutputs());
    }

    private void applyShapelessConsumption(Inventory input, ShapelessMatchPlan plan) {
        int[] consumed = plan.consumed();
        for (int i = 0; i < consumed.length; i++) {
            if (consumed[i] > 0) {
                consume(input, i, consumed[i]);
            }
        }
    }

    private boolean matchesSlotIdentity(ItemStack current, SfxRecipeSlot required) {
        if (required == null || required.isEmpty()) {
            return false;
        }
        if (current == null || current.getType().isAir() || current.getAmount() <= 0) {
            return false;
        }
        if (required.isSfxItem()) {
            return items.readMarker(current)
                    .map(marker -> marker.itemId().equals(required.sfxItemId()))
                    .orElse(false);
        }
        if (items.isSfxItem(current)) {
            return false;
        }
        return current.getType() == required.material();
    }

    private boolean canFitAfterConsume(Inventory input, Inventory output, SfxManualMachineRecipe recipe, List<SfxManualMachineOutput> outputs) {
        ItemStack[] inputCopy = cloneContents(input);
        for (int i = 0; i < 9; i++) {
            SfxRecipeSlot required = recipe.input().get(i);
            if (required != null && !required.isEmpty()) {
                consume(inputCopy[i], required.amount());
            }
        }
        return canFitAll(outputContentsAfterInputConsumption(input, output, inputCopy), outputs);
    }

    private boolean canFitAfterConsume(Inventory input, Inventory output, int consumedSlot, int consumedAmount, List<SfxManualMachineOutput> outputs) {
        ItemStack[] inputCopy = cloneContents(input);
        consume(inputCopy[consumedSlot], consumedAmount);
        return canFitAll(outputContentsAfterInputConsumption(input, output, inputCopy), outputs);
    }

    private boolean canFitRandomAfterConsume(Inventory input, Inventory output, int consumedSlot, int consumedAmount, SfxManualMachineRecipe recipe) {
        ItemStack[] inputCopy = cloneContents(input);
        consume(inputCopy[consumedSlot], consumedAmount);
        ItemStack[] contents = outputContentsAfterInputConsumption(input, output, inputCopy);
        return canFitRandomRecipe(contents, recipe);
    }

    private boolean canFitRandomRecipe(ItemStack[] contents, SfxManualMachineRecipe recipe) {
        ItemStack[] simulated = cloneContents(contents);
        for (SfxManualMachineOutput fixedOutput : recipe.fixedOutputs()) {
            ItemStack stack = fixedOutput.create(items);
            if (!canFit(simulated, stack)) {
                return false;
            }
            place(simulated, stack);
        }
        return hasEmptySlot(simulated);
    }

    private ItemStack[] outputContentsAfterInputConsumption(Inventory input, Inventory output, ItemStack[] inputCopy) {
        if (input == output) {
            return inputCopy;
        }
        return cloneContents(output);
    }

    private ItemStack[] cloneContents(Inventory inventory) {
        return cloneContents(inventory.getContents());
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private boolean canFitAll(ItemStack[] contents, List<SfxManualMachineOutput> outputs) {
        ItemStack[] simulated = cloneContents(contents);
        for (SfxManualMachineOutput output : outputs) {
            ItemStack stack = output.create(items);
            if (!canFit(simulated, stack)) {
                return false;
            }
            place(simulated, stack);
        }
        return true;
    }
    private OutputPlan planOutputAfterConsume(Inventory input, Inventory output, ItemStack[] inputAfterConsume, List<SfxManualMachineOutput> outputs) {
        ItemStack[] planned = outputContentsAfterInputConsumption(input, output, inputAfterConsume);
        planned = cloneContents(planned);
        for (SfxManualMachineOutput outputItem : outputs) {
            ItemStack stack = outputItem.create(items);
            if (!tryPlace(planned, stack)) {
                return null;
            }
        }
        return new OutputPlan(planned);
    }

    private void applyOutputPlan(Inventory inventory, OutputPlan plan) {
        ItemStack[] planned = plan.contents();
        int size = Math.min(inventory.getSize(), planned.length);
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, planned[i] == null ? null : planned[i].clone());
        }
    }

    private boolean tryPlace(ItemStack[] contents, ItemStack output) {
        ItemStack stack = output.clone();
        int remaining = stack.getAmount();
        int maxStack = stack.getMaxStackSize();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack current = contents[i];
            if (current == null || current.getType().isAir() || current.getAmount() <= 0) {
                int placed = Math.min(maxStack, remaining);
                ItemStack created = stack.clone();
                created.setAmount(placed);
                contents[i] = created;
                remaining -= placed;
                continue;
            }
            if (current.isSimilar(stack)) {
                int room = Math.max(0, maxStack - current.getAmount());
                if (room <= 0) {
                    continue;
                }
                int placed = Math.min(room, remaining);
                current.setAmount(current.getAmount() + placed);
                remaining -= placed;
            }
        }
        return remaining <= 0;
    }


    private boolean canFit(ItemStack[] contents, ItemStack output) {
        int remaining = output.getAmount();
        int maxStack = output.getMaxStackSize();
        for (ItemStack current : contents) {
            if (current == null || current.getType().isAir() || current.getAmount() <= 0) {
                remaining -= maxStack;
                if (remaining <= 0) {
                    return true;
                }
                continue;
            }
            if (current.isSimilar(output)) {
                remaining -= Math.max(0, maxStack - current.getAmount());
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void place(ItemStack[] contents, ItemStack output) {
        int remaining = output.getAmount();
        int maxStack = output.getMaxStackSize();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack current = contents[i];
            if (current == null || current.getType().isAir() || current.getAmount() <= 0) {
                int placed = Math.min(maxStack, remaining);
                ItemStack created = output.clone();
                created.setAmount(placed);
                contents[i] = created;
                remaining -= placed;
                continue;
            }
            if (current.isSimilar(output)) {
                int room = Math.max(0, maxStack - current.getAmount());
                if (room <= 0) {
                    continue;
                }
                int placed = Math.min(room, remaining);
                current.setAmount(current.getAmount() + placed);
                remaining -= placed;
            }
        }
    }

    private boolean hasEmptySlot(ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isInventoryEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    private void applySlotConsumption(Inventory inventory, int[] consumed) {
        for (int i = 0; i < consumed.length; i++) {
            if (consumed[i] > 0) {
                consume(inventory, i, consumed[i]);
            }
        }
    }

    private void consume(Inventory inventory, int slot, int amount) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() == Material.AIR || amount <= 0) {
            return;
        }
        int next = item.getAmount() - amount;
        if (next <= 0) {
            inventory.setItem(slot, null);
        } else {
            item.setAmount(next);
        }
    }

    private void consume(ItemStack item, int amount) {
        if (item == null || item.getType() == Material.AIR || amount <= 0) {
            return;
        }
        int next = item.getAmount() - amount;
        if (next <= 0) {
            item.setAmount(0);
        } else {
            item.setAmount(next);
        }
    }

    private void success(Block clickedBlock, SfxManualMachineDefinition definition) {
        playEffect(clickedBlock, definition);
        handlePostCraftSideEffects(clickedBlock, definition);
    }

    private void handlePostCraftSideEffects(Block clickedBlock, SfxManualMachineDefinition definition) {
        if (MAKESHIFT_SMELTERY.equals(definition.id())) {
            extinguishIgnitionFire(clickedBlock, definition);
            return;
        }
        if (SMELTERY.equals(definition.id())) {
            int chance = Math.max(0, Math.min(100, plugin.getConfig().getInt("options.fire-breaking-chance", 34)));
            if (chance > 0 && ThreadLocalRandom.current().nextInt(100) < chance) {
                extinguishIgnitionFire(clickedBlock, definition);
            }
        }
    }

    private void extinguishIgnitionFire(Block clickedBlock, SfxManualMachineDefinition definition) {
        if (SMELTERY.equals(definition.id())) {
            Dispenser dispenser = resolveInputDispenser(definition, clickedBlock);
            if (dispenser != null && basicBlockMachines.useIgnitionChamber(null, dispenser.getBlock())) {
                return;
            }
        }
        Block fireBlock = definition.centerBlock(clickedBlock).getRelative(BlockFace.DOWN);
        Material type = fireBlock.getType();
        if (type == Material.FIRE || type == Material.SOUL_FIRE) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, definition.id(), fireBlock, Material.AIR, false, "manual-machine", "consume-fire");
        }
    }

    private void playEffect(Block clickedBlock, SfxManualMachineDefinition definition) {
        var world = clickedBlock.getWorld();
        var location = clickedBlock.getLocation().add(0.5, 0.5, 0.5);
        switch (definition.id()) {
            case ENHANCED_CRAFTING_TABLE -> {
                world.spawnParticle(Particle.ENCHANT, location.clone().add(0.0, 0.4, 0.0), 24, 0.35, 0.25, 0.35, 0.0);
                playBlockSound(clickedBlock, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
            }
            case GRIND_STONE -> playBlockSound(clickedBlock, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
            case MANUAL_COMPRESSOR -> playBlockSound(clickedBlock, Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.0f);
            case ORE_CRUSHER -> {
                world.spawnParticle(Particle.BLOCK, location, 18, 0.25, 0.1, 0.25, Material.STONE.createBlockData());
                playBlockSound(clickedBlock, Sound.BLOCK_STONE_BREAK, 0.9f, 0.9f);
            }
            case ORE_WASHER -> {
                world.spawnParticle(Particle.SPLASH, location.clone().add(0.0, 0.2, 0.0), 18, 0.3, 0.15, 0.3, 0.1);
                playBlockSound(clickedBlock, Sound.ENTITY_PLAYER_SPLASH, 1.0f, 1.0f);
            }
            case ARMOR_FORGE -> {
                playBlockSound(clickedBlock, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.0f);
                playBlockSound(clickedBlock, Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
            }
            case MAGIC_WORKBENCH -> {
                world.spawnParticle(Particle.ENCHANT, location.clone().add(0.0, 0.35, 0.0), 18, 0.3, 0.25, 0.3, 0.0);
                world.spawnParticle(Particle.PORTAL, location.clone().add(0.0, 0.55, 0.0), 16, 0.25, 0.15, 0.25, 0.02);
                playBlockSound(clickedBlock, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
                playBlockSound(clickedBlock, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.0f);
            }
            case SMELTERY, MAKESHIFT_SMELTERY -> {
                world.spawnParticle(Particle.FLAME, location.clone().add(0.0, 0.25, 0.0), 16, 0.25, 0.15, 0.25, 0.01);
                playBlockSound(clickedBlock, Sound.BLOCK_LAVA_POP, 0.8f, 1.0f);
            }
            case PRESSURE_CHAMBER -> {
                playBlockSound(clickedBlock, Sound.ENTITY_TNT_PRIMED, 0.8f, 1.0f);
                playBlockSound(clickedBlock, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.0f);
            }
            case JUICER -> playBlockSound(clickedBlock, Sound.ENTITY_PLAYER_SPLASH, 1.0f, 1.0f);
            case TABLE_SAW -> playBlockSound(clickedBlock, Sound.BLOCK_WOOD_BREAK, 0.9f, 1.0f);
            case AUTOMATED_PANNING_MACHINE -> playBlockSound(clickedBlock, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.0f);
            default -> playBlockSound(clickedBlock, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.7f, 1.0f);
        }
    }

    private void playBlockSound(Block source, Sound sound, float volume, float pitch) {
        playBlockSound(source.getLocation(), sound, volume, pitch);
    }

    private void playBlockSound(Location source, Sound sound, float volume, float pitch) {
        World world = source.getWorld();
        if (world != null) {
            world.playSound(source.clone().add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }

    private void message(Player player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(Text.prefixed(plugin, message));
    }


}
