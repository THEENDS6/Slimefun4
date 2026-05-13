package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

final class SfxConfigurableMachineState {
    private static final int SCHEMA = 1;
    private static final int MAX_INPUTS = 6;
    private static final int MAX_OUTPUTS = 3;
    private final SfxElectricStack[] inputs = new SfxElectricStack[MAX_INPUTS];
    private final SfxElectricStack[] outputs = new SfxElectricStack[MAX_OUTPUTS];
    private int storedEnergy;
    private boolean enabled;
    private int offsetTenths = 30;
    private int cooldownTicks;
    private int mode;
    private String activeFuelKey;
    private int fuelProgressTicks;
    private int fuelTotalTicks;
    private int coolantProgressTicks;
    private int coolantTotalTicks;

    static SfxConfigurableMachineState empty() {
        return new SfxConfigurableMachineState();
    }

    static SfxConfigurableMachineState decode(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return empty();
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(blob))) {
            int schema = input.readInt();
            if (schema != SCHEMA) {
                return empty();
            }
            SfxConfigurableMachineState state = new SfxConfigurableMachineState();
            for (int slot = 0; slot < MAX_INPUTS; slot++) {
                if (input.readBoolean()) {
                    state.inputs[slot] = SfxElectricStack.readV2(input);
                }
            }
            for (int slot = 0; slot < MAX_OUTPUTS; slot++) {
                if (input.readBoolean()) {
                    state.outputs[slot] = SfxElectricStack.readV2(input);
                }
            }
            state.storedEnergy = Math.max(0, input.readInt());
            state.enabled = input.readBoolean();
            state.offsetTenths = Math.max(-100, Math.min(100, input.readInt()));
            state.cooldownTicks = Math.max(0, input.readInt());
            state.mode = Math.max(0, Math.min(1, input.readInt()));
            String key = input.readUTF();
            state.activeFuelKey = key.isBlank() ? null : key;
            state.fuelProgressTicks = Math.max(0, input.readInt());
            state.fuelTotalTicks = Math.max(0, input.readInt());
            state.coolantProgressTicks = Math.max(0, input.readInt());
            state.coolantTotalTicks = Math.max(0, input.readInt());
            return state;
        } catch (IOException | IllegalArgumentException ignored) {
            return empty();
        }
    }

    byte[] encode() {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(SCHEMA);
            for (SfxElectricStack stack : inputs) {
                output.writeBoolean(stack != null);
                if (stack != null) {
                    stack.writeV2(output);
                }
            }
            for (SfxElectricStack stack : outputs) {
                output.writeBoolean(stack != null);
                if (stack != null) {
                    stack.writeV2(output);
                }
            }
            output.writeInt(storedEnergy);
            output.writeBoolean(enabled);
            output.writeInt(offsetTenths);
            output.writeInt(cooldownTicks);
            output.writeInt(mode);
            output.writeUTF(activeFuelKey == null ? "" : activeFuelKey);
            output.writeInt(fuelProgressTicks);
            output.writeInt(fuelTotalTicks);
            output.writeInt(coolantProgressTicks);
            output.writeInt(coolantTotalTicks);
            output.flush();
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode configurable machine state", exception);
        }
    }

    SfxElectricStack input(int slot) {
        return inputs[slot];
    }

    void input(int slot, SfxElectricStack stack) {
        inputs[slot] = stack;
    }

    SfxElectricStack output(int slot) {
        return outputs[slot];
    }

    void output(int slot, SfxElectricStack stack) {
        outputs[slot] = stack;
    }

    int inputCapacity() {
        return inputs.length;
    }

    int outputCapacity() {
        return outputs.length;
    }

    int storedEnergy() {
        return storedEnergy;
    }

    void storedEnergy(int storedEnergy) {
        this.storedEnergy = Math.max(0, storedEnergy);
    }

    boolean enabled() {
        return enabled;
    }

    void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    int offsetTenths() {
        return offsetTenths;
    }

    void offsetTenths(int offsetTenths) {
        this.offsetTenths = Math.max(-100, Math.min(100, offsetTenths));
    }

    int cooldownTicks() {
        return cooldownTicks;
    }

    void cooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Math.max(0, cooldownTicks);
    }

    int mode() {
        return mode;
    }

    void mode(int mode) {
        this.mode = mode <= 0 ? 0 : 1;
    }

    String activeFuelKey() {
        return activeFuelKey;
    }

    void activeFuelKey(String activeFuelKey) {
        this.activeFuelKey = activeFuelKey == null || activeFuelKey.isBlank() ? null : activeFuelKey;
    }

    int fuelProgressTicks() {
        return fuelProgressTicks;
    }

    void fuelProgressTicks(int fuelProgressTicks) {
        this.fuelProgressTicks = Math.max(0, fuelProgressTicks);
    }

    int fuelTotalTicks() {
        return fuelTotalTicks;
    }

    void fuelTotalTicks(int fuelTotalTicks) {
        this.fuelTotalTicks = Math.max(0, fuelTotalTicks);
    }

    int coolantProgressTicks() {
        return coolantProgressTicks;
    }

    void coolantProgressTicks(int coolantProgressTicks) {
        this.coolantProgressTicks = Math.max(0, coolantProgressTicks);
    }

    int coolantTotalTicks() {
        return coolantTotalTicks;
    }

    void coolantTotalTicks(int coolantTotalTicks) {
        this.coolantTotalTicks = Math.max(0, coolantTotalTicks);
    }

    boolean hasActiveFuel() {
        return activeFuelKey != null && fuelTotalTicks > 0;
    }

    void clearFuel() {
        activeFuelKey = null;
        fuelProgressTicks = 0;
        fuelTotalTicks = 0;
    }

    boolean hasInventory() {
        for (SfxElectricStack input : inputs) {
            if (input != null) {
                return true;
            }
        }
        for (SfxElectricStack output : outputs) {
            if (output != null) {
                return true;
            }
        }
        return false;
    }

    boolean isActive() {
        return hasActiveFuel() || cooldownTicks > 0 || storedEnergy > 0;
    }
}
