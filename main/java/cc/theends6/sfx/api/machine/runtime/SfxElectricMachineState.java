package cc.theends6.sfx.api.machine.runtime;

import cc.theends6.sfx.api.machine.runtime.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SfxElectricMachineState {
    public static final int MAX_INPUTS = 7;
    public static final int MAX_OUTPUTS = 10;
    private static final int SCHEMA = 12;
    private static final Logger LOGGER = Logger.getLogger(SfxElectricMachineState.class.getName());

    private final SfxElectricStack[] inputs = new SfxElectricStack[MAX_INPUTS];
    private final SfxElectricStack[] outputs = new SfxElectricStack[MAX_OUTPUTS];
    private final SfxElectricStack[] reservedInputs = new SfxElectricStack[MAX_INPUTS];
    private final SfxElectricStack[] activeOutputs = new SfxElectricStack[MAX_OUTPUTS];
    private int progressWork;
    private String activeRecipeKey;
    private int activeInputSlot = -1;
    private int activeBaseTicks;
    private SfxElectricStack pendingOutput;
    private int storedEnergy;
    private int specialData;
    private int specialData2;
    private boolean enabled = true;
    private final SfxElectricStack[] specialInputs = new SfxElectricStack[MAX_INPUTS];
    private final SfxElectricStack[] specialOutputs = new SfxElectricStack[MAX_OUTPUTS];

    public static SfxElectricMachineState empty() {
        return new SfxElectricMachineState();
    }

    public static SfxElectricMachineState decode(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return empty();
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(blob))) {
            int schema = input.readInt();
            if (schema == 1) {
                SfxElectricMachineState legacy = new SfxElectricMachineState();
                if (input.readBoolean()) {
                    legacy.inputs[0] = SfxElectricStack.read(input);
                }
                if (input.readBoolean()) {
                    legacy.outputs[0] = SfxElectricStack.read(input);
                }
                legacy.progressWork = Math.max(0, input.readInt());
                String recipeKey = input.readUTF();
                legacy.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
                if (legacy.activeRecipeKey != null) {
                    legacy.resetProgress();
                }
                return legacy;
            }
            if (schema == 2) {
                SfxElectricMachineState legacy = new SfxElectricMachineState();
                readLegacyStacks(input, legacy.inputs, 2);
                readLegacyStacks(input, legacy.outputs, 2);
                legacy.progressWork = Math.max(0, input.readInt());
                String recipeKey = input.readUTF();
                legacy.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
                legacy.activeInputSlot = Math.max(-1, Math.min(MAX_INPUTS - 1, input.readInt()));
                if (legacy.activeRecipeKey != null) {
                    legacy.resetProgress();
                }
                return legacy;
            }
            if (schema == 3 || schema == 4) {
                SfxElectricMachineState state = new SfxElectricMachineState();
                readLegacyStacks(input, state.inputs, 2);
                readLegacyStacks(input, state.outputs, 2);
                state.progressWork = Math.max(0, input.readInt());
                String recipeKey = input.readUTF();
                state.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
                state.activeInputSlot = Math.max(-1, Math.min(MAX_INPUTS - 1, input.readInt()));
                if (input.readBoolean()) {
                    state.reservedInputs[0] = SfxElectricStack.read(input);
                }
                if (input.readBoolean()) {
                    state.pendingOutput = SfxElectricStack.read(input);
                }
                if (schema == 4) {
                    state.storedEnergy = Math.max(0, input.readInt());
                }
                return state;
            }
            if (schema == 5) {
                SfxElectricMachineState state = new SfxElectricMachineState();
                readLegacyStacks(input, state.inputs, 2);
                readLegacyStacks(input, state.outputs, 2);
                state.progressWork = Math.max(0, input.readInt());
                String recipeKey = input.readUTF();
                state.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
                state.activeInputSlot = Math.max(-1, Math.min(MAX_INPUTS - 1, input.readInt()));
                readLegacyStacks(input, state.reservedInputs, 2);
                if (input.readBoolean()) {
                    state.pendingOutput = SfxElectricStack.read(input);
                }
                state.storedEnergy = Math.max(0, input.readInt());
                return state;
            }
            if (schema == 6 || schema == 7) {
                SfxElectricMachineState state = new SfxElectricMachineState();
                readStacksV2(input, state.inputs, 6);
                readStacksV2(input, state.outputs, 2);
                state.progressWork = Math.max(0, input.readInt());
                String recipeKey = input.readUTF();
                state.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
                state.activeInputSlot = Math.max(-1, Math.min(MAX_INPUTS - 1, input.readInt()));
                state.activeBaseTicks = Math.max(0, input.readInt());
                readStacksV2(input, state.reservedInputs, 6);
                readStacksV2(input, state.activeOutputs, 2);
                if (input.readBoolean()) {
                    state.pendingOutput = SfxElectricStack.readV2(input);
                }
                state.storedEnergy = Math.max(0, input.readInt());
                return state;
            }
            if (schema == 8) {
                SfxElectricMachineState state = new SfxElectricMachineState();
                readStacksV2(input, state.inputs, 7);
                readStacksV2(input, state.outputs, 3);
                state.progressWork = Math.max(0, input.readInt());
                String recipeKey = input.readUTF();
                state.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
                state.activeInputSlot = Math.max(-1, Math.min(MAX_INPUTS - 1, input.readInt()));
                state.activeBaseTicks = Math.max(0, input.readInt());
                readStacksV2(input, state.reservedInputs, 7);
                readStacksV2(input, state.activeOutputs, 3);
                if (input.readBoolean()) {
                    state.pendingOutput = SfxElectricStack.readV2(input);
                }
                state.storedEnergy = Math.max(0, input.readInt());
                state.specialData = Math.max(0, input.readInt());
                return state;
            }
            if (schema != SCHEMA && schema != 9 && schema != 10 && schema != 11) {
                LOGGER.warning("Unsupported electric machine state schema " + schema + "; returning empty state to keep the machine usable");
                return empty();
            }
            SfxElectricMachineState state = new SfxElectricMachineState();
            int encodedOutputSlots = schema >= SCHEMA ? state.outputs.length : 7;
            readStacksV2(input, state.inputs, 7);
            readStacksV2(input, state.outputs, encodedOutputSlots);
            state.progressWork = Math.max(0, input.readInt());
            String recipeKey = input.readUTF();
            state.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
            state.activeInputSlot = Math.max(-1, Math.min(MAX_INPUTS - 1, input.readInt()));
            state.activeBaseTicks = Math.max(0, input.readInt());
            readStacksV2(input, state.reservedInputs, 7);
            readStacksV2(input, state.activeOutputs, encodedOutputSlots);
            if (input.readBoolean()) {
                state.pendingOutput = SfxElectricStack.readV2(input);
            }
            state.storedEnergy = Math.max(0, input.readInt());
            state.specialData = Math.max(0, input.readInt());
            if (schema >= 10) {
                state.enabled = input.readBoolean();
            }
            if (schema >= 11) {
                state.specialData2 = Math.max(0, input.readInt());
                readStacksV2(input, state.specialInputs, 7);
                readStacksV2(input, state.specialOutputs, encodedOutputSlots);
            }
            return state;
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "Failed to decode electric machine state; returning empty state to keep the machine usable", exception);
            return empty();
        }
    }

    private static void readLegacyStacks(DataInputStream input, SfxElectricStack[] target, int count) throws IOException {
        for (int slot = 0; slot < count; slot++) {
            if (input.readBoolean()) {
                target[slot] = SfxElectricStack.read(input);
            }
        }
    }

    private static void readStacksV2(DataInputStream input, SfxElectricStack[] target) throws IOException {
        readStacksV2(input, target, target.length);
    }

    private static void readStacksV2(DataInputStream input, SfxElectricStack[] target, int count) throws IOException {
        int safeCount = Math.min(count, target.length);
        for (int slot = 0; slot < safeCount; slot++) {
            if (input.readBoolean()) {
                target[slot] = SfxElectricStack.readV2(input);
            }
        }
        for (int slot = safeCount; slot < count; slot++) {
            if (input.readBoolean()) {
                SfxElectricStack.readV2(input);
            }
        }
    }

    public byte[] encode() {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(SCHEMA);
            writeStacksV2(output, inputs);
            writeStacksV2(output, outputs);
            output.writeInt(progressWork);
            output.writeUTF(activeRecipeKey == null ? "" : activeRecipeKey);
            output.writeInt(activeInputSlot);
            output.writeInt(activeBaseTicks);
            writeStacksV2(output, reservedInputs);
            writeStacksV2(output, activeOutputs);
            output.writeBoolean(pendingOutput != null);
            if (pendingOutput != null) {
                pendingOutput.writeV2(output);
            }
            output.writeInt(storedEnergy);
            output.writeInt(specialData);
            output.writeBoolean(enabled);
            output.writeInt(specialData2);
            writeStacksV2(output, specialInputs);
            writeStacksV2(output, specialOutputs);
            output.flush();
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode electric machine state", exception);
        }
    }

    private static void writeStacksV2(DataOutputStream output, SfxElectricStack[] stacks) throws IOException {
        for (SfxElectricStack stack : stacks) {
            output.writeBoolean(stack != null);
            if (stack != null) {
                stack.writeV2(output);
            }
        }
    }

    public SfxElectricStack input(int slot) {
        return inputs[slot];
    }

    public void input(int slot, SfxElectricStack input) {
        inputs[slot] = input;
    }

    public SfxElectricStack output(int slot) {
        return outputs[slot];
    }

    public void output(int slot, SfxElectricStack output) {
        outputs[slot] = output;
    }

    public int inputCapacity() {
        return inputs.length;
    }

    public int outputCapacity() {
        return outputs.length;
    }

    public int progressWork() {
        return progressWork;
    }

    public void progressWork(int progressWork) {
        this.progressWork = Math.max(0, progressWork);
    }

    public String activeRecipeKey() {
        return activeRecipeKey;
    }

    public void activeRecipeKey(String activeRecipeKey) {
        this.activeRecipeKey = activeRecipeKey == null || activeRecipeKey.isBlank() ? null : activeRecipeKey;
    }

    public int activeInputSlot() {
        return activeInputSlot;
    }

    public void activeInputSlot(int activeInputSlot) {
        this.activeInputSlot = activeInputSlot < 0 ? -1 : Math.min(MAX_INPUTS - 1, activeInputSlot);
    }

    public int activeBaseTicks() {
        return activeBaseTicks;
    }

    public void activeBaseTicks(int activeBaseTicks) {
        this.activeBaseTicks = Math.max(0, activeBaseTicks);
    }

    public List<SfxElectricStack> activeOutputs() {
        return compact(activeOutputs);
    }

    public void activeOutputs(List<SfxElectricStack> stacks) {
        clear(activeOutputs);
        if (stacks == null) {
            return;
        }
        for (int index = 0; index < Math.min(activeOutputs.length, stacks.size()); index++) {
            activeOutputs[index] = stacks.get(index);
        }
    }

    public SfxElectricStack reservedInput() {
        return reservedInputs[0];
    }

    public void reservedInput(SfxElectricStack reservedInput) {
        clear(reservedInputs);
        reservedInputs[0] = reservedInput;
    }

    public List<SfxElectricStack> reservedInputs() {
        return compact(reservedInputs);
    }

    public void reservedInputs(List<SfxElectricStack> stacks) {
        clear(reservedInputs);
        if (stacks == null) {
            return;
        }
        for (int index = 0; index < Math.min(reservedInputs.length, stacks.size()); index++) {
            reservedInputs[index] = stacks.get(index);
        }
    }

    private static List<SfxElectricStack> compact(SfxElectricStack[] stacks) {
        List<SfxElectricStack> result = new ArrayList<>(stacks.length);
        for (SfxElectricStack stack : stacks) {
            if (stack != null) {
                result.add(stack);
            }
        }
        return List.copyOf(result);
    }

    private static void clear(SfxElectricStack[] stacks) {
        for (int index = 0; index < stacks.length; index++) {
            stacks[index] = null;
        }
    }

    public SfxElectricStack pendingOutput() {
        return pendingOutput;
    }

    public void pendingOutput(SfxElectricStack pendingOutput) {
        this.pendingOutput = pendingOutput;
    }

    public boolean hasAnyInput() {
        for (int slot = 0; slot < inputs.length; slot++) {
            if (hasInput(slot)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasInput(int slot) {
        SfxElectricStack input = inputs[slot];
        return input != null && input.amount() > 0;
    }

    public boolean hasOutput(int slot) {
        SfxElectricStack output = outputs[slot];
        return output != null && output.amount() > 0;
    }

    public boolean hasPendingOutput() {
        return pendingOutput != null;
    }

    public boolean hasReservedInput() {
        for (SfxElectricStack reservedInput : reservedInputs) {
            if (reservedInput != null) {
                return true;
            }
        }
        return false;
    }

    public boolean hasProgress() {
        return progressWork > 0 || activeRecipeKey != null || hasReservedInput() || pendingOutput != null || !activeOutputs().isEmpty();
    }

    public int storedEnergy() {
        return storedEnergy;
    }

    public void storedEnergy(int storedEnergy) {
        this.storedEnergy = Math.max(0, storedEnergy);
    }

    public int specialData() {
        return specialData;
    }

    public void specialData(int specialData) {
        this.specialData = Math.max(0, specialData);
    }

    public int specialData2() {
        return specialData2;
    }

    public void specialData2(int specialData2) {
        this.specialData2 = Math.max(0, specialData2);
    }

    public SfxElectricStack specialInput(int slot) {
        return specialInputs[slot];
    }

    public void specialInput(int slot, SfxElectricStack stack) {
        specialInputs[slot] = stack;
    }

    public SfxElectricStack specialOutput(int slot) {
        return specialOutputs[slot];
    }

    public void specialOutput(int slot, SfxElectricStack stack) {
        specialOutputs[slot] = stack;
    }

    public void clearSpecialInputs() {
        clear(specialInputs);
    }

    public void clearSpecialOutputs() {
        clear(specialOutputs);
    }

    public void clearSpecialWorkData() {
        specialData2 = 0;
        clear(specialInputs);
        clear(specialOutputs);
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void resetProgress() {
        progressWork = 0;
        activeRecipeKey = null;
        activeInputSlot = -1;
        activeBaseTicks = 0;
        clear(reservedInputs);
        clear(activeOutputs);
        pendingOutput = null;
    }
}
