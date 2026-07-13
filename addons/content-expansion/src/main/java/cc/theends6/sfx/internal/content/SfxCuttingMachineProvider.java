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
    public int requestedEnergyConsumption(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location) {
        Plan plan = plan(items, state);
        return (state.hasProgress() || (plan != null && canPush(items, definition, state, plan.output()))) ? definition.energyConsumptionPerTick() : 0;
    }

    @Override
    public SfxElectricMachineTickResult tickSpecial(JavaPlugin plugin, SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, Location location, SfxMachineTickContext context) {
        if (state.hasProgress()) {
            return advance(items, definition, state);
        }
        Plan plan = plan(items, state);
        if (plan == null) {
            return new SfxElectricMachineTickResult(state.hasAnyInput() ? SfxElectricMachineRenderStatus.NO_RECIPE : SfxElectricMachineRenderStatus.IDLE, 0, false, state.hasAnyInput());
        }
        if (!canPush(items, definition, state, plan.output())) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.OUTPUT_FULL, true);
        }
        state.activeRecipeKey("sfx:cutting");
        state.activeBaseTicks(plan.ticks());
        state.progressWork(0);
        SfxElectricStack reserved = state.input(INPUT).copyWithAmount(1);
        consumeOne(state);
        state.reservedInput(reserved);
        state.specialOutput(SNAPSHOT_OUTPUT, plan.output());
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.WORKING, 0, true);
    }

    private SfxElectricMachineTickResult advance(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        if (!ensureReservedInput(state)) {
            interrupt(state);
            return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.IDLE, 0, true);
        }
        SfxElectricStack output = state.specialOutput(SNAPSHOT_OUTPUT);
        if (output == null || !canPush(items, definition, state, output)) {
            return SfxElectricMachineTickResult.status(SfxElectricMachineRenderStatus.BLOCKED_OUTPUT, true);
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
        push(items, definition, state, output);
        interrupt(state);
        return SfxElectricMachineTickResult.changed(SfxElectricMachineRenderStatus.IDLE, definition.energyConsumptionPerTick(), true);
    }

    private Plan plan(SfxItems items, SfxElectricMachineState state) {
        SfxElectricStack input = state.input(INPUT);
        if (input == null || input.amount() <= 0) {
            return null;
        }
        SfxElectricStack scrape = SfxCopperVariants.cutOrScrape(items, input);
        if (scrape != null) {
            int ticks = (int) Math.ceil(Math.max(1.0D, SfxCopperVariants.copperValue(input.copyWithAmount(1))) * 60.0D);
            SfxElectricStack output = scrape.copyWithAmount(1);
            return new Plan(output, ticks);
        }
        return null;
    }

    private void interrupt(SfxElectricMachineState state) {
        state.resetProgress();
        state.clearSpecialWorkData();
    }

    private boolean ensureReservedInput(SfxElectricMachineState state) {
        if (state.reservedInput() != null) {
            return true;
        }
        SfxElectricStack legacySnapshot = state.specialInput(SNAPSHOT_INPUT);
        SfxElectricStack current = state.input(INPUT);
        if (legacySnapshot == null || current == null || current.amount() <= 0 || !current.sameKind(legacySnapshot)) {
            return false;
        }
        state.reservedInput(current.copyWithAmount(1));
        consumeOne(state);
        return true;
    }

    private void consumeOne(SfxElectricMachineState state) {
        SfxElectricStack input = state.input(INPUT);
        if (input == null) return;
        state.input(INPUT, input.amount() <= 1 ? null : input.copyWithAmount(input.amount() - 1));
    }

    private boolean canPush(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricStack stack) {
        return outputSlot(items, definition, state, stack) >= 0;
    }

    private void push(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricStack stack) {
        int slot = outputSlot(items, definition, state, stack);
        if (slot < 0) return;
        SfxElectricStack current = state.output(slot);
        state.output(slot, current == null ? stack : current.copyWithAmount(current.amount() + stack.amount()));
    }

    private int outputSlot(SfxItems items, SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricStack stack) {
        int count = Math.min(definition.outputSlots().length, state.outputCapacity());
        for (int slot = 0; slot < count; slot++) {
            SfxElectricStack current = state.output(slot);
            if (current != null && stack.canMerge(current, items)) return slot;
        }
        for (int slot = 0; slot < count; slot++) {
            if (state.output(slot) == null) return slot;
        }
        return -1;
    }

    private record Plan(SfxElectricStack output, int ticks) {
    }
}
