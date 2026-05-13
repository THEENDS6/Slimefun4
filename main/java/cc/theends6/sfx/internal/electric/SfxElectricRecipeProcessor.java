package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.ArrayList;
import java.util.List;
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
            int[] slots = matchInputSlots(state, recipe.inputs());
            if (slots != null) {
                return new SfxElectricRecipeMatch(slots, recipe);
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
        if (match != null && !canFitOutputForRecipe(state, match.recipe())) {
            return SfxElectricMachineRenderStatus.OUTPUT_FULL;
        }
        return SfxElectricMachineRenderStatus.IDLE;
    }

    int requiredWork(SfxElectricRecipe recipe) {
        return Math.max(1, recipe.baseTicks() * 20);
    }

    SfxElectricRecipeStart tryStartNextRecipe(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        SfxElectricRecipeMatch match = findRecipeMatch(definition, state);
        if (match == null || !canFitOutputForRecipe(state, match.recipe())) {
            return null;
        }
        List<SfxElectricStack> reservedInputs = consumeInputs(state, match.inputSlots(), match.recipe().inputs());
        if (reservedInputs == null) {
            return null;
        }
        state.activeRecipeKey(match.recipe().key());
        state.activeInputSlot(match.primaryInputSlot());
        state.progressWork(0);
        state.reservedInputs(reservedInputs);
        state.pendingOutput(null);
        return new SfxElectricRecipeStart(match.recipe(), match.inputSlots());
    }

    Integer findOutputSlotForRecipe(SfxElectricMachineState state, SfxElectricRecipe recipe) {
        int[] slots = findOutputSlotsForRecipe(state, recipe);
        return slots == null || slots.length == 0 ? null : slots[0];
    }

    boolean canFitOutputForRecipe(SfxElectricMachineState state, SfxElectricRecipe recipe) {
        return findOutputSlotsForRecipe(state, recipe) != null;
    }

    boolean canFitCompletionOutputForRecipe(SfxElectricMachineState state, SfxElectricRecipe recipe) {
        return findOutputSlots(state, recipe.outputs()) != null;
    }

    int[] findOutputSlotsForRecipe(SfxElectricMachineState state, SfxElectricRecipe recipe) {
        if (recipe.hasRandomOutput()) {
            return findEmptyOutputSlot(state) == null ? null : new int[]{findEmptyOutputSlot(state)};
        }
        return findOutputSlots(state, recipe.outputs());
    }

    int[] findCompletionOutputSlots(SfxElectricMachineState state, SfxElectricRecipe recipe, List<SfxElectricStack> rolledOutputs) {
        List<SfxElectricStack> outputs = recipe.hasRandomOutput()
                ? rolledOutputs
                : recipe.outputs();
        return findOutputSlots(state, outputs);
    }

    int[] findOutputSlots(SfxElectricMachineState state, List<SfxElectricStack> outputs) {
        SfxElectricStack[] simulated = {state.output(0), state.output(1)};
        int[] slots = new int[outputs.size()];
        for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
            SfxElectricStack output = outputs.get(outputIndex);
            Integer slot = findMergeSlot(simulated, output);
            if (slot == null) {
                return null;
            }
            slots[outputIndex] = slot;
            simulated[slot] = simulated[slot] == null
                    ? output
                    : simulated[slot].copyWithAmount(simulated[slot].amount() + output.amount());
        }
        return slots;
    }

    Integer findOutputSlot(SfxElectricMachineState state, SfxElectricStack recipeOutput) {
        return findMergeSlot(new SfxElectricStack[]{state.output(0), state.output(1)}, recipeOutput);
    }

    List<SfxElectricStack> rollOutputs(SfxElectricRecipe recipe) {
        return recipe.rollOutputs();
    }

    private Integer findEmptyOutputSlot(SfxElectricMachineState state) {
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            if (state.output(slot) == null) {
                return slot;
            }
        }
        return null;
    }

    private Integer findMergeSlot(SfxElectricStack[] simulatedOutputs, SfxElectricStack recipeOutput) {
        for (int slot = 0; slot < simulatedOutputs.length; slot++) {
            SfxElectricStack current = simulatedOutputs[slot];
            if (current != null && recipeOutput.canMerge(current, items)) {
                return slot;
            }
        }
        for (int slot = 0; slot < simulatedOutputs.length; slot++) {
            if (simulatedOutputs[slot] == null) {
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

    private List<SfxElectricStack> consumeInputs(SfxElectricMachineState state, int[] slots, List<SfxRecipeSlot> requirements) {
        if (slots.length != requirements.size()) {
            return null;
        }
        List<SfxElectricStack> reservedInputs = new ArrayList<>(slots.length);
        List<ConsumedInput> consumed = new ArrayList<>(slots.length);
        for (int index = 0; index < slots.length; index++) {
            int slot = slots[index];
            SfxElectricStack before = state.input(slot);
            SfxElectricStack reserved = consumeInput(state, slot, requirements.get(index).amount());
            if (reserved == null) {
                rollbackConsumedInputs(state, consumed);
                return null;
            }
            consumed.add(new ConsumedInput(slot, before));
            reservedInputs.add(reserved);
        }
        return List.copyOf(reservedInputs);
    }

    private void rollbackConsumedInputs(SfxElectricMachineState state, List<ConsumedInput> consumed) {
        for (ConsumedInput input : consumed) {
            state.input(input.slot(), input.previous());
        }
    }

    void pushOutput(SfxElectricMachineState state, int slot, SfxElectricStack recipeOutput) {
        SfxElectricStack current = state.output(slot);
        if (current == null) {
            state.output(slot, recipeOutput);
            return;
        }
        state.output(slot, current.copyWithAmount(current.amount() + recipeOutput.amount()));
    }

    void pushOutputs(SfxElectricMachineState state, int[] slots, List<SfxElectricStack> outputs) {
        if (outputs.isEmpty()) {
            return;
        }
        if (slots == null || slots.length != outputs.size()) {
            throw new IllegalStateException("Output slot count does not match output count.");
        }
        for (int index = 0; index < outputs.size(); index++) {
            pushOutput(state, slots[index], outputs.get(index));
        }
    }

    private int[] matchInputSlots(SfxElectricMachineState state, List<SfxRecipeSlot> requiredInputs) {
        if (requiredInputs.size() == 1) {
            SfxRecipeSlot required = requiredInputs.getFirst();
            for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
                SfxElectricStack input = state.input(slot);
                if (input != null && input.matches(required)) {
                    return new int[]{slot};
                }
            }
            return null;
        }
        if (requiredInputs.size() == 2) {
            for (int firstSlot = 0; firstSlot < INPUT_SLOTS.length; firstSlot++) {
                SfxElectricStack firstInput = state.input(firstSlot);
                if (firstInput == null || !firstInput.matches(requiredInputs.get(0))) {
                    continue;
                }
                for (int secondSlot = 0; secondSlot < INPUT_SLOTS.length; secondSlot++) {
                    if (secondSlot == firstSlot) {
                        continue;
                    }
                    SfxElectricStack secondInput = state.input(secondSlot);
                    if (secondInput != null && secondInput.matches(requiredInputs.get(1))) {
                        return new int[]{firstSlot, secondSlot};
                    }
                }
            }
        }
        return null;
    }

    private record ConsumedInput(int slot, SfxElectricStack previous) {
    }
}
