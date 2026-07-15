package cc.theends6.sfx.api.ui;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SfxUiItems {
    private SfxUiItems() {
    }

    public static ItemStack named(Material material, Component name) {
        return named(material, name, List.of());
    }

    public static ItemStack named(Material material, Component name, List<Component> lore) {
        Objects.requireNonNull(material, "material");
        if (material.isAir()) {
            throw new IllegalArgumentException("UI item material must not be air");
        }
        return named(new ItemStack(material), name, lore);
    }

    public static ItemStack named(ItemStack base, Component name) {
        return named(base, name, List.of());
    }

    public static ItemStack named(ItemStack base, Component name, List<Component> lore) {
        Objects.requireNonNull(base, "base");
        if (base.getType().isAir()) {
            throw new IllegalArgumentException("UI item base must not be air");
        }
        ItemStack stack = base.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name == null ? Component.text(" ") : name);
            meta.lore(lore == null ? List.of() : lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack blankPane(Material material) {
        return named(material, Component.text(" "), List.of());
    }
}
