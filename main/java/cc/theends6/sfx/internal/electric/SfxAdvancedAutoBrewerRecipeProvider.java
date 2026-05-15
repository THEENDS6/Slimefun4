package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxAdvancedAutoBrewerRecipeProvider implements SfxElectricRecipeProvider {
    static final int BLAZE_FUEL_TICKS = 600;
    static final int MAX_BLAZE_FUEL_TICKS = 3000;
    static final int AUTO_REFILL_THRESHOLD_TICKS = 2400;
    private static final String ACTIVE_KEY = "sfx:auto_brewer";
    private static final int BLAZE_INPUT = 0;
    private static final int INGREDIENT_INPUT = 1;
    private static final int POTION_INPUT_BASE = 2;
    private static final int SNAPSHOT_INGREDIENT = 0;
    private static final int SNAPSHOT_POTION_BASE = 1;
    private static final int RESULT_BASE = 0;

    private final SfxPotionBrewEngine engine;

    SfxAdvancedAutoBrewerRecipeProvider(JavaPlugin plugin) {
        this.engine = new SfxPotionBrewEngine(plugin);
    }

    @Override
    public List<SfxElectricRecipe> recipes() {
        return List.of();
    }

    @Override
    public boolean hasSpecialTick() {
        return true;
    }

    @Override
    public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        if (state.hasProgress()) {
            int remainingWork = Math.max(0, state.activeBaseTicks() - state.progressWork());
            int requiredFuel = Math.min(Math.max(1, definition.speed()), Math.max(1, remainingWork));
            return state.specialData() >= requiredFuel ? definition.energyConsumptionPerTick() : 0;
        }
        BrewPlan plan = createPlan(items, state);
        return plan != null && hasAvailableFuel(state) ? definition.energyConsumptionPerTick() : 0;
    }

    @Override
    public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        if (state.hasProgress()) {
            return advance(items, definition, state);
        }
        boolean refilled = autoRefillFuel(state);
        BrewPlan plan = createPlan(items, state);
        if (plan == null) {
            SfxElectricMachineTickResult idle = idleStatus(items, state);
            return refilled ? new SfxElectricMachineTickResult(idle.status(), idle.consumedEnergy(), true, idle.keepActive()) : idle;
        }
        if (!hasAvailableFuel(state)) {
            return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.IDLE, 0, refilled, true);
        }
        state.activeRecipeKey(ACTIVE_KEY);
        state.activeBaseTicks(plan.workTicks());
        state.progressWork(0);
        state.specialData2(0);
        state.specialInput(SNAPSHOT_INGREDIENT, copy(state.input(INGREDIENT_INPUT)));
        for (int index = 0; index < 4; index++) {
            state.specialInput(SNAPSHOT_POTION_BASE + index, copy(state.input(POTION_INPUT_BASE + index)));
            state.specialOutput(RESULT_BASE + index, plan.results()[index]);
        }
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, 0, true);
    }

    @Override
    public List<SfxElectricStack> dropsOnDestroy(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        int fuel = Math.max(0, state.specialData()) + Math.max(0, state.specialData2());
        int powders = fuel / BLAZE_FUEL_TICKS;
        if (powders <= 0) {
            return List.of();
        }
        state.specialData(fuel % BLAZE_FUEL_TICKS);
        state.specialData2(0);
        return List.of(SfxElectricStack.vanilla(Material.BLAZE_POWDER, powders));
    }

    private SfxElectricMachineTickResult advance(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        if (!snapshotsMatch(items, state)) {
            interrupt(state, true);
            return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.IDLE, 0, true);
        }
        boolean refilled = autoRefillFuel(state);
        int totalWork = Math.max(1, state.activeBaseTicks());
        if (state.progressWork() >= totalWork) {
            return complete(items, state);
        }
        int remainingWork = Math.max(1, totalWork - state.progressWork());
        int delta = Math.min(Math.max(1, definition.speed()), remainingWork);
        if (state.specialData() < delta) {
            return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_BLAZE_FUEL, 0, refilled, true);
        }
        if (definition.energyConsumptionPerTick() > 0 && state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_POWER, 0, refilled, true);
        }
        if (definition.energyConsumptionPerTick() > 0) {
            state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
        }
        state.specialData(state.specialData() - delta);
        state.specialData2(Math.max(0, state.specialData2()) + delta);
        state.progressWork(state.progressWork() + delta);
        if (state.progressWork() >= totalWork) {
            SfxElectricMachineTickResult completed = complete(items, state);
            return new SfxElectricMachineTickResult(completed.status(), definition.energyConsumptionPerTick(), 0, true, completed.keepActive());
        }
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true);
    }

    private SfxElectricMachineTickResult complete(SfxItems items, SfxElectricMachineState state) {
        if (!snapshotsMatch(items, state)) {
            interrupt(state, true);
            return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.IDLE, 0, true);
        }
        SfxElectricStack ingredient = state.input(INGREDIENT_INPUT);
        if (ingredient == null || ingredient.amount() <= 0) {
            interrupt(state, true);
            return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.IDLE, 0, true);
        }
        state.input(INGREDIENT_INPUT, ingredient.amount() <= 1 ? null : ingredient.copyWithAmount(ingredient.amount() - 1));
        for (int index = 0; index < 4; index++) {
            SfxElectricStack result = state.specialOutput(RESULT_BASE + index);
            if (result != null) {
                state.input(POTION_INPUT_BASE + index, result);
            }
        }
        state.progressWork(0);
        state.activeRecipeKey(null);
        state.activeBaseTicks(0);
        state.specialData2(0);
        state.clearSpecialWorkData();
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.IDLE, 0, true);
    }

    private SfxElectricMachineTickResult idleStatus(SfxItems items, SfxElectricMachineState state) {
        boolean hasIngredient = state.input(INGREDIENT_INPUT) != null;
        boolean hasPotion = hasAnyPotion(state);
        if (hasIngredient && hasPotion) {
            return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_RECIPE, 0, false, true);
        }
        return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.IDLE, 0, false, state.hasAnyInput() || state.specialData() > 0);
    }

    private BrewPlan createPlan(SfxItems items, SfxElectricMachineState state) {
        SfxElectricStack ingredient = state.input(INGREDIENT_INPUT);
        if (ingredient == null || !engine.isBrewingIngredient(ingredient)) {
            return null;
        }
        SfxElectricStack[] results = new SfxElectricStack[4];
        int workTicks = 0;
        int matches = 0;
        for (int index = 0; index < 4; index++) {
            SfxElectricStack potionStack = state.input(POTION_INPUT_BASE + index);
            if (!isPotionStack(potionStack)) {
                continue;
            }
            ItemStack potion = potionStack.toItemStack(items);
            if (!engine.isValidPotionItem(items, potion)) {
                continue;
            }
            SfxPotionBrewEngine.BrewResult result = engine.brew(items, potion, ingredient);
            if (result == null || result.result() == null || result.result().getType().isAir()) {
                continue;
            }
            ItemStack item = result.result();
            item.setAmount(potionStack.amount());
            results[index] = SfxElectricStack.snapshot(item);
            workTicks = Math.max(workTicks, result.workTicks());
            matches++;
        }
        if (matches <= 0 || workTicks <= 0) {
            return null;
        }
        return new BrewPlan(results, workTicks);
    }


    private boolean hasAvailableFuel(SfxElectricMachineState state) {
        return Math.max(0, state.specialData()) > 0 || isBlazePowder(state.input(BLAZE_INPUT));
    }

    private boolean autoRefillFuel(SfxElectricMachineState state) {
        int storedFuel = Math.max(0, Math.min(MAX_BLAZE_FUEL_TICKS, state.specialData()));
        boolean changed = storedFuel != state.specialData();
        SfxElectricStack blaze = state.input(BLAZE_INPUT);
        if (storedFuel <= AUTO_REFILL_THRESHOLD_TICKS && isBlazePowder(blaze)) {
            storedFuel = Math.min(MAX_BLAZE_FUEL_TICKS, storedFuel + BLAZE_FUEL_TICKS);
            int remaining = blaze.amount() - 1;
            state.input(BLAZE_INPUT, remaining <= 0 ? null : SfxElectricStack.vanilla(Material.BLAZE_POWDER, remaining));
            changed = true;
        }
        state.specialData(storedFuel);
        return changed;
    }

    private void interrupt(SfxElectricMachineState state, boolean refundConsumedFuel) {
        if (refundConsumedFuel && state.specialData2() > 0) {
            state.specialData(Math.min(MAX_BLAZE_FUEL_TICKS, Math.max(0, state.specialData()) + state.specialData2()));
        }
        state.progressWork(0);
        state.activeRecipeKey(null);
        state.activeBaseTicks(0);
        state.specialData2(0);
        state.clearSpecialWorkData();
    }

    private boolean snapshotsMatch(SfxItems items, SfxElectricMachineState state) {
        if (!sameStack(items, state.input(INGREDIENT_INPUT), state.specialInput(SNAPSHOT_INGREDIENT))) {
            return false;
        }
        for (int index = 0; index < 4; index++) {
            if (!sameStack(items, state.input(POTION_INPUT_BASE + index), state.specialInput(SNAPSHOT_POTION_BASE + index))) {
                return false;
            }
        }
        return true;
    }

    private boolean sameStack(SfxItems items, SfxElectricStack current, SfxElectricStack snapshot) {
        if (current == null || snapshot == null) {
            return current == null && snapshot == null;
        }
        return current.amount() == snapshot.amount() && current.sameKind(snapshot);
    }

    private boolean isPotionStack(SfxElectricStack stack) {
        if (stack == null || stack.isSfxItem() || stack.amount() <= 0) {
            return false;
        }
        Material material = stack.material();
        return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION;
    }

    private boolean isBlazePowder(SfxElectricStack stack) {
        return stack != null && !stack.isSfxItem() && !stack.hasSnapshot() && stack.material() == Material.BLAZE_POWDER && stack.amount() > 0;
    }

    private boolean hasAnyPotion(SfxElectricMachineState state) {
        for (int index = 0; index < 4; index++) {
            if (isPotionStack(state.input(POTION_INPUT_BASE + index))) {
                return true;
            }
        }
        return false;
    }

    private SfxElectricStack copy(SfxElectricStack stack) {
        return stack == null ? null : stack.copyWithAmount(stack.amount());
    }

    private record BrewPlan(SfxElectricStack[] results, int workTicks) {
    }
}
