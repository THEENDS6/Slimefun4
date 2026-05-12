package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class SfxElectricStack {
    private final String itemId;
    private final Material material;
    private final int amount;

    private SfxElectricStack(String itemId, Material material, int amount) {
        if ((itemId == null || itemId.isBlank()) == (material == null)) {
            throw new IllegalArgumentException("Electric stack must be either an SFX item or a vanilla material.");
        }
        this.itemId = itemId;
        this.material = material;
        this.amount = Math.max(1, amount);
    }

    public static SfxElectricStack sfx(String itemId, int amount) {
        return new SfxElectricStack(Objects.requireNonNull(itemId, "itemId"), null, amount);
    }

    public static SfxElectricStack vanilla(Material material, int amount) {
        return new SfxElectricStack(null, Objects.requireNonNull(material, "material"), amount);
    }

    public static SfxElectricStack fromItemStack(SfxItems items, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        SfxItemMarker marker = items.readMarker(stack).orElse(null);
        if (marker != null) {
            return sfx(marker.itemId(), stack.getAmount());
        }
        return vanilla(stack.getType(), stack.getAmount());
    }

    public static SfxElectricStack read(DataInputStream input) throws IOException {
        boolean sfx = input.readBoolean();
        String raw = input.readUTF();
        int amount = input.readInt();
        return sfx ? sfx(raw, amount) : vanilla(Material.valueOf(raw), amount);
    }

    public void write(DataOutputStream output) throws IOException {
        output.writeBoolean(isSfxItem());
        output.writeUTF(isSfxItem() ? itemId : material.name());
        output.writeInt(amount);
    }

    public boolean isSfxItem() {
        return itemId != null;
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
        return other.amount + amount <= toItemStack(items).getMaxStackSize();
    }

    public boolean sameKind(SfxElectricStack other) {
        if (other == null || isSfxItem() != other.isSfxItem()) {
            return false;
        }
        if (isSfxItem()) {
            return itemId.equals(other.itemId);
        }
        return material == other.material;
    }

    public SfxElectricStack copyWithAmount(int newAmount) {
        return isSfxItem() ? sfx(itemId, newAmount) : vanilla(material, newAmount);
    }

    public ItemStack toItemStack(SfxItems items) {
        return isSfxItem() ? items.create(itemId, amount) : new ItemStack(material, amount);
    }
}
