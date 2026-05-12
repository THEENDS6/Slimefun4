package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import java.util.Objects;

final class SfxElectricRecipeProcessor {
    private static final int[] INPUT_SLOTS = {19, 20};
    private static final int[] OUTPUT_SLOTS = {24, 25};

    private final SfxItems items;

    SfxElectricRecipeProcessor(SfxItems items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    SfxElectricRecipe activeRecipe(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        String key = state.activeRecipeKey();
        if (key == null) {
            return null;
        }
        for (SfxElectricRecipe recipe : definition.recipeProvider().recipes()) {
            if (recipe.key().equals(key)) {
                return recipe;
            }
        }
        state.resetProgress();
        return null;
    }

    SfxElectricRecipeMatch findRecipeMatch(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        for (SfxElectricRecipe recipe : definition.recipeProvider().recipes()) {
            for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
                SfxElectricStack input = state.input(slot);
                if (input != null && input.matches(recipe.input())) {
                    return new SfxElectricRecipeMatch(slot, recipe);
                }
            }
        }
        return null;
    }

    SfxElectricMachineRenderStatus deriveStatus(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        if (state.hasPendingOutput()) {
            return SfxElectricMachineRenderStatus.BLOCKED_OUTPUT;
        }
        SfxElectricRecipe recipe = activeRecipe(definition, state);
        if (recipe != null) {
            if (definition.energyConsumptionPerTick() > 0 && state.storedEnergy() < definition.energyConsumptionPerTick()) {
                return SfxElectricMachineRenderStatus.NO_POWER;
            }
            return SfxElectricMachineRenderStatus.WORKING;
        }
        SfxElectricRecipeMatch match = findRecipeMatch(definition, state);
        if (match == null && state.hasAnyInput()) {
            return SfxElectricMachineRenderStatus.NO_RECIPE;
        }
        if (match != null && findOutputSlot(state, match.recipe().output()) == null) {
            return SfxElectricMachineRenderStatus.OUTPUT_FULL;
        }
        return SfxElectricMachineRenderStatus.IDLE;
    }

    int requiredWork(SfxElectricRecipe recipe) {
        return Math.max(1, recipe.baseTicks() * 20);
    }

    SfxElectricRecipeStart tryStartNextRecipe(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        SfxElectricRecipeMatch match = findRecipeMatch(definition, state);
        if (match == null || findOutputSlot(state, match.recipe().output()) == null) {
            return null;
        }
        SfxElectricStack reservedInput = consumeInput(state, match.inputSlot(), match.recipe().input().amount());
        if (reservedInput == null) {
            return null;
        }
        state.activeRecipeKey(match.recipe().key());
        state.activeInputSlot(match.inputSlot());
        state.progressWork(0);
        state.reservedInput(reservedInput);
        state.pendingOutput(null);
        return new SfxElectricRecipeStart(match.recipe(), match.inputSlot());
    }

    Integer findOutputSlot(SfxElectricMachineState state, SfxElectricStack recipeOutput) {
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            SfxElectricStack current = state.output(slot);
            if (current != null && recipeOutput.canMerge(current, items)) {
                return slot;
            }
        }
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            if (state.output(slot) == null) {
                return slot;
            }
        }
        return null;
    }

    SfxElectricStack consumeInput(SfxElectricMachineState state, int slot, int amount) {
        SfxElectricStack input = state.input(slot);
        if (input == null) {
            return null;
        }
        SfxElectricStack reserved = input.copyWithAmount(amount);
        int remaining = input.amount() - amount;
        state.input(slot, remaining <= 0 ? null : input.copyWithAmount(remaining));
        return reserved;
    }

    void pushOutput(SfxElectricMachineState state, int slot, SfxElectricStack recipeOutput) {
        SfxElectricStack current = state.output(slot);
        if (current == null) {
            state.output(slot, recipeOutput);
            return;
        }
        state.output(slot, current.copyWithAmount(current.amount() + recipeOutput.amount()));
    }
}
