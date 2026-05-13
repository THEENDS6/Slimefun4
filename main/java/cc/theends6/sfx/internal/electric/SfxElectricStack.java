package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class SfxElectricStack {
    private static final int MAX_SERIALIZED_ITEMSTACK_BYTES = 1_048_576;

    private final String itemId;
    private final Material material;
    private final int amount;
    private final ItemStack snapshot;

    private SfxElectricStack(String itemId, Material material, int amount, ItemStack snapshot) {
        if ((itemId == null || itemId.isBlank()) == (material == null)) {
            throw new IllegalArgumentException("Electric stack must be either an SFX item or a vanilla material.");
        }
        this.itemId = itemId;
        this.material = material;
        this.amount = Math.max(1, amount);
        if (snapshot != null) {
            ItemStack clone = snapshot.clone();
            clone.setAmount(this.amount);
            this.snapshot = clone;
        } else {
            this.snapshot = null;
        }
    }

    public static SfxElectricStack sfx(String itemId, int amount) {
        return new SfxElectricStack(Objects.requireNonNull(itemId, "itemId"), null, amount, null);
    }

    public static SfxElectricStack sfxSnapshot(String itemId, ItemStack stack) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(stack, "stack");
        if (stack.getType().isAir()) {
            throw new IllegalArgumentException("Cannot create an electric stack from air.");
        }
        return new SfxElectricStack(itemId, null, stack.getAmount(), stack);
    }

    public static SfxElectricStack vanilla(Material material, int amount) {
        return new SfxElectricStack(null, Objects.requireNonNull(material, "material"), amount, null);
    }

    public static SfxElectricStack snapshot(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.getType().isAir()) {
            throw new IllegalArgumentException("Cannot create an electric stack from air.");
        }
        return new SfxElectricStack(null, stack.getType(), stack.getAmount(), stack);
    }

    public static SfxElectricStack fromItemStack(SfxItems items, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        SfxItemMarker marker = items.readMarker(stack).orElse(null);
        if (marker != null) {
            return isCanonicalSfxStack(items, marker.itemId(), stack)
                    ? sfx(marker.itemId(), stack.getAmount())
                    : sfxSnapshot(marker.itemId(), stack);
        }
        if (hasPersistentVanillaState(stack)) {
            return snapshot(stack);
        }
        return vanilla(stack.getType(), stack.getAmount());
    }

    private static boolean isCanonicalSfxStack(SfxItems items, String itemId, ItemStack stack) {
        try {
            ItemStack canonical = items.create(itemId, stack.getAmount());
            canonical.setAmount(stack.getAmount());
            return stack.isSimilar(canonical);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean hasPersistentVanillaState(ItemStack stack) {
        return stack.hasItemMeta() || stack.getEnchantments().size() > 0 || stack.getType().getMaxDurability() > 0;
    }

    public static SfxElectricStack read(DataInputStream input) throws IOException {
        boolean sfx = input.readBoolean();
        String raw = input.readUTF();
        int amount = input.readInt();
        return sfx ? sfx(raw, amount) : vanilla(Material.valueOf(raw), amount);
    }

    public static SfxElectricStack readV2(DataInputStream input) throws IOException {
        int kind = input.readUnsignedByte();
        if (kind == 1) {
            return sfx(input.readUTF(), input.readInt());
        }
        if (kind == 2) {
            return vanilla(Material.valueOf(input.readUTF()), input.readInt());
        }
        if (kind == 3) {
            return snapshot(readSerializedItemStack(input));
        }
        if (kind == 4) {
            String itemId = input.readUTF();
            int amount = input.readInt();
            ItemStack stack = readSerializedItemStack(input);
            stack.setAmount(amount);
            return sfxSnapshot(itemId, stack);
        }
        throw new IOException("Unknown electric stack kind: " + kind);
    }

    private static ItemStack readSerializedItemStack(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_SERIALIZED_ITEMSTACK_BYTES) {
            throw new IOException("Invalid serialized ItemStack length: " + length);
        }
        byte[] payload = input.readNBytes(length);
        try (BukkitObjectInputStream objectInput = new BukkitObjectInputStream(new ByteArrayInputStream(payload))) {
            Object object = objectInput.readObject();
            if (!(object instanceof ItemStack stack)) {
                throw new IOException("Serialized electric stack is not an ItemStack.");
            }
            return stack;
        } catch (ClassNotFoundException exception) {
            throw new IOException("Failed to deserialize electric ItemStack", exception);
        }
    }

    public void write(DataOutputStream output) throws IOException {
        output.writeBoolean(isSfxItem());
        output.writeUTF(isSfxItem() ? itemId : material.name());
        output.writeInt(amount);
    }

    public void writeV2(DataOutputStream output) throws IOException {
        if (isSfxItem()) {
            if (snapshot == null) {
                output.writeByte(1);
                output.writeUTF(itemId);
                output.writeInt(amount);
                return;
            }
            output.writeByte(4);
            output.writeUTF(itemId);
            output.writeInt(amount);
            writeSerializedItemStack(output, snapshot);
            return;
        }
        if (snapshot == null) {
            output.writeByte(2);
            output.writeUTF(material.name());
            output.writeInt(amount);
            return;
        }
        output.writeByte(3);
        writeSerializedItemStack(output, snapshot);
    }

    private static void writeSerializedItemStack(DataOutputStream output, ItemStack stack) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream objectOutput = new BukkitObjectOutputStream(buffer)) {
            objectOutput.writeObject(stack.clone());
        }
        byte[] payload = buffer.toByteArray();
        output.writeInt(payload.length);
        output.write(payload);
    }

    public boolean isSfxItem() {
        return itemId != null;
    }

    public boolean hasSnapshot() {
        return snapshot != null;
    }

    public String itemId() {
        return itemId;
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public ItemStack snapshot() {
        if (snapshot == null) {
            return null;
        }
        ItemStack clone = snapshot.clone();
        clone.setAmount(amount);
        return clone;
    }

    public boolean matches(SfxRecipeSlot slot) {
        if (slot == null || slot.isEmpty()) {
            return false;
        }
        if (amount < slot.amount()) {
            return false;
        }
        if (slot.isSfxItem()) {
            return isSfxItem() && itemId.equals(slot.sfxItemId());
        }
        return !isSfxItem() && material == slot.material();
    }

    public boolean canMerge(SfxElectricStack other, SfxItems items) {
        if (other == null) {
            return true;
        }
        if (!sameKind(other)) {
            return false;
        }
        return other.amount + amount <= Math.min(toItemStack(items).getMaxStackSize(), other.toItemStack(items).getMaxStackSize());
    }

    public boolean sameKind(SfxElectricStack other) {
        if (other == null || isSfxItem() != other.isSfxItem()) {
            return false;
        }
        if (isSfxItem()) {
            if (!itemId.equals(other.itemId)) {
                return false;
            }
            if (snapshot == null && other.snapshot == null) {
                return true;
            }
            return snapshot != null && other.snapshot != null && snapshot.isSimilar(other.snapshot);
        }
        if (material != other.material) {
            return false;
        }
        if (snapshot == null && other.snapshot == null) {
            return true;
        }
        return snapshot != null && other.snapshot != null && snapshot.isSimilar(other.snapshot);
    }

    public SfxElectricStack copyWithAmount(int newAmount) {
        return new SfxElectricStack(itemId, material, newAmount, snapshot);
    }

    public ItemStack toItemStack(SfxItems items) {
        if (snapshot != null) {
            ItemStack clone = snapshot.clone();
            clone.setAmount(amount);
            return clone;
        }
        if (isSfxItem()) {
            return Objects.requireNonNull(items, "items").create(itemId, amount);
        }
        return new ItemStack(material, amount);
    }
}
