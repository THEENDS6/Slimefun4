package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.block.data.Levelled;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Goat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

final class SfxAreaElectricMachineProviders {
    private static final int PRODUCE_RANGE = 2;
    private static final int ACTION_WORK_TICKS = 10;
    private static final int PRODUCE_WORK_TICKS = 100;
    private static final double BREEDER_RANGE_XZ = 4.0D;
    private static final double BREEDER_RANGE_Y = 2.0D;
    private static final double ANIMAL_GROWTH_RANGE = 3.0D;
    private static final int CLASSIC_TREE_GROWTH_RADIUS = 9;
    private static final int CLASSIC_TREE_GROWTH_ATTEMPTS = 4;
    private static final int SFX_GROWTH_RADIUS = 9;
    private static final int SFX_GROWTH_TOTAL_TICKS = 600;
    private static final int SFX_GROWTH_INTERVAL_TICKS = 200;
    private static final double SFX_GROWTH_SUCCESS_CHANCE = 0.5D;
    private static final double XP_COLLECTOR_RANGE = 4.0D;
    private static final double XP_COLLECTOR_ABSORB_RANGE = 1.0D;
    private static final double XP_COLLECTOR_ATTRACT_SPEED = 0.18D;
    private static final int XP_PER_FLASK = 10;
    private static final int XP_FLASK_ENERGY_COST = 1000;
    private static final Enchantment UNBREAKING = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
    private static final Enchantment MENDING = Enchantment.getByKey(NamespacedKey.minecraft("mending"));

    private SfxAreaElectricMachineProviders() {
    }


    static SfxElectricRecipeProvider fluidPump() {
        return new WorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                FluidPumpAction action = findFluidPumpAction(items, definition, state, location);
                if (action.status() == SfxElectricMachineRenderStatus.NO_INPUT) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
                }
                if (action.status() == SfxElectricMachineRenderStatus.NO_TARGET) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
                }
                if (action.status() == SfxElectricMachineRenderStatus.OUTPUT_FULL) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OUTPUT_FULL, true);
                }
                if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
                }
                consumeInput(state, action.inputSlot(), 1);
                pushOutput(items, definition, state, action.output());
                if (action.consumeSource()) {
                    action.source().setType(Material.AIR, false);
                }
                state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
                return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                FluidPumpAction action = findFluidPumpAction(items, definition, state, location);
                return action.status() == SfxElectricMachineRenderStatus.WORKING ? definition.energyConsumptionPerTick() : 0;
            }
        };
    }

    static SfxElectricRecipeProvider produceCollector() {
        return new WorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (isActive(state, "sf:produce_collector:")) {
                    return advanceProduce(plugin, items, definition, state, location, useSfxBalance(plugin, "produce-collector"));
                }
                ProduceStart start = findProduceStart(plugin, items, definition, state, location, useSfxBalance(plugin, "produce-collector"));
                return startProduce(definition, state, start);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!useSfxBalance(plugin, "produce-collector")) {
                    return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
                }
                if (state.hasProgress()) {
                    return definition.energyConsumptionPerTick();
                }
                ProduceStart start = findProduceStart(plugin, items, definition, state, location, true);
                return start.status() == ProduceStartStatus.READY ? definition.energyConsumptionPerTick() : 0;
            }
        };
    }

    static SfxElectricRecipeProvider autoBreeder() {
        return entityActionProvider(
                "sf:auto_breeder",
                "auto-breeder",
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
        return new WorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                Predicate<Entity> predicate = entity -> entity instanceof org.bukkit.entity.Ageable ageable && entity.isValid() && !ageable.isAdult();
                Consumer<Entity> action = entity -> {
                    if (entity instanceof org.bukkit.entity.Ageable ageable) {
                        int amount = useSfxBalance(plugin, "animal-growth-accelerator") ? 4000 : 2000;
                        ageable.setAge(Math.min(0, ageable.getAge() + amount));
                    }
                    spawnEntityHearts(entity);
                };
                if (isActive(state, "sf:animal_growth_accelerator")) {
                    return advanceEntityAction(definition, state, location, "sf:animal_growth_accelerator", true, predicate, action);
                }
                int inputSlot = firstInputSlot(state, true);
                if (inputSlot < 0) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
                }
                if (findTargetEntity(location, "sf:animal_growth_accelerator", predicate) == null) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
                }
                return startTimedWork(definition, state, inputSlot, null, ACTION_WORK_TICKS, "sf:animal_growth_accelerator");
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
            }
        };
    }

    static SfxElectricRecipeProvider cropGrowthAccelerator(SfxBlockDataService blockData, int classicRadius, int sfxAttempts) {
        return new WorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (useSfxBalance(plugin, "crop-growth-accelerator")) {
                    return tickSfxCropGrowth(blockData, definition, state, location, sfxAttempts);
                }
                return tickClassicCropGrowth(definition, state, location, classicRadius, classicRadius);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!useSfxBalance(plugin, "crop-growth-accelerator")) {
                    return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
                }
                if (state.hasProgress()) {
                    return hasOverlappingCropAccelerator(blockData, location) ? 0 : definition.energyConsumptionPerTick();
                }
                if (firstInputSlot(state, false) < 0 || hasOverlappingCropAccelerator(blockData, location) || !hasGrowableCrop(location, SFX_GROWTH_RADIUS)) {
                    return 0;
                }
                return definition.energyConsumptionPerTick();
            }
        };
    }

    static SfxElectricRecipeProvider treeGrowthAccelerator() {
        return new WorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (useSfxBalance(plugin, "tree-growth-accelerator")) {
                    return tickSfxTreeGrowth(definition, state, location);
                }
                return tickClassicTreeGrowth(definition, state, location);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!useSfxBalance(plugin, "tree-growth-accelerator")) {
                    return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
                }
                if (state.hasProgress()) {
                    return definition.energyConsumptionPerTick();
                }
                if (firstInputSlot(state, false) < 0 || !hasGrowableSapling(location, SFX_GROWTH_RADIUS)) {
                    return 0;
                }
                return definition.energyConsumptionPerTick();
            }
        };
    }

    static SfxElectricRecipeProvider expCollector() {
        return new SpecialProvider() {
            @Override
            public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                boolean useBalance = useSfxBalance(plugin, "xp-collector");
                FlushResult initialFlush = flushKnowledgeFlasks(items, definition, state, useBalance);
                boolean changed = initialFlush.changed();
                int consumedEnergy = initialFlush.consumedEnergy();
                int supplementalEnergy = initialFlush.consumedEnergy();
                SfxElectricStack flask = SfxElectricStack.sfx("sf:filled_flask_of_knowledge", 1);
                if (state.specialData() >= XP_PER_FLASK && !canFitOutput(items, definition, state, flask)) {
                    state.activeRecipeKey("sf:xp_collector");
                    state.activeBaseTicks(ACTION_WORK_TICKS);
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.OUTPUT_FULL, consumedEnergy, supplementalEnergy, changed, true);
                }
                if (useBalance && state.specialData() >= XP_PER_FLASK && state.storedEnergy() < XP_FLASK_ENERGY_COST) {
                    state.activeRecipeKey("sf:xp_collector");
                    state.activeBaseTicks(ACTION_WORK_TICKS);
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_POWER, consumedEnergy, supplementalEnergy, changed, true);
                }

                state.activeRecipeKey("sf:xp_collector");
                state.activeBaseTicks(ACTION_WORK_TICKS);
                if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_POWER, consumedEnergy, supplementalEnergy, changed, true);
                }

                state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
                int nextProgress = state.progressWork() + 1;
                consumedEnergy += definition.energyConsumptionPerTick();
                changed = true;
                if (useBalance) {
                    attractExperience(location);
                }
                if (nextProgress >= ACTION_WORK_TICKS) {
                    state.progressWork(0);
                    int collected = useBalance ? absorbCloseExperience(location) : collectAllNearbyExperience(location);
                    if (collected > 0) {
                        state.specialData(state.specialData() + collected);
                    }
                    FlushResult flush = flushKnowledgeFlasks(items, definition, state, useBalance);
                    consumedEnergy += flush.consumedEnergy();
                    supplementalEnergy += flush.consumedEnergy();
                    changed = changed || flush.changed() || collected > 0;
                } else {
                    state.progressWork(nextProgress);
                }

                if (state.specialData() >= XP_PER_FLASK && !canFitOutput(items, definition, state, flask)) {
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.OUTPUT_FULL, consumedEnergy, supplementalEnergy, true, true);
                }
                return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.WORKING, consumedEnergy, supplementalEnergy, true, true);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                SfxElectricStack flask = SfxElectricStack.sfx("sf:filled_flask_of_knowledge", 1);
                if (state.specialData() >= XP_PER_FLASK && !canFitOutput(items, definition, state, flask)) {
                    return 0;
                }
                return definition.energyConsumptionPerTick();
            }
        };
    }

    private static SfxElectricRecipeProvider entityActionProvider(String key, String balanceKey, boolean organicFood, Predicate<Entity> predicate, Consumer<Entity> action) {
        return new WorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
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
                return startTimedWork(definition, state, inputSlot, null, ACTION_WORK_TICKS, key);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!useSfxBalance(plugin, balanceKey)) {
                    return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
                }
                if (state.hasProgress()) {
                    return definition.energyConsumptionPerTick();
                }
                int inputSlot = firstInputSlot(state, organicFood);
                if (inputSlot < 0 || findTargetEntity(location, key, predicate) == null) {
                    return 0;
                }
                return definition.energyConsumptionPerTick();
            }
        };
    }

    private static SfxElectricMachineTickResult tickClassicCropGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, int radius, int maxTargets) {
        if (isActive(state, "sf:crop_growth_accelerator:")) {
            return advanceClassicCropGrowth(definition, state, location, radius, maxTargets);
        }
        int inputSlot = firstInputSlot(state, false);
        if (inputSlot < 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (findCrops(location, radius, maxTargets).isEmpty()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        return startTimedWork(definition, state, inputSlot, null, ACTION_WORK_TICKS, "sf:crop_growth_accelerator:" + radius);
    }

    private static SfxElectricMachineTickResult tickClassicTreeGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        if (isActive(state, "sf:tree_growth_accelerator")) {
            return advanceClassicTreeGrowth(definition, state, location);
        }
        int inputSlot = firstInputSlot(state, false);
        if (inputSlot < 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (findSaplings(location, CLASSIC_TREE_GROWTH_ATTEMPTS).isEmpty()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        return startTimedWork(definition, state, inputSlot, null, ACTION_WORK_TICKS, "sf:tree_growth_accelerator");
    }

    private static SfxElectricMachineTickResult tickSfxCropGrowth(SfxBlockDataService blockData, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, int attempts) {
        if (isActive(state, "sf:crop_growth_accelerator:sfx")) {
            if (hasOverlappingCropAccelerator(blockData, location)) {
                return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OVERLAPPING_AREA, true);
            }
            return advanceSfxGrowth(definition, state, location, attempts, GrowthTarget.CROP);
        }
        if (hasOverlappingCropAccelerator(blockData, location)) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OVERLAPPING_AREA, true);
        }
        int inputSlot = firstInputSlot(state, false);
        if (inputSlot < 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (!hasGrowableCrop(location, SFX_GROWTH_RADIUS)) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        return startTimedWork(definition, state, inputSlot, null, SFX_GROWTH_TOTAL_TICKS, "sf:crop_growth_accelerator:sfx:" + attempts);
    }

    private static SfxElectricMachineTickResult tickSfxTreeGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        if (isActive(state, "sf:tree_growth_accelerator:sfx")) {
            return advanceSfxGrowth(definition, state, location, 30, GrowthTarget.SAPLING);
        }
        int inputSlot = firstInputSlot(state, false);
        if (inputSlot < 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (!hasGrowableSapling(location, SFX_GROWTH_RADIUS)) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        return startTimedWork(definition, state, inputSlot, null, SFX_GROWTH_TOTAL_TICKS, "sf:tree_growth_accelerator:sfx");
    }

    private static SfxElectricMachineTickResult startTimedWork(SfxElectricMachineDefinition definition, SfxElectricMachineState state, int inputSlot, SfxElectricStack output, int workTicks, String key) {
        if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
        }
        SfxElectricStack reserved = consumeInput(state, inputSlot, 1);
        if (reserved == null) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        state.activeRecipeKey(key);
        state.activeInputSlot(inputSlot);
        state.activeBaseTicks(workTicks);
        state.activeOutputs(output == null ? List.of() : List.of(output));
        state.reservedInputs(List.of(reserved));
        state.pendingOutput(null);
        state.progressWork(0);
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, 0, true);
    }

    private static SfxElectricMachineTickResult startProduce(SfxElectricMachineDefinition definition, SfxElectricMachineState state, ProduceStart start) {
        return switch (start.status()) {
            case NO_INPUT -> SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
            case NO_TARGET -> SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
            case OUTPUT_FULL -> SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OUTPUT_FULL, true);
            case READY -> startTimedWork(definition, state, start.inputSlot(), start.primaryOutput(), PRODUCE_WORK_TICKS, "sf:produce_collector:" + start.action().key());
        };
    }

    private static SfxElectricMachineTickResult advanceProduce(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, boolean sfxBalance) {
        return advanceTimedAction(definition, state, () -> {
            ProduceAction action = ProduceAction.fromKey(state.activeRecipeKey());
            if (action == null) {
                restoreReservedAndReset(items, definition, state);
                return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
            }
            ProduceCompletion completion = completeProduce(plugin, items, definition, state, location, action, sfxBalance);
            if (completion.status() != SfxElectricMachineRenderStatus.WORKING) {
                return SfxElectricMachineTickResult.status(completion.status(), true);
            }
            state.resetProgress();
            return continueProduce(plugin, items, definition, state, location, sfxBalance);
        });
    }

    private static SfxElectricMachineTickResult advanceEntityAction(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, String key, boolean organicFood, Predicate<Entity> predicate, Consumer<Entity> action) {
        return advanceTimedAction(definition, state, () -> {
            Entity entity = findTargetEntity(location, key, predicate);
            if (entity == null) {
                restoreReservedAndReset(null, definition, state);
                return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
            }
            action.accept(entity);
            state.resetProgress();
            return continueEntityAction(definition, state, location, key, organicFood, predicate);
        });
    }

    private static SfxElectricMachineTickResult advanceClassicCropGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, int radius, int maxTargets) {
        return advanceTimedAction(definition, state, () -> {
            List<Block> crops = findCrops(location, radius, maxTargets);
            int grown = 0;
            for (Block crop : crops) {
                if (growCropWithBoneMealParticles(crop)) {
                    grown++;
                }
            }
            if (grown <= 0) {
                restoreReservedAndReset(null, definition, state);
                return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
            }
            state.resetProgress();
            return continueClassicCropGrowth(definition, state, location, radius, maxTargets);
        });
    }

    private static SfxElectricMachineTickResult advanceClassicTreeGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        return advanceTimedAction(definition, state, () -> {
            List<Block> saplings = findSaplings(location, CLASSIC_TREE_GROWTH_ATTEMPTS);
            int attempts = 0;
            for (Block sapling : saplings) {
                if (!Tag.SAPLINGS.isTagged(sapling.getType())) {
                    continue;
                }
                sapling.applyBoneMeal(BlockFace.UP);
                spawnBoneMealParticles(sapling);
                attempts++;
            }
            if (attempts <= 0) {
                restoreReservedAndReset(null, definition, state);
                return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
            }
            state.resetProgress();
            return continueClassicTreeGrowth(definition, state, location);
        });
    }

    private static SfxElectricMachineTickResult advanceSfxGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, int attempts, GrowthTarget target) {
        if ((target == GrowthTarget.CROP && !hasGrowableCrop(location, SFX_GROWTH_RADIUS))
                || (target == GrowthTarget.SAPLING && !hasGrowableSapling(location, SFX_GROWTH_RADIUS))) {
            state.resetProgress();
            return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.NO_TARGET, 0, true);
        }
        if (!state.hasProgress()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.IDLE, true);
        }
        if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
        }
        int previous = state.progressWork();
        int next = Math.min(SFX_GROWTH_TOTAL_TICKS, previous + Math.max(1, definition.speed()));
        state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
        state.progressWork(next);
        for (int marker = SFX_GROWTH_INTERVAL_TICKS; marker <= SFX_GROWTH_TOTAL_TICKS; marker += SFX_GROWTH_INTERVAL_TICKS) {
            if (previous < marker && next >= marker) {
                if (target == GrowthTarget.CROP) {
                    spawnGrowthAreaBoneMealParticles(location);
                }
                runRandomGrowthAttempts(location, attempts, target);
            }
        }
        if (next >= SFX_GROWTH_TOTAL_TICKS) {
            state.resetProgress();
            SfxElectricMachineTickResult result = target == GrowthTarget.CROP
                    ? continueSfxCropGrowth(definition, state, location)
                    : continueSfxTreeGrowth(definition, state, location);
            return new SfxElectricMachineTickResult(result.status(), definition.energyConsumptionPerTick() + result.consumedEnergy(), true, result.keepActive());
        }
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true);
    }

    private static SfxElectricMachineTickResult advanceTimedAction(SfxElectricMachineDefinition definition, SfxElectricMachineState state, java.util.function.Supplier<SfxElectricMachineTickResult> completion) {
        if (!state.hasProgress()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.IDLE, true);
        }
        if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
        }
        int totalWork = Math.max(1, state.activeBaseTicks());
        state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
        state.progressWork(Math.min(totalWork, state.progressWork() + Math.max(1, definition.speed())));
        if (state.progressWork() >= totalWork) {
            SfxElectricMachineTickResult result = completion.get();
            return new SfxElectricMachineTickResult(result.status(), definition.energyConsumptionPerTick() + result.consumedEnergy(), true, result.keepActive());
        }
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true);
    }

    private static SfxElectricMachineTickResult continueProduce(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, boolean sfxBalance) {
        ProduceStart start = findProduceStart(plugin, items, definition, state, location, sfxBalance);
        return startProduce(definition, state, start);
    }

    private static SfxElectricMachineTickResult continueEntityAction(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, String key, boolean organicFood, Predicate<Entity> predicate) {
        int inputSlot = firstInputSlot(state, organicFood);
        if (inputSlot < 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (findTargetEntity(location, key, predicate) == null) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        return startTimedWork(definition, state, inputSlot, null, ACTION_WORK_TICKS, key);
    }

    private static SfxElectricMachineTickResult continueClassicCropGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, int radius, int maxTargets) {
        int inputSlot = firstInputSlot(state, false);
        if (inputSlot < 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (findCrops(location, radius, maxTargets).isEmpty()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        return startTimedWork(definition, state, inputSlot, null, ACTION_WORK_TICKS, "sf:crop_growth_accelerator:" + radius);
    }

    private static SfxElectricMachineTickResult continueClassicTreeGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        int inputSlot = firstInputSlot(state, false);
        if (inputSlot < 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (findSaplings(location, CLASSIC_TREE_GROWTH_ATTEMPTS).isEmpty()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        return startTimedWork(definition, state, inputSlot, null, ACTION_WORK_TICKS, "sf:tree_growth_accelerator");
    }

    private static SfxElectricMachineTickResult continueSfxCropGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        int inputSlot = firstInputSlot(state, false);
        if (inputSlot < 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (!hasGrowableCrop(location, SFX_GROWTH_RADIUS)) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        return startTimedWork(definition, state, inputSlot, null, SFX_GROWTH_TOTAL_TICKS, "sf:crop_growth_accelerator:sfx");
    }

    private static SfxElectricMachineTickResult continueSfxTreeGrowth(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        int inputSlot = firstInputSlot(state, false);
        if (inputSlot < 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (!hasGrowableSapling(location, SFX_GROWTH_RADIUS)) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        return startTimedWork(definition, state, inputSlot, null, SFX_GROWTH_TOTAL_TICKS, "sf:tree_growth_accelerator:sfx");
    }

    private static ProduceStart findProduceStart(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, boolean sfxBalance) {
        boolean sawTool = false;
        for (int slot = 0; slot < state.inputCapacity(); slot++) {
            SfxElectricStack input = state.input(slot);
            if (input == null || input.isSfxItem() || input.material() == null) {
                continue;
            }
            Material material = input.material();
            if (!isProduceTool(material, sfxBalance)) {
                continue;
            }
            sawTool = true;
            ProduceStart start = switch (material) {
                case BUCKET -> produceStartForAction(items, definition, state, location, slot, ProduceAction.MILK);
                case BOWL -> produceStartForAction(items, definition, state, location, slot, ProduceAction.STEW);
                case GLASS_BOTTLE -> sfxBalance ? produceStartForAction(items, definition, state, location, slot, ProduceAction.HONEY_BOTTLE) : ProduceStart.noTarget();
                case SHEARS -> sfxBalance ? firstReadyProduce(items, definition, state, location, slot, ProduceAction.SHEEP_WOOL, ProduceAction.HONEYCOMB, ProduceAction.MOOSHROOM_SHEAR) : ProduceStart.noTarget();
                case BRUSH -> sfxBalance ? produceStartForAction(items, definition, state, location, slot, ProduceAction.ARMADILLO_SCUTE) : ProduceStart.noTarget();
                default -> ProduceStart.noTarget();
            };
            if (start.status() == ProduceStartStatus.READY || start.status() == ProduceStartStatus.OUTPUT_FULL) {
                return start;
            }
        }
        return sawTool ? ProduceStart.noTarget() : ProduceStart.noInput();
    }

    private static ProduceStart firstReadyProduce(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, int slot, ProduceAction... actions) {
        ProduceStart last = ProduceStart.noTarget();
        for (ProduceAction action : actions) {
            ProduceStart start = produceStartForAction(items, definition, state, location, slot, action);
            if (start.status() == ProduceStartStatus.READY || start.status() == ProduceStartStatus.OUTPUT_FULL) {
                return start;
            }
            last = start;
        }
        return last;
    }

    private static ProduceStart produceStartForAction(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, int slot, ProduceAction action) {
        List<SfxElectricStack> outputs = previewProduceOutputs(location, action);
        if (outputs.isEmpty()) {
            return ProduceStart.noTarget();
        }
        if (!canFitOutputs(items, definition, state, outputs)) {
            return ProduceStart.outputFull();
        }
        return ProduceStart.ready(slot, action, outputs.getFirst());
    }

    private static ProduceCompletion completeProduce(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, ProduceAction action, boolean sfxBalance) {
        SfxElectricStack reserved = state.reservedInputs().isEmpty() ? null : state.reservedInputs().getFirst();
        ProduceTarget target = findProduceTarget(location, action);
        if (target == null) {
            restoreReservedAndReset(items, definition, state);
            return ProduceCompletion.status(SfxElectricMachineRenderStatus.NO_TARGET);
        }
        List<SfxElectricStack> outputs = target.outputs();
        ToolUseResult toolUse = ToolUseResult.noTool();
        if (action.usesDurableTool()) {
            toolUse = useDurableTool(items, reserved);
            if (toolUse.status() == ToolUseStatus.OUTPUT_TOOL) {
                outputs = append(outputs, toolUse.tool());
            }
        }
        if (!canFitOutputs(items, definition, state, outputs)) {
            return ProduceCompletion.status(SfxElectricMachineRenderStatus.BLOCKED_OUTPUT);
        }
        target.apply().run();
        for (SfxElectricStack output : target.outputs()) {
            pushOutput(items, definition, state, output);
        }
        if (action.usesDurableTool()) {
            if (toolUse.status() == ToolUseStatus.RESTORE_TOOL) {
                restoreOrOutput(items, definition, state, state.activeInputSlot(), toolUse.tool());
            } else if (toolUse.status() == ToolUseStatus.OUTPUT_TOOL) {
                pushOutput(items, definition, state, toolUse.tool());
            }
        }
        return ProduceCompletion.status(SfxElectricMachineRenderStatus.WORKING);
    }

    private static List<SfxElectricStack> previewProduceOutputs(Location location, ProduceAction action) {
        ProduceTarget target = findProduceTarget(location, action);
        return target == null ? List.of() : target.outputs();
    }

    private static ProduceTarget findProduceTarget(Location location, ProduceAction action) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return switch (action) {
            case MILK -> {
                Entity target = firstNearbyEntity(location, PRODUCE_RANGE, PRODUCE_RANGE, entity -> entity instanceof org.bukkit.entity.Ageable ageable && ageable.isAdult() && (entity instanceof Cow || entity instanceof Goat));
                yield target == null ? null : new ProduceTarget(List.of(SfxElectricStack.vanilla(Material.MILK_BUCKET, 1)), () -> {});
            }
            case STEW -> {
                Entity target = firstNearbyEntity(location, PRODUCE_RANGE, PRODUCE_RANGE, entity -> entity instanceof MushroomCow cow && cow.isAdult());
                yield target == null ? null : new ProduceTarget(List.of(SfxElectricStack.vanilla(Material.MUSHROOM_STEW, 1)), () -> {});
            }
            case SHEEP_WOOL -> {
                Entity target = firstNearbyEntity(location, PRODUCE_RANGE, PRODUCE_RANGE, entity -> entity instanceof Sheep sheep && sheep.isAdult() && !sheep.isSheared());
                if (!(target instanceof Sheep sheep)) {
                    yield null;
                }
                Material wool = woolMaterial(sheep.getColor());
                yield new ProduceTarget(List.of(SfxElectricStack.vanilla(wool, 1)), () -> sheep.setSheared(true));
            }
            case HONEY_BOTTLE -> {
                Block hive = findMatureBeehive(location);
                yield hive == null ? null : new ProduceTarget(List.of(SfxElectricStack.vanilla(Material.HONEY_BOTTLE, 1)), () -> resetHoney(hive));
            }
            case HONEYCOMB -> {
                Block hive = findMatureBeehive(location);
                yield hive == null ? null : new ProduceTarget(List.of(SfxElectricStack.vanilla(Material.HONEYCOMB, 3)), () -> resetHoney(hive));
            }
            case ARMADILLO_SCUTE -> {
                Entity target = firstNearbyEntity(location, PRODUCE_RANGE, PRODUCE_RANGE, entity -> entity instanceof Armadillo && entity.isValid());
                yield target == null ? null : new ProduceTarget(List.of(SfxElectricStack.vanilla(Material.ARMADILLO_SCUTE, 1)), () -> {});
            }
            case MOOSHROOM_SHEAR -> {
                Entity target = firstNearbyEntity(location, PRODUCE_RANGE, PRODUCE_RANGE, entity -> entity instanceof MushroomCow cow && cow.isAdult());
                if (!(target instanceof MushroomCow cow)) {
                    yield null;
                }
                Material mushroom = cow.getVariant() == MushroomCow.Variant.BROWN ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
                yield new ProduceTarget(List.of(SfxElectricStack.vanilla(mushroom, 5)), () -> convertMooshroomToCow(cow));
            }
        };
    }

    private static boolean isProduceTool(Material material, boolean sfxBalance) {
        if (material == Material.BUCKET || material == Material.BOWL) {
            return true;
        }
        return sfxBalance && (material == Material.SHEARS || material == Material.GLASS_BOTTLE || material == Material.BRUSH);
    }

    private static boolean hasOverlappingCropAccelerator(SfxBlockDataService blockData, Location location) {
        if (blockData == null || location == null || location.getWorld() == null) {
            return false;
        }
        UUID currentId = blockData.findAnchor(location).map(anchor -> anchor.instanceId()).orElse(null);
        SfxBlockAnchorKey current = SfxBlockAnchorKey.fromLocation(location);
        for (var anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || Objects.equals(instance.instanceId(), currentId)) {
                continue;
            }
            if (!instance.typeId().equals("sf:crop_growth_accelerator") && !instance.typeId().equals("sf:crop_growth_accelerator_2")) {
                continue;
            }
            SfxBlockAnchorKey other = instance.anchorKey();
            if (!other.worldId().equals(current.worldId())) {
                continue;
            }
            if (Math.abs(other.x() - current.x()) <= SFX_GROWTH_RADIUS * 2 && Math.abs(other.z() - current.z()) <= SFX_GROWTH_RADIUS * 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGrowableCrop(Location location, int radius) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block center = location.getBlock();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (isGrowableCrop(center.getRelative(x, 0, z))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasGrowableSapling(Location location, int radius) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block center = location.getBlock();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Tag.SAPLINGS.isTagged(center.getRelative(x, 0, z).getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void runRandomGrowthAttempts(Location location, int attempts, GrowthTarget target) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Block center = location.getBlock();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < attempts; i++) {
            int x = random.nextInt(-SFX_GROWTH_RADIUS, SFX_GROWTH_RADIUS + 1);
            int z = random.nextInt(-SFX_GROWTH_RADIUS, SFX_GROWTH_RADIUS + 1);
            Block block = center.getRelative(x, 0, z);
            if (target == GrowthTarget.CROP) {
                if (isGrowableCrop(block) && random.nextDouble() < SFX_GROWTH_SUCCESS_CHANCE) {
                    growCropWithHeartParticles(block);
                }
                continue;
            }
            if (Tag.SAPLINGS.isTagged(block.getType()) && random.nextDouble() < SFX_GROWTH_SUCCESS_CHANCE) {
                block.applyBoneMeal(BlockFace.UP);
                spawnBoneMealParticles(block);
            }
        }
    }

    private static boolean growCropWithBoneMealParticles(Block crop) {
        if (!increaseCropAge(crop)) {
            return false;
        }
        spawnBoneMealParticles(crop);
        return true;
    }

    private static boolean growCropWithHeartParticles(Block crop) {
        if (!increaseCropAge(crop)) {
            return false;
        }
        spawnBlockHearts(crop);
        return true;
    }

    private static boolean increaseCropAge(Block crop) {
        BlockData data = crop.getBlockData();
        if (!(data instanceof org.bukkit.block.data.Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge()) {
            return false;
        }
        ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
        crop.setBlockData(ageable, false);
        return true;
    }

    private static boolean isGrowableCrop(Block block) {
        if (block == null || Tag.SAPLINGS.isTagged(block.getType())) {
            return false;
        }
        BlockData data = block.getBlockData();
        return data instanceof org.bukkit.block.data.Ageable ageable && ageable.getAge() < ageable.getMaximumAge();
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
                if (isGrowableCrop(block)) {
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
        for (int x = -CLASSIC_TREE_GROWTH_RADIUS; x <= CLASSIC_TREE_GROWTH_RADIUS; x++) {
            for (int z = -CLASSIC_TREE_GROWTH_RADIUS; z <= CLASSIC_TREE_GROWTH_RADIUS; z++) {
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

    private static Block findMatureBeehive(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Block center = location.getBlock();
        for (int x = -PRODUCE_RANGE; x <= PRODUCE_RANGE; x++) {
            for (int y = -PRODUCE_RANGE; y <= PRODUCE_RANGE; y++) {
                for (int z = -PRODUCE_RANGE; z <= PRODUCE_RANGE; z++) {
                    Block block = center.getRelative(x, y, z);
                    if (block.getType() != Material.BEEHIVE && block.getType() != Material.BEE_NEST) {
                        continue;
                    }
                    BlockData data = block.getBlockData();
                    if (data instanceof Beehive hive && hive.getHoneyLevel() >= hive.getMaximumHoneyLevel()) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    private static void resetHoney(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Beehive hive) {
            hive.setHoneyLevel(0);
            block.setBlockData(hive, false);
        }
    }

    private static void convertMooshroomToCow(MushroomCow cow) {
        if (!cow.isValid() || cow.getWorld() == null) {
            return;
        }
        Location spawn = cow.getLocation();
        Cow replacement = cow.getWorld().spawn(spawn, Cow.class);
        replacement.setAge(cow.getAge());
        replacement.setHealth(Math.min(replacement.getHealth(), cow.getHealth()));
        replacement.setCustomName(cow.getCustomName());
        replacement.setCustomNameVisible(cow.isCustomNameVisible());
        replacement.setPersistent(cow.isPersistent());
        cow.remove();
    }

    private static Material woolMaterial(DyeColor color) {
        return switch (color == null ? DyeColor.WHITE : color) {
            case WHITE -> Material.WHITE_WOOL;
            case ORANGE -> Material.ORANGE_WOOL;
            case MAGENTA -> Material.MAGENTA_WOOL;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_WOOL;
            case YELLOW -> Material.YELLOW_WOOL;
            case LIME -> Material.LIME_WOOL;
            case PINK -> Material.PINK_WOOL;
            case GRAY -> Material.GRAY_WOOL;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_WOOL;
            case CYAN -> Material.CYAN_WOOL;
            case PURPLE -> Material.PURPLE_WOOL;
            case BLUE -> Material.BLUE_WOOL;
            case BROWN -> Material.BROWN_WOOL;
            case GREEN -> Material.GREEN_WOOL;
            case RED -> Material.RED_WOOL;
            case BLACK -> Material.BLACK_WOOL;
        };
    }

    private static ToolUseResult useDurableTool(SfxItems items, SfxElectricStack reserved) {
        if (reserved == null) {
            return ToolUseResult.broken();
        }
        ItemStack stack = reserved.toItemStack(items);
        short maxDurability = stack.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return ToolUseResult.restore(SfxElectricStack.fromItemStack(items, stack));
        }
        int unbreaking = UNBREAKING == null ? 0 : stack.getEnchantmentLevel(UNBREAKING);
        boolean consumeDurability = unbreaking <= 0 || ThreadLocalRandom.current().nextInt(unbreaking + 1) == 0;
        if (!consumeDurability) {
            return ToolUseResult.restore(SfxElectricStack.fromItemStack(items, stack));
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return ToolUseResult.restore(SfxElectricStack.fromItemStack(items, stack));
        }
        int nextDamage = damageable.getDamage() + 1;
        if (nextDamage >= maxDurability) {
            return ToolUseResult.broken();
        }
        damageable.setDamage(nextDamage);
        stack.setItemMeta(meta);
        boolean hasMending = MENDING != null && stack.containsEnchantment(MENDING);
        if (hasMending && maxDurability - nextDamage <= 1) {
            return ToolUseResult.output(SfxElectricStack.fromItemStack(items, stack));
        }
        return ToolUseResult.restore(SfxElectricStack.fromItemStack(items, stack));
    }

    private static void restoreOrOutput(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, int slot, SfxElectricStack stack) {
        if (stack == null) {
            return;
        }
        if (restoreInputSafely(items, state, slot, stack)) {
            return;
        }
        if (canFitOutput(items, definition, state, stack)) {
            pushOutput(items, definition, state, stack);
        } else {
            restoreInput(state, Math.max(0, slot), stack);
        }
    }

    private static boolean restoreInputSafely(SfxItems items, SfxElectricMachineState state, int slot, SfxElectricStack stack) {
        if (slot < 0 || slot >= state.inputCapacity()) {
            return false;
        }
        SfxElectricStack current = state.input(slot);
        if (current == null) {
            state.input(slot, stack);
            return true;
        }
        if (stack.canMerge(current, items)) {
            state.input(slot, current.copyWithAmount(current.amount() + stack.amount()));
            return true;
        }
        return false;
    }

    private static List<SfxElectricStack> append(List<SfxElectricStack> stacks, SfxElectricStack stack) {
        List<SfxElectricStack> result = new ArrayList<>(stacks);
        if (stack != null) {
            result.add(stack);
        }
        return List.copyOf(result);
    }

    private static void spawnEntityHearts(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            entity.getWorld().spawnParticle(Particle.HEART, livingEntity.getEyeLocation(), 8, 0.2F, 0.2F, 0.2F, 0.0D);
        }
    }

    private static void spawnBoneMealParticles(Block block) {
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5D, 0.5D, 0.5D), 8, 0.25D, 0.25D, 0.25D, 0.0D);
    }

    private static void spawnGrowthAreaBoneMealParticles(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0.5D, 0.75D, 0.5D), 160, SFX_GROWTH_RADIUS + 0.5D, 0.35D, SFX_GROWTH_RADIUS + 0.5D, 0.0D);
    }

    private static void spawnBlockHearts(Block block) {
        block.getWorld().spawnParticle(Particle.HEART, block.getLocation().add(0.5D, 0.5D, 0.5D), 4, 0.1F, 0.1F, 0.1F, 0.0D);
    }

    private static int collectAllNearbyExperience(Location location) {
        int collected = 0;
        for (ExperienceOrb orb : nearbyExperienceOrbs(location)) {
            if (!orb.isValid()) {
                continue;
            }
            collected += Math.max(0, orb.getExperience());
            orb.remove();
        }
        return collected;
    }

    private static void attractExperience(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Location target = location.clone().add(0.5D, 0.5D, 0.5D);
        for (ExperienceOrb orb : nearbyExperienceOrbs(location)) {
            if (!orb.isValid()) {
                continue;
            }
            Vector direction = target.toVector().subtract(orb.getLocation().toVector());
            if (direction.lengthSquared() > 0.0001D) {
                orb.setVelocity(direction.normalize().multiply(XP_COLLECTOR_ATTRACT_SPEED));
            }
        }
    }

    private static int absorbCloseExperience(Location location) {
        if (location == null || location.getWorld() == null) {
            return 0;
        }
        Location target = location.clone().add(0.5D, 0.5D, 0.5D);
        int collected = 0;
        for (ExperienceOrb orb : nearbyExperienceOrbs(location)) {
            if (!orb.isValid()) {
                continue;
            }
            if (orb.getLocation().distanceSquared(target) <= XP_COLLECTOR_ABSORB_RANGE * XP_COLLECTOR_ABSORB_RANGE) {
                collected += Math.max(0, orb.getExperience());
                orb.remove();
            }
        }
        return collected;
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

    private static FlushResult flushKnowledgeFlasks(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, boolean chargePerFlask) {
        boolean changed = false;
        int consumedEnergy = 0;
        SfxElectricStack flask = SfxElectricStack.sfx("sf:filled_flask_of_knowledge", 1);
        while (state.specialData() >= XP_PER_FLASK && canFitOutput(items, definition, state, flask)) {
            if (chargePerFlask && state.storedEnergy() < XP_FLASK_ENERGY_COST) {
                break;
            }
            if (chargePerFlask) {
                state.storedEnergy(state.storedEnergy() - XP_FLASK_ENERGY_COST);
                consumedEnergy += XP_FLASK_ENERGY_COST;
            }
            pushOutput(items, definition, state, flask);
            state.specialData(state.specialData() - XP_PER_FLASK);
            changed = true;
        }
        return new FlushResult(changed, consumedEnergy);
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

    private static void restoreReservedAndReset(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        int slot = Math.max(0, state.activeInputSlot());
        for (SfxElectricStack stack : state.reservedInputs()) {
            if (items != null && definition != null) {
                restoreOrOutput(items, definition, state, slot, stack);
            } else {
                restoreInput(state, slot, stack);
            }
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

    private static boolean useSfxBalance(JavaPlugin plugin, String key) {
        return plugin == null || plugin.getConfig().getBoolean("electric-machines.sfx-balance." + key, true);
    }


    private static FluidPumpAction findFluidPumpAction(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        int inputSlot = -1;
        SfxElectricStack input = null;
        for (int slot = 0; slot < state.inputCapacity(); slot++) {
            SfxElectricStack candidate = state.input(slot);
            if (candidate == null || candidate.isSfxItem()) {
                continue;
            }
            if (candidate.material() == Material.BUCKET || candidate.material() == Material.GLASS_BOTTLE) {
                inputSlot = slot;
                input = candidate;
                break;
            }
        }
        if (inputSlot < 0 || input == null) {
            return FluidPumpAction.status(SfxElectricMachineRenderStatus.NO_INPUT);
        }
        Block source = findFluidSource(location, input.material());
        if (source == null) {
            return FluidPumpAction.status(SfxElectricMachineRenderStatus.NO_TARGET);
        }
        SfxElectricStack output;
        boolean consumeSource = true;
        if (input.material() == Material.BUCKET) {
            if (source.getType() == Material.LAVA) {
                output = SfxElectricStack.vanilla(Material.LAVA_BUCKET, 1);
            } else if (source.getType() == Material.WATER || source.getType() == Material.BUBBLE_COLUMN) {
                output = SfxElectricStack.vanilla(Material.WATER_BUCKET, 1);
            } else {
                return FluidPumpAction.status(SfxElectricMachineRenderStatus.NO_TARGET);
            }
        } else {
            if (source.getType() != Material.WATER && source.getType() != Material.BUBBLE_COLUMN) {
                return FluidPumpAction.status(SfxElectricMachineRenderStatus.NO_TARGET);
            }
            output = waterBottle(items);
            consumeSource = ThreadLocalRandom.current().nextDouble() < 0.30D;
        }
        if (!canFitOutput(items, definition, state, output)) {
            return FluidPumpAction.status(SfxElectricMachineRenderStatus.OUTPUT_FULL);
        }
        return new FluidPumpAction(SfxElectricMachineRenderStatus.WORKING, inputSlot, output, source, consumeSource);
    }

    private static Block findFluidSource(Location location, Material container) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Block below = location.getBlock().getRelative(BlockFace.DOWN);
        if (container == Material.GLASS_BOTTLE) {
            return (below.getType() == Material.WATER || below.getType() == Material.BUBBLE_COLUMN) && isSourceLiquid(below) ? below : null;
        }
        if ((below.getType() == Material.WATER || below.getType() == Material.BUBBLE_COLUMN) && isSourceLiquid(below)) {
            return below;
        }
        if (below.getType() != Material.LAVA) {
            return null;
        }
        return findConnectedLavaSource(below, 42);
    }

    private static Block findConnectedLavaSource(Block start, int limit) {
        List<Block> queue = new ArrayList<>();
        List<Block> seen = new ArrayList<>();
        queue.add(start);
        seen.add(start);
        for (int index = 0; index < queue.size() && seen.size() <= limit; index++) {
            Block block = queue.get(index);
            if (block.getType() == Material.LAVA && isSourceLiquid(block)) {
                return block;
            }
            for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                Block relative = block.getRelative(face);
                if (relative.getType() != Material.LAVA || seen.contains(relative)) {
                    continue;
                }
                seen.add(relative);
                queue.add(relative);
            }
        }
        return isSourceLiquid(start) ? start : null;
    }

    private static boolean isSourceLiquid(Block block) {
        BlockData data = block.getBlockData();
        return !(data instanceof Levelled levelled) || levelled.getLevel() == 0;
    }

    private static SfxElectricStack waterBottle(SfxItems items) {
        ItemStack stack = new ItemStack(Material.POTION, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(PotionType.WATER);
            stack.setItemMeta(potionMeta);
        }
        return SfxElectricStack.snapshot(stack);
    }

    private abstract static class WorldActionProvider implements SfxElectricRecipeProvider {
        @Override
        public List<SfxElectricRecipe> recipes() {
            return List.of();
        }

        @Override
        public boolean hasWorldAction() {
            return true;
        }
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
            return 1;
        }
    }


    private record FluidPumpAction(SfxElectricMachineRenderStatus status, int inputSlot, SfxElectricStack output, Block source, boolean consumeSource) {
        static FluidPumpAction status(SfxElectricMachineRenderStatus status) {
            return new FluidPumpAction(status, -1, null, null, false);
        }
    }

    private enum GrowthTarget {
        CROP,
        SAPLING
    }

    private enum ProduceStartStatus {
        NO_INPUT,
        NO_TARGET,
        OUTPUT_FULL,
        READY
    }

    private enum ProduceAction {
        MILK("milk", false),
        STEW("stew", false),
        SHEEP_WOOL("sheep_wool", true),
        HONEY_BOTTLE("honey_bottle", false),
        HONEYCOMB("honeycomb", true),
        ARMADILLO_SCUTE("armadillo_scute", true),
        MOOSHROOM_SHEAR("mooshroom_shear", true);

        private final String key;
        private final boolean durableTool;

        ProduceAction(String key, boolean durableTool) {
            this.key = key;
            this.durableTool = durableTool;
        }

        String key() {
            return key;
        }

        boolean usesDurableTool() {
            return durableTool;
        }

        static ProduceAction fromKey(String key) {
            if (key == null) {
                return null;
            }
            String prefix = "sf:produce_collector:";
            if (!key.startsWith(prefix)) {
                return null;
            }
            String actionKey = key.substring(prefix.length()).toLowerCase(Locale.ROOT);
            for (ProduceAction action : values()) {
                if (action.key.equals(actionKey)) {
                    return action;
                }
            }
            return null;
        }
    }

    private enum ToolUseStatus {
        RESTORE_TOOL,
        OUTPUT_TOOL,
        BROKEN
    }

    private record ProduceStart(ProduceStartStatus status, int inputSlot, ProduceAction action, SfxElectricStack primaryOutput) {
        static ProduceStart noInput() {
            return new ProduceStart(ProduceStartStatus.NO_INPUT, -1, null, null);
        }

        static ProduceStart noTarget() {
            return new ProduceStart(ProduceStartStatus.NO_TARGET, -1, null, null);
        }

        static ProduceStart outputFull() {
            return new ProduceStart(ProduceStartStatus.OUTPUT_FULL, -1, null, null);
        }

        static ProduceStart ready(int inputSlot, ProduceAction action, SfxElectricStack primaryOutput) {
            return new ProduceStart(ProduceStartStatus.READY, inputSlot, action, primaryOutput);
        }
    }

    private record ProduceTarget(List<SfxElectricStack> outputs, Runnable apply) {
    }

    private record ProduceCompletion(SfxElectricMachineRenderStatus status) {
        static ProduceCompletion status(SfxElectricMachineRenderStatus status) {
            return new ProduceCompletion(status);
        }
    }

    private record ToolUseResult(ToolUseStatus status, SfxElectricStack tool) {
        static ToolUseResult restore(SfxElectricStack tool) {
            return new ToolUseResult(ToolUseStatus.RESTORE_TOOL, tool);
        }

        static ToolUseResult output(SfxElectricStack tool) {
            return new ToolUseResult(ToolUseStatus.OUTPUT_TOOL, tool);
        }

        static ToolUseResult broken() {
            return new ToolUseResult(ToolUseStatus.BROKEN, null);
        }

        static ToolUseResult noTool() {
            return new ToolUseResult(ToolUseStatus.BROKEN, null);
        }
    }

    private record FlushResult(boolean changed, int consumedEnergy) {
    }
}
