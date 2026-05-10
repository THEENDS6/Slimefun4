package cc.theends6.sfx.internal.recipe;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
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
    private final Logger logger;

    public SfxRecipeYamlLoader(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
    }

    public void ensureDefaultFiles(boolean overwriteExisting) {
        for (String resourcePath : DEFAULT_RECIPE_FILES) {
            syncBundledFile(resourcePath, overwriteExisting);
        }
    }

    public void loadInto(DefaultSfxRecipeRegistry registry) {
        File recipeRoot = new File(plugin.getDataFolder(), "content/recipes");
        if (!recipeRoot.isDirectory()) {
            return;
        }

        List<File> files = new ArrayList<>();
        collectYaml(recipeRoot, files);
        files.sort(Comparator.comparing(File::getPath));

        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (Map<?, ?> entry : yaml.getMapList("recipes")) {
                try {
                    registry.register(parseRecipe(entry));
                } catch (Exception ex) {
                    logger.warning("Failed to load recipe from YAML " + file.getName() + ": " + ex.getMessage());
                }
            }
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
        String recipeType = string(orDefault(entry, "recipe-type", orDefault(entry, "machine", "unknown")));
        SfxRecipeOperation operation = SfxRecipeOperation.parse(string(orDefault(entry, "operation", "shaped")));

        SfxRecipeDefinition.Builder builder = SfxRecipeDefinition.builder(id, recipeType, operation)
                .guideOrder(integer(orDefault(entry, "guide-order", 0)))
                .matchPriority(entry.containsKey("match-priority") ? integer(entry.get("match-priority")) : null)
                .durationTicks(entry.containsKey("time") ? integer(entry.get("time")) : null)
                .source(optionalString(orDefault(entry, "source", "custom")))
                .note(optionalString(entry.get("note")))
                .runtimeEnabled(Boolean.TRUE.equals(orDefault(entry, "runtime", Boolean.FALSE)));

        List<String> runtimeMachines = stringList(entry.get("runtime-machines"));
        if (!runtimeMachines.isEmpty()) {
            builder.runtimeMachineIds(runtimeMachines);
        } else {
            builder.runtimeMachineId(optionalString(entry.get("machine")));
        }

        builder.inputs(parseInputs(operation, entry));
        builder.outputs(parseOutputs(entry.get("outputs")));
        builder.randomOutputs(parseOutputs(entry.get("random-outputs")));
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
        if (raw == null) {
            return SfxRecipeSlot.empty();
        }
        if (raw instanceof String text) {
            String normalized = text.trim();
            if (normalized.isEmpty() || normalized.equalsIgnoreCase("air") || normalized.equalsIgnoreCase("empty")) {
                return SfxRecipeSlot.empty();
            }
            if (normalized.contains(":")) {
                return SfxRecipeSlot.sfx(normalized);
            }
            return SfxRecipeSlot.vanilla(parseMaterial(normalized));
        }
        if (raw instanceof Map<?, ?> map) {
            String type = string(orDefault(map, "type", map.containsKey("id") ? "sfx" : "vanilla"));
            int amount = integer(orDefault(map, "amount", 1));
            if (type.equalsIgnoreCase("sfx")) {
                return SfxRecipeSlot.sfx(string(map.get("id")), amount);
            }
            return SfxRecipeSlot.vanilla(parseMaterial(string(map.get("material"))), amount);
        }
        throw new IllegalArgumentException("unsupported recipe slot: " + raw);
    }

    private List<SfxRecipeOutputDefinition> parseOutputs(Object raw) {
        List<SfxRecipeOutputDefinition> outputs = new ArrayList<>();
        if (!(raw instanceof List<?> entries)) {
            return outputs;
        }
        for (Object entry : entries) {
            outputs.add(parseOutput(entry));
        }
        return outputs;
    }

    private SfxRecipeOutputDefinition parseOutput(Object raw) {
        if (raw instanceof String text) {
            String trimmed = text.trim();
            int amount = 1;
            if (trimmed.contains("*")) {
                String[] split = trimmed.split("\\*", 2);
                trimmed = split[0].trim();
                amount = integer(split[1]);
            }
            if (trimmed.contains(":")) {
                return SfxRecipeOutputDefinition.sfx(trimmed, amount);
            }
            return SfxRecipeOutputDefinition.vanilla(parseMaterial(trimmed), amount);
        }
        if (raw instanceof Map<?, ?> map) {
            String type = string(orDefault(map, "type", map.containsKey("id") ? "sfx" : "vanilla"));
            int amount = integer(orDefault(map, "amount", 1));
            Double chance = map.containsKey("chance") ? Double.parseDouble(String.valueOf(map.get("chance"))) : null;
            if (type.equalsIgnoreCase("sfx")) {
                return SfxRecipeOutputDefinition.sfx(string(map.get("id")), amount, chance);
            }
            return SfxRecipeOutputDefinition.vanilla(parseMaterial(string(map.get("material"))), amount, chance);
        }
        throw new IllegalArgumentException("unsupported recipe output: " + raw);
    }

    private Material parseMaterial(String input) {
        Material material = Material.matchMaterial(input);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + input);
        }
        return material;
    }

    private static Object orDefault(Map<?, ?> map, String key, Object defaultValue) {
        return map.containsKey(key) ? map.get(key) : defaultValue;
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
