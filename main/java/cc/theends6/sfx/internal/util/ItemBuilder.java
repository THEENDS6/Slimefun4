package cc.theends6.sfx.internal.util;

import cc.theends6.sfx.api.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemBuilder {
    private final ItemStack item;
    private final List<Component> lore = new ArrayList<>();
    private Component name;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder name(String text) {
        this.name = Text.renderFlexible(text);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        this.lore.addAll(Text.lore(lines));
        return this;
    }

    public ItemBuilder loreComponents(List<Component> components) {
        this.lore.addAll(components);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(99, amount)));
        return this;
    }

    public ItemBuilder editMeta(Consumer<ItemMeta> editor) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            editor.accept(meta);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        return editMeta(meta -> meta.addItemFlags(flags));
    }

    public ItemStack build() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(name);
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
