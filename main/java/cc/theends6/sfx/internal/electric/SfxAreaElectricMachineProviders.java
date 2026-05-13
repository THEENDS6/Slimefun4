package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Goat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxAreaElectricMachineProviders {
    private static final int PRODUCE_RANGE = 2;
    private static final int PRODUCE_BASE_SECONDS = 5;
    private static final int ACTION_BASE_SECONDS = 2;
    private static final double BREEDER_RANGE_XZ = 4.0D;
    private static final double BREEDER_RANGE_Y = 2.0D;
    private static final double ANIMAL_GROWTH_RANGE = 3.0D;
    private static final int TREE_GROWTH_RADIUS = 9;
    private static final int TREE_GROWTH_ATTEMPTS = 4;
    private static final double XP_COLLECTOR_RANGE = 4.0D;
    private static final int XP_PER_FLASK = 10;

    private SfxAreaElectricMachineProviders() {
    }

    static SfxElectricRecipeProvider produceCollector() {
        return new SpecialProvider() {
            @Override
            public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (isActive(state, "sf:produce_collector:")) {
                    return advanceProduce(items, definition, state, location);
                }
                ProduceStart start = findProduceStart(items, definition, state, location);
                return switch (start.status()) {
                    case NO_INPUT -> SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
                    case NO_TARGET -> SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
                    case OUTPUT_FULL -> SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OUTPUT_FULL, true);
                    case READY -> startTimedWork(definition, state, start.inputSlot(), start.output(), PRODUCE_BASE_SECONDS,
                            "sf:produce_collector:" + start.output().material().name().toLowerCase(java.util.Locale.ROOT));
                };
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
            }
        };
    }

    static SfxElectricRecipeProvider autoBreeder() {
        return entityActionProvider(
                "sf:auto_breeder",
                true,
                entity -> entity instanceof Animals animal && entity.isValid() && animal.isAdult() && animal.canBreed() && !animal.isLoveMode(),
                entity -> {
                    if (entity instanceof Animals animal) {
                        animal.setLoveModeTicks(600);
                    }
                    spawnEntityHearts(entity);
                });
    }

    static SfxElectricRecipeProvider animalGrowthAccelerator() {
        return entityActionProvider(
                "sf:animal_growth_accelerator",
                true,
                entity -> entity instanceof org.bukkit.entity.Ageable ageable && entity.isValid() && !ageable.isAdult(),
                entity -> {
                    if (entity instanceof org.bukkit.entity.Ageable ageable) {
                        ageable.setAge(Math.min(0, ageable.getAge() + 2000));
                    }
                    spawnEntityHearts(entity);
                });
    }

    static SfxElectricRecipeProvider cropGrowthAccelerator(int radius) {
        return new SpecialProvider() {
            @Override
            public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (isActive(state, "sf:crop_growth_accelerator:")) {
                    return advanceCropGrowth(definition, state, location, radius, radius);
                }
                int inputSlot = firstInputSlot(state, false);
                if (inputSlot < 0) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
                }
                if (findCrops(location, radius, radius).isEmpty()) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
                }
                return startTimedWork(definition, state, inputSlot, null, ACTION_BASE_SECONDS, "sf:crop_growth_accelerator:" + radius);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
            }
        };
    }

    static SfxElectricRecipeProvider treeGrowthAccelerator() {
        return new SpecialProvider() {
            @Override
            public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (isActive(state, "sf:tree_growth_accelerator")) {
                    return advanceTreeGrowth(definition, state, location);
                }
                int inputSlot = firstInputSlot(state, false);
                if (inputSlot < 0) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
                }
                if (findSaplings(location, TREE_GROWTH_ATTEMPTS).isEmpty()) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
                }
                return startTimedWork(definition, state, inputSlot, null, ACTION_BASE_SECONDS, "sf:tree_growth_accelerator");
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
            }
        };
    }

    static SfxElectricRecipeProvider expCollector() {
        return new SpecialProvider() {
            @Override
            public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                boolean changed = flushKnowledgeFlasks(items, definition, state);
                SfxElectricStack flask = SfxElectricStack.sfx("sf:filled_flask_of_knowledge", 1);
                if (state.specialData() >= XP_PER_FLASK && !canFitOutput(items, definition, state, flask)) {
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.OUTPUT_FULL, 0, changed, true);
                }

                List<ExperienceOrb> orbs = nearbyExperienceOrbs(location);
                if (orbs.isEmpty()) {
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.IDLE, 0, changed, true);
                }
                if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_POWER, 0, changed, true);
                }

                int collected = 0;
                for (ExperienceOrb orb : orbs) {
                    if (!orb.isValid()) {
                        continue;
                    }
                    collected += Math.max(0, orb.getExperience());
                    orb.remove();
                }
                if (collected <= 0) {
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.IDLE, 0, changed, true);
                }

                state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
                state.specialData(state.specialData() + collected);
                changed = true;
                flushKnowledgeFlasks(items, definition, state);
                if (state.specialData() >= XP_PER_FLASK && !canFitOutput(items, definition, state, flask)) {
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.OUTPUT_FULL, definition.energyConsumptionPerTick(), true, true);
                }
                return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true, true);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                return definition.energyConsumptionPerTick();
            }
        };
    }

    private static SfxElectricRecipeProvider entityActionProvider(String key, boolean organicFood, Predicate<Entity> predicate, Consumer<Entity> action) {
        return new SpecialProvider() {
            @Override
            public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (isActive(state, key)) {
                    return advanceEntityAction(definition, state, location, key, organicFood, predicate, action);
                }
                int inputSlot = firstInputSlot(state, organicFood);
                if (inputSlot < 0) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
                }
                if (findTargetEntity(location, key, predicate) == null) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
                }
                return startTimedWork(definition, state, inputSlot, null, ACTION_BASE_SECONDS, key);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
            }
        };
    }

    private static SfxElectricMachineTickResult startTimedWork(SfxElectricMachineDefinition definition, SfxElectricMachineState state, int inputSlot, SfxElectricStack output, int baseSeconds, String key) {
        if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
        }
        SfxElectricStack reserved = consumeInput(state, inputSlot, 1);
        if (reserved == null) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        state.activeRecipeKey(key);
        state.activeInputSlot(inputSlot);
        state.activeBaseTicks(baseSeconds);
        state.activeOutputs(output == null ? List.of() : List.of(output));
        state.reservedInputs(List.of(reserved));
        state.pendingOutput(null);
        state.progressWork(0);
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, 0, true);
    }

    private static SfxElectricMachineTickResult advanceProduce(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        List<SfxElectricStack> outputs = state.activeOutputs();
        if (outputs.isEmpty()) {
            restoreReservedAndReset(state);
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        if (!canFitOutputs(items, definition, state, outputs)) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.BLOCKED_OUTPUT, true);
        }
        return advanceTimedAction(definition, state, () -> {
            SfxElectricStack output = outputs.getFirst();
            if (!hasProduceTarget(location, output)) {
                restoreReservedAndReset(state);
                return SfxElectricMachineRenderStatus.NO_TARGET;
            }
            for (SfxElectricStack stack : outputs) {
                pushOutput(items, definition, state, stack);
            }
            state.resetProgress();
            return SfxElectricMachineRenderStatus.IDLE;
        });
    }

    private static SfxElectricMachineTickResult advanceEntityAction(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, String key, boolean organicFood, Predicate<Entity> predicate, Consumer<Entity> action) {
        return advanceTimedAction(definition, state, () -> {
            Entity entity = findTargetEntity(location, key, predicate);
            if (entity == null) {
                restoreReservedAndReset(state);
                return SfxElectricMachineRenderStatus.NO_TARGET;
            }
            action.accept(entity);
            state.resetProgress();
            return SfxElectricMachineRenderStatus.IDLE;
        });
    }

    private static SfxElectricMachineTickResult advanceCropGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, int radius, int maxTargets) {
        return advanceTimedAction(definition, state, () -> {
            List<Block> crops = findCrops(location, radius, maxTargets);
            int grown = 0;
            for (Block crop : crops) {
                BlockData data = crop.getBlockData();
                if (!(data instanceof org.bukkit.block.data.Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge()) {
                    continue;
                }
                ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
                crop.setBlockData(ageable, false);
                crop.getWorld().spawnParticle(Particle.HEART, crop.getLocation().add(0.5D, 0.5D, 0.5D), 4, 0.1F, 0.1F, 0.1F, 0.0D);
                grown++;
            }
            if (grown <= 0) {
                restoreReservedAndReset(state);
                return SfxElectricMachineRenderStatus.NO_TARGET;
            }
            state.resetProgress();
            return SfxElectricMachineRenderStatus.IDLE;
        });
    }

    private static SfxElectricMachineTickResult advanceTreeGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        return advanceTimedAction(definition, state, () -> {
            List<Block> saplings = findSaplings(location, TREE_GROWTH_ATTEMPTS);
            int attempts = 0;
            for (Block sapling : saplings) {
                if (!Tag.SAPLINGS.isTagged(sapling.getType())) {
                    continue;
                }
                sapling.applyBoneMeal(BlockFace.UP);
                if (Tag.SAPLINGS.isTagged(sapling.getType())) {
                    sapling.getWorld().spawnParticle(Particle.HEART, sapling.getLocation().add(0.5D, 0.5D, 0.5D), 4, 0.1F, 0.1F, 0.1F, 0.0D);
                }
                attempts++;
            }
            if (attempts <= 0) {
                restoreReservedAndReset(state);
                return SfxElectricMachineRenderStatus.NO_TARGET;
            }
            state.resetProgress();
            return SfxElectricMachineRenderStatus.IDLE;
        });
    }

    private static SfxElectricMachineTickResult advanceTimedAction(SfxElectricMachineDefinition definition, SfxElectricMachineState state, java.util.function.Supplier<SfxElectricMachineRenderStatus> completion) {
        if (!state.hasProgress()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.IDLE, true);
        }
        if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
        }
        int totalWork = Math.max(1, state.activeBaseTicks() * 20);
        state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
        state.progressWork(Math.min(totalWork, state.progressWork() + Math.max(1, definition.speed())));
        if (state.progressWork() >= totalWork) {
            SfxElectricMachineRenderStatus result = completion.get();
            return SfxElectricMachineTickResult.changed(result, definition.energyConsumptionPerTick(), true);
        }
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true);
    }

    private static ProduceStart findProduceStart(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        boolean sawContainer = false;
        for (int slot = 0; slot < state.inputCapacity(); slot++) {
            SfxElectricStack input = state.input(slot);
            if (input == null) {
                continue;
            }
            if (input.material() == Material.BUCKET) {
                sawContainer = true;
                SfxElectricStack output = SfxElectricStack.vanilla(Material.MILK_BUCKET, 1);
                if (!canFitOutput(items, definition, state, output)) {
                    return ProduceStart.outputFull();
                }
                if (hasNearbyAdult(location, entity -> entity instanceof Cow || entity instanceof Goat)) {
                    return ProduceStart.ready(slot, output);
                }
            }
            if (input.material() == Material.BOWL) {
                sawContainer = true;
                SfxElectricStack output = SfxElectricStack.vanilla(Material.MUSHROOM_STEW, 1);
                if (!canFitOutput(items, definition, state, output)) {
                    return ProduceStart.outputFull();
                }
                if (hasNearbyAdult(location, entity -> entity instanceof MushroomCow)) {
                    return ProduceStart.ready(slot, output);
                }
            }
        }
        return sawContainer ? ProduceStart.noTarget() : ProduceStart.noInput();
    }

    private static boolean hasProduceTarget(Location location, SfxElectricStack output) {
        if (output == null) {
            return false;
        }
        if (output.material() == Material.MILK_BUCKET) {
            return hasNearbyAdult(location, entity -> entity instanceof Cow || entity instanceof Goat);
        }
        if (output.material() == Material.MUSHROOM_STEW) {
            return hasNearbyAdult(location, entity -> entity instanceof MushroomCow);
        }
        return false;
    }

    private static boolean hasNearbyAdult(Location location, Predicate<Entity> predicate) {
        return firstNearbyEntity(location, PRODUCE_RANGE, PRODUCE_RANGE, entity -> entity instanceof org.bukkit.entity.Ageable ageable && ageable.isAdult() && predicate.test(entity)) != null;
    }

    private static Entity findTargetEntity(Location location, String key, Predicate<Entity> predicate) {
        boolean breed = key.equals("sf:auto_breeder");
        return firstNearbyEntity(location, breed ? BREEDER_RANGE_XZ : ANIMAL_GROWTH_RANGE, breed ? BREEDER_RANGE_Y : ANIMAL_GROWTH_RANGE, predicate);
    }

    private static List<Block> findCrops(Location location, int radius, int maxTargets) {
        if (location == null || location.getWorld() == null) {
            return List.of();
        }
        Block center = location.getBlock();
        List<Block> crops = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block block = center.getRelative(x, 0, z);
                BlockData data = block.getBlockData();
                if (data instanceof org.bukkit.block.data.Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
                    crops.add(block);
                }
            }
        }
        return selectTargets(crops, maxTargets);
    }

    private static List<Block> findSaplings(Location location, int maxTargets) {
        if (location == null || location.getWorld() == null) {
            return List.of();
        }
        Block center = location.getBlock();
        List<Block> saplings = new ArrayList<>();
        for (int x = -TREE_GROWTH_RADIUS; x <= TREE_GROWTH_RADIUS; x++) {
            for (int z = -TREE_GROWTH_RADIUS; z <= TREE_GROWTH_RADIUS; z++) {
                Block block = center.getRelative(x, 0, z);
                if (Tag.SAPLINGS.isTagged(block.getType())) {
                    saplings.add(block);
                }
            }
        }
        return selectTargets(saplings, maxTargets);
    }

    private static List<Block> selectTargets(List<Block> candidates, int maxTargets) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() <= maxTargets) {
            return List.copyOf(candidates);
        }
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        return List.copyOf(candidates.subList(0, maxTargets));
    }

    private static Entity firstNearbyEntity(Location location, double radiusXz, double radiusY, Predicate<Entity> predicate) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (Entity entity : location.getWorld().getNearbyEntities(location, radiusXz, radiusY, radiusXz, predicate)) {
            return entity;
        }
        return null;
    }

    private static void spawnEntityHearts(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            entity.getWorld().spawnParticle(Particle.HEART, livingEntity.getEyeLocation(), 8, 0.2F, 0.2F, 0.2F, 0.0D);
        }
    }

    private static List<ExperienceOrb> nearbyExperienceOrbs(Location location) {
        if (location == null || location.getWorld() == null) {
            return List.of();
        }
        List<ExperienceOrb> orbs = new ArrayList<>();
        for (Entity entity : location.getWorld().getNearbyEntities(location, XP_COLLECTOR_RANGE, XP_COLLECTOR_RANGE, XP_COLLECTOR_RANGE,
                entityCandidate -> entityCandidate instanceof ExperienceOrb && entityCandidate.isValid())) {
            if (entity instanceof ExperienceOrb orb) {
                orbs.add(orb);
            }
        }
        return orbs;
    }

    private static boolean flushKnowledgeFlasks(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        boolean changed = false;
        SfxElectricStack flask = SfxElectricStack.sfx("sf:filled_flask_of_knowledge", 1);
        while (state.specialData() >= XP_PER_FLASK && canFitOutput(items, definition, state, flask)) {
            pushOutput(items, definition, state, flask);
            state.specialData(state.specialData() - XP_PER_FLASK);
            changed = true;
        }
        return changed;
    }

    private static int firstInputSlot(SfxElectricMachineState state, boolean organicFood) {
        for (int slot = 0; slot < state.inputCapacity(); slot++) {
            SfxElectricStack stack = state.input(slot);
            if (organicFood ? isOrganicFood(stack) : isOrganicFertilizer(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isOrganicFood(SfxElectricStack stack) {
        return stack != null && stack.isSfxItem() && (stack.itemId().equals("sf:organic_food") || stack.itemId().startsWith("sf:organic_food_"));
    }

    private static boolean isOrganicFertilizer(SfxElectricStack stack) {
        return stack != null && stack.isSfxItem() && (stack.itemId().equals("sf:fertilizer") || stack.itemId().startsWith("sf:fertilizer_"));
    }

    private static SfxElectricStack consumeInput(SfxElectricMachineState state, int slot, int amount) {
        SfxElectricStack input = state.input(slot);
        if (input == null || input.amount() < amount) {
            return null;
        }
        SfxElectricStack consumed = input.copyWithAmount(amount);
        int remaining = input.amount() - amount;
        state.input(slot, remaining <= 0 ? null : input.copyWithAmount(remaining));
        return consumed;
    }

    private static void restoreReservedAndReset(SfxElectricMachineState state) {
        int slot = Math.max(0, state.activeInputSlot());
        for (SfxElectricStack stack : state.reservedInputs()) {
            restoreInput(state, slot, stack);
        }
        state.resetProgress();
    }

    private static void restoreInput(SfxElectricMachineState state, int slot, SfxElectricStack stack) {
        if (stack == null) {
            return;
        }
        SfxElectricStack current = state.input(slot);
        state.input(slot, current == null ? stack : current.copyWithAmount(current.amount() + stack.amount()));
    }

    private static boolean canFitOutputs(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, List<SfxElectricStack> outputs) {
        SfxElectricStack[] simulated = new SfxElectricStack[Math.max(1, definition.outputSlots().length)];
        for (int slot = 0; slot < simulated.length; slot++) {
            simulated[slot] = state.output(slot);
        }
        for (SfxElectricStack output : outputs) {
            Integer slot = findOutputSlot(items, simulated, output);
            if (slot == null) {
                return false;
            }
            simulated[slot] = simulated[slot] == null ? output : simulated[slot].copyWithAmount(simulated[slot].amount() + output.amount());
        }
        return true;
    }

    private static boolean canFitOutput(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricStack output) {
        return canFitOutputs(items, definition, state, List.of(output));
    }

    private static void pushOutput(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricStack output) {
        int outputCapacity = Math.max(1, definition.outputSlots().length);
        SfxElectricStack[] simulated = new SfxElectricStack[outputCapacity];
        for (int slot = 0; slot < outputCapacity; slot++) {
            simulated[slot] = state.output(slot);
        }
        Integer slot = findOutputSlot(items, simulated, output);
        if (slot == null) {
            return;
        }
        SfxElectricStack current = state.output(slot);
        state.output(slot, current == null ? output : current.copyWithAmount(current.amount() + output.amount()));
    }

    private static Integer findOutputSlot(SfxItems items, SfxElectricStack[] outputs, SfxElectricStack output) {
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

    private static boolean isActive(SfxElectricMachineState state, String key) {
        return state.activeRecipeKey() != null && state.activeRecipeKey().startsWith(key) && state.hasReservedInput();
    }

    private abstract static class SpecialProvider implements SfxElectricRecipeProvider {
        @Override
        public List<SfxElectricRecipe> recipes() {
            return List.of();
        }

        @Override
        public boolean hasSpecialTick() {
            return true;
        }

        @Override
        public int specialTickIntervalTicks() {
            return 10;
        }
    }

    private enum ProduceStartStatus {
        NO_INPUT,
        NO_TARGET,
        OUTPUT_FULL,
        READY
    }

    private record ProduceStart(ProduceStartStatus status, int inputSlot, SfxElectricStack output) {
        static ProduceStart noInput() {
            return new ProduceStart(ProduceStartStatus.NO_INPUT, -1, null);
        }

        static ProduceStart noTarget() {
            return new ProduceStart(ProduceStartStatus.NO_TARGET, -1, null);
        }

        static ProduceStart outputFull() {
            return new ProduceStart(ProduceStartStatus.OUTPUT_FULL, -1, null);
        }

        static ProduceStart ready(int inputSlot, SfxElectricStack output) {
            return new ProduceStart(ProduceStartStatus.READY, inputSlot, output);
        }
    }
}
