package cc.theends6.sfx.internal.recipe;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
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

        List<File> compiledFiles = new ArrayList<>();
        collectYaml(new File(plugin.getDataFolder(), "content/compiled/content/recipes"), compiledFiles);
        compiledFiles.sort(Comparator.comparing(File::getPath));
        if (compiledFiles.isEmpty()) {
            List<YamlConfiguration> bundledCompiled = SfxCompiledYamlResolver.loadBundledCompiledUnder(plugin, "content/recipes");
            if (bundledCompiled.isEmpty() && compiledOnly) {
                throw new IllegalStateException("Compiled-only content runtime is enabled, but no compiled recipes were found.");
            }
            int index = 0;
            for (YamlConfiguration yaml : bundledCompiled) {
                loadYamlInto(registry, yaml, "bundled compiled recipe " + (++index), true);
            }
        } else {
            for (File file : compiledFiles) {
                loadYamlInto(registry, YamlConfiguration.loadConfiguration(file), file.getName(), true);
            }
        }
    }

    private void loadYamlInto(DefaultSfxRecipeRegistry registry, YamlConfiguration yaml, String sourceName, boolean strict) {
        for (Map<?, ?> entry : recipeEntries(yaml)) {
            try {
                if (!isFeatureEnabled(entry)) {
                    continue;
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

    private List<Map<?, ?>> recipeEntries(YamlConfiguration yaml) {
        Object raw = yaml.get("recipes");
        if (raw instanceof List<?>) {
            return yaml.getMapList("recipes");
        }
        if (raw instanceof org.bukkit.configuration.ConfigurationSection section) {
            List<Map<?, ?>> result = new ArrayList<>();
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                if (value instanceof org.bukkit.configuration.ConfigurationSection child) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (String childKey : child.getKeys(false)) {
                        map.put(childKey, child.get(childKey));
                    }
                    map.putIfAbsent("id", key);
                    result.add(map);
                } else if (value instanceof Map<?, ?> map) {
                    Map<Object, Object> copy = new LinkedHashMap<>(map);
                    copy.putIfAbsent("id", key);
                    result.add(copy);
                }
            }
            return result;
        }
        return List.of();
    }


    private boolean isFeatureEnabled(Map<?, ?> entry) {
        boolean sfxGeneratorBalance = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true);
        if (Boolean.TRUE.equals(entry.get("requires-sfx-generator-balance")) && !sfxGeneratorBalance) {
            return false;
        }
        if (Boolean.TRUE.equals(entry.get("requires-classic-generator-balance")) && sfxGeneratorBalance) {
            return false;
        }
        return true;
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

    private List<SfxRecipeSlot> parseInputs(SfxRecipeOperation operation, Map<?, ?> entry) {
        return switch (operation) {
            case SHAPED -> parseMatrix(entry.get("matrix"));
            case SHAPELESS -> parseList(entry.get("inputs"));
            case SINGLE, HAND -> List.of(parseSlot(entry.get("input")));
        };
    }

    private List<SfxRecipeSlot> parseMatrix(Object raw) {
        if (!(raw instanceof List<?> entries) || entries.size() != 9) {
            throw new IllegalArgumentException("recipe matrix must contain exactly 9 entries");
        }
        List<SfxRecipeSlot> matrix = new ArrayList<>(9);
        for (Object entry : entries) {
            matrix.add(parseSlot(entry));
        }
        return matrix;
    }

    private List<SfxRecipeSlot> parseList(Object raw) {
        if (!(raw instanceof List<?> entries) || entries.isEmpty()) {
            throw new IllegalArgumentException("recipe inputs must contain at least one entry");
        }
        List<SfxRecipeSlot> inputs = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            inputs.add(parseSlot(entry));
        }
        return inputs;
    }

    private SfxRecipeSlot parseSlot(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("recipe slot must be an explicit map");
        }
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

    private List<SfxRecipeOutputDefinition> parseOutputs(Object raw, boolean random) {
        List<SfxRecipeOutputDefinition> outputs = new ArrayList<>();
        if (!(raw instanceof List<?> entries)) {
            return outputs;
        }
        for (Object entry : entries) {
            outputs.add(parseOutput(entry, random));
        }
        return outputs;
    }

    private SfxRecipeOutputDefinition parseOutput(Object raw, boolean random) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("recipe output must be an explicit map");
        }
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

    private Material parseMaterial(String input) {
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
