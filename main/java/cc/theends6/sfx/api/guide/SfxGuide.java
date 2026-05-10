package cc.theends6.sfx.api.guide;

import org.bukkit.entity.Player;

public interface SfxGuide {
    void open(Player player, GuideMode mode);

    void openSettings(Player player, GuideMode mode);
}
