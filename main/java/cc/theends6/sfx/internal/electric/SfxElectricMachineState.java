package cc.theends6.sfx.internal.electric;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SfxElectricMachineState {
    private static final int SCHEMA = 5;

    private final SfxElectricStack[] inputs = new SfxElectricStack[2];
    private final SfxElectricStack[] outputs = new SfxElectricStack[2];
    private final SfxElectricStack[] reservedInputs = new SfxElectricStack[2];
    private int progressWork;
    private String activeRecipeKey;
    private int activeInputSlot = -1;
    private SfxElectricStack pendingOutput;
    private int storedEnergy;

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
                    legacy.progressWork = 0;
                    legacy.activeRecipeKey = null;
                }
                return legacy;
            }
            if (schema == 2) {
                SfxElectricMachineState legacy = new SfxElectricMachineState();
                for (int slot = 0; slot < legacy.inputs.length; slot++) {
                    if (input.readBoolean()) {
                        legacy.inputs[slot] = SfxElectricStack.read(input);
                    }
                }
                for (int slot = 0; slot < legacy.outputs.length; slot++) {
                    if (input.readBoolean()) {
                        legacy.outputs[slot] = SfxElectricStack.read(input);
                    }
                }
                legacy.progressWork = Math.max(0, input.readInt());
                String recipeKey = input.readUTF();
                legacy.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
                legacy.activeInputSlot = Math.max(-1, Math.min(1, input.readInt()));
                if (legacy.activeRecipeKey != null) {
                    legacy.progressWork = 0;
                    legacy.activeRecipeKey = null;
                    legacy.activeInputSlot = -1;
                }
                return legacy;
            }
            if (schema == 3 || schema == 4) {
                SfxElectricMachineState state = new SfxElectricMachineState();
                for (int slot = 0; slot < state.inputs.length; slot++) {
                    if (input.readBoolean()) {
                        state.inputs[slot] = SfxElectricStack.read(input);
                    }
                }
                for (int slot = 0; slot < state.outputs.length; slot++) {
                    if (input.readBoolean()) {
                        state.outputs[slot] = SfxElectricStack.read(input);
                    }
                }
                state.progressWork = Math.max(0, input.readInt());
                String recipeKey = input.readUTF();
                state.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
                state.activeInputSlot = Math.max(-1, Math.min(1, input.readInt()));
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
            if (schema != SCHEMA) {
                return empty();
            }
            SfxElectricMachineState state = new SfxElectricMachineState();
            for (int slot = 0; slot < state.inputs.length; slot++) {
                if (input.readBoolean()) {
                    state.inputs[slot] = SfxElectricStack.read(input);
                }
            }
            for (int slot = 0; slot < state.outputs.length; slot++) {
                if (input.readBoolean()) {
                    state.outputs[slot] = SfxElectricStack.read(input);
                }
            }
            state.progressWork = Math.max(0, input.readInt());
            String recipeKey = input.readUTF();
            state.activeRecipeKey = recipeKey.isBlank() ? null : recipeKey;
            state.activeInputSlot = Math.max(-1, Math.min(1, input.readInt()));
            for (int slot = 0; slot < state.reservedInputs.length; slot++) {
                if (input.readBoolean()) {
                    state.reservedInputs[slot] = SfxElectricStack.read(input);
                }
            }
            if (input.readBoolean()) {
                state.pendingOutput = SfxElectricStack.read(input);
            }
            state.storedEnergy = Math.max(0, input.readInt());
            return state;
        } catch (IOException | IllegalArgumentException ignored) {
            return empty();
        }
    }

    public byte[] encode() {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(SCHEMA);
            for (SfxElectricStack inputStack : inputs) {
                output.writeBoolean(inputStack != null);
                if (inputStack != null) {
                    inputStack.write(output);
                }
            }
            for (SfxElectricStack outputStack : outputs) {
                output.writeBoolean(outputStack != null);
                if (outputStack != null) {
                    outputStack.write(output);
                }
            }
            output.writeInt(progressWork);
            output.writeUTF(activeRecipeKey == null ? "" : activeRecipeKey);
            output.writeInt(activeInputSlot);
            for (SfxElectricStack reservedInput : reservedInputs) {
                output.writeBoolean(reservedInput != null);
                if (reservedInput != null) {
                    reservedInput.write(output);
                }
            }
            output.writeBoolean(pendingOutput != null);
            if (pendingOutput != null) {
                pendingOutput.write(output);
            }
            output.writeInt(storedEnergy);
            output.flush();
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode electric machine state", exception);
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
        this.activeInputSlot = activeInputSlot < 0 ? -1 : Math.min(1, activeInputSlot);
    }

    public SfxElectricStack reservedInput() {
        return reservedInputs[0];
    }

    public void reservedInput(SfxElectricStack reservedInput) {
        reservedInputs[0] = reservedInput;
        reservedInputs[1] = null;
    }

    public List<SfxElectricStack> reservedInputs() {
        List<SfxElectricStack> stacks = new ArrayList<>(reservedInputs.length);
        for (SfxElectricStack reservedInput : reservedInputs) {
            if (reservedInput != null) {
                stacks.add(reservedInput);
            }
        }
        return List.copyOf(stacks);
    }

    public void reservedInputs(List<SfxElectricStack> stacks) {
        reservedInputs[0] = null;
        reservedInputs[1] = null;
        if (stacks == null) {
            return;
        }
        for (int index = 0; index < Math.min(reservedInputs.length, stacks.size()); index++) {
            reservedInputs[index] = stacks.get(index);
        }
    }

    public SfxElectricStack pendingOutput() {
        return pendingOutput;
    }

    public void pendingOutput(SfxElectricStack pendingOutput) {
        this.pendingOutput = pendingOutput;
    }

    public boolean hasAnyInput() {
        return hasInput(0) || hasInput(1);
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
        return progressWork > 0 || activeRecipeKey != null || hasReservedInput() || pendingOutput != null;
    }

    public int storedEnergy() {
        return storedEnergy;
    }

    public void storedEnergy(int storedEnergy) {
        this.storedEnergy = Math.max(0, storedEnergy);
    }

    public void resetProgress() {
        progressWork = 0;
        activeRecipeKey = null;
        activeInputSlot = -1;
        reservedInputs[0] = null;
        reservedInputs[1] = null;
        pendingOutput = null;
    }
}
