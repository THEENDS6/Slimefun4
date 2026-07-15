package cc.theends6.sfx.api.energy.runtime;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SfxEnergyNodeState {
    private static final int SCHEMA = 6;
    private static final int LEGACY_SCHEMA = 1;
    private static final Logger LOGGER = Logger.getLogger(SfxEnergyNodeState.class.getName());

    public static final int INPUT_CAPACITY = 4;
    public static final int OUTPUT_CAPACITY = 2;
    private final SfxElectricStack[] inputs = new SfxElectricStack[INPUT_CAPACITY];
    private final SfxElectricStack[] outputs = new SfxElectricStack[OUTPUT_CAPACITY];
    private int storedEnergy;
    private String activeFuelKey;
    private int fuelProgressTenths;
    private int fuelTotalTenths;
    private SfxElectricStack pendingOutput;
    private int specialData;
    private int specialData2;
    private int specialData3;

    public static SfxEnergyNodeState empty() {
        return new SfxEnergyNodeState();
    }

    public static SfxEnergyNodeState decode(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return empty();
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(blob))) {
            int schema = input.readInt();
            if (schema != SCHEMA && schema != LEGACY_SCHEMA && schema != 2 && schema != 3 && schema != 4 && schema != 5) {
                LOGGER.warning("Unsupported energy node state schema " + schema + "; returning empty state to keep the node usable");
                return empty();
            }
            SfxEnergyNodeState state = new SfxEnergyNodeState();
            int encodedInputCount = schema >= 5 ? INPUT_CAPACITY : schema >= 4 ? 3 : 2;
            for (int i = 0; i < encodedInputCount; i++) {
                if (input.readBoolean()) {
                    state.inputs[i] = schema == LEGACY_SCHEMA ? SfxElectricStack.read(input) : SfxElectricStack.readV2(input);
                }
            }
            for (int i = 0; i < state.outputs.length; i++) {
                if (input.readBoolean()) {
                    state.outputs[i] = schema == LEGACY_SCHEMA ? SfxElectricStack.read(input) : SfxElectricStack.readV2(input);
                }
            }
            state.storedEnergy = Math.max(0, input.readInt());
            String activeFuelKey = input.readUTF();
            state.activeFuelKey = activeFuelKey.isBlank() ? null : activeFuelKey;
            state.fuelProgressTenths = Math.max(0, input.readInt());
            state.fuelTotalTenths = Math.max(0, input.readInt());
            if (input.readBoolean()) {
                state.pendingOutput = schema == LEGACY_SCHEMA ? SfxElectricStack.read(input) : SfxElectricStack.readV2(input);
            }
            if (schema >= 3) {
                state.specialData = Math.max(0, input.readInt());
                state.specialData2 = Math.max(0, input.readInt());
            }
            if (schema >= 6) {
                state.specialData3 = Math.max(0, input.readInt());
            }
            return state;
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "Failed to decode energy node state; returning empty state to keep the node usable", exception);
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
                    inputStack.writeV2(output);
                }
            }
            for (SfxElectricStack outputStack : outputs) {
                output.writeBoolean(outputStack != null);
                if (outputStack != null) {
                    outputStack.writeV2(output);
                }
            }
            output.writeInt(storedEnergy);
            output.writeUTF(activeFuelKey == null ? "" : activeFuelKey);
            output.writeInt(fuelProgressTenths);
            output.writeInt(fuelTotalTenths);
            output.writeBoolean(pendingOutput != null);
            if (pendingOutput != null) {
                pendingOutput.writeV2(output);
            }
            output.writeInt(specialData);
            output.writeInt(specialData2);
            output.writeInt(specialData3);
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

    public int specialData3() {
        return specialData3;
    }

    public void specialData3(int specialData3) {
        this.specialData3 = Math.max(0, specialData3);
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
