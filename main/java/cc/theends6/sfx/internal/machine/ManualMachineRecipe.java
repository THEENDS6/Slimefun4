package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public final class ManualMachineRecipe {
    private final String machineId;
    private final ManualMachineOperation operation;
    private final List<SfxRecipeSlot> input;
    private final List<ManualMachineOutput> outputs;
    private final List<ManualMachineOutput> randomOutputs;
    private final Component note;
    private final int priority;

    private ManualMachineRecipe(String machineId, ManualMachineOperation operation, List<SfxRecipeSlot> input, List<ManualMachineOutput> outputs, Component note) {
        this(machineId, operation, input, outputs, List.of(), note, inferPriority(input));
    }

    private ManualMachineRecipe(String machineId, ManualMachineOperation operation, List<SfxRecipeSlot> input, List<ManualMachineOutput> outputs, Component note, int priority) {
        this(machineId, operation, input, outputs, List.of(), note, priority);
    }

    private ManualMachineRecipe(String machineId, ManualMachineOperation operation, List<SfxRecipeSlot> input, List<ManualMachineOutput> outputs, List<ManualMachineOutput> randomOutputs, Component note) {
        this(machineId, operation, input, outputs, randomOutputs, note, inferPriority(input));
    }

    private ManualMachineRecipe(String machineId, ManualMachineOperation operation, List<SfxRecipeSlot> input, List<ManualMachineOutput> outputs, List<ManualMachineOutput> randomOutputs, Component note, int priority) {
        this.machineId = SfxItemDefinition.normalizeId(machineId);
        this.operation = Objects.requireNonNull(operation, "operation");
        this.input = Collections.unmodifiableList(new ArrayList<>(input));
        this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
        this.randomOutputs = Collections.unmodifiableList(new ArrayList<>(randomOutputs));
        this.note = note;
        this.priority = priority;
        if (operation == ManualMachineOperation.SHAPED_3X3 && input.size() != 9) {
            throw new IllegalArgumentException("A shaped manual machine recipe must have exactly 9 slots.");
        }
        if ((operation == ManualMachineOperation.SINGLE_INPUT || operation == ManualMachineOperation.HAND_INPUT) && input.size() != 1) {
            throw new IllegalArgumentException("A single-slot manual machine recipe must have exactly 1 slot.");
        }
        if (operation == ManualMachineOperation.SHAPELESS_INPUT) {
            if (input.isEmpty() || input.size() > 9) {
                throw new IllegalArgumentException("A shapeless manual machine recipe must have between 1 and 9 input slots.");
            }
            for (SfxRecipeSlot slot : input) {
                if (slot == null || slot.isEmpty()) {
                    throw new IllegalArgumentException("A shapeless manual machine recipe cannot contain empty slots.");
                }
            }
        }
        if (this.outputs.isEmpty() && this.randomOutputs.isEmpty()) {
            throw new IllegalArgumentException("A manual machine recipe must provide at least one output.");
        }
    }

    public static ManualMachineRecipe shaped(String machineId, List<SfxRecipeSlot> input, ManualMachineOutput output, Component note) {
        return shaped(machineId, input, List.of(output), note);
    }

    public static ManualMachineRecipe shaped(String machineId, List<SfxRecipeSlot> input, List<ManualMachineOutput> outputs, Component note) {
        return new ManualMachineRecipe(machineId, ManualMachineOperation.SHAPED_3X3, input, outputs, note);
    }

    public static ManualMachineRecipe shapeless(String machineId, List<SfxRecipeSlot> input, ManualMachineOutput output, Component note) {
        return shapeless(machineId, input, List.of(output), note);
    }

    public static ManualMachineRecipe shapeless(String machineId, List<SfxRecipeSlot> input, List<ManualMachineOutput> outputs, Component note) {
        return new ManualMachineRecipe(machineId, ManualMachineOperation.SHAPELESS_INPUT, input, outputs, note);
    }

    public static ManualMachineRecipe shapeless(String machineId, List<SfxRecipeSlot> input, ManualMachineOutput output, Component note, int priority) {
        return shapeless(machineId, input, List.of(output), note, priority);
    }

    public static ManualMachineRecipe shapeless(String machineId, List<SfxRecipeSlot> input, List<ManualMachineOutput> outputs, Component note, int priority) {
        return new ManualMachineRecipe(machineId, ManualMachineOperation.SHAPELESS_INPUT, input, outputs, note, priority);
    }

    public static ManualMachineRecipe single(String machineId, SfxRecipeSlot input, ManualMachineOutput output, Component note) {
        return single(machineId, input, List.of(output), note);
    }

    public static ManualMachineRecipe single(String machineId, SfxRecipeSlot input, List<ManualMachineOutput> outputs, Component note) {
        return new ManualMachineRecipe(machineId, ManualMachineOperation.SINGLE_INPUT, List.of(input), outputs, note);
    }

    public static ManualMachineRecipe randomSingle(String machineId, SfxRecipeSlot input, List<ManualMachineOutput> randomOutputs, List<ManualMachineOutput> fixedOutputs, Component note) {
        return new ManualMachineRecipe(machineId, ManualMachineOperation.SINGLE_INPUT, List.of(input), fixedOutputs, randomOutputs, note);
    }

    public static ManualMachineRecipe hand(String machineId, SfxRecipeSlot input, ManualMachineOutput output, Component note) {
        return hand(machineId, input, List.of(output), note);
    }

    public static ManualMachineRecipe hand(String machineId, SfxRecipeSlot input, List<ManualMachineOutput> outputs, Component note) {
        return new ManualMachineRecipe(machineId, ManualMachineOperation.HAND_INPUT, List.of(input), outputs, note);
    }

    private static int inferPriority(List<SfxRecipeSlot> input) {
        int nonEmpty = 0;
        int totalAmount = 0;
        for (SfxRecipeSlot slot : input) {
            if (slot != null && !slot.isEmpty()) {
                nonEmpty++;
                totalAmount += Math.max(1, slot.amount());
            }
        }
        return nonEmpty * 100 + totalAmount;
    }

    public String machineId() {
        return machineId;
    }

    public ManualMachineOperation operation() {
        return operation;
    }

    public List<SfxRecipeSlot> input() {
        return input;
    }

    



    public List<ManualMachineOutput> outputs() {
        if (randomOutputs.isEmpty()) {
            return outputs;
        }
        List<ManualMachineOutput> combined = new ArrayList<>(outputs.size() + randomOutputs.size());
        combined.addAll(outputs);
        combined.addAll(randomOutputs);
        return Collections.unmodifiableList(combined);
    }

    public List<ManualMachineOutput> fixedOutputs() {
        return outputs;
    }

    public List<ManualMachineOutput> randomOutputs() {
        return randomOutputs;
    }

    public boolean hasRandomOutputs() {
        return !randomOutputs.isEmpty();
    }

    public ManualMachineOutput output() {
        List<ManualMachineOutput> allOutputs = outputs();
        return allOutputs.getFirst();
    }

    public Component note() {
        return note;
    }

    public int priority() {
        return priority;
    }
}
