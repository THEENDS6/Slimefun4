package cc.theends6.sfx.api.guide;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemCategory;
import org.bukkit.entity.Player;

public interface SfxGuideAccessPolicy {
    boolean canOpen(Player player, GuideMode mode);

    default boolean canViewCategory(Player player, GuideMode mode, SfxItemCategory category) {
        return !category.hidden();
    }

    boolean canViewItem(Player player, GuideMode mode, SfxItemDefinition item);

    boolean canReceiveFromCheatGuide(Player player, SfxItemDefinition item);
}
