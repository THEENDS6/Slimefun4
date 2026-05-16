package cc.theends6.sfx.internal.cargo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

final class SfxCargoNodeState {
    static final int FILTER_SIZE = 9;

    BlockFace attachedFace = BlockFace.NORTH;
    int channel = 0;
    SfxCargoFilterMode filterMode = SfxCargoFilterMode.BLACKLIST;
    boolean matchLore = false;
    boolean smartFill = true;
    SfxCargoDistributionMode distributionMode = SfxCargoDistributionMode.CLASSIC;
    int maxItemsPerCycle = 128;
    int maxDistinctTypes = 8;
    int priority = 1;
    int roundRobinCursor = 0;
    boolean enabled = true;
    String selectedRecipeKey = "";
    ItemStack[] filterItems = new ItemStack[FILTER_SIZE];

    static SfxCargoNodeState defaultFor(SfxCargoComponentType type, BlockFace attachedFace) {
        SfxCargoNodeState state = new SfxCargoNodeState();
        if (attachedFace != null) {
            state.attachedFace = attachedFace;
        }
        if (type == SfxCargoComponentType.ADVANCED_INPUT_NODE) {
            state.distributionMode = SfxCargoDistributionMode.EVEN;
        }
        if (type == SfxCargoComponentType.ADVANCED_OUTPUT_NODE) {
            state.priority = 1;
        }
        return state;
    }

    static SfxCargoNodeState decode(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return new SfxCargoNodeState();
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(blob))) {
            int version = input.readInt();
            SfxCargoNodeState state = new SfxCargoNodeState();
            state.attachedFace = safeFace(input.readUTF());
            state.channel = clamp(input.readInt(), 0, 15);
            state.filterMode = safeFilter(input.readUTF());
            state.matchLore = input.readBoolean();
            state.smartFill = input.readBoolean();
            state.distributionMode = safeDistribution(input.readUTF());
            state.maxItemsPerCycle = clamp(input.readInt(), 1, 128);
            state.maxDistinctTypes = clamp(input.readInt(), 1, 16);
            state.priority = clamp(input.readInt(), 1, 16);
            state.roundRobinCursor = Math.max(0, input.readInt());
            if (version >= 2) {
                state.enabled = input.readBoolean();
                state.selectedRecipeKey = input.readUTF();
            }
            int filterLength = input.readInt();
            state.filterItems = new ItemStack[FILTER_SIZE];
            for (int i = 0; i < filterLength; i++) {
                boolean present = input.readBoolean();
                ItemStack stack = null;
                if (present) {
                    int length = input.readInt();
                    byte[] payload = input.readNBytes(length);
                    try (BukkitObjectInputStream objectInput = new BukkitObjectInputStream(new ByteArrayInputStream(payload))) {
                        Object object = objectInput.readObject();
                        stack = object instanceof ItemStack itemStack ? itemStack : null;
                    }
                }
                if (i < FILTER_SIZE) {
                    state.filterItems[i] = stack;
                }
            }
            return state;
        } catch (IOException | ClassNotFoundException | RuntimeException ignored) {
            return new SfxCargoNodeState();
        }
    }

    byte[] encode() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(2);
                output.writeUTF(attachedFace.name());
                output.writeInt(clamp(channel, 0, 15));
                output.writeUTF(filterMode.name());
                output.writeBoolean(matchLore);
                output.writeBoolean(smartFill);
                output.writeUTF(distributionMode.name());
                output.writeInt(clamp(maxItemsPerCycle, 1, 128));
                output.writeInt(clamp(maxDistinctTypes, 1, 16));
                output.writeInt(clamp(priority, 1, 16));
                output.writeInt(Math.max(0, roundRobinCursor));
                output.writeBoolean(enabled);
                output.writeUTF(selectedRecipeKey == null ? "" : selectedRecipeKey);
                output.writeInt(filterItems.length);
                for (ItemStack stack : filterItems) {
                    output.writeBoolean(stack != null && !stack.getType().isAir());
                    if (stack != null && !stack.getType().isAir()) {
                        ByteArrayOutputStream itemBuffer = new ByteArrayOutputStream();
                        try (BukkitObjectOutputStream objectOutput = new BukkitObjectOutputStream(itemBuffer)) {
                            ItemStack clone = stack.clone();
                            clone.setAmount(Math.max(1, clone.getAmount()));
                            objectOutput.writeObject(clone);
                        }
                        byte[] payload = itemBuffer.toByteArray();
                        output.writeInt(payload.length);
                        output.write(payload);
                    }
                }
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode cargo node state", exception);
        }
    }

    private static SfxCargoFilterMode safeFilter(String raw) {
        try {
            return SfxCargoFilterMode.valueOf(raw);
        } catch (RuntimeException ignored) {
            return SfxCargoFilterMode.BLACKLIST;
        }
    }

    private static SfxCargoDistributionMode safeDistribution(String raw) {
        try {
            return SfxCargoDistributionMode.valueOf(raw);
        } catch (RuntimeException ignored) {
            return SfxCargoDistributionMode.CLASSIC;
        }
    }

    private static BlockFace safeFace(String raw) {
        try {
            return BlockFace.valueOf(raw);
        } catch (RuntimeException ignored) {
            return BlockFace.NORTH;
        }
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
