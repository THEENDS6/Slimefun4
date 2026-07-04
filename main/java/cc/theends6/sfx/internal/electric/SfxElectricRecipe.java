package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class SfxElectricRecipe {
    private final String key;
    private final String recipeType;
    private final Set<String> recipeTags;
    private final List<SfxRecipeSlot> inputs;
    private final List<List<SfxElectricStack>> outputGroups;
    private final int baseTicks;

    public SfxElectricRecipe(String key, SfxRecipeSlot input, SfxElectricStack output, int baseTicks) {
        this(key, null, Set.of(), List.of(Objects.requireNonNull(input, "input")), List.of(List.of(Objects.requireNonNull(output, "output"))), baseTicks);
    }

    public SfxElectricRecipe(String key, List<SfxRecipeSlot> inputs, SfxElectricStack output, int baseTicks) {
        this(key, null, Set.of(), inputs, List.of(List.of(Objects.requireNonNull(output, "output"))), baseTicks);
    }

    private SfxElectricRecipe(String key, String recipeType, Set<String> recipeTags, List<SfxRecipeSlot> inputs, List<List<SfxElectricStack>> outputGroups, int baseTicks) {
        this.key = Objects.requireNonNull(key, "key");
        this.recipeType = normalizeRecipeType(recipeType);
        this.recipeTags = normalizeRecipeTags(recipeTags);
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputGroups, "outputGroups");
        if (inputs.isEmpty() || inputs.size() > SfxElectricMachineState.MAX_INPUTS) {
            throw new IllegalArgumentException("Electric recipes support one to six input slots.");
        }
        if (outputGroups.isEmpty()) {
            throw new IllegalArgumentException("Electric recipes must declare at least one output group.");
        }
        for (List<SfxElectricStack> group : outputGroups) {
            if (group == null || group.isEmpty() || group.size() > SfxElectricMachineState.MAX_OUTPUTS) {
                throw new IllegalArgumentException("Electric recipe output groups must contain one or two stacks.");
            }
        }
        this.inputs = List.copyOf(inputs);
        this.outputGroups = outputGroups.stream().map(List::copyOf).toList();
        this.baseTicks = Math.max(1, baseTicks);
    }

    public static SfxElectricRecipe fixedOutputs(String key, List<SfxRecipeSlot> inputs, List<SfxElectricStack> outputs, int baseTicks) {
        return fixedOutputs(key, null, Set.of(), inputs, outputs, baseTicks);
    }

    public static SfxElectricRecipe fixedOutputs(String key, String recipeType, Set<String> recipeTags, List<SfxRecipeSlot> inputs, List<SfxElectricStack> outputs, int baseTicks) {
        return new SfxElectricRecipe(key, recipeType, recipeTags, inputs, List.of(outputs), baseTicks);
    }

    public static SfxElectricRecipe randomOutput(String key, SfxRecipeSlot input, List<SfxElectricStack> outputs, int baseTicks) {
        return randomOutput(key, null, Set.of(), input, outputs, baseTicks);
    }

    public static SfxElectricRecipe randomOutput(String key, String recipeType, Set<String> recipeTags, SfxRecipeSlot input, List<SfxElectricStack> outputs, int baseTicks) {
        return new SfxElectricRecipe(
                key,
                recipeType,
                recipeTags,
                List.of(Objects.requireNonNull(input, "input")),
                outputs.stream().map(stack -> List.of(stack)).toList(),
                baseTicks);
    }

    public String key() {
        return key;
    }

    public String recipeType() {
        return recipeType;
    }

    public Set<String> recipeTags() {
        return recipeTags;
    }

    public SfxRecipeSlot input() {
        return inputs.getFirst();
    }

    public List<SfxRecipeSlot> inputs() {
        return inputs;
    }

    public SfxElectricStack output() {
        return outputs().getFirst();
    }

    public List<SfxElectricStack> outputs() {
        return outputGroups.getFirst();
    }

    public List<List<SfxElectricStack>> outputGroups() {
        return outputGroups;
    }

    public boolean hasRandomOutput() {
        return outputGroups.size() > 1;
    }

    public List<SfxElectricStack> rollOutputs() {
        if (outputGroups.size() == 1) {
            return outputGroups.getFirst();
        }
        return outputGroups.get(ThreadLocalRandom.current().nextInt(outputGroups.size()));
    }

    public int baseTicks() {
        return baseTicks;
    }

    private static String normalizeRecipeType(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static Set<String> normalizeRecipeTags(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().replace('_', '-').toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }
}
