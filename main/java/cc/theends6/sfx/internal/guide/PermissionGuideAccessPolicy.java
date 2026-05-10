package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.guide.SfxGuideAccessPolicy;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import org.bukkit.entity.Player;

public final class PermissionGuideAccessPolicy implements SfxGuideAccessPolicy {
    @Override
    public boolean canOpen(Player player, GuideMode mode) {
        return mode == GuideMode.CHEAT ? player.hasPermission("sfx.command.cheatguide") : player.hasPermission("sfx.command.guide");
    }

    @Override
    public boolean canViewItem(Player player, GuideMode mode, SfxItemDefinition item) {
        return !item.hidden();
    }

    @Override
    public boolean canReceiveFromCheatGuide(Player player, SfxItemDefinition item) {
        return player.hasPermission("sfx.command.cheatguide") && item.giveable();
    }
}
