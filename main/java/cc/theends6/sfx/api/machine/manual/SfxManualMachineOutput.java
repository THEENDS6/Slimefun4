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
    private final Double chance;

    private SfxManualMachineOutput(Material material, String sfxItemId, int amount, Double chance) {
        if (material == null && (sfxItemId == null || sfxItemId.isBlank())) {
            throw new IllegalArgumentException("Manual machine output requires a vanilla material or SFX item id.");
        }
        if (material != null && sfxItemId != null && !sfxItemId.isBlank()) {
            throw new IllegalArgumentException("Manual machine output cannot be both vanilla and SFX item.");
        }
        this.material = material;
        this.sfxItemId = sfxItemId == null ? null : SfxItemDefinition.normalizeId(sfxItemId);
        this.amount = Math.max(1, amount);
        if (chance != null && (!Double.isFinite(chance) || chance <= 0.0D)) {
            throw new IllegalArgumentException("Manual machine output chance must be positive and finite.");
        }
        this.chance = chance;
    }

    public static SfxManualMachineOutput vanilla(Material material, int amount) {
        return vanilla(material, amount, null);
    }

    public static SfxManualMachineOutput vanilla(Material material, int amount, Double chance) {
        return new SfxManualMachineOutput(Objects.requireNonNull(material, "material"), null, amount, chance);
    }

    public static SfxManualMachineOutput sfx(String itemId, int amount) {
        return sfx(itemId, amount, null);
    }

    public static SfxManualMachineOutput sfx(String itemId, int amount, Double chance) {
        return new SfxManualMachineOutput(null, itemId, amount, chance);
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

    public Double chance() {
        return chance;
    }

    public ItemStack create(SfxItems items) {
        return isSfxItem() ? items.create(sfxItemId, amount) : new ItemStack(material, amount);
    }
}
