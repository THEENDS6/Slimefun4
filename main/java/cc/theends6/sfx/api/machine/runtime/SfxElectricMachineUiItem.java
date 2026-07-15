package cc.theends6.sfx.api.machine.runtime;

import cc.theends6.sfx.api.ui.SfxUiItems;
import cc.theends6.sfx.api.localization.SfxLocalizationView;
import cc.theends6.sfx.api.text.Text;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record SfxElectricMachineUiItem(Material material, String name, List<String> lore, String nameKey, String loreKey, boolean glint) {
    public SfxElectricMachineUiItem {
        Objects.requireNonNull(material, "material");
        name = name == null ? " " : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
        nameKey = blankToNull(nameKey);
        loreKey = blankToNull(loreKey);
    }

    public SfxElectricMachineUiItem(Material material, String name, List<String> lore, boolean glint) {
        this(material, name, lore, null, null, glint);
    }

    public SfxElectricMachineUiItem(Material material, String name, List<String> lore) {
        this(material, name, lore, null, null, false);
    }

    public ItemStack toItemStack() {
        return toItemStack((SfxLocalizationView) null, Map.of());
    }

    public ItemStack toItemStack(Map<String, ?> placeholders) {
        return toItemStack((SfxLocalizationView) null, placeholders);
    }

    public ItemStack toItemStack(SfxLocalizationView localization, Map<String, ?> placeholders) {
        return toItemStack(material, localization, placeholders);
    }

    public ItemStack toItemStack(Material materialOverride, Map<String, ?> placeholders) {
        return toItemStack(materialOverride, null, placeholders);
    }

    public ItemStack toItemStack(Material materialOverride, SfxLocalizationView localization, Map<String, ?> placeholders) {
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

    private String localizedName(SfxLocalizationView localization) {
        if (localization == null) {
            return name;
        }
        if (nameKey != null) {
            return localization.requiredText(nameKey);
        }
        if (name == null || name.isBlank()) {
            return " ";
        }
        throw new IllegalStateException("Electric UI item is missing name-key for non-blank name: " + name);
    }

    private List<String> localizedLore(SfxLocalizationView localization) {
        if (localization == null) {
            return lore;
        }
        if (loreKey != null) {
            return localization.requiredList(loreKey);
        }
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        throw new IllegalStateException("Electric UI item is missing lore-key for non-empty lore");
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
