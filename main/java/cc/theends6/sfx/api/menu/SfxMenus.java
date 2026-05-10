package cc.theends6.sfx.api.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public interface SfxMenus extends Listener {
    void openRoot(Player player, SfxMenu menu);

    void open(Player player, SfxMenu menu);

    void replace(Player player, SfxMenu menu);

    void close(Player player);

    void close(Player player, boolean restoreHistory);

    boolean hasHistory(Player player);

    void closeAll();
}
