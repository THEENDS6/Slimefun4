package cc.theends6.sfx.internal.research;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SfxResearchRegistry {
    private final Map<String, SfxResearchDefinition> researches = new LinkedHashMap<>();
    private final Map<String, SfxResearchDefinition> byItemId = new LinkedHashMap<>();

    public void clear() {
        researches.clear();
        byItemId.clear();
    }

    public void register(SfxResearchDefinition definition) {
        if (researches.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate research id: " + definition.id());
        }
        researches.put(definition.id(), definition);
        for (String itemId : definition.itemIds()) {
            SfxResearchDefinition existing = byItemId.putIfAbsent(itemId, definition);
            if (existing != null) {
                throw new IllegalArgumentException("Item " + itemId + " is already bound to research " + existing.id());
            }
        }
    }

    public Optional<SfxResearchDefinition> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(researches.get(id.trim().toLowerCase()));
    }

    public Optional<SfxResearchDefinition> byItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byItemId.get(itemId.trim().toLowerCase()));
    }

    public Collection<SfxResearchDefinition> all() {
        return researches.values().stream()
                .sorted(Comparator.comparingInt(SfxResearchDefinition::order).thenComparing(SfxResearchDefinition::id))
                .toList();
    }
}
