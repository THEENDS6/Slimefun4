package cc.theends6.sfx.api.machine.manual;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItems;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class SfxManualMachineOutput {
    private final Material material;
    private final String sfxItemId;
    private final int amount;

    private SfxManualMachineOutput(Material material, String sfxItemId, int amount) {
        if (material == null && (sfxItemId == null || sfxItemId.isBlank())) {
            throw new IllegalArgumentException("Manual machine output requires a vanilla material or SFX item id.");
        }
        if (material != null && sfxItemId != null && !sfxItemId.isBlank()) {
            throw new IllegalArgumentException("Manual machine output cannot be both vanilla and SFX item.");
        }
        this.material = material;
        this.sfxItemId = sfxItemId == null ? null : SfxItemDefinition.normalizeId(sfxItemId);
        this.amount = Math.max(1, amount);
    }

    public static SfxManualMachineOutput vanilla(Material material, int amount) {
        return new SfxManualMachineOutput(Objects.requireNonNull(material, "material"), null, amount);
    }

    public static SfxManualMachineOutput sfx(String itemId, int amount) {
        return new SfxManualMachineOutput(null, itemId, amount);
    }

    public boolean isSfxItem() {
        return sfxItemId != null;
    }

    public Material material() {
        return material;
    }

    public String sfxItemId() {
        return sfxItemId;
    }

    public int amount() {
        return amount;
    }

    public ItemStack create(SfxItems items) {
        return isSfxItem() ? items.create(sfxItemId, amount) : new ItemStack(material, amount);
    }
}
