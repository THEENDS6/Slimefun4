package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class SfxElectricRecipeProcessor {
    private final SfxItems items;

    SfxElectricRecipeProcessor(SfxItems items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    SfxElectricRecipe activeRecipe(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        String key = state.activeRecipeKey();
        if (key == null) {
            return null;
        }
        List<SfxElectricStack> activeOutputs = state.activeOutputs();
        if (state.activeBaseTicks() > 0) {
            if (!activeOutputs.isEmpty()) {
                return SfxElectricRecipe.fixedOutputs(key, List.of(SfxRecipeSlot.vanilla(org.bukkit.Material.STONE)), activeOutputs, state.activeBaseTicks());
            }
            if (definition.recipeProvider().hasSpecialTick() || definition.recipeProvider().hasWorldAction()) {
                return null;
            }
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
        SfxElectricRecipeMatch dynamic = definition.recipeProvider().findDynamicMatch(definition, state);
        if (dynamic != null) {
            return dynamic;
        }
        for (SfxElectricRecipe recipe : definition.recipeProvider().recipes()) {
            int[] slots = matchInputSlots(state, definition.inputSlots(), recipe.inputs());
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
        if (match != null && !canFitOutputForRecipe(definition, state, match.recipe())) {
            return SfxElectricMachineRenderStatus.OUTPUT_FULL;
        }
        return SfxElectricMachineRenderStatus.IDLE;
    }

    int requiredWork(SfxElectricRecipe recipe) {
        return Math.max(1, recipe.baseTicks() * 20);
    }

    SfxElectricRecipeStart tryStartNextRecipe(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        SfxElectricRecipeMatch match = findRecipeMatch(definition, state);
        if (match == null || !canFitOutputForRecipe(definition, state, match.recipe())) {
            return null;
        }
        List<SfxElectricStack> outputs = match.recipe().rollOutputs();
        if (findCompletionOutputSlots(definition, state, match.recipe(), outputs) == null) {
            return null;
        }
        List<SfxElectricStack> reservedInputs = consumeInputs(state, match.inputSlots(), match.recipe().inputs());
        if (reservedInputs == null) {
            return null;
        }
        state.activeRecipeKey(match.recipe().key());
        state.activeInputSlot(match.primaryInputSlot());
        state.progressWork(0);
        state.activeBaseTicks(match.recipe().baseTicks());
        state.activeOutputs(outputs);
        state.reservedInputs(reservedInputs);
        state.pendingOutput(null);
        return new SfxElectricRecipeStart(match.recipe(), match.inputSlots());
    }

    Integer findOutputSlot(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricStack recipeOutput) {
        return findMergeSlot(currentOutputs(definition, state), recipeOutput);
    }

    boolean canFitOutputForRecipe(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe) {
        return findOutputSlots(definition, state, recipe.outputs()) != null;
    }

    boolean canFitCompletionOutputForRecipe(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe) {
        List<SfxElectricStack> activeOutputs = state.activeOutputs();
        return findOutputSlots(definition, state, activeOutputs.isEmpty() ? recipe.outputs() : activeOutputs) != null;
    }

    int[] findCompletionOutputSlots(SfxElectricMachineDefinition definition, SfxElectricMachineState state, SfxElectricRecipe recipe, List<SfxElectricStack> rolledOutputs) {
        List<SfxElectricStack> outputs = rolledOutputs == null || rolledOutputs.isEmpty()
                ? recipe.outputs()
                : rolledOutputs;
        return findOutputSlots(definition, state, outputs);
    }

    int[] findOutputSlots(SfxElectricMachineDefinition definition, SfxElectricMachineState state, List<SfxElectricStack> outputs) {
        int outputCapacity = definition.outputSlots().length;
        if (outputs == null || outputs.isEmpty() || outputs.size() > outputCapacity || outputCapacity > SfxElectricMachineState.MAX_OUTPUTS) {
            return null;
        }
        SfxElectricStack[] simulated = currentOutputs(definition, state);
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

    private SfxElectricStack[] currentOutputs(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        SfxElectricStack[] result = new SfxElectricStack[definition.outputSlots().length];
        for (int slot = 0; slot < result.length; slot++) {
            result[slot] = state.output(slot);
        }
        return result;
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

    private int[] matchInputSlots(SfxElectricMachineState state, int[] availableSlots, List<SfxRecipeSlot> requiredInputs) {
        if (requiredInputs.isEmpty() || requiredInputs.size() > availableSlots.length) {
            return null;
        }
        boolean[] used = new boolean[availableSlots.length];
        int[] matched = new int[requiredInputs.size()];
        return matchInputSlotsRecursive(state, availableSlots, requiredInputs, used, matched, 0) ? matched : null;
    }

    private boolean matchInputSlotsRecursive(SfxElectricMachineState state, int[] availableSlots, List<SfxRecipeSlot> requiredInputs, boolean[] used, int[] matched, int depth) {
        if (depth >= requiredInputs.size()) {
            return true;
        }
        SfxRecipeSlot required = requiredInputs.get(depth);
        for (int index = 0; index < availableSlots.length; index++) {
            if (used[index]) {
                continue;
            }
            SfxElectricStack input = state.input(index);
            if (input != null && input.matches(required)) {
                used[index] = true;
                matched[depth] = index;
                if (matchInputSlotsRecursive(state, availableSlots, requiredInputs, used, matched, depth + 1)) {
                    return true;
                }
                used[index] = false;
            }
        }
        return false;
    }

    private record ConsumedInput(int slot, SfxElectricStack previous) {
    }
}
