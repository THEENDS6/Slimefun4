package cc.theends6.sfx.internal.playerdata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

final class SfxItemStackCodec {
    private static final int MAGIC = 0x53465849; 
    private static final int VERSION = 1;
    private static final int MAX_CONTENTS = 1024;
    private static final int MAX_ITEM_BYTES = 1_048_576;

    private SfxItemStackCodec() {
    }

    static byte[] encode(ItemStack[] contents) throws IOException {
        if (contents == null || contents.length > MAX_CONTENTS) {
            throw new IOException("Invalid SFX inventory size: " + (contents == null ? -1 : contents.length));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(output)) {
            stream.writeInt(MAGIC);
            stream.writeInt(VERSION);
            stream.writeInt(contents.length);
            for (ItemStack stack : contents) {
                if (stack == null || stack.getType().isAir()) {
                    stream.writeInt(-1);
                    continue;
                }
                byte[] itemBytes = stack.serializeAsBytes();
                if (itemBytes.length > MAX_ITEM_BYTES) {
                    throw new IOException("Serialized item exceeds " + MAX_ITEM_BYTES + " bytes");
                }
                stream.writeInt(itemBytes.length);
                stream.write(itemBytes);
            }
        }
        return output.toByteArray();
    }

    static ItemStack[] decode(byte[] bytes) throws IOException, ClassNotFoundException {
        if (bytes == null || bytes.length < Integer.BYTES) {
            throw new IOException("Invalid empty SFX inventory payload");
        }
        try (DataInputStream header = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (header.readInt() == MAGIC) {
                return decodeModern(header);
            }
        }
        return decodeLegacy(bytes);
    }

    private static ItemStack[] decodeModern(DataInputStream stream) throws IOException {
        int version = stream.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported SFX inventory codec version: " + version);
        }
        int size = checkedSize(stream.readInt());
        ItemStack[] contents = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            int length = stream.readInt();
            if (length == -1) {
                continue;
            }
            if (length < 0 || length > MAX_ITEM_BYTES) {
                throw new IOException("Invalid serialized item length: " + length);
            }
            byte[] itemBytes = stream.readNBytes(length);
            if (itemBytes.length != length) {
                throw new IOException("Truncated serialized item payload");
            }
            try {
                contents[i] = ItemStack.deserializeBytes(itemBytes);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid serialized ItemStack at slot " + i, exception);
            }
        }
        return contents;
    }

    


    private static ItemStack[] decodeLegacy(byte[] bytes) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream stream = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            int size = checkedSize(stream.readInt());
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                Object raw = stream.readObject();
                contents[i] = raw instanceof ItemStack stack ? stack : null;
            }
            return contents;
        }
    }

    private static int checkedSize(int size) throws IOException {
        if (size < 0 || size > MAX_CONTENTS) {
            throw new IOException("Invalid SFX inventory size: " + size);
        }
        return size;
    }
}
