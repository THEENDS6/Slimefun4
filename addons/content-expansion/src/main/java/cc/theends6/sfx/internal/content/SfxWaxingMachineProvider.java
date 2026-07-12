package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.electric.SfxElectricMachineDefinition;
import cc.theends6.sfx.internal.electric.SfxElectricMachineRenderStatus;
import cc.theends6.sfx.internal.electric.SfxElectricMachineState;
import cc.theends6.sfx.internal.electric.SfxElectricMachineTickResult;
import cc.theends6.sfx.internal.electric.SfxElectricRecipe;
import cc.theends6.sfx.internal.electric.SfxElectricRecipeProvider;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxWaxingMachineProvider implements SfxElectricRecipeProvider {
    private static final int TARGET_INPUT = 0;
    private static final int WAX_INPUT = 1;
    private static final int RESULT_OUTPUT = 0;
    private static final int BOTTLE_OUTPUT = 1;
    private static final int WORK_TICKS = 80;
    private static final int WAX_MAX = 10;

    @Override
    public List<SfxElectricRecipe> recipes() {
        return List.of();
    }

    @Override
    public boolean hasSpecialTick() {
        return true;
    }

    @Override
    public boolean locksInputsDuringProgress() {
        return true;
    }

    @Override
    public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        return plan(items, definition, state) == null ? 0 : definition.energyConsumptionPerTick();
    }

    @Override
    public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, SfxMachineTickContext context) {
        boolean changed = refillWax(definition, state);
        Plan plan = plan(items, definition, state);
        if (plan == null) {
            state.resetProgress();
            return new SfxElectricMachineTickResult(state.hasAnyInput() ? SfxElectricMachineRenderStatus.NO_RECIPE : SfxElectricMachineRenderStatus.IDLE, 0, changed, state.hasAnyInput() || state.specialData() > 0);
        }
        if (state.specialData() <= 0) {
            return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_RECIPE, 0, changed, true);
        }
        if (definition.energyConsumptionPerTick() > 0 && state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.NO_POWER, 0, changed, true);
        }
        if (!state.hasProgress()) {
            state.activeRecipeKey("sfx:waxing");
            state.activeBaseTicks(WORK_TICKS);
            state.progressWork(0);
            changed = true;
        }
        if (definition.energyConsumptionPerTick() > 0) {
            state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
        }
        state.progressWork(state.progressWork() + Math.max(1, definition.speed()));
        if (state.progressWork() < WORK_TICKS) {
            return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true);
        }
        consumeOne(state, TARGET_INPUT);
        state.specialData(Math.max(0, state.specialData() - 1));
        push(state, RESULT_OUTPUT, plan.output());
        state.resetProgress();
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.IDLE, definition.energyConsumptionPerTick(), true);
    }

    private boolean refillWax(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        SfxElectricStack wax = state.input(WAX_INPUT);
        if (wax == null || wax.isSfxItem() || wax.hasSnapshot() || state.specialData() >= WAX_MAX) {
            return false;
        }
        int units = wax.material() == Material.HONEYCOMB ? 3 : wax.material() == Material.HONEY_BOTTLE ? 1 : 0;
        if (units <= 0 || state.specialData() + units > WAX_MAX) {
            return false;
        }
        if (wax.material() == Material.HONEY_BOTTLE && !canPush(state, BOTTLE_OUTPUT, SfxElectricStack.vanilla(Material.GLASS_BOTTLE, 1))) {
            return false;
        }
        state.specialData(state.specialData() + units);
        consumeOne(state, WAX_INPUT);
        if (wax.material() == Material.HONEY_BOTTLE) {
            push(state, BOTTLE_OUTPUT, SfxElectricStack.vanilla(Material.GLASS_BOTTLE, 1));
        }
        return true;
    }

    private Plan plan(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        SfxElectricStack target = state.input(TARGET_INPUT);
        SfxElectricStack output = SfxCopperVariants.waxed(items, target);
        if (output == null || !canPush(state, RESULT_OUTPUT, output)) {
            return null;
        }
        return new Plan(output);
    }

    private void consumeOne(SfxElectricMachineState state, int slot) {
        SfxElectricStack input = state.input(slot);
        if (input == null) return;
        state.input(slot, input.amount() <= 1 ? null : input.copyWithAmount(input.amount() - 1));
    }

    private boolean canPush(SfxElectricMachineState state, int slot, SfxElectricStack stack) {
        SfxElectricStack current = state.output(slot);
        return current == null || stack.canMerge(current, null);
    }

    private void push(SfxElectricMachineState state, int slot, SfxElectricStack stack) {
        SfxElectricStack current = state.output(slot);
        state.output(slot, current == null ? stack : current.copyWithAmount(current.amount() + stack.amount()));
    }

    private record Plan(SfxElectricStack output) {
    }
}
