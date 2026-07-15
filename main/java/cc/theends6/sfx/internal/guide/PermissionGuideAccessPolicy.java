package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.guide.SfxGuideAccessPolicy;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemCategory;
import org.bukkit.entity.Player;

public final class PermissionGuideAccessPolicy implements SfxGuideAccessPolicy {
    @Override
    public boolean canOpen(Player player, GuideMode mode) {
        return mode == GuideMode.CHEAT ? player.hasPermission("sfx.command.cheatguide") : player.hasPermission("sfx.command.guide");
    }

    @Override
    public boolean canViewItem(Player player, GuideMode mode, SfxItemDefinition item) {
        return !item.hidden() && hasPermission(player, item.permission());
    }

    @Override
    public boolean canReceiveFromCheatGuide(Player player, SfxItemDefinition item) {
        return player.hasPermission("sfx.command.cheatguide")
                && item.giveable()
                && hasPermission(player, item.permission())
                && hasPermission(player, item.usePermission());
    }

    @Override
    public boolean canViewCategory(Player player, GuideMode mode, SfxItemCategory category) {
        return !category.hidden() && hasPermission(player, category.permission());
    }

    private static boolean hasPermission(Player player, String permission) {
        return permission == null || player.hasPermission(permission);
    }
}
