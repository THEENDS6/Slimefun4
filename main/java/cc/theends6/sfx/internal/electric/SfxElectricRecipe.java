package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class SfxElectricRecipe {
    private final String key;
    private final List<SfxRecipeSlot> inputs;
    private final List<List<SfxElectricStack>> outputGroups;
    private final int baseTicks;

    public SfxElectricRecipe(String key, SfxRecipeSlot input, SfxElectricStack output, int baseTicks) {
        this(key, List.of(Objects.requireNonNull(input, "input")), List.of(List.of(Objects.requireNonNull(output, "output"))), baseTicks);
    }

    public SfxElectricRecipe(String key, List<SfxRecipeSlot> inputs, SfxElectricStack output, int baseTicks) {
        this(key, inputs, List.of(List.of(Objects.requireNonNull(output, "output"))), baseTicks);
    }

    private SfxElectricRecipe(String key, List<SfxRecipeSlot> inputs, List<List<SfxElectricStack>> outputGroups, int baseTicks) {
        this.key = Objects.requireNonNull(key, "key");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputGroups, "outputGroups");
        if (inputs.isEmpty() || inputs.size() > 2) {
            throw new IllegalArgumentException("Electric recipes support one or two input slots.");
        }
        if (outputGroups.isEmpty()) {
            throw new IllegalArgumentException("Electric recipes must declare at least one output group.");
        }
        for (List<SfxElectricStack> group : outputGroups) {
            if (group == null || group.isEmpty() || group.size() > 2) {
                throw new IllegalArgumentException("Electric recipe output groups must contain one or two stacks.");
            }
        }
        this.inputs = List.copyOf(inputs);
        this.outputGroups = outputGroups.stream().map(List::copyOf).toList();
        this.baseTicks = Math.max(1, baseTicks);
    }

    public static SfxElectricRecipe fixedOutputs(String key, List<SfxRecipeSlot> inputs, List<SfxElectricStack> outputs, int baseTicks) {
        return new SfxElectricRecipe(key, inputs, List.of(outputs), baseTicks);
    }

    public static SfxElectricRecipe randomOutput(String key, SfxRecipeSlot input, List<SfxElectricStack> outputs, int baseTicks) {
        return new SfxElectricRecipe(
                key,
                List.of(Objects.requireNonNull(input, "input")),
                outputs.stream().map(stack -> List.of(stack)).toList(),
                baseTicks);
    }

    public String key() {
        return key;
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
}
