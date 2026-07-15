package cc.theends6.sfx.api.machine.manual;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public final class SfxManualMachineRecipe {
    private final String machineId;
    private final SfxManualMachineOperation operation;
    private final List<SfxRecipeSlot> input;
    private final List<SfxManualMachineOutput> outputs;
    private final List<SfxManualMachineOutput> randomOutputs;
    private final Component note;
    private final int priority;

    private SfxManualMachineRecipe(String machineId, SfxManualMachineOperation operation, List<SfxRecipeSlot> input, List<SfxManualMachineOutput> outputs, Component note) {
        this(machineId, operation, input, outputs, List.of(), note, inferPriority(input));
    }

    private SfxManualMachineRecipe(String machineId, SfxManualMachineOperation operation, List<SfxRecipeSlot> input, List<SfxManualMachineOutput> outputs, Component note, int priority) {
        this(machineId, operation, input, outputs, List.of(), note, priority);
    }

    private SfxManualMachineRecipe(String machineId, SfxManualMachineOperation operation, List<SfxRecipeSlot> input, List<SfxManualMachineOutput> outputs, List<SfxManualMachineOutput> randomOutputs, Component note) {
        this(machineId, operation, input, outputs, randomOutputs, note, inferPriority(input));
    }

    private SfxManualMachineRecipe(String machineId, SfxManualMachineOperation operation, List<SfxRecipeSlot> input, List<SfxManualMachineOutput> outputs, List<SfxManualMachineOutput> randomOutputs, Component note, int priority) {
        this.machineId = SfxItemDefinition.normalizeId(machineId);
        this.operation = Objects.requireNonNull(operation, "operation");
        this.input = Collections.unmodifiableList(new ArrayList<>(input));
        this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
        this.randomOutputs = Collections.unmodifiableList(new ArrayList<>(randomOutputs));
        this.note = note;
        this.priority = priority;
        if (operation == SfxManualMachineOperation.SHAPED_3X3 && input.size() != 9) {
            throw new IllegalArgumentException("A shaped manual machine recipe must have exactly 9 slots.");
        }
        if ((operation == SfxManualMachineOperation.SINGLE_INPUT || operation == SfxManualMachineOperation.HAND_INPUT) && input.size() != 1) {
            throw new IllegalArgumentException("A single-slot manual machine recipe must have exactly 1 slot.");
        }
        if (operation == SfxManualMachineOperation.SHAPELESS_INPUT) {
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

    public static SfxManualMachineRecipe shaped(String machineId, List<SfxRecipeSlot> input, SfxManualMachineOutput output, Component note) {
        return shaped(machineId, input, List.of(output), note);
    }

    public static SfxManualMachineRecipe shaped(String machineId, List<SfxRecipeSlot> input, List<SfxManualMachineOutput> outputs, Component note) {
        return new SfxManualMachineRecipe(machineId, SfxManualMachineOperation.SHAPED_3X3, input, outputs, note);
    }

    public static SfxManualMachineRecipe shapeless(String machineId, List<SfxRecipeSlot> input, SfxManualMachineOutput output, Component note) {
        return shapeless(machineId, input, List.of(output), note);
    }

    public static SfxManualMachineRecipe shapeless(String machineId, List<SfxRecipeSlot> input, List<SfxManualMachineOutput> outputs, Component note) {
        return new SfxManualMachineRecipe(machineId, SfxManualMachineOperation.SHAPELESS_INPUT, input, outputs, note);
    }

    public static SfxManualMachineRecipe shapeless(String machineId, List<SfxRecipeSlot> input, SfxManualMachineOutput output, Component note, int priority) {
        return shapeless(machineId, input, List.of(output), note, priority);
    }

    public static SfxManualMachineRecipe shapeless(String machineId, List<SfxRecipeSlot> input, List<SfxManualMachineOutput> outputs, Component note, int priority) {
        return new SfxManualMachineRecipe(machineId, SfxManualMachineOperation.SHAPELESS_INPUT, input, outputs, note, priority);
    }

    public static SfxManualMachineRecipe single(String machineId, SfxRecipeSlot input, SfxManualMachineOutput output, Component note) {
        return single(machineId, input, List.of(output), note);
    }

    public static SfxManualMachineRecipe single(String machineId, SfxRecipeSlot input, List<SfxManualMachineOutput> outputs, Component note) {
        return new SfxManualMachineRecipe(machineId, SfxManualMachineOperation.SINGLE_INPUT, List.of(input), outputs, note);
    }

    public static SfxManualMachineRecipe randomSingle(String machineId, SfxRecipeSlot input, List<SfxManualMachineOutput> randomOutputs, List<SfxManualMachineOutput> fixedOutputs, Component note) {
        return new SfxManualMachineRecipe(machineId, SfxManualMachineOperation.SINGLE_INPUT, List.of(input), fixedOutputs, randomOutputs, note);
    }

    public static SfxManualMachineRecipe hand(String machineId, SfxRecipeSlot input, SfxManualMachineOutput output, Component note) {
        return hand(machineId, input, List.of(output), note);
    }

    public static SfxManualMachineRecipe hand(String machineId, SfxRecipeSlot input, List<SfxManualMachineOutput> outputs, Component note) {
        return new SfxManualMachineRecipe(machineId, SfxManualMachineOperation.HAND_INPUT, List.of(input), outputs, note);
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

    public SfxManualMachineOperation operation() {
        return operation;
    }

    public List<SfxRecipeSlot> input() {
        return input;
    }

    



    public List<SfxManualMachineOutput> outputs() {
        if (randomOutputs.isEmpty()) {
            return outputs;
        }
        List<SfxManualMachineOutput> combined = new ArrayList<>(outputs.size() + randomOutputs.size());
        combined.addAll(outputs);
        combined.addAll(randomOutputs);
        return Collections.unmodifiableList(combined);
    }

    public List<SfxManualMachineOutput> fixedOutputs() {
        return outputs;
    }

    public List<SfxManualMachineOutput> randomOutputs() {
        return randomOutputs;
    }

    public boolean hasRandomOutputs() {
        return !randomOutputs.isEmpty();
    }

    public SfxManualMachineOutput output() {
        List<SfxManualMachineOutput> allOutputs = outputs();
        return allOutputs.getFirst();
    }

    public Component note() {
        return note;
    }

    public int priority() {
        return priority;
    }
}
