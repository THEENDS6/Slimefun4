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
import org.bukkit.plugin.java.JavaPlugin;

final class SfxCuttingMachineProvider implements SfxElectricRecipeProvider {
    private static final int INPUT = 0;
    private static final int OUTPUT = 0;
    private static final int SNAPSHOT_INPUT = 0;
    private static final int SNAPSHOT_OUTPUT = 0;

    SfxCuttingMachineProvider(JavaPlugin plugin) {
    }

    @Override
    public List<SfxElectricRecipe> recipes() {
        return SfxCopperVariants.cuttingRecipes();
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
        return (state.hasProgress() || plan(items, definition, state) != null) ? definition.energyConsumptionPerTick() : 0;
    }

    @Override
    public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, SfxMachineTickContext context) {
        if (state.hasProgress()) {
            return advance(items, definition, state);
        }
        Plan plan = plan(items, definition, state);
        if (plan == null) {
            return new SfxElectricMachineTickResult(state.hasAnyInput() ? SfxElectricMachineRenderStatus.NO_RECIPE : SfxElectricMachineRenderStatus.IDLE, 0, false, state.hasAnyInput());
        }
        state.activeRecipeKey("sfx:cutting");
        state.activeBaseTicks(plan.ticks());
        state.progressWork(0);
        state.specialInput(SNAPSHOT_INPUT, copy(state.input(INPUT)));
        state.specialOutput(SNAPSHOT_OUTPUT, plan.output());
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, 0, true);
    }

    private SfxElectricMachineTickResult advance(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        if (!sameKindWithAvailableItem(state.input(INPUT), state.specialInput(SNAPSHOT_INPUT))) {
            interrupt(state);
            return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.IDLE, 0, true);
        }
        SfxElectricStack output = state.specialOutput(SNAPSHOT_OUTPUT);
        if (output == null || !canPush(items, state, output)) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OUTPUT_FULL, true);
        }
        if (definition.energyConsumptionPerTick() > 0 && state.storedEnergy() < definition.energyConsumptionPerTick()) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.NO_POWER, true);
        }
        if (definition.energyConsumptionPerTick() > 0) {
            state.storedEnergy(state.storedEnergy() - definition.energyConsumptionPerTick());
        }
        state.progressWork(state.progressWork() + Math.max(1, definition.speed()));
        if (state.progressWork() < Math.max(1, state.activeBaseTicks())) {
            return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, definition.energyConsumptionPerTick(), true);
        }
        consumeOne(state);
        push(state, output);
        interrupt(state);
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.IDLE, definition.energyConsumptionPerTick(), true);
    }

    private Plan plan(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        SfxElectricStack input = state.input(INPUT);
        if (input == null || input.amount() <= 0) {
            return null;
        }
        SfxElectricStack scrape = SfxCopperVariants.cutOrScrape(items, input);
        if (scrape != null) {
            int ticks = (int) Math.ceil(Math.max(1.0D, SfxCopperVariants.copperValue(input.copyWithAmount(1))) * 120.0D);
            SfxElectricStack output = scrape.copyWithAmount(1);
            return canPush(items, state, output) ? new Plan(output, ticks) : null;
        }
        return null;
    }

    private void interrupt(SfxElectricMachineState state) {
        state.progressWork(0);
        state.activeRecipeKey(null);
        state.activeBaseTicks(0);
        state.clearSpecialWorkData();
    }

    private boolean sameKindWithAvailableItem(SfxElectricStack current, SfxElectricStack snapshot) {
        return current != null && current.amount() > 0 && snapshot != null && current.sameKind(snapshot);
    }

    private SfxElectricStack copy(SfxElectricStack stack) {
        return stack == null ? null : stack.copyWithAmount(stack.amount());
    }

    private void consumeOne(SfxElectricMachineState state) {
        SfxElectricStack input = state.input(INPUT);
        if (input == null) return;
        state.input(INPUT, input.amount() <= 1 ? null : input.copyWithAmount(input.amount() - 1));
    }

    private boolean canPush(SfxItems items, SfxElectricMachineState state, SfxElectricStack stack) {
        SfxElectricStack current = state.output(OUTPUT);
        return current == null || stack.canMerge(current, items);
    }

    private void push(SfxElectricMachineState state, SfxElectricStack stack) {
        SfxElectricStack current = state.output(OUTPUT);
        state.output(OUTPUT, current == null ? stack : current.copyWithAmount(current.amount() + stack.amount()));
    }

    private record Plan(SfxElectricStack output, int ticks) {
    }
}
