package cc.theends6.sfx.api.item;

import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;

public record SfxRecipeSlot(Material material, String sfxItemId, int amount) {
    public SfxRecipeSlot {
        boolean empty = material == null && (sfxItemId == null || sfxItemId.isBlank());
        if (material != null && sfxItemId != null && !sfxItemId.isBlank()) {
            throw new IllegalArgumentException("Recipe slot cannot be both vanilla and SFX item.");
        }
        sfxItemId = sfxItemId == null ? null : SfxItemDefinition.normalizeId(sfxItemId);
        amount = empty ? 0 : Math.max(1, amount);
    }

    public static SfxRecipeSlot empty() {
        return new SfxRecipeSlot(null, null, 0);
    }

    public static SfxRecipeSlot vanilla(Material material) {
        return new SfxRecipeSlot(Objects.requireNonNull(material, "material"), null, 1);
    }

    public static SfxRecipeSlot vanilla(Material material, int amount) {
        return new SfxRecipeSlot(Objects.requireNonNull(material, "material"), null, amount);
    }

    public static SfxRecipeSlot sfx(String itemId) {
        return new SfxRecipeSlot(null, itemId, 1);
    }

    public static SfxRecipeSlot sfx(String itemId, int amount) {
        return new SfxRecipeSlot(null, itemId, amount);
    }

    public boolean isEmpty() {
        return material == null && sfxItemId == null;
    }

    public boolean isSfxItem() {
        return sfxItemId != null;
    }

    public Optional<String> sfxId() {
        return Optional.ofNullable(sfxItemId);
    }
}
