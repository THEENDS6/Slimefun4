package cc.theends6.sfx.internal.item;

import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.internal.core.SfxOwnedEntries;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DefaultSfxItemRegistry implements SfxItemRegistry {
    private static final Comparator<SfxItemDefinition> ITEM_ORDER = Comparator
            .comparingInt(SfxItemDefinition::order)
            .thenComparing(SfxItemDefinition::id);

    private final Map<String, Owned<SfxItemCategory>> categories = new LinkedHashMap<>();
    private final Map<String, Owned<SfxItemDefinition>> items = new LinkedHashMap<>();
    private final ThreadLocal<String> registrationOwner = ThreadLocal.withInitial(() -> SfxOwnedEntries.CORE_OWNER);
    private long revision;

    @Override
    public synchronized void registerCategory(SfxItemCategory category) {
        if (categories.containsKey(category.id())) {
            throw new IllegalArgumentException("Duplicate SFX category: " + category.id());
        }
        categories.put(category.id(), new Owned<>(registrationOwner.get(), category));
    }

    @Override
    public synchronized void registerItem(SfxItemDefinition definition) {
        if (items.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate SFX item: " + definition.id());
        }
        if (definition.categoryId() != null && !categories.containsKey(definition.categoryId())) {
            throw new IllegalArgumentException("Unknown category '" + definition.categoryId() + "' for item " + definition.id());
        }
        items.put(definition.id(), new Owned<>(registrationOwner.get(), definition));
        revision++;
    }


    public synchronized void replaceCategory(SfxItemCategory category) {
        categories.put(category.id(), new Owned<>(SfxOwnedEntries.CORE_OWNER, category));
    }

    public synchronized void replaceItem(SfxItemDefinition definition) {
        if (definition.categoryId() != null && !categories.containsKey(definition.categoryId())) {
            throw new IllegalArgumentException("Unknown category '" + definition.categoryId() + "' for item " + definition.id());
        }
        items.put(definition.id(), new Owned<>(SfxOwnedEntries.CORE_OWNER, definition));
        revision++;
    }

    public synchronized void clear() {
        categories.clear();
        items.clear();
        revision++;
    }

    public synchronized void removeOwner(String owner) {
        items.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        categories.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        revision++;
    }

    public SfxItemRegistry registrarFor(String owner) {
        return (SfxItemRegistry) Proxy.newProxyInstance(
                SfxItemRegistry.class.getClassLoader(),
                new Class<?>[] {SfxItemRegistry.class},
                (proxy, method, args) -> {
                    String previous = registrationOwner.get();
                    registrationOwner.set(owner);
                    try {
                        return method.invoke(this, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    } finally {
                        registrationOwner.set(previous);
                    }
                });
    }

    @Override
    public synchronized Optional<SfxItemCategory> category(String id) {
        Owned<SfxItemCategory> entry = categories.get(SfxItemCategory.normalizeId(id));
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    @Override
    public synchronized Optional<SfxItemDefinition> item(String id) {
        Owned<SfxItemDefinition> entry = items.get(SfxItemDefinition.normalizeId(id));
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    @Override
    public synchronized Collection<SfxItemCategory> categories() {
        return categories.values().stream().map(Owned::value)
                .sorted(Comparator.comparingInt(SfxItemCategory::order).thenComparing(SfxItemCategory::id))
                .toList();
    }

    @Override
    public synchronized Collection<SfxItemDefinition> items() {
        return items.values().stream().map(Owned::value)
                .sorted(ITEM_ORDER)
                .toList();
    }

    @Override
    public synchronized Collection<SfxItemDefinition> visibleItemsInCategory(String categoryId) {
        String normalized = SfxItemCategory.normalizeId(categoryId);
        return items.values().stream().map(Owned::value)
                .filter(item -> !item.hidden())
                .filter(item -> normalized.equals(item.categoryId()))
                .sorted(ITEM_ORDER)
                .toList();
    }

    public long revision() {
        return revision;
    }

    private record Owned<T>(String owner, T value) {
    }
}
