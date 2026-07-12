package cc.theends6.sfx.api.behavior;

import org.bukkit.Material;

public record SfxAutoBrewerInputContext(
        int rawSlot,
        Material material,
        String sfxItemId,
        boolean empty,
        boolean hasItemMeta,
        boolean brewingIngredient,
        boolean validPotion
) {
}
