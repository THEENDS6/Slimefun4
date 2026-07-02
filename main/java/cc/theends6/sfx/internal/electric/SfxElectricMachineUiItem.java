package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.ui.SfxUiItems;
import cc.theends6.sfx.internal.util.Text;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

record SfxElectricMachineUiItem(Material material, String name, List<String> lore, boolean glint) {
    SfxElectricMachineUiItem {
        Objects.requireNonNull(material, "material");
        name = name == null ? " " : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    SfxElectricMachineUiItem(Material material, String name, List<String> lore) {
        this(material, name, lore, false);
    }

    ItemStack toItemStack() {
        return toItemStack(Map.of());
    }

    ItemStack toItemStack(Map<String, ?> placeholders) {
        return toItemStack(material, placeholders);
    }

    ItemStack toItemStack(Material materialOverride, Map<String, ?> placeholders) {
        Map<String, ?> values = placeholders == null ? Map.of() : new LinkedHashMap<>(placeholders);
        Component displayName = Text.renderFlexible(applyPlaceholders(name, values));
        List<Component> lines = lore.stream().map(line -> Text.renderFlexible(applyPlaceholders(line, values))).toList();
        ItemStack stack = SfxUiItems.named(materialOverride == null ? material : materialOverride, displayName, lines);
        if (glint && stack.hasItemMeta()) {
            var meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
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
