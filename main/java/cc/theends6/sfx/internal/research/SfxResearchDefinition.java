package cc.theends6.sfx.internal.research;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SfxResearchDefinition(String id, String name, int cost, int order, Set<String> itemIds) {
    public SfxResearchDefinition {
        id = normalizeId(id);
        name = Objects.requireNonNull(name, "name");
        if (cost < 0) {
            throw new IllegalArgumentException("Research cost must be zero or greater");
        }
        itemIds = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(itemIds, "itemIds").stream()
                .map(SfxResearchDefinition::normalizeItemId)
                .toList()));
    }

    public static SfxResearchDefinition of(String id, String name, int cost, int order, String... itemIds) {
        return new SfxResearchDefinition(id, name, cost, order, new LinkedHashSet<>(List.of(itemIds)));
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Research id cannot be blank");
        }
        return id.trim().toLowerCase();
    }

    private static String normalizeItemId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Research item id cannot be blank");
        }
        return id.trim().toLowerCase();
    }
}
