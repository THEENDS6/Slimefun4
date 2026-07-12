package cc.theends6.sfx.internal.electric;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

record SfxElectricMachineRuntimeBinding(
        String executor,
        String provider,
        Set<String> recipeTags,
        Set<String> includeRecipes,
        Set<String> excludeRecipes,
        Set<String> guideRecipeTypes
) {
    SfxElectricMachineRuntimeBinding {
        executor = normalizeId(executor);
        provider = normalizeId(provider);
        recipeTags = normalizeTags(recipeTags);
        includeRecipes = normalizeIds(includeRecipes);
        excludeRecipes = normalizeIds(excludeRecipes);
        guideRecipeTypes = normalizeIds(guideRecipeTypes);
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Electric machine runtime requires executor.");
        }
        if ("sf:provider".equals(executor) && (provider == null || provider.isBlank())) {
            throw new IllegalArgumentException("Electric machine provider runtime requires provider.");
        }
        if (!"sf:provider".equals(executor) && provider != null && !provider.isBlank()) {
            throw new IllegalArgumentException("Electric machine runtime provider is only valid for sf:provider.");
        }
        Set<String> conflicts = new LinkedHashSet<>(includeRecipes);
        conflicts.retainAll(excludeRecipes);
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException("Electric machine runtime include/exclude recipe conflict: " + conflicts);
        }
    }

    private static Set<String> normalizeTags(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw) {
            String normalized = normalizeTag(value);
            if (normalized != null && !normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> normalizeIds(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw) {
            String normalized = normalizeId(value);
            if (normalized != null && !normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return Set.copyOf(result);
    }

    static String normalizeTag(String raw) {
        return raw == null ? null : raw.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    static String normalizeId(String raw) {
        return raw == null ? null : raw.trim();
    }
}
