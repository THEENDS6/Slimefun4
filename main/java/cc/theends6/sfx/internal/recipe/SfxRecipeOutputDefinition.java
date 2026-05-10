package cc.theends6.sfx.internal.recipe;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import java.util.Objects;
import org.bukkit.Material;

public record SfxRecipeOutputDefinition(String sfxItemId, Material material, int amount, Double chance) {

    public SfxRecipeOutputDefinition {
        if ((sfxItemId == null || sfxItemId.isBlank()) && material == null) {
            throw new IllegalArgumentException("Recipe output must declare either an SFX item id or a vanilla material.");
        }
        sfxItemId = sfxItemId == null || sfxItemId.isBlank() ? null : SfxItemDefinition.normalizeId(sfxItemId);
        amount = Math.max(1, amount);
        if (chance != null && (chance <= 0.0D || chance > 1.0D)) {
            throw new IllegalArgumentException("Recipe output chance must be within (0, 1].");
        }
    }

    public static SfxRecipeOutputDefinition sfx(String id, int amount) {
        return new SfxRecipeOutputDefinition(id, null, amount, null);
    }

    public static SfxRecipeOutputDefinition sfx(String id, int amount, Double chance) {
        return new SfxRecipeOutputDefinition(id, null, amount, chance);
    }

    public static SfxRecipeOutputDefinition vanilla(Material material, int amount) {
        return new SfxRecipeOutputDefinition(null, Objects.requireNonNull(material, "material"), amount, null);
    }

    public static SfxRecipeOutputDefinition vanilla(Material material, int amount, Double chance) {
        return new SfxRecipeOutputDefinition(null, Objects.requireNonNull(material, "material"), amount, chance);
    }

    public boolean isSfxItem() {
        return sfxItemId != null;
    }

    public boolean isVanilla() {
        return material != null;
    }
}
