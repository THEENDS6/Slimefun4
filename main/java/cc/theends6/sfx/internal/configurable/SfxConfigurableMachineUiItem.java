package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.internal.ui.SfxUiItems;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

record SfxConfigurableMachineUiItem(Material material, String name, List<String> lore, String nameKey, String loreKey, boolean glint) {
    SfxConfigurableMachineUiItem {
        Objects.requireNonNull(material, "material");
        name = name == null ? " " : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
        nameKey = blankToNull(nameKey);
        loreKey = blankToNull(loreKey);
    }

    ItemStack toItemStack(SfxLocalization localization) {
        Component displayName = Text.renderFlexible(localizedName(localization));
        List<Component> lines = localizedLore(localization).stream().map(Text::renderFlexible).toList();
        ItemStack stack = SfxUiItems.named(material, displayName, lines);
        if (glint && stack.hasItemMeta()) {
            var meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String localizedName(SfxLocalization localization) {
        if (nameKey != null) {
            return localization.requiredText(nameKey);
        }
        if (name == null || name.isBlank()) {
            return " ";
        }
        throw new IllegalStateException("Configurable UI item is missing name-key for non-blank name: " + name);
    }

    private List<String> localizedLore(SfxLocalization localization) {
        if (loreKey != null) {
            return localization.requiredList(loreKey);
        }
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        throw new IllegalStateException("Configurable UI item is missing lore-key for non-empty lore");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
