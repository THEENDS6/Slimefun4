package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;


final class SfxElectricRecipeYamlLoader {
    private static final String RESOURCE_PATH = "content/machines/electric-recipes.yml";

    private final JavaPlugin plugin;
    private final Map<String, SfxElectricRecipe> recipesById;

    private SfxElectricRecipeYamlLoader(JavaPlugin plugin, Map<String, SfxElectricRecipe> recipesById) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.recipesById = Map.copyOf(recipesById);
    }

    static SfxElectricRecipeYamlLoader load(JavaPlugin plugin) {
        ensureBundledFile(plugin);
        boolean strict = plugin.getConfig().getBoolean("content.runtime.compiled-only", true);
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        List<Map<?, ?>> entries = requiredRecipeList(yaml, "electric-recipes");
        if (entries.isEmpty()) {
            String message = "No recipes in " + RESOURCE_PATH + "; static electric recipes will be empty.";
            if (strict) {
                throw new IllegalStateException(message);
            }
            plugin.getLogger().warning(message);
            return new SfxElectricRecipeYamlLoader(plugin, Map.of());
        }
        Map<String, SfxElectricRecipe> indexedRecipes = new LinkedHashMap<>();
        int recipes = 0;
        for (Map<?, ?> raw : entries) {
            try {
                List<SfxElectricRecipe> parsed = parseRecipeOrExpansion(raw);
                for (SfxElectricRecipe recipe : parsed) {
                    SfxElectricRecipe previous = indexedRecipes.put(recipe.key(), recipe);
                    if (previous != null) {
                        throw new IllegalStateException("Duplicate electric recipe id in YAML: " + recipe.key());
                    }
                }
                recipes += parsed.size();
            } catch (RuntimeException ex) {
                if (strict) {
                    throw new IllegalStateException("Invalid electric recipe YAML entry", ex);
                }
                plugin.getLogger().log(Level.WARNING, "Invalid electric recipe YAML entry; skipping it.", ex);
            }
        }
        SfxValidationDiagnostics.log(plugin, "machine-yaml", "electric recipes=" + recipes);
        return new SfxElectricRecipeYamlLoader(plugin, indexedRecipes);
    }

    SfxElectricRecipeProvider provider(SfxElectricMachineRuntimeBinding runtime) {
        Objects.requireNonNull(runtime, "runtime");
        String executor = runtime.executor();
        Set<String> acceptedTags = runtime.recipeTags();
        Set<String> excluded = runtime.excludeRecipes();
        Map<String, SfxElectricRecipe> selected = new LinkedHashMap<>();
        for (SfxElectricRecipe recipe : recipesById.values()) {
            if (!executor.equals(recipe.recipeType())) {
                continue;
            }
            if (matchesAnyTag(recipe, acceptedTags) && !excluded.contains(recipe.key())) {
                selected.put(recipe.key(), recipe);
            }
        }
        for (String includeId : runtime.includeRecipes()) {
            SfxElectricRecipe recipe = recipesById.get(includeId);
            if (recipe == null) {
                throw new IllegalStateException("Electric machine runtime includes unknown recipe: " + includeId);
            }
            if (!executor.equals(recipe.recipeType())) {
                throw new IllegalStateException("Electric machine runtime include " + includeId + " has recipe-type "
                        + recipe.recipeType() + " but executor is " + executor);
            }
            selected.put(includeId, recipe);
        }
        if (selected.isEmpty()) {
            throw new IllegalStateException("Electric machine runtime executor " + executor
                    + " with recipe-tags " + acceptedTags + " did not match any electric recipes.");
        }
        List<SfxElectricRecipe> snapshot = List.copyOf(selected.values());
        return () -> snapshot;
    }

    private static boolean matchesAnyTag(SfxElectricRecipe recipe, Set<String> acceptedTags) {
        if (acceptedTags == null || acceptedTags.isEmpty()) {
            return false;
        }
        for (String tag : recipe.recipeTags()) {
            if (acceptedTags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static List<SfxElectricRecipe> parseRecipeOrExpansion(Map<?, ?> entry) {
        String expand = optionalString(entry.get("expand"));
        if (expand == null || expand.isBlank()) {
            return List.of(parseRecipe(entry));
        }
        throw new IllegalArgumentException("Compiled electric recipe entry must not contain expand shorthand: " + expand);
    }

    private static SfxElectricRecipe parseRecipe(Map<?, ?> entry) {
        String id = string(entry.get("id"));
        String recipeType = optionalString(entry.get("recipe-type"));
        Set<String> recipeTags = strings(entry.get("recipe-tags"));
        int ticks = integer(requiredValue(entry, "ticks"));
        List<SfxRecipeSlot> inputs = parseInputs(entry.get("inputs"));
        Object randomOutputs = entry.get("random-outputs");
        if (randomOutputs instanceof List<?>) {
            return SfxElectricRecipe.randomOutput(id, recipeType, recipeTags, inputs.getFirst(), parseWeightedOutputs(randomOutputs), ticks);
        }
        return SfxElectricRecipe.fixedOutputs(id, recipeType, recipeTags, inputs, parseOutputs(entry.get("outputs")), ticks);
    }

    private static List<SfxRecipeSlot> parseInputs(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("electric recipe inputs must contain at least one entry");
        }
        List<SfxRecipeSlot> result = new ArrayList<>();
        for (Object entry : list) {
            result.add(parseInput(entry));
        }
        return result;
    }

    private static SfxRecipeSlot parseInput(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("electric recipe input must be an explicit map: " + raw);
        }
        int amount = integer(requiredValue(map, "amount"));
        Object item = map.get("item");
        if (item != null) {
            return SfxRecipeSlot.sfx(String.valueOf(item), amount);
        }
        Object material = map.get("material");
        if (material != null) {
            return SfxRecipeSlot.vanilla(parseMaterial(String.valueOf(material)), amount);
        }
        throw new IllegalArgumentException("electric input requires item or material: " + raw);
    }

    private static List<SfxElectricStack> parseOutputs(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("electric recipe outputs must contain at least one entry");
        }
        List<SfxElectricStack> result = new ArrayList<>();
        for (Object entry : list) {
            result.add(parseOutput(entry));
        }
        return result;
    }

    private static List<SfxElectricStack> parseWeightedOutputs(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("random-outputs must contain at least one entry");
        }
        List<SfxElectricStack> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("weighted random output must be a map: " + entry);
            }
            int weight = Math.max(1, integer(requiredValue(map, "weight")));
            SfxElectricStack stack = parseOutput(map);
            for (int i = 0; i < weight; i++) {
                result.add(stack);
            }
        }
        return result;
    }

    private static SfxElectricStack parseOutput(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("electric recipe output must be an explicit map: " + raw);
        }
        int amount = integer(requiredValue(map, "amount"));
        Object item = map.get("item");
        if (item != null) {
            return SfxElectricStack.sfx(String.valueOf(item), amount);
        }
        Object material = map.get("material");
        if (material != null) {
            return SfxElectricStack.vanilla(parseMaterial(String.valueOf(material)), amount);
        }
        throw new IllegalArgumentException("electric output requires item or material: " + raw);
    }

    private static Material parseMaterial(String raw) {
        Material material = Material.matchMaterial(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + raw);
        }
        return material;
    }

    private static void ensureBundledFile(JavaPlugin plugin) {
        File target = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (target.isFile()) {
            return;
        }
        try {
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            plugin.saveResource(RESOURCE_PATH, false);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Bundled electric recipe YAML is missing: " + RESOURCE_PATH, ex);
        }
    }

    private static List<Map<?, ?>> requiredRecipeList(ConfigurationSection section, String path) {
        if (!section.contains(path) || !(section.get(path) instanceof List<?>)) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return section.getMapList(path);
    }

    private static Object requiredValue(Map<?, ?> map, String key) {
        if (!map.containsKey(key) || map.get(key) == null) {
            throw new IllegalArgumentException("map requires " + key);
        }
        return map.get(key);
    }

    private static String string(Object raw) {
        String value = optionalString(raw);
        if (value == null) {
            throw new IllegalArgumentException("Missing string value");
        }
        return value;
    }

    private static String optionalString(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static Set<String> strings(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object value : list) {
            if (value != null && !String.valueOf(value).isBlank()) {
                result.add(String.valueOf(value).trim().replace('_', '-').toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }

    private static int integer(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }
}
