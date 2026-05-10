package cc.theends6.sfx.api.menu;

import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.inventory.ItemStack;

public record SfxMenuButton(ItemStack icon, Consumer<SfxMenuClickContext> handler) {
    public SfxMenuButton {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(handler, "handler");
        icon = icon.clone();
    }

    public ItemStack icon() {
        return icon.clone();
    }
}
