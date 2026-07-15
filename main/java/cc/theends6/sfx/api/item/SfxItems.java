package cc.theends6.sfx.api.item;

import cc.theends6.sfx.api.guide.GuideMode;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface SfxItems {
    ItemStack create(String id);

    ItemStack create(String id, int amount);

    ItemStack create(SfxItemDefinition definition, int amount);

    ItemStack createGuideBook(GuideMode mode);

    Optional<SfxItemMarker> readMarker(ItemStack item);

    Optional<SfxItemDefinition> definition(String id);

    default boolean canUse(Player player, String id) {
        return definition(id)
                .map(definition -> definition.usePermission() == null
                        || player.hasPermission(definition.usePermission()))
                .orElse(true);
    }

    Optional<GuideMode> readGuideMode(ItemStack item);

    boolean isSfxItem(ItemStack item);

    boolean matches(ItemStack item, SfxRecipeSlot slot);

    void give(Player player, ItemStack item);
}
