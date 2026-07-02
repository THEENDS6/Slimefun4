package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads static electric-machine recipe providers from YAML. Dynamic providers stay in Java. */
final class SfxElectricRecipeYamlLoader {
    private static final String RESOURCE_PATH = "content/machines/electric-recipes.yml";

    private final JavaPlugin plugin;
    private final Map<String, SfxElectricRecipeProvider> providers;

    private SfxElectricRecipeYamlLoader(JavaPlugin plugin, Map<String, SfxElectricRecipeProvider> providers) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.providers = Map.copyOf(providers);
    }

    static SfxElectricRecipeYamlLoader load(JavaPlugin plugin) {
        ensureBundledFile(plugin);
        boolean strict = plugin.getConfig().getBoolean("content.runtime.compiled-only", true);
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        ConfigurationSection root = yaml.getConfigurationSection("providers");
        if (root == null) {
            String message = "No providers section in " + RESOURCE_PATH + "; static electric providers will be empty.";
            if (strict) {
                throw new IllegalStateException(message);
            }
            plugin.getLogger().warning(message);
            return new SfxElectricRecipeYamlLoader(plugin, Map.of());
        }
        Map<String, SfxElectricRecipeProvider> result = new LinkedHashMap<>();
        int recipes = 0;
        for (String providerId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(providerId);
            if (section == null) {
                continue;
            }
            List<SfxElectricRecipe> parsed = new ArrayList<>();
            List<Map<?, ?>> entries = requiredRecipeList(section, "recipes");
            for (Map<?, ?> raw : entries) {
                try {
                    parsed.addAll(parseRecipeOrExpansion(raw));
                } catch (RuntimeException ex) {
                    if (strict) {
                        throw new IllegalStateException("Invalid electric recipe YAML entry in provider " + providerId, ex);
                    }
                    plugin.getLogger().log(Level.WARNING, "Invalid electric recipe YAML entry in provider " + providerId + "; skipping it.", ex);
                }
            }
            List<SfxElectricRecipe> snapshot = List.copyOf(parsed);
            result.put(providerId, () -> snapshot);
            recipes += snapshot.size();
        }
        SfxValidationDiagnostics.log(plugin, "machine-yaml", "electric recipe providers=" + result.size() + ", recipes=" + recipes);
        return new SfxElectricRecipeYamlLoader(plugin, result);
    }

    SfxElectricRecipeProvider provider(String id) {
        SfxElectricRecipeProvider provider = providers.get(id);
        if (provider == null) {
            if (plugin.getConfig().getBoolean("content.runtime.compiled-only", true)) {
                throw new IllegalStateException("Missing electric recipe provider in YAML: " + id);
            }
            plugin.getLogger().warning("Missing electric recipe provider in YAML: " + id);
            return List::of;
        }
        return provider;
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
        int ticks = integer(requiredValue(entry, "ticks"));
        List<SfxRecipeSlot> inputs = parseInputs(entry.get("inputs"));
        Object randomOutputs = entry.get("random-outputs");
        if (randomOutputs instanceof List<?>) {
            return SfxElectricRecipe.randomOutput(id, inputs.getFirst(), parseWeightedOutputs(randomOutputs), ticks);
        }
        return SfxElectricRecipe.fixedOutputs(id, inputs, parseOutputs(entry.get("outputs")), ticks);
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
        if (raw instanceof String text) {
            String value = text.trim();
            if (value.contains(":")) {
                return SfxRecipeSlot.sfx(value);
            }
            return SfxRecipeSlot.vanilla(parseMaterial(value));
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("unsupported electric input: " + raw);
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
        if (raw instanceof String text) {
            String value = text.trim();
            int amount = 1;
            if (value.contains("*")) {
                String[] split = value.split("\\*", 2);
                value = split[0].trim();
                amount = integer(split[1]);
            }
            if (value.contains(":")) {
                return SfxElectricStack.sfx(value, amount);
            }
            return SfxElectricStack.vanilla(parseMaterial(value), amount);
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("unsupported electric output: " + raw);
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

    private static int integer(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }
}
