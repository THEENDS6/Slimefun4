package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRules;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRuntime;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import cc.theends6.sfx.internal.inventory.SfxTransferResult;
import cc.theends6.sfx.internal.inventory.SfxTransferTransaction;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Goat;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Wither;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

final class SfxBasicExpansionAreaElectricMachineProviders {
    private static final Logger LOGGER = Logger.getLogger("SlimeFunX");
    private static final String PRODUCE_DEBUG_PREFIX = "[SFX Debug][BasicProduceCollector] ";
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
    private static final int FLUID_PUMP_WORK_TICKS = 10;
    private static final int FLUID_SOURCE_SEARCH_LIMIT = 42;
    private static final Map<FluidPoolCacheKey, FluidPoolCacheEntry> FLUID_POOL_CACHE = new ConcurrentHashMap<>();
    private static final Map<FluidPumpSourceCacheKey, FluidPumpSourceCacheEntry> FLUID_PUMP_SOURCE_CACHE = new ConcurrentHashMap<>();
    private static final Enchantment UNBREAKING = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
    private static final Enchantment MENDING = Enchantment.getByKey(NamespacedKey.minecraft("mending"));

    private SfxBasicExpansionAreaElectricMachineProviders() {
    }


    static void warmFluidPumpPoolCache(JavaPlugin plugin, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Block below = location.getBlock().getRelative(BlockFace.DOWN);
        Material fluid = fluidMaterial(below);
        if (fluid != null && isSourceLiquid(below)) {
            hasLargeEnoughSourcePool(SfxAreaMachineBalance.rules(plugin), location, below, fluid);
        }
    }

    static SfxElectricRecipeProvider fluidPump(SfxAreaMachineRules rules) {
        return new SfxBasicExpansionWorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (isActive(state, "sf:fluid_pump")) {
                    return advanceFluidPump(rules, plugin, items, definition, state, location);
                }
                FluidPumpAction action = findFluidPumpStart(rules, plugin, items, definition, state, location);
                if (action.status() != SfxElectricMachineRenderStatus.WORKING) {
                    return SfxElectricMachineTickResult.status(action.status(), action.status() != SfxElectricMachineRenderStatus.NO_INPUT);
                }
                SfxElectricStack reserved = consumeInput(state, action.inputSlot(), 1);
                if (reserved == null) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
                }
                state.activeRecipeKey("sf:fluid_pump");
                state.activeInputSlot(action.inputSlot());
                state.activeBaseTicks(FLUID_PUMP_WORK_TICKS);
                state.activeOutputs(List.of(action.output()));
                state.reservedInputs(List.of(reserved));
                state.pendingOutput(null);
                state.progressWork(0);
                return advanceFluidPump(rules, plugin, items, definition, state, location);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (isActive(state, "sf:fluid_pump")) {
                    return state.progressWork() >= Math.max(1, state.activeBaseTicks()) ? 0 : definition.energyConsumptionPerTick();
                }
                return requestedFluidPumpEnergy(rules, items, definition, state, location);
            }
        };
    }

    static SfxElectricRecipeProvider assembler(String key, EntityType spawnType, Material headMaterial, int headAmount, Set<Material> bodyMaterials, int bodyAmount, int workTicks) {
        return new SfxBasicExpansionWorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!assemblerEnabled(state)) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.IDLE, state.hasProgress() || state.hasAnyInput());
                }
                String recipeKey = "sf:assembler:" + key;
                if (!isActive(state, recipeKey)) {
                    AssemblerStart start = findAssemblerStart(state, headMaterial, headAmount, bodyMaterials, bodyAmount);
                    if (start.status() != SfxElectricMachineRenderStatus.WORKING) {
                        return SfxElectricMachineTickResult.status(start.status(), start.status() != SfxElectricMachineRenderStatus.NO_INPUT);
                    }
                    consumeAssemblerStacks(state, start.reservedInputs());
                    state.activeRecipeKey(recipeKey);
                    state.activeInputSlot(start.primaryInputSlot());
                    state.activeBaseTicks(workTicks);
                    state.activeOutputs(List.of());
                    state.reservedInputs(start.reservedInputs());
                    state.pendingOutput(null);
                    state.progressWork(0);
                }
                int totalWork = Math.max(1, state.activeBaseTicks());
                if (state.progressWork() >= totalWork) {
                    return completeAssembler(definition, state, location, spawnType);
                }
                if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
                    return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
                }
                state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
                int progressed = Math.min(totalWork, state.progressWork() + definition.speed());
                state.progressWork(progressed);
                if (progressed >= totalWork) {
                    SfxElectricMachineTickResult complete = completeAssembler(definition, state, location, spawnType);
                    return new SfxElectricMachineTickResult(complete.status(), definition.energyConsumptionPerTick(), true, complete.keepActive());
                }
                return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!assemblerEnabled(state)) {
                    return 0;
                }
                String recipeKey = "sf:assembler:" + key;
                if (isActive(state, recipeKey)) {
                    return state.progressWork() >= Math.max(1, state.activeBaseTicks()) ? 0 : definition.energyConsumptionPerTick();
                }
                AssemblerStart start = findAssemblerStart(state, headMaterial, headAmount, bodyMaterials, bodyAmount);
                return start.status() == SfxElectricMachineRenderStatus.WORKING ? definition.energyConsumptionPerTick() : 0;
            }
        };
    }

    static SfxElectricRecipeProvider produceCollector(boolean sfxBalance) {
        return new SfxBasicExpansionWorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (isActiveProduce(state)) {
                    return advanceProduce(plugin, items, definition, state, location, sfxBalance);
                }
                ProduceStart start = findProduceStart(plugin, items, definition, state, location, sfxBalance);
                return startProduce(definition, state, start);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!sfxBalance) {
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

    static SfxElectricRecipeProvider autoBreeder(boolean sfxBalance) {
        return autoBreeder(sfxBalance, ACTION_WORK_TICKS);
    }

    static SfxElectricRecipeProvider autoBreeder(boolean sfxBalance, int workTicks) {
        return entityActionProvider(
                "sf:auto_breeder",
                sfxBalance,
                true,
                workTicks,
                entity -> entity instanceof Animals animal && entity.isValid() && animal.isAdult() && animal.canBreed() && !animal.isLoveMode(),
                entity -> {
                    if (entity instanceof Animals animal) {
                        animal.setLoveModeTicks(600);
                    }
                    spawnEntityHearts(entity);
                });
    }

    static SfxElectricRecipeProvider animalGrowthAccelerator(int ageIncrement) {
        return animalGrowthAccelerator(ageIncrement, ACTION_WORK_TICKS);
    }

    static SfxElectricRecipeProvider animalGrowthAccelerator(int ageIncrement, int workTicks) {
        return new SfxBasicExpansionWorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                Predicate<Entity> predicate = entity -> entity instanceof org.bukkit.entity.Ageable ageable && entity.isValid() && !ageable.isAdult();
                Consumer<Entity> action = entity -> {
                    if (entity instanceof org.bukkit.entity.Ageable ageable) {
                        ageable.setAge(Math.min(0, ageable.getAge() + Math.max(1, ageIncrement)));
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
                return startTimedWork(definition, state, inputSlot, null, workTicks, "sf:animal_growth_accelerator");
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
            }
        };
    }

    static SfxElectricRecipeProvider cropGrowthAccelerator(SfxAreaMachineRuntime areaMachines, int classicRadius, int sfxAttempts, boolean sfxMode) {
        return new SfxBasicExpansionWorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (sfxMode) {
                    return tickSfxCropGrowth(areaMachines, definition, state, location, sfxAttempts);
                }
                return tickClassicCropGrowth(definition, state, location, classicRadius, classicRadius);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!sfxMode) {
                    return state.hasProgress() || state.hasAnyInput() ? definition.energyConsumptionPerTick() : 0;
                }
                if (state.hasProgress()) {
                    return hasOverlappingCropAccelerator(areaMachines, location) ? 0 : definition.energyConsumptionPerTick();
                }
                if (firstInputSlot(state, false) < 0 || hasOverlappingCropAccelerator(areaMachines, location) || !hasGrowableCrop(location, SFX_GROWTH_RADIUS)) {
                    return 0;
                }
                return definition.energyConsumptionPerTick();
            }
        };
    }

    static SfxElectricRecipeProvider treeGrowthAccelerator(boolean sfxMode) {
        return new SfxBasicExpansionWorldActionProvider() {
            @Override
            public SfxElectricMachineTickResult tickWorldAction(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (sfxMode) {
                    return tickSfxTreeGrowth(definition, state, location);
                }
                return tickClassicTreeGrowth(definition, state, location);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!sfxMode) {
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

    static SfxElectricRecipeProvider expCollector(boolean sfxBalance, int flaskEnergyCost) {
        return new SfxBasicExpansionTickProvider() {
            @Override
            public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, SfxMachineTickContext context) {
                FlushResult initialFlush = flushKnowledgeFlasks(items, definition, state, sfxBalance, flaskEnergyCost);
                boolean changed = initialFlush.changed();
                int consumedEnergy = initialFlush.consumedEnergy();
                int supplementalEnergy = initialFlush.consumedEnergy();
                SfxElectricStack flask = SfxElectricStack.sfx("sf:filled_flask_of_knowledge", 1);
                if (state.specialData() >= XP_PER_FLASK && !canFitOutput(items, definition, state, flask)) {
                    state.activeRecipeKey("sf:xp_collector");
                    state.activeBaseTicks(ACTION_WORK_TICKS);
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.OUTPUT_FULL, consumedEnergy, supplementalEnergy, changed, true);
                }
                if (sfxBalance && state.specialData() >= XP_PER_FLASK && state.storedEnergy() < flaskEnergyCost) {
                    state.activeRecipeKey("sf:xp_collector");
                    state.activeBaseTicks(ACTION_WORK_TICKS);
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_POWER, consumedEnergy, supplementalEnergy, changed, true);
                }

                state.activeRecipeKey("sf:xp_collector");
                state.activeBaseTicks(ACTION_WORK_TICKS);
                if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_POWER, consumedEnergy, supplementalEnergy, changed, true);
                }

                int elapsedTicks = Math.max(1, context == null ? 1 : context.elapsedTicksInt());
                int energyPerTick = Math.max(1, definition.energyConsumptionPerTick());
                int progressTicks = Math.min(elapsedTicks, state.storedEnergy() / energyPerTick);
                if (progressTicks <= 0) {
                    return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_POWER, consumedEnergy, supplementalEnergy, changed, true);
                }
                int baseEnergy = progressTicks * energyPerTick;
                state.storedEnergy(state.storedEnergy() - baseEnergy);
                int nextProgress = state.progressWork() + progressTicks;
                consumedEnergy += baseEnergy;
                changed = true;
                if (sfxBalance) {
                    for (int tick = 0; tick < progressTicks; tick++) {
                        attractExperience(location);
                        int collected = absorbCloseExperience(location);
                        if (collected > 0) {
                            state.specialData(state.specialData() + collected);
                            changed = true;
                        }
                        FlushResult flush = flushKnowledgeFlasks(items, definition, state, true, flaskEnergyCost);
                        consumedEnergy += flush.consumedEnergy();
                        supplementalEnergy += flush.consumedEnergy();
                        changed = changed || flush.changed();
                    }
                }
                while (nextProgress >= ACTION_WORK_TICKS) {
                    nextProgress -= ACTION_WORK_TICKS;
                    int collected = sfxBalance ? 0 : collectAllNearbyExperience(location);
                    if (collected > 0) {
                        state.specialData(state.specialData() + collected);
                    }
                    FlushResult flush = flushKnowledgeFlasks(items, definition, state, sfxBalance, flaskEnergyCost);
                    consumedEnergy += flush.consumedEnergy();
                    supplementalEnergy += flush.consumedEnergy();
                    changed = changed || flush.changed() || collected > 0;
                }
                state.progressWork(nextProgress);

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

    private static SfxElectricRecipeProvider entityActionProvider(String key, boolean sfxBalance, boolean organicFood, int workTicks, Predicate<Entity> predicate, Consumer<Entity> action) {
        return new SfxBasicExpansionWorldActionProvider() {
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
                return startTimedWork(definition, state, inputSlot, null, workTicks, key);
            }

            @Override
            public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
                if (!sfxBalance) {
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

    private static SfxElectricMachineTickResult tickSfxCropGrowth(SfxAreaMachineRuntime areaMachines, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, int attempts) {
        if (isActive(state, "sf:crop_growth_accelerator:sfx")) {
            if (hasOverlappingCropAccelerator(areaMachines, location)) {
                return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OVERLAPPING_AREA, true);
            }
            return advanceSfxGrowth(definition, state, location, attempts, GrowthTarget.CROP);
        }
        if (hasOverlappingCropAccelerator(areaMachines, location)) {
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
        if (isBrushProduceKey(key) || isBrushStack(reserved)) {
            debugProduce("unexpected timed path consumed input key=" + key
                    + " slot=" + inputSlot
                    + " reserved=" + describeStack(reserved));
        }
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, 0, true);
    }

    private static SfxElectricMachineTickResult startProduce(SfxElectricMachineDefinition definition, SfxElectricMachineState state, ProduceStart start) {
        if (start.action() == ProduceAction.ARMADILLO_SCUTE || isBrushStack(slotInput(state, start.inputSlot()))) {
            debugProduce("startProduce status=" + start.status()
                    + " action=" + start.action()
                    + " route=" + (start.action() != null && start.action().usesDurableTool() ? "tool" : "timed")
                    + " slot=" + start.inputSlot()
                    + " input=" + describeStack(slotInput(state, start.inputSlot()))
                    + " activeBefore=" + describeActiveState(state));
        }
        return switch (start.status()) {
            case NO_INPUT -> SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
            case NO_TARGET -> SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
            case OUTPUT_FULL -> SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OUTPUT_FULL, true);
            case READY -> start.action().usesDurableTool()
                    ? startToolProduceWork(definition, state, start.inputSlot(), PRODUCE_WORK_TICKS, "sf:produce_collector:" + start.action().key())
                    : startTimedWork(definition, state, start.inputSlot(), start.primaryOutput(), PRODUCE_WORK_TICKS, "sf:produce_collector:" + start.action().key());
        };
    }

    private static SfxElectricMachineTickResult startToolProduceWork(SfxElectricMachineDefinition definition, SfxElectricMachineState state, int inputSlot, int workTicks, String key) {
        if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
        }
        SfxElectricStack tool = state.input(inputSlot);
        if (tool == null || tool.isSfxItem() || tool.amount() <= 0) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, false);
        }
        if (isBrushProduceKey(key) || isBrushStack(tool)) {
            debugProduce("startToolProduceWork key=" + key
                    + " slot=" + inputSlot
                    + " toolBefore=" + describeStack(tool)
                    + " storedEnergy=" + state.storedEnergy()
                    + " workTicks=" + workTicks);
        }
        state.activeRecipeKey(key);
        state.activeInputSlot(inputSlot);
        state.activeBaseTicks(workTicks);
        state.activeOutputs(List.of());
        state.reservedInputs(List.of());
        state.pendingOutput(null);
        state.progressWork(0);
        if (isBrushProduceKey(key) || isBrushStack(tool)) {
            debugProduce("startToolProduceWork activeAfter=" + describeActiveState(state)
                    + " toolAfter=" + describeStack(state.input(inputSlot)));
        }
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, 0, true);
    }

    private static SfxElectricMachineTickResult advanceProduce(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, boolean sfxBalance) {
        recoverLegacyReservedProduceTool(state);
        ProduceAction activeAction = ProduceAction.fromKey(state.activeRecipeKey());
        if (activeAction != null && activeAction.usesDurableTool() && !isExpectedTool(currentActiveInput(state), expectedTool(activeAction))) {
            if (activeAction == ProduceAction.ARMADILLO_SCUTE) {
                debugProduce("advanceProduce interrupted missing tool action=" + activeAction
                        + " active=" + describeActiveState(state)
                        + " input=" + describeStack(currentActiveInput(state)));
            }
            state.resetProgress();
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_INPUT, true);
        }
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

    private static void recoverLegacyReservedProduceTool(SfxElectricMachineState state) {
        ProduceAction action = ProduceAction.fromKey(state.activeRecipeKey());
        if (action == null || !action.usesDurableTool()) {
            return;
        }
        int slot = state.activeInputSlot();
        if (slot < 0 || slot >= state.inputCapacity() || state.input(slot) != null) {
            if (action == ProduceAction.ARMADILLO_SCUTE && !state.reservedInputs().isEmpty()) {
                debugProduce("recoverLegacyReservedProduceTool clear reserved without restore action=" + action
                        + " slot=" + slot
                        + " input=" + describeStack(slotInput(state, slot))
                        + " reserved=" + describeStacks(state.reservedInputs()));
            }
            state.reservedInputs(List.of());
            state.activeOutputs(List.of());
            return;
        }
        Material expected = expectedTool(action);
        for (SfxElectricStack reserved : state.reservedInputs()) {
            if (isExpectedTool(reserved, expected)) {
                if (action == ProduceAction.ARMADILLO_SCUTE) {
                    debugProduce("recoverLegacyReservedProduceTool restored action=" + action
                            + " slot=" + slot
                            + " reserved=" + describeStack(reserved));
                }
                state.input(slot, reserved);
                state.reservedInputs(List.of());
                state.activeOutputs(List.of());
                return;
            }
        }
        if (action == ProduceAction.ARMADILLO_SCUTE && !state.reservedInputs().isEmpty()) {
            debugProduce("recoverLegacyReservedProduceTool drop non-matching reserved action=" + action
                    + " slot=" + slot
                    + " reserved=" + describeStacks(state.reservedInputs()));
        }
        state.reservedInputs(List.of());
        state.activeOutputs(List.of());
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
        SfxElectricStack tool = slot >= 0 && slot < state.inputCapacity() ? state.input(slot) : null;
        if (action.usesDurableTool() && !isExpectedTool(tool, expectedTool(action))) {
            return ProduceStart.noTarget();
        }
        List<SfxElectricStack> outputs = previewProduceOutputs(location, action);
        if (outputs.isEmpty()) {
            return ProduceStart.noTarget();
        }
        outputs = withPossibleProtectedToolOutput(outputs, tool, action);
        if (!canFitOutputs(items, definition, state, outputs)) {
            return ProduceStart.outputFull();
        }
        return ProduceStart.ready(slot, action, outputs.getFirst());
    }

    private static ProduceCompletion completeProduce(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, ProduceAction action, boolean sfxBalance) {
        SfxElectricStack tool = action.usesDurableTool()
                ? currentActiveInput(state)
                : state.reservedInputs().isEmpty() ? null : state.reservedInputs().getFirst();
        if (action == ProduceAction.ARMADILLO_SCUTE) {
            debugProduce("completeProduce begin location=" + describeLocation(location)
                    + " active=" + describeActiveState(state)
                    + " tool=" + describeStack(tool)
                    + " reserved=" + describeStacks(state.reservedInputs()));
        }
        if (action.usesDurableTool() && !isExpectedTool(tool, expectedTool(action))) {
            if (action == ProduceAction.ARMADILLO_SCUTE) {
                debugProduce("completeProduce no expected tool expected=" + expectedTool(action)
                        + " tool=" + describeStack(tool));
            }
            state.resetProgress();
            return ProduceCompletion.status(SfxElectricMachineRenderStatus.NO_INPUT);
        }
        ProduceTarget target = findProduceTarget(location, action);
        if (target == null) {
            if (action == ProduceAction.ARMADILLO_SCUTE) {
                debugProduce("completeProduce no target tool=" + describeStack(tool)
                        + " location=" + describeLocation(location));
            }
            if (action.usesDurableTool()) {
                state.resetProgress();
            } else {
                restoreReservedAndReset(items, definition, state);
            }
            return ProduceCompletion.status(SfxElectricMachineRenderStatus.NO_TARGET);
        }
        List<SfxElectricStack> outputs = target.outputs();
        ToolDamageResult toolDamage = null;
        if (action.usesDurableTool()) {
            toolDamage = damageToolStack(tool, expectedTool(action), toolDamage(action));
            if (toolDamage != null && toolDamage.moveToOutput() && toolDamage.stack() != null) {
                outputs = append(outputs, toolDamage.stack());
            }
            if (action == ProduceAction.ARMADILLO_SCUTE) {
                debugProduce("completeProduce damageResult damage=" + toolDamage(action)
                        + " result=" + describeToolDamage(toolDamage)
                        + " outputs=" + describeStacks(outputs));
            }
        }
        if (!canFitOutputs(items, definition, state, outputs)) {
            if (action == ProduceAction.ARMADILLO_SCUTE) {
                debugProduce("completeProduce blockedOutput outputs=" + describeStacks(outputs));
            }
            return ProduceCompletion.status(SfxElectricMachineRenderStatus.BLOCKED_OUTPUT);
        }
        if (action.usesDurableTool()) {
            applyToolDamageToActiveInput(state, toolDamage);
        }
        target.apply().run();
        for (SfxElectricStack output : outputs) {
            pushOutput(items, definition, state, output);
        }
        if (action == ProduceAction.ARMADILLO_SCUTE) {
            debugProduce("completeProduce success active=" + describeActiveState(state)
                    + " inputAfter=" + describeStack(slotInput(state, state.activeInputSlot())));
        }
        return ProduceCompletion.status(SfxElectricMachineRenderStatus.WORKING);
    }

    private static SfxElectricStack currentActiveInput(SfxElectricMachineState state) {
        int slot = state.activeInputSlot();
        return slot < 0 || slot >= state.inputCapacity() ? null : state.input(slot);
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

    private static boolean isExpectedTool(SfxElectricStack tool, Material expected) {
        return expected != null && tool != null && !tool.isSfxItem() && tool.material() == expected;
    }

    private static List<SfxElectricStack> withPossibleProtectedToolOutput(List<SfxElectricStack> outputs, SfxElectricStack tool, ProduceAction action) {
        if (!action.usesDurableTool()) {
            return outputs;
        }
        SfxElectricStack possible = possibleProtectedZeroToolOutput(tool, expectedTool(action), toolDamage(action));
        return appendIfNotNull(outputs, possible);
    }

    private static List<SfxElectricStack> appendIfNotNull(List<SfxElectricStack> base, SfxElectricStack extra) {
        if (extra == null) {
            return base;
        }
        List<SfxElectricStack> result = new ArrayList<>(base.size() + 1);
        result.addAll(base);
        result.add(extra);
        return List.copyOf(result);
    }

    private static Material expectedTool(ProduceAction action) {
        return switch (action) {
            case SHEEP_WOOL, HONEYCOMB, MOOSHROOM_SHEAR -> Material.SHEARS;
            case ARMADILLO_SCUTE -> Material.BRUSH;
            default -> null;
        };
    }

    private static int toolDamage(ProduceAction action) {
        return action == ProduceAction.ARMADILLO_SCUTE ? 16 : 1;
    }

    private static void applyToolDamageToActiveInput(SfxElectricMachineState state, ToolDamageResult result) {
        int slot = state.activeInputSlot();
        if (slot < 0 || slot >= state.inputCapacity()) {
            return;
        }
        if (result == null || result.moveToOutput()) {
            state.input(slot, null);
            return;
        }
        state.input(slot, result.stack());
    }

    private static ToolDamageResult damageToolStack(SfxElectricStack tool, Material expected, int damage) {
        ItemStack item = tool.hasSnapshot() ? tool.snapshot() : new ItemStack(expected);
        item.setAmount(1);
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            if (expected == Material.BRUSH) {
                debugProduce("damageToolStack no Damageable tool=" + describeStack(tool));
            }
            return new ToolDamageResult(SfxElectricStack.snapshot(item), false);
        }
        int maxDurability = expected.getMaxDurability();
        int currentDamage = damageable.getDamage();
        if (maxDurability > 0 && currentDamage >= maxDurability) {
            if (expected == Material.BRUSH) {
                debugProduce("damageToolStack already zero durability currentDamage=" + currentDamage
                        + " max=" + maxDurability
                        + " tool=" + describeStack(tool));
            }
            return null;
        }
        int appliedDamage = rollDurabilityDamage(item, damage);
        int rawDamage = currentDamage + appliedDamage;
        boolean overDamaged = maxDurability > 0 && rawDamage >= maxDurability;
        if (overDamaged && !hasDurabilityProtection(item)) {
            if (expected == Material.BRUSH) {
                debugProduce("damageToolStack break unprotected currentDamage=" + currentDamage
                        + " applied=" + appliedDamage
                        + " max=" + maxDurability
                        + " tool=" + describeStack(tool));
            }
            return null;
        }
        int newDamage = maxDurability > 0 ? Math.min(maxDurability, rawDamage) : rawDamage;
        damageable.setDamage(newDamage);
        item.setItemMeta(damageable);
        if (expected == Material.BRUSH) {
            debugProduce("damageToolStack applied currentDamage=" + currentDamage
                    + " applied=" + appliedDamage
                    + " newDamage=" + newDamage
                    + " max=" + maxDurability
                    + " overDamaged=" + overDamaged
                    + " protected=" + hasDurabilityProtection(item));
        }
        return new ToolDamageResult(SfxElectricStack.snapshot(item), overDamaged);
    }

    private static int rollDurabilityDamage(ItemStack item, int damage) {
        int rolls = Math.max(1, damage);
        int unbreaking = UNBREAKING == null ? 0 : item.getEnchantmentLevel(UNBREAKING);
        if (unbreaking <= 0) {
            return rolls;
        }
        int applied = 0;
        for (int roll = 0; roll < rolls; roll++) {
            if (ThreadLocalRandom.current().nextInt(unbreaking + 1) == 0) {
                applied++;
            }
        }
        return applied;
    }

    private static SfxElectricStack possibleProtectedZeroToolOutput(SfxElectricStack tool, Material expected, int damage) {
        if (!isExpectedTool(tool, expected)) {
            return null;
        }
        ItemStack item = tool.hasSnapshot() ? tool.snapshot() : new ItemStack(expected);
        item.setAmount(1);
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return null;
        }
        int maxDurability = expected.getMaxDurability();
        int currentDamage = damageable.getDamage();
        if (maxDurability <= 0 || currentDamage >= maxDurability || currentDamage + Math.max(1, damage) < maxDurability || !hasDurabilityProtection(item)) {
            return null;
        }
        damageable.setDamage(maxDurability);
        item.setItemMeta(damageable);
        return SfxElectricStack.snapshot(item);
    }

    private static boolean hasDurabilityProtection(ItemStack item) {
        return (UNBREAKING != null && item.getEnchantmentLevel(UNBREAKING) > 0)
                || (MENDING != null && item.getEnchantmentLevel(MENDING) > 0);
    }

    private static boolean hasOverlappingCropAccelerator(SfxAreaMachineRuntime areaMachines, Location location) {
        return areaMachines != null && areaMachines.hasOverlappingMachine(
                location, "sf:crop_growth_accelerator", SFX_GROWTH_RADIUS * 2, Integer.MAX_VALUE);
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
        cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(null, "sf:area_machine", crop, ageable, false, "electric-area", "growth-accelerator");
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
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(null, "sf:area_machine", block, hive, false, "electric-area", "produce-collector:hive-reset");
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

    private static FlushResult flushKnowledgeFlasks(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, boolean chargePerFlask, int energyCost) {
        boolean changed = false;
        int consumedEnergy = 0;
        SfxElectricStack flask = SfxElectricStack.sfx("sf:filled_flask_of_knowledge", 1);
        while (state.specialData() >= XP_PER_FLASK && canFitOutput(items, definition, state, flask)) {
            if (chargePerFlask && state.storedEnergy() < energyCost) {
                break;
            }
            if (chargePerFlask) {
                state.storedEnergy(state.storedEnergy() - energyCost);
                consumedEnergy += energyCost;
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
        SfxElectricOutputEndpoint endpoint = new SfxElectricOutputEndpoint(items, state, slot);
        SfxTransferResult result = new SfxTransferTransaction().commit(
                output.toItemStack(items),
                output.amount(),
                List.of(new SfxTransferTransaction.Target(endpoint, output.amount())),
                true
        );
        if (result.inserted() != output.amount()) {
            return;
        }
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

    private static boolean isActiveProduce(SfxElectricMachineState state) {
        if (state.activeRecipeKey() == null || !state.activeRecipeKey().startsWith("sf:produce_collector:") || !state.hasProgress()) {
            return false;
        }
        ProduceAction action = ProduceAction.fromKey(state.activeRecipeKey());
        return action != null && action.usesDurableTool() || state.hasReservedInput();
    }

    private static boolean isActive(SfxElectricMachineState state, String key) {
        return state.activeRecipeKey() != null && state.activeRecipeKey().startsWith(key) && state.hasReservedInput();
    }

    private static boolean isBrushProduceKey(String key) {
        return key != null && key.contains("armadillo_scute");
    }

    private static boolean isBrushStack(SfxElectricStack stack) {
        return stack != null && !stack.isSfxItem() && stack.material() == Material.BRUSH;
    }

    private static SfxElectricStack slotInput(SfxElectricMachineState state, int slot) {
        return slot < 0 || slot >= state.inputCapacity() ? null : state.input(slot);
    }

    private static void debugProduce(String message) {
        LOGGER.warning(PRODUCE_DEBUG_PREFIX + message);
    }

    private static String describeActiveState(SfxElectricMachineState state) {
        return "key=" + state.activeRecipeKey()
                + ",slot=" + state.activeInputSlot()
                + ",progress=" + state.progressWork() + "/" + state.activeBaseTicks()
                + ",reserved=" + state.reservedInputs().size()
                + ",outputs=" + state.activeOutputs().size()
                + ",hasProgress=" + state.hasProgress()
                + ",hasReserved=" + state.hasReservedInput();
    }

    private static String describeStacks(List<SfxElectricStack> stacks) {
        if (stacks == null) {
            return "null";
        }
        List<String> descriptions = new ArrayList<>(stacks.size());
        for (SfxElectricStack stack : stacks) {
            descriptions.add(describeStack(stack));
        }
        return descriptions.toString();
    }

    private static String describeStack(SfxElectricStack stack) {
        if (stack == null) {
            return "null";
        }
        if (stack.isSfxItem()) {
            return "sfx:" + stack.itemId() + "x" + stack.amount()
                    + ",snapshot=" + stack.hasSnapshot();
        }
        StringBuilder builder = new StringBuilder();
        builder.append(stack.material()).append('x').append(stack.amount())
                .append(",snapshot=").append(stack.hasSnapshot());
        ItemStack snapshot = stack.snapshot();
        if (snapshot != null && snapshot.getItemMeta() instanceof Damageable damageable) {
            builder.append(",damage=").append(damageable.getDamage())
                    .append('/').append(stack.material().getMaxDurability());
        } else if (stack.material() != null && stack.material().getMaxDurability() > 0) {
            builder.append(",damage=0/").append(stack.material().getMaxDurability());
        }
        return builder.toString();
    }

    private static String describeToolDamage(ToolDamageResult result) {
        if (result == null) {
            return "null";
        }
        return "moveToOutput=" + result.moveToOutput()
                + ",stack=" + describeStack(result.stack());
    }

    private static String describeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }
        return location.getWorld().getName()
                + "@" + location.getBlockX()
                + "," + location.getBlockY()
                + "," + location.getBlockZ();
    }

    private static int requestedFluidPumpEnergy(SfxAreaMachineRules rules, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        if (state.hasPendingOutput()) {
            return 0;
        }
        Material container = null;
        for (int slot = 0; slot < Math.min(state.inputCapacity(), definition.inputSlots().length); slot++) {
            SfxElectricStack candidate = state.input(slot);
            if (candidate == null || candidate.isSfxItem()) {
                continue;
            }
            if (candidate.material() == Material.BUCKET || candidate.material() == Material.GLASS_BOTTLE) {
                container = candidate.material();
                break;
            }
        }
        if (container == null) {
            return 0;
        }
        Material fluid = cachedFluidForRequest(rules, location, container).orElse(null);
        if (fluid == null) {
            return 0;
        }
        SfxElectricStack output = outputForFluid(items, container, fluid);
        return output != null && canFitOutput(items, definition, state, output) ? definition.energyConsumptionPerTick() : 0;
    }

    private static SfxElectricMachineTickResult advanceFluidPump(SfxAreaMachineRules rules, JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        int totalWork = Math.max(1, state.activeBaseTicks());
        if (state.progressWork() >= totalWork) {
            return completeFluidPump(rules, plugin, items, definition, state, location);
        }
        if (state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
        }
        state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
        int progressed = Math.min(totalWork, state.progressWork() + definition.speed());
        state.progressWork(progressed);
        if (progressed >= totalWork) {
            SfxElectricMachineTickResult completion = completeFluidPump(rules, plugin, items, definition, state, location);
            return new SfxElectricMachineTickResult(completion.status(), definition.energyConsumptionPerTick(), true, completion.keepActive());
        }
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true);
    }

    private static SfxElectricMachineTickResult completeFluidPump(SfxAreaMachineRules rules, JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        FluidPumpAction action = findFluidPumpCompletion(rules, plugin, items, definition, state, location, true);
        if (action.status() == SfxElectricMachineRenderStatus.NO_INPUT || action.status() == SfxElectricMachineRenderStatus.NO_TARGET) {
            return SfxElectricMachineTickResult.status(action.status(), true);
        }
        if (action.status() == SfxElectricMachineRenderStatus.OUTPUT_FULL) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OUTPUT_FULL, true);
        }
        pushOutput(items, definition, state, action.output());
        if (action.consumeSource()) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, definition.id(), action.source(), Material.AIR, true, "electric-area", "consume-source");
        }
        state.resetProgress();
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, 0, true);
    }

    private static FluidPumpAction findFluidPumpStart(SfxAreaMachineRules rules, JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        int inputSlot = -1;
        SfxElectricStack input = null;
        for (int slot = 0; slot < Math.min(state.inputCapacity(), definition.inputSlots().length); slot++) {
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
        return buildFluidPumpAction(rules, plugin, items, definition, state, location, input.material(), inputSlot, false);
    }

    private static FluidPumpAction findFluidPumpCompletion(SfxAreaMachineRules rules, JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, boolean rollChance) {
        SfxElectricStack reserved = state.reservedInputs().isEmpty() ? null : state.reservedInputs().getFirst();
        if (reserved == null || reserved.isSfxItem() || (reserved.material() != Material.BUCKET && reserved.material() != Material.GLASS_BOTTLE)) {
            return FluidPumpAction.status(SfxElectricMachineRenderStatus.NO_INPUT);
        }
        return buildFluidPumpAction(rules, plugin, items, definition, state, location, reserved.material(), state.activeInputSlot(), rollChance);
    }

    private static FluidPumpAction buildFluidPumpAction(SfxAreaMachineRules rules, JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, Material container, int inputSlot, boolean rollChance) {
        Block source = findFluidSource(rules, location, container, true);
        if (source == null) {
            return FluidPumpAction.status(SfxElectricMachineRenderStatus.NO_TARGET);
        }
        Material fluid = fluidMaterial(source);
        SfxElectricStack output = outputForFluid(items, container, fluid);
        if (output == null) {
            return FluidPumpAction.status(SfxElectricMachineRenderStatus.NO_TARGET);
        }
        if (!canFitOutput(items, definition, state, output)) {
            return FluidPumpAction.status(SfxElectricMachineRenderStatus.OUTPUT_FULL);
        }
        boolean largePool = hasLargeEnoughSourcePool(rules, location, source, fluid);
        boolean consumeSource = !largePool && (container != Material.GLASS_BOTTLE || (rollChance && ThreadLocalRandom.current().nextDouble() < 0.30D));
        return new FluidPumpAction(SfxElectricMachineRenderStatus.WORKING, inputSlot, output, source, consumeSource);
    }

    private static Block findFluidSource(SfxAreaMachineRules rules, Location location, Material container, boolean refreshIfExpired) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        FluidPumpSourceCacheKey key = new FluidPumpSourceCacheKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), container);
        long now = location.getWorld().getFullTime();
        int interval = fluidPumpProbeInterval(rules);
        FluidPumpSourceCacheEntry cached = FLUID_PUMP_SOURCE_CACHE.get(key);
        if (cached != null && now - cached.checkedTick() < interval) {
            if (!cached.found() || cached.fluid() == null) {
                return null;
            }
            Block cachedBlock = location.getWorld().getBlockAt(cached.x(), cached.y(), cached.z());
            if (isSameFluid(cachedBlock, cached.fluid()) && isSourceLiquid(cachedBlock)) {
                return cachedBlock;
            }
            FLUID_PUMP_SOURCE_CACHE.remove(key);
        }
        if (!refreshIfExpired) {
            return null;
        }
        Block found = searchFluidSource(location, container);
        if (found == null) {
            FLUID_PUMP_SOURCE_CACHE.put(key, new FluidPumpSourceCacheEntry(now, false, null, 0, 0, 0));
            return null;
        }
        Material fluid = fluidMaterial(found);
        FLUID_PUMP_SOURCE_CACHE.put(key, new FluidPumpSourceCacheEntry(now, true, fluid, found.getX(), found.getY(), found.getZ()));
        return found;
    }

    private static Optional<Material> cachedFluidForRequest(SfxAreaMachineRules rules, Location location, Material container) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        FluidPumpSourceCacheKey key = new FluidPumpSourceCacheKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), container);
        FluidPumpSourceCacheEntry cached = FLUID_PUMP_SOURCE_CACHE.get(key);
        if (cached == null || !cached.found() || cached.fluid() == null) {
            return Optional.empty();
        }
        long now = location.getWorld().getFullTime();
        return now - cached.checkedTick() < fluidPumpProbeInterval(rules) ? Optional.of(cached.fluid()) : Optional.empty();
    }

    private static int fluidPumpProbeInterval(SfxAreaMachineRules rules) {
        return Math.max(1, rules.fluidPumpProbeIntervalTicks());
    }

    private static SfxElectricStack outputForFluid(SfxItems items, Material container, Material fluid) {
        if (container == Material.BUCKET) {
            if (fluid == Material.LAVA) {
                return SfxElectricStack.vanilla(Material.LAVA_BUCKET, 1);
            }
            if (fluid == Material.WATER) {
                return SfxElectricStack.vanilla(Material.WATER_BUCKET, 1);
            }
            return null;
        }
        return fluid == Material.WATER ? waterBottle(items) : null;
    }

    private static Block searchFluidSource(Location location, Material container) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Block below = location.getBlock().getRelative(BlockFace.DOWN);
        if (container == Material.GLASS_BOTTLE) {
            return isSameFluid(below, Material.WATER) ? findConnectedFluidSource(below, Material.WATER, FLUID_SOURCE_SEARCH_LIMIT) : null;
        }
        if (isSameFluid(below, Material.WATER)) {
            return findConnectedFluidSource(below, Material.WATER, FLUID_SOURCE_SEARCH_LIMIT);
        }
        if (isSameFluid(below, Material.LAVA)) {
            return findConnectedFluidSource(below, Material.LAVA, FLUID_SOURCE_SEARCH_LIMIT);
        }
        return null;
    }

    private static Block findConnectedFluidSource(Block start, Material fluid, int limit) {
        List<Block> queue = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        queue.add(start);
        seen.add(blockKey(start));
        for (int index = 0; index < queue.size() && seen.size() <= limit; index++) {
            Block block = queue.get(index);
            if (isSameFluid(block, fluid) && isSourceLiquid(block)) {
                return block;
            }
            for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                Block relative = block.getRelative(face);
                if (!isSameFluid(relative, fluid) || !seen.add(blockKey(relative))) {
                    continue;
                }
                queue.add(relative);
            }
        }
        return isSameFluid(start, fluid) && isSourceLiquid(start) ? start : null;
    }

    private static boolean hasLargeEnoughSourcePool(SfxAreaMachineRules rules, Location machine, Block source, Material fluid) {
        if (!rules.fluidPumpOptimization()) {
            return false;
        }
        if (machine == null || source == null || fluid == null || source.getWorld() == null) {
            return false;
        }
        Block below = machine.getBlock().getRelative(BlockFace.DOWN);
        if (!sameBlock(below, source) || !isSameFluid(below, fluid) || !isSourceLiquid(below)) {
            return false;
        }
        int threshold = fluid == Material.LAVA
                ? rules.lavaSourceThreshold()
                : rules.waterSourceThreshold();
        int interval = rules.fluidPumpProbeIntervalTicks();
        FluidPoolCacheKey key = new FluidPoolCacheKey(source.getWorld().getUID(), source.getX(), source.getY(), source.getZ(), fluid, threshold);
        long now = source.getWorld().getFullTime();
        FluidPoolCacheEntry cached = FLUID_POOL_CACHE.get(key);
        if (cached != null && now - cached.checkedTick() < Math.max(1, interval)) {
            return cached.largeEnough();
        }
        boolean largeEnough = hasAtLeastConnectedSourceBlocks(source, fluid, threshold);
        FLUID_POOL_CACHE.put(key, new FluidPoolCacheEntry(now, largeEnough));
        return largeEnough;
    }

    private static boolean hasAtLeastConnectedSourceBlocks(Block start, Material fluid, int threshold) {
        if (threshold <= 1) {
            return true;
        }
        List<Block> queue = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        queue.add(start);
        seen.add(blockKey(start));
        int sources = 0;
        int maxVisited = Math.max(threshold * 8, threshold + 16);
        for (int index = 0; index < queue.size() && seen.size() <= maxVisited; index++) {
            Block block = queue.get(index);
            if (!isSameFluid(block, fluid)) {
                continue;
            }
            if (isSourceLiquid(block) && ++sources >= threshold) {
                return true;
            }
            for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                Block relative = block.getRelative(face);
                if (!isSameFluid(relative, fluid) || !seen.add(blockKey(relative))) {
                    continue;
                }
                queue.add(relative);
            }
        }
        return false;
    }

    private static Material fluidMaterial(Block block) {
        if (block == null) {
            return null;
        }
        if (block.getType() == Material.WATER || block.getType() == Material.BUBBLE_COLUMN) {
            return Material.WATER;
        }
        if (block.getType() == Material.LAVA) {
            return Material.LAVA;
        }
        return null;
    }

    private static boolean isSameFluid(Block block, Material fluid) {
        if (block == null || fluid == null) {
            return false;
        }
        return fluid == Material.WATER
                ? block.getType() == Material.WATER || block.getType() == Material.BUBBLE_COLUMN
                : block.getType() == fluid;
    }

    private static boolean sameBlock(Block first, Block second) {
        return first != null && second != null
                && first.getWorld().getUID().equals(second.getWorld().getUID())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }

    private static long blockKey(Block block) {
        return ((long) (block.getX() & 0x3FFFFFF) << 38)
                | ((long) (block.getZ() & 0x3FFFFFF) << 12)
                | (long) (block.getY() & 0xFFF);
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

    static boolean assemblerEnabled(SfxElectricMachineState state) {
        return state.enabled();
    }

    static int assemblerOffsetTenths(SfxElectricMachineState state) {
        if (state.specialData() == 0) {
            return 30;
        }
        return Math.max(-100, Math.min(100, (state.specialData() & 0xFF) - 100));
    }

    static void assemblerEnabled(SfxElectricMachineState state, boolean enabled) {
        state.enabled(enabled);
    }

    static void assemblerOffsetTenths(SfxElectricMachineState state, int offsetTenths) {
        state.specialData(encodeAssemblerData(offsetTenths));
    }

    private static int encodeAssemblerData(int offsetTenths) {
        int clamped = Math.max(-100, Math.min(100, offsetTenths));
        return 0x200 | ((clamped + 100) & 0xFF);
    }

    private static AssemblerStart findAssemblerStart(SfxElectricMachineState state, Material headMaterial, int headAmount, Set<Material> bodyMaterials, int bodyAmount) {
        List<SfxElectricStack> reserved = new ArrayList<>();
        List<AssemblerConsume> consumed = new ArrayList<>();
        if (!collectMaterialForAssembler(state, reserved, consumed, new int[] {0, 1}, Set.of(headMaterial), headAmount)) {
            return AssemblerStart.status(SfxElectricMachineRenderStatus.NO_INPUT);
        }
        if (!collectMaterialForAssembler(state, reserved, consumed, new int[] {2, 3}, bodyMaterials, bodyAmount)) {
            return AssemblerStart.status(SfxElectricMachineRenderStatus.NO_INPUT);
        }
        return new AssemblerStart(SfxElectricMachineRenderStatus.WORKING, List.copyOf(reserved), consumed.isEmpty() ? -1 : consumed.getFirst().slot());
    }

    private static boolean collectMaterialForAssembler(SfxElectricMachineState state, List<SfxElectricStack> reserved, List<AssemblerConsume> consumed, int[] slots, Set<Material> materials, int amount) {
        int remaining = amount;
        for (int slot : slots) {
            SfxElectricStack stack = state.input(slot);
            if (stack == null || stack.isSfxItem() || !materials.contains(stack.material())) {
                continue;
            }
            int take = Math.min(remaining, stack.amount());
            if (take <= 0) {
                continue;
            }
            reserved.add(stack.copyWithAmount(take));
            consumed.add(new AssemblerConsume(slot, take));
            remaining -= take;
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static void consumeAssemblerStacks(SfxElectricMachineState state, List<SfxElectricStack> reservedInputs) {
        if (reservedInputs == null || reservedInputs.isEmpty()) {
            return;
        }
        for (SfxElectricStack reserved : reservedInputs) {
            int remaining = reserved.amount();
            for (int slot = 0; slot < state.inputCapacity() && remaining > 0; slot++) {
                SfxElectricStack stack = state.input(slot);
                if (stack == null || !stack.sameKind(reserved)) {
                    continue;
                }
                int take = Math.min(remaining, stack.amount());
                remaining -= take;
                int left = stack.amount() - take;
                state.input(slot, left <= 0 ? null : stack.copyWithAmount(left));
            }
        }
    }

    private static SfxElectricMachineTickResult completeAssembler(SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, EntityType spawnType) {
        if (location == null || location.getWorld() == null) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_TARGET, true);
        }
        Location spawn = location.clone().add(0.5D, assemblerOffsetTenths(state) / 10.0D, 0.5D);
        if (spawnType == EntityType.IRON_GOLEM) {
            IronGolem golem = (IronGolem) location.getWorld().spawnEntity(spawn, EntityType.IRON_GOLEM);
            golem.setPlayerCreated(true);
            location.getWorld().playSound(location, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0F, 1.0F);
        } else if (spawnType == EntityType.WITHER) {
            Wither wither = (Wither) location.getWorld().spawnEntity(spawn, EntityType.WITHER);
            wither.setInvulnerableTicks(220);
        } else {
            location.getWorld().spawnEntity(spawn, spawnType);
        }
        state.resetProgress();
        return SfxElectricMachineTickResult.changed(state.hasAnyInput() ? SfxElectricMachineRenderStatus.WORKING : SfxElectricMachineRenderStatus.IDLE, 0, state.hasAnyInput());
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

    private record ToolDamageResult(SfxElectricStack stack, boolean moveToOutput) {
    }

    private record FlushResult(boolean changed, int consumedEnergy) {
    }

    private record FluidPumpAction(
            SfxElectricMachineRenderStatus status,
            int inputSlot,
            SfxElectricStack output,
            Block source,
            boolean consumeSource
    ) {
        static FluidPumpAction status(SfxElectricMachineRenderStatus status) {
            return new FluidPumpAction(status, -1, null, null, false);
        }
    }

    private record FluidPoolCacheKey(UUID worldId, int x, int y, int z, Material fluid, int threshold) {
    }

    private record FluidPoolCacheEntry(long checkedTick, boolean largeEnough) {
    }

    private record FluidPumpSourceCacheKey(UUID worldId, int x, int y, int z, Material container) {
    }

    private record FluidPumpSourceCacheEntry(long checkedTick, boolean found, Material fluid, int x, int y, int z) {
    }

    private record AssemblerStart(
            SfxElectricMachineRenderStatus status,
            List<SfxElectricStack> reservedInputs,
            int primaryInputSlot
    ) {
        static AssemblerStart status(SfxElectricMachineRenderStatus status) {
            return new AssemblerStart(status, List.of(), -1);
        }
    }

    private record AssemblerConsume(int slot, int amount) {
    }

}
