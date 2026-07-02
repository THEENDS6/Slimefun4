package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.ui.SfxUiItems;
import cc.theends6.sfx.internal.util.Text;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

record SfxElectricMachineUiItem(Material material, String name, List<String> lore) {
    SfxElectricMachineUiItem {
        Objects.requireNonNull(material, "material");
        name = name == null ? " " : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    ItemStack toItemStack() {
        Component displayName = Text.renderFlexible(name);
        List<Component> lines = lore.stream().map(Text::renderFlexible).toList();
        return SfxUiItems.named(material, displayName, lines);
    }
}
