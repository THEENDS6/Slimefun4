package cc.theends6.sfx.internal.recipe;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.feature.SfxFeatureSwitch;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxRecipeYamlLoader {
    private static final List<String> DEFAULT_RECIPE_FILES = List.of(
            "content/recipes/00-manual-basics.yml",
            "content/recipes/10-legacy-enhanced.yml",
            "content/recipes/20-legacy-magic.yml",
            "content/recipes/30-legacy-altar.yml",
            "content/recipes/40-legacy-smeltery.yml",
            "content/recipes/50-legacy-armor.yml",
            "content/recipes/60-legacy-electric.yml",
            "content/recipes/70-legacy-special.yml"
    );

    private final JavaPlugin plugin;
    private final SfxLocalization localization;
    private final Logger logger;

    public SfxRecipeYamlLoader(JavaPlugin plugin, SfxLocalization localization) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.logger = plugin.getLogger();
    }

    public void ensureDefaultFiles(boolean overwriteExisting) {
        for (String resourcePath : DEFAULT_RECIPE_FILES) {
            syncBundledFile(resourcePath, overwriteExisting);
        }
    }

    public void loadInto(DefaultSfxRecipeRegistry registry) {
        boolean compiledOnly = plugin.getConfig().getBoolean("content.runtime.compiled-only", true);
        if (!compiledOnly) {
            List<File> files = new ArrayList<>();
            File recipeRoot = new File(plugin.getDataFolder(), "content/recipes");
            collectYaml(recipeRoot, files);
            files.sort(Comparator.comparing(File::getPath));
            for (File file : files) {
                loadYamlInto(registry, YamlConfiguration.loadConfiguration(file), file.getName(), false);
            }
        }

        int index = 0;
        for (YamlConfiguration yaml : SfxCompiledYamlResolver.loadCompiledUnder(plugin, "content/recipes")) {
            loadYamlInto(registry, yaml, "compiled recipe content " + (++index), true);
        }
    }

    private void loadYamlInto(DefaultSfxRecipeRegistry registry, YamlConfiguration yaml, String sourceName, boolean strict) {
        for (Map<?, ?> entry : recipeEntries(yaml)) {
            try {
                if (!isFeatureEnabled(entry)) {
                    continue;
                }
                if (strict) {
                    validateCompiledRecipeEntry(entry);
                }
                registry.register(parseRecipe(entry));
            } catch (Exception ex) {
                if (strict) {
                    throw new IllegalStateException("Failed to load compiled recipe from YAML " + sourceName + ": " + ex.getMessage(), ex);
                }
                logger.warning("Failed to load recipe from YAML " + sourceName + ": " + ex.getMessage());
            }
        }
    }

    static void validateCompiledYamlShape(YamlConfiguration yaml, String sourceName) {
        for (Map<?, ?> entry : recipeEntries(yaml)) {
            try {
                validateCompiledRecipeEntry(entry);
                SfxRecipeOperation operation = SfxRecipeOperation.parse(string(entry.get("operation")));
                parseInputs(operation, entry);
                parseOutputs(entry.get("outputs"), false);
                parseOutputs(entry.get("random-outputs"), true);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to parse compiled recipe shape from YAML " + sourceName + ": " + ex.getMessage(), ex);
            }
        }
    }

    private static List<Map<?, ?>> recipeEntries(YamlConfiguration yaml) {
        Object raw = yaml.get("recipes");
        if (raw instanceof List<?> list) {
            List<Map<?, ?>> result = new ArrayList<>();
            for (Object entry : list) {
                result.add(requiredMap(entry, "recipe entry"));
            }
            return result;
        }
        if (raw instanceof ConfigurationSection section) {
            List<Map<?, ?>> result = new ArrayList<>();
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                if (value instanceof ConfigurationSection child) {
                    Map<Object, Object> map = new LinkedHashMap<>(sectionToMap(child));
                    map.putIfAbsent("id", key);
                    result.add(map);
                } else if (value instanceof Map<?, ?> map) {
                    Map<Object, Object> copy = new LinkedHashMap<>(normalizeMap(map));
                    copy.putIfAbsent("id", key);
                    result.add(copy);
                }
            }
            return result;
        }
        return List.of();
    }


    private boolean isFeatureEnabled(Map<?, ?> entry) {
        if (!requiredFeaturesEnabled(entry.get("requires-feature"))) {
            return false;
        }
        if (!excludedFeaturesAbsent(entry.get("excludes-feature"))) {
            return false;
        }
        return true;
    }

    private boolean requiredFeaturesEnabled(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (!requiredFeatureEnabled(value)) {
                    return false;
                }
            }
            return true;
        }
        return requiredFeatureEnabled(raw);
    }

    private boolean requiredFeatureEnabled(Object raw) {
        if (raw == null) {
            return true;
        }
        String id = String.valueOf(raw).trim();
        return SfxFeatureSwitch.requirementEnabled(plugin, id);
    }

    private boolean excludedFeaturesAbsent(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (!excludedFeatureAbsent(value)) {
                    return false;
                }
            }
            return true;
        }
        return excludedFeatureAbsent(raw);
    }

    private boolean excludedFeatureAbsent(Object raw) {
        if (raw == null) {
            return true;
        }
        String id = String.valueOf(raw).trim();
        return !SfxFeatureSwitch.requirementEnabled(plugin, id);
    }

    private static void validateCompiledRecipeEntry(Map<?, ?> entry) {
        for (Object keyRaw : entry.keySet()) {
            String key = String.valueOf(keyRaw);
            if (key.startsWith("@")) {
                throw new IllegalArgumentException("compiled recipe must not contain template directive: " + key);
            }
            if (key.equals("expand")
                    || key.equals("id-prefix")
                    || key.equals("input-prefix")
                    || key.equals("input-amount")) {
                throw new IllegalArgumentException("compiled recipe must not contain expansion helper: " + key);
            }
        }
        if (entry.containsKey("machine") || entry.containsKey("runtime-machines")) {
            throw new IllegalArgumentException("compiled recipe must use runtime-machine-tags instead of direct machine references");
        }
        if (Boolean.TRUE.equals(entry.get("runtime")) && !entry.containsKey("runtime-machine-tags")) {
            throw new IllegalArgumentException("compiled runtime recipe must declare runtime-machine-tags");
        }
    }

    private void collectYaml(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectYaml(child, files);
            } else if (child.isFile() && child.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                files.add(child);
            }
        }
    }

    private SfxRecipeDefinition parseRecipe(Map<?, ?> entry) {
        String id = string(entry.get("id"));
        String recipeType = string(entry.get("recipe-type"));
        SfxRecipeOperation operation = SfxRecipeOperation.parse(string(entry.get("operation")));

        SfxRecipeDefinition.Builder builder = SfxRecipeDefinition.builder(id, recipeType, operation)
                .guideOrder(requiredInteger(entry, "guide-order"))
                .matchPriority(entry.containsKey("match-priority") ? integer(entry.get("match-priority")) : null)
                .durationTicks(entry.containsKey("time") ? integer(entry.get("time")) : null)
                .source(requiredString(entry, "source"))
                .note(optionalNote(entry))
                .runtimeEnabled(requiredBoolean(entry, "runtime"));

        List<String> runtimeMachines = stringList(entry.get("runtime-machines"));
        if (!runtimeMachines.isEmpty()) {
            builder.runtimeMachineIds(runtimeMachines);
        } else {
            builder.runtimeMachineId(optionalString(entry.get("machine")));
        }
        List<String> runtimeMachineTags = stringList(entry.get("runtime-machine-tags"));
        builder.runtimeMachineTags(runtimeMachineTags);
        if (Boolean.TRUE.equals(entry.get("runtime"))
                && runtimeMachines.isEmpty()
                && optionalString(entry.get("machine")) == null
                && runtimeMachineTags.isEmpty()) {
            throw new IllegalArgumentException("runtime recipe must declare machine, runtime-machines, or runtime-machine-tags");
        }

        builder.inputs(parseInputs(operation, entry));
        builder.outputs(parseOutputs(entry.get("outputs"), false));
        builder.randomOutputs(parseOutputs(entry.get("random-outputs"), true));
        return builder.build();
    }

    private static List<SfxRecipeSlot> parseInputs(SfxRecipeOperation operation, Map<?, ?> entry) {
        return switch (operation) {
            case SHAPED -> parseMatrix(entry.get("matrix"));
            case SHAPELESS -> parseList(entry.get("inputs"));
            case SINGLE, HAND -> List.of(parseSlot(entry.get("input")));
        };
    }

    private static List<SfxRecipeSlot> parseMatrix(Object raw) {
        if (!(raw instanceof List<?> entries) || entries.size() != 9) {
            throw new IllegalArgumentException("recipe matrix must contain exactly 9 entries");
        }
        List<SfxRecipeSlot> matrix = new ArrayList<>(9);
        for (Object entry : entries) {
            matrix.add(parseSlot(entry));
        }
        return matrix;
    }

    private static List<SfxRecipeSlot> parseList(Object raw) {
        if (!(raw instanceof List<?> entries) || entries.isEmpty()) {
            throw new IllegalArgumentException("recipe inputs must contain at least one entry");
        }
        List<SfxRecipeSlot> inputs = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            inputs.add(parseSlot(entry));
        }
        return inputs;
    }

    private static SfxRecipeSlot parseSlot(Object raw) {
        Map<?, ?> map = requiredMap(raw, "recipe slot");
        String type = requiredString(map, "type");
        int amount = requiredInteger(map, "amount");
        if (type.equalsIgnoreCase("empty")) {
            return SfxRecipeSlot.empty();
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("recipe slot amount must be positive");
        }
        if (type.equalsIgnoreCase("sfx")) {
            return SfxRecipeSlot.sfx(requiredString(map, "id"), amount);
        }
        if (type.equalsIgnoreCase("vanilla")) {
            return SfxRecipeSlot.vanilla(parseMaterial(requiredString(map, "material")), amount);
        }
        throw new IllegalArgumentException("unsupported recipe slot type: " + type);
    }

    private String optionalNote(Map<?, ?> entry) {
        if (entry.containsKey("note")) {
            throw new IllegalArgumentException("literal note is not allowed in compiled recipe content; use note-key");
        }
        String key = optionalString(entry.get("note-key"));
        if (key == null) {
            return null;
        }
        if (!localization.has(key)) {
            throw new IllegalArgumentException("language key missing: " + key);
        }
        return localization.requiredText(key);
    }

    private static List<SfxRecipeOutputDefinition> parseOutputs(Object raw, boolean random) {
        List<SfxRecipeOutputDefinition> outputs = new ArrayList<>();
        if (!(raw instanceof List<?> entries)) {
            return outputs;
        }
        for (Object entry : entries) {
            outputs.add(parseOutput(entry, random));
        }
        return outputs;
    }

    private static SfxRecipeOutputDefinition parseOutput(Object raw, boolean random) {
        Map<?, ?> map = requiredMap(raw, "recipe output");
        String type = requiredString(map, "type");
        int amount = requiredInteger(map, "amount");
        if (amount <= 0) {
            throw new IllegalArgumentException("recipe output amount must be positive");
        }
        if (random && !map.containsKey("chance")) {
            throw new IllegalArgumentException("random recipe output must declare chance");
        }
        Double chance = map.containsKey("chance") ? Double.parseDouble(String.valueOf(map.get("chance"))) : null;
        if (type.equalsIgnoreCase("sfx")) {
            return SfxRecipeOutputDefinition.sfx(requiredString(map, "id"), amount, chance);
        }
        if (type.equalsIgnoreCase("vanilla")) {
            return SfxRecipeOutputDefinition.vanilla(parseMaterial(requiredString(map, "material")), amount, chance);
        }
        throw new IllegalArgumentException("unsupported recipe output type: " + type);
    }

    private static Material parseMaterial(String input) {
        Material material = Material.matchMaterial(input);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + input);
        }
        return material;
    }

    private static String requiredString(Map<?, ?> map, String key) {
        String value = optionalString(map.get(key));
        if (value == null) {
            throw new IllegalArgumentException("required string value missing: " + key);
        }
        return value;
    }

    private static int requiredInteger(Map<?, ?> map, String key) {
        if (!map.containsKey(key) || map.get(key) == null) {
            throw new IllegalArgumentException("required integer value missing: " + key);
        }
        return integer(map.get(key));
    }

    private static boolean requiredBoolean(Map<?, ?> map, String key) {
        if (!map.containsKey(key) || map.get(key) == null) {
            throw new IllegalArgumentException("required boolean value missing: " + key);
        }
        Object raw = map.get(key);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private static int integer(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    private static String optionalString(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object entry : list) {
            String text = optionalString(entry);
            if (text != null) {
                values.add(text);
            }
        }
        return values;
    }

    private static String string(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("required string value missing");
        }
        return String.valueOf(raw);
    }

    private static Map<?, ?> requiredMap(Object raw, String label) {
        if (raw instanceof ConfigurationSection section) {
            return sectionToMap(section);
        }
        if (raw instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        throw new IllegalArgumentException(label + " must be an explicit map");
    }

    private static Map<Object, Object> sectionToMap(ConfigurationSection section) {
        Map<Object, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            result.put(key, normalizeYamlValue(section.get(key)));
        }
        return result;
    }

    private static Map<Object, Object> normalizeMap(Map<?, ?> map) {
        Map<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(entry.getKey(), normalizeYamlValue(entry.getValue()));
        }
        return result;
    }

    private static Object normalizeYamlValue(Object raw) {
        if (raw instanceof ConfigurationSection section) {
            return sectionToMap(section);
        }
        if (raw instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (raw instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object entry : list) {
                result.add(normalizeYamlValue(entry));
            }
            return result;
        }
        return raw;
    }

    private void syncBundledFile(String resourcePath, boolean overwriteExisting) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (!overwriteExisting && target.exists()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Failed to create parent directory for " + target.getPath());
            return;
        }
        try {
            plugin.saveResource(resourcePath, overwriteExisting);
        } catch (IllegalArgumentException ignored) {
            if (target.exists() && !overwriteExisting) {
                return;
            }
            try (var stream = plugin.getResource(resourcePath)) {
                if (stream == null) {
                    return;
                }
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                    yaml.save(target);
                }
            } catch (IOException ex) {
                logger.warning("Failed to create content file " + target.getPath() + ": " + ex.getMessage());
            }
        }
    }
}
