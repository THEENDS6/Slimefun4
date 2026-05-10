package cc.theends6.sfx.internal.item;

import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DefaultSfxItemRegistry implements SfxItemRegistry {
    private final Map<String, SfxItemCategory> categories = new LinkedHashMap<>();
    private final Map<String, SfxItemDefinition> items = new LinkedHashMap<>();

    @Override
    public void registerCategory(SfxItemCategory category) {
        if (categories.containsKey(category.id())) {
            throw new IllegalArgumentException("Duplicate SFX category: " + category.id());
        }
        categories.put(category.id(), category);
    }

    @Override
    public void registerItem(SfxItemDefinition definition) {
        if (items.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate SFX item: " + definition.id());
        }
        if (definition.categoryId() != null && !categories.containsKey(definition.categoryId())) {
            throw new IllegalArgumentException("Unknown category '" + definition.categoryId() + "' for item " + definition.id());
        }
        items.put(definition.id(), definition);
    }


    public void replaceCategory(SfxItemCategory category) {
        categories.put(category.id(), category);
    }

    public void replaceItem(SfxItemDefinition definition) {
        if (definition.categoryId() != null && !categories.containsKey(definition.categoryId())) {
            throw new IllegalArgumentException("Unknown category '" + definition.categoryId() + "' for item " + definition.id());
        }
        items.put(definition.id(), definition);
    }

    @Override
    public Optional<SfxItemCategory> category(String id) {
        return Optional.ofNullable(categories.get(SfxItemCategory.normalizeId(id)));
    }

    @Override
    public Optional<SfxItemDefinition> item(String id) {
        return Optional.ofNullable(items.get(SfxItemDefinition.normalizeId(id)));
    }

    @Override
    public Collection<SfxItemCategory> categories() {
        return categories.values().stream()
                .sorted(Comparator.comparingInt(SfxItemCategory::order).thenComparing(SfxItemCategory::id))
                .toList();
    }

    @Override
    public Collection<SfxItemDefinition> items() {
        return List.copyOf(items.values());
    }

    @Override
    public Collection<SfxItemDefinition> visibleItemsInCategory(String categoryId) {
        String normalized = SfxItemCategory.normalizeId(categoryId);
        return items.values().stream()
                .filter(item -> !item.hidden())
                .filter(item -> normalized.equals(item.categoryId()))
                .toList();
    }
}
