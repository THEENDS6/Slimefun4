package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.ui.SfxUiItems;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

record SfxElectricMachineUiItem(Material material, String name, List<String> lore, String nameKey, String loreKey, boolean glint) {
    SfxElectricMachineUiItem {
        Objects.requireNonNull(material, "material");
        name = name == null ? " " : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
        nameKey = blankToNull(nameKey);
        loreKey = blankToNull(loreKey);
    }

    SfxElectricMachineUiItem(Material material, String name, List<String> lore, boolean glint) {
        this(material, name, lore, null, null, glint);
    }

    SfxElectricMachineUiItem(Material material, String name, List<String> lore) {
        this(material, name, lore, null, null, false);
    }

    ItemStack toItemStack() {
        return toItemStack((SfxLocalization) null, Map.of());
    }

    ItemStack toItemStack(Map<String, ?> placeholders) {
        return toItemStack((SfxLocalization) null, placeholders);
    }

    ItemStack toItemStack(SfxLocalization localization, Map<String, ?> placeholders) {
        return toItemStack(material, localization, placeholders);
    }

    ItemStack toItemStack(Material materialOverride, Map<String, ?> placeholders) {
        return toItemStack(materialOverride, null, placeholders);
    }

    ItemStack toItemStack(Material materialOverride, SfxLocalization localization, Map<String, ?> placeholders) {
        Map<String, ?> values = placeholders == null ? Map.of() : new LinkedHashMap<>(placeholders);
        Component displayName = Text.renderFlexible(applyPlaceholders(localizedName(localization), values));
        List<Component> lines = localizedLore(localization).stream().map(line -> Text.renderFlexible(applyPlaceholders(line, values))).toList();
        ItemStack stack = SfxUiItems.named(materialOverride == null ? material : materialOverride, displayName, lines);
        if (glint && stack.hasItemMeta()) {
            var meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String localizedName(SfxLocalization localization) {
        if (localization == null) {
            return name;
        }
        if (nameKey != null) {
            return localization.requiredText(nameKey);
        }
        return localization.textOrLiteral(name, "electric-ui.name");
    }

    private List<String> localizedLore(SfxLocalization localization) {
        if (localization == null) {
            return lore;
        }
        if (loreKey != null) {
            return localization.requiredList(loreKey);
        }
        return localization.listOrLiterals(lore, "electric-ui.lore");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String applyPlaceholders(String text, Map<String, ?> placeholders) {
        if (text == null || text.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
