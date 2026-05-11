package cc.theends6.sfx.internal.playerdata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

final class SfxItemStackCodec {
    private SfxItemStackCodec() {
    }

    static byte[] encode(ItemStack[] contents) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(output)) {
            stream.writeInt(contents.length);
            for (ItemStack stack : contents) {
                stream.writeObject(stack);
            }
        }
        return output.toByteArray();
    }

    static ItemStack[] decode(byte[] bytes) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream stream = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            int size = stream.readInt();
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                Object raw = stream.readObject();
                contents[i] = raw instanceof ItemStack stack ? stack : null;
            }
            return contents;
        }
    }
}
