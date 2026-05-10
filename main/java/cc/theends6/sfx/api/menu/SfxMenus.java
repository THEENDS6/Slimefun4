package cc.theends6.sfx.api.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public interface SfxMenus extends Listener {
    void open(Player player, SfxMenu menu);

    void close(Player player);

    void closeAll();
}
