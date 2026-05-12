package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class SfxEnergyNodeState {
    private static final int SCHEMA = 1;

    private final SfxElectricStack[] inputs = new SfxElectricStack[2];
    private final SfxElectricStack[] outputs = new SfxElectricStack[2];
    private int storedEnergy;
    private String activeFuelKey;
    private int fuelProgressTenths;
    private int fuelTotalTenths;
    private SfxElectricStack pendingOutput;

    public static SfxEnergyNodeState empty() {
        return new SfxEnergyNodeState();
    }

    public static SfxEnergyNodeState decode(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return empty();
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(blob))) {
            if (input.readInt() != SCHEMA) {
                return empty();
            }
            SfxEnergyNodeState state = new SfxEnergyNodeState();
            for (int i = 0; i < state.inputs.length; i++) {
                if (input.readBoolean()) {
                    state.inputs[i] = SfxElectricStack.read(input);
                }
            }
            for (int i = 0; i < state.outputs.length; i++) {
                if (input.readBoolean()) {
                    state.outputs[i] = SfxElectricStack.read(input);
                }
            }
            state.storedEnergy = Math.max(0, input.readInt());
            String activeFuelKey = input.readUTF();
            state.activeFuelKey = activeFuelKey.isBlank() ? null : activeFuelKey;
            state.fuelProgressTenths = Math.max(0, input.readInt());
            state.fuelTotalTenths = Math.max(0, input.readInt());
            if (input.readBoolean()) {
                state.pendingOutput = SfxElectricStack.read(input);
            }
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
            output.writeInt(storedEnergy);
            output.writeUTF(activeFuelKey == null ? "" : activeFuelKey);
            output.writeInt(fuelProgressTenths);
            output.writeInt(fuelTotalTenths);
            output.writeBoolean(pendingOutput != null);
            if (pendingOutput != null) {
                pendingOutput.write(output);
            }
            output.flush();
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode energy node state", exception);
        }
    }

    public SfxElectricStack input(int slot) {
        return inputs[slot];
    }

    public void input(int slot, SfxElectricStack stack) {
        inputs[slot] = stack;
    }

    public SfxElectricStack output(int slot) {
        return outputs[slot];
    }

    public void output(int slot, SfxElectricStack stack) {
        outputs[slot] = stack;
    }

    public int storedEnergy() {
        return storedEnergy;
    }

    public void storedEnergy(int storedEnergy) {
        this.storedEnergy = Math.max(0, storedEnergy);
    }

    public String activeFuelKey() {
        return activeFuelKey;
    }

    public void activeFuelKey(String activeFuelKey) {
        this.activeFuelKey = activeFuelKey == null || activeFuelKey.isBlank() ? null : activeFuelKey;
    }

    public int fuelProgressTenths() {
        return fuelProgressTenths;
    }

    public void fuelProgressTenths(int fuelProgressTenths) {
        this.fuelProgressTenths = Math.max(0, fuelProgressTenths);
    }

    public int fuelTotalTenths() {
        return fuelTotalTenths;
    }

    public void fuelTotalTenths(int fuelTotalTenths) {
        this.fuelTotalTenths = Math.max(0, fuelTotalTenths);
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

    public boolean hasActiveFuel() {
        return activeFuelKey != null && fuelTotalTenths > 0;
    }

    public boolean hasPendingOutput() {
        return pendingOutput != null;
    }

    public void clearFuelOperation() {
        activeFuelKey = null;
        fuelProgressTenths = 0;
        fuelTotalTenths = 0;
    }
}
