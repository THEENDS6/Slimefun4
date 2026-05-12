package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.Objects;

public record SfxElectricRecipe(String key, SfxRecipeSlot input, SfxElectricStack output, int baseTicks) {
    public SfxElectricRecipe {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        baseTicks = Math.max(1, baseTicks);
    }
}
