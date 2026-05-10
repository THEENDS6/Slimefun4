package cc.theends6.sfx.api.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public record SfxMenuClickContext(Player player, int slot, ClickType clickType, SfxMenus menus) {
    public boolean isShiftClick() {
        return clickType != null && clickType.isShiftClick();
    }
}
