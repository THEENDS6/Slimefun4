package cc.theends6.sfx.api.item;

import java.util.Collection;
import java.util.Optional;

public interface SfxItemRegistry {
    void registerCategory(SfxItemCategory category);

    void registerItem(SfxItemDefinition definition);

    Optional<SfxItemCategory> category(String id);

    Optional<SfxItemDefinition> item(String id);

    Collection<SfxItemCategory> categories();

    Collection<SfxItemDefinition> items();

    Collection<SfxItemDefinition> visibleItemsInCategory(String categoryId);
}
