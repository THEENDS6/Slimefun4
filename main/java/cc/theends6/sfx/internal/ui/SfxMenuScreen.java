package cc.theends6.sfx.internal.ui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public interface SfxMenuScreen {
    SfxMenuLayout layout();

    void render(Player player, Inventory inventory);

    default void onClick(SfxClickContext context) {
    }

    default void onDrag(SfxDragContext context) {
    }

    default void onClose(Player player) {
    }
}
