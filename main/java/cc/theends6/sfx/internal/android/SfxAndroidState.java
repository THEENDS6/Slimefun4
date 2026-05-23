package cc.theends6.sfx.internal.android;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class SfxAndroidState {
    public static final int VERSION = 1;
    public static final int OUTPUT_SIZE = 6;
    public static final int MAX_BODY_LENGTH = 52;

    private List<SfxAndroidInstruction> body;
    private int index;
    private int fuelTicks;
    private BlockFace rotation;
    private boolean paused;
    private SfxAndroidRuntimeState runtimeState;
    private long sleepingUntilTick;
    private ItemStack fuelSlot;
    private final ItemStack[] outputs;

    public SfxAndroidState(List<SfxAndroidInstruction> body, int index, int fuelTicks, BlockFace rotation, boolean paused, SfxAndroidRuntimeState runtimeState, long sleepingUntilTick, ItemStack fuelSlot, ItemStack[] outputs) {
        setBody(body == null || body.isEmpty() ? List.of(SfxAndroidInstruction.TURN_LEFT) : body);
        this.index = Math.max(0, Math.min(this.body.size() - 1, index));
        this.fuelTicks = Math.max(0, fuelTicks);
        this.rotation = normalizeRotation(rotation);
        this.paused = paused;
        this.runtimeState = runtimeState == null ? (paused ? SfxAndroidRuntimeState.PAUSED : SfxAndroidRuntimeState.ACTIVE) : runtimeState;
        this.sleepingUntilTick = Math.max(0L, sleepingUntilTick);
        this.fuelSlot = cloneOrNull(fuelSlot);
        this.outputs = normalizeOutputs(outputs);
    }

    public static SfxAndroidState createDefault(BlockFace rotation) {
        return new SfxAndroidState(List.of(SfxAndroidInstruction.TURN_LEFT), 0, 0, rotation, true, SfxAndroidRuntimeState.PAUSED, 0L, null, new ItemStack[OUTPUT_SIZE]);
    }

    public static SfxAndroidState decode(byte[] blob, BlockFace fallbackRotation) {
        if (blob == null || blob.length == 0) {
            return createDefault(fallbackRotation);
        }
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(blob))) {
            int version = input.readInt();
            if (version != VERSION) {
                return createDefault(fallbackRotation);
            }
            int count = input.readInt();
            List<SfxAndroidInstruction> body = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String name = input.readUTF();
                try {
                    body.add(SfxAndroidInstruction.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                    // Skip stale instructions and let validation repair the script.
                }
            }
            int index = input.readInt();
            int fuelTicks = input.readInt();
            BlockFace rotation = normalizeRotation(BlockFace.valueOf(input.readUTF()));
            boolean paused = input.readBoolean();
            SfxAndroidRuntimeState runtimeState = SfxAndroidRuntimeState.valueOf(input.readUTF());
            long sleepingUntilTick = input.readLong();
            Object fuel = input.readObject();
            ItemStack[] outputs = new ItemStack[OUTPUT_SIZE];
            int outputCount = input.readInt();
            for (int i = 0; i < outputCount && i < OUTPUT_SIZE; i++) {
                Object raw = input.readObject();
                outputs[i] = raw instanceof ItemStack stack ? stack : null;
            }
            return new SfxAndroidState(body, index, fuelTicks, rotation, paused, runtimeState, sleepingUntilTick, fuel instanceof ItemStack stack ? stack : null, outputs);
        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
            return createDefault(fallbackRotation);
        }
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(buffer)) {
                output.writeInt(VERSION);
                output.writeInt(body.size());
                for (SfxAndroidInstruction instruction : body) {
                    output.writeUTF(instruction.name());
                }
                output.writeInt(index);
                output.writeInt(fuelTicks);
                output.writeUTF(rotation.name());
                output.writeBoolean(paused);
                output.writeUTF(runtimeState.name());
                output.writeLong(sleepingUntilTick);
                output.writeObject(fuelSlot == null ? null : fuelSlot.clone());
                output.writeInt(outputs.length);
                for (ItemStack outputStack : outputs) {
                    output.writeObject(outputStack == null ? null : outputStack.clone());
                }
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            return new byte[0];
        }
    }

    public List<SfxAndroidInstruction> body() {
        return List.copyOf(body);
    }

    public void setBody(List<SfxAndroidInstruction> body) {
        List<SfxAndroidInstruction> normalized = new ArrayList<>();
        if (body != null) {
            for (SfxAndroidInstruction instruction : body) {
                if (instruction != null && normalized.size() < MAX_BODY_LENGTH) {
                    normalized.add(instruction);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(SfxAndroidInstruction.WAIT);
        }
        this.body = normalized;
        if (index >= normalized.size()) {
            index = 0;
        }
    }

    public int index() {
        return index;
    }

    public void index(int index) {
        this.index = body.isEmpty() ? 0 : Math.floorMod(index, body.size());
    }

    public SfxAndroidInstruction currentInstruction() {
        return body.isEmpty() ? SfxAndroidInstruction.WAIT : body.get(Math.floorMod(index, body.size()));
    }

    public void advance() {
        index(index + 1);
    }

    public int fuelTicks() {
        return fuelTicks;
    }

    public void fuelTicks(int fuelTicks) {
        this.fuelTicks = Math.max(0, fuelTicks);
    }

    public void consumeFuelTick() {
        fuelTicks(Math.max(0, fuelTicks - 1));
    }

    public BlockFace rotation() {
        return rotation;
    }

    public void rotation(BlockFace rotation) {
        this.rotation = normalizeRotation(rotation);
    }

    public boolean paused() {
        return paused;
    }

    public void paused(boolean paused) {
        this.paused = paused;
        if (paused) {
            runtimeState = SfxAndroidRuntimeState.PAUSED;
        } else if (runtimeState == SfxAndroidRuntimeState.PAUSED) {
            runtimeState = SfxAndroidRuntimeState.ACTIVE;
        }
    }

    public SfxAndroidRuntimeState runtimeState() {
        return runtimeState;
    }

    public void runtimeState(SfxAndroidRuntimeState runtimeState) {
        this.runtimeState = runtimeState == null ? SfxAndroidRuntimeState.ACTIVE : runtimeState;
    }

    public long sleepingUntilTick() {
        return sleepingUntilTick;
    }

    public void sleepingUntilTick(long sleepingUntilTick) {
        this.sleepingUntilTick = Math.max(0L, sleepingUntilTick);
    }

    public ItemStack fuelSlot() {
        return cloneOrNull(fuelSlot);
    }

    public void fuelSlot(ItemStack fuelSlot) {
        this.fuelSlot = cloneOrNull(fuelSlot);
    }

    public ItemStack[] outputs() {
        return Arrays.stream(outputs).map(SfxAndroidState::cloneOrNull).toArray(ItemStack[]::new);
    }

    public void output(int index, ItemStack stack) {
        if (index >= 0 && index < outputs.length) {
            outputs[index] = cloneOrNull(stack);
        }
    }

    public boolean hasFreeOutputSpace() {
        for (ItemStack stack : outputs) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                return true;
            }
            if (stack.getAmount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public boolean pushOutput(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return true;
        }
        ItemStack remaining = stack.clone();
        for (int i = 0; i < outputs.length; i++) {
            ItemStack existing = outputs[i];
            if (existing == null || existing.getType().isAir() || existing.getAmount() <= 0) {
                int amount = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                ItemStack inserted = remaining.clone();
                inserted.setAmount(amount);
                outputs[i] = inserted;
                remaining.setAmount(remaining.getAmount() - amount);
                if (remaining.getAmount() <= 0) {
                    return true;
                }
                continue;
            }
            if (!existing.isSimilar(remaining) || existing.getAmount() >= existing.getMaxStackSize()) {
                continue;
            }
            int insert = Math.min(remaining.getAmount(), existing.getMaxStackSize() - existing.getAmount());
            existing.setAmount(existing.getAmount() + insert);
            remaining.setAmount(remaining.getAmount() - insert);
            if (remaining.getAmount() <= 0) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack[] normalizeOutputs(ItemStack[] input) {
        ItemStack[] result = new ItemStack[OUTPUT_SIZE];
        if (input != null) {
            for (int i = 0; i < Math.min(OUTPUT_SIZE, input.length); i++) {
                result[i] = cloneOrNull(input[i]);
            }
        }
        return result;
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        return stack.clone();
    }

    private static BlockFace normalizeRotation(BlockFace face) {
        if (face == BlockFace.NORTH || face == BlockFace.EAST || face == BlockFace.SOUTH || face == BlockFace.WEST) {
            return face;
        }
        return BlockFace.NORTH;
    }
}
