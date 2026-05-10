package cc.theends6.sfx.api.guide;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import org.bukkit.entity.Player;

public interface SfxGuideAccessPolicy {
    boolean canOpen(Player player, GuideMode mode);

    boolean canViewItem(Player player, GuideMode mode, SfxItemDefinition item);

    boolean canReceiveFromCheatGuide(Player player, SfxItemDefinition item);
}
