package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

final class LegacySfBootstrapSupport {
    private LegacySfBootstrapSupport() {
    }

    static ItemStack icon(Material material, Component name, String textureHash, Integer colorRgb) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (material == Material.PLAYER_HEAD && textureHash != null) {
                HeadTextures.apply(meta, textureHash);
            }
            if (colorRgb != null) {
                applyColor(meta, colorRgb);
            }
            meta.displayName(Text.noItalic(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void applyColor(ItemMeta meta, int rgb) {
        Color color = Color.fromRGB(rgb);
        if (meta instanceof LeatherArmorMeta leatherArmorMeta) {
            leatherArmorMeta.setColor(color);
        } else if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setColor(color);
        } else if (meta instanceof FireworkEffectMeta fireworkEffectMeta) {
            fireworkEffectMeta.setEffect(FireworkEffect.builder().withColor(color).build());
        }
    }
}
