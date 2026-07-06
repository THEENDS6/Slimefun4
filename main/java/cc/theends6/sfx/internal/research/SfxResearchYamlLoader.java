package cc.theends6.sfx.internal.research;

import cc.theends6.sfx.internal.feature.SfxFeatureSwitch;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxResearchYamlLoader {
    private static final List<String> DEFAULT_RESEARCH_FILES = List.of(
            "content/researches/10-legacy-slimefun.yml"
    );

    private final JavaPlugin plugin;
    private final Logger logger;

    public SfxResearchYamlLoader(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
    }

    public void ensureDefaultFiles(boolean overwriteExisting) {
        for (String resourcePath : DEFAULT_RESEARCH_FILES) {
            syncBundledFile(resourcePath, overwriteExisting);
        }
    }

    public void loadInto(SfxResearchRegistry registry) {
        registry.clear();

        if (plugin.getConfig().getBoolean("content.runtime.compiled-only", true)) {
            int index = 0;
            for (YamlConfiguration yaml : SfxCompiledYamlResolver.loadCompiledUnder(plugin, "content/researches")) {
                loadYamlInto(registry, yaml, "compiled research content " + (++index), true);
            }
            return;
        }

        File root = new File(plugin.getDataFolder(), "content/researches");
        if (!root.isDirectory()) {
            return;
        }

        List<File> files = new ArrayList<>();
        collectYaml(root, files);
        files.sort(Comparator.comparing(File::getPath));

        for (File file : files) {
            loadYamlInto(registry, YamlConfiguration.loadConfiguration(file), file.getName(), false);
        }
    }

    private void loadYamlInto(SfxResearchRegistry registry, YamlConfiguration yaml, String sourceName, boolean strict) {
        for (Map<?, ?> entry : yaml.getMapList("researches")) {
            try {
                if (strict) {
                    validateCompiledResearchEntry(entry);
                }
                registry.register(parseResearch(entry, strict));
            } catch (Exception ex) {
                if (strict) {
                    throw new IllegalStateException("Failed to load compiled research from YAML " + sourceName + ": " + ex.getMessage(), ex);
                }
                logger.warning("Failed to load research from YAML " + sourceName + ": " + ex.getMessage());
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

    private SfxResearchDefinition parseResearch(Map<?, ?> entry, boolean strict) {
        String id = string(entry.get("id"));
        String nameKey = string(entry.get("name-key"));
        int cost = integer(entry.get("cost"));
        int order = integer(entry.get("order"));
        List<String> items = strict ? requiredStringList(entry, "items") : stringList(entry.get("items"));
        items = filterFeatureItems(items, entry.get("item-feature-gates"));
        return new SfxResearchDefinition(id, nameKey, cost, order, new LinkedHashSet<>(items));
    }

    private static void validateCompiledResearchEntry(Map<?, ?> entry) {
        for (Object keyRaw : entry.keySet()) {
            String key = String.valueOf(keyRaw);
            if (key.startsWith("@")) {
                throw new IllegalArgumentException("compiled research must not contain template directive: " + key);
            }
            if (key.equals("profile")
                    || key.equals("expand")
                    || key.equals("id-prefix")
                    || key.equals("input-prefix")
                    || key.equals("input-amount")) {
                throw new IllegalArgumentException("compiled research must not contain helper field: " + key);
            }
        }
    }

    private List<String> filterFeatureItems(List<String> items, Object rawGates) {
        Map<String, List<String>> gates = itemFeatureGates(rawGates);
        if (gates.isEmpty()) {
            return items;
        }
        return items.stream()
                .filter(item -> requiredFeaturesEnabled(gates.get(normalizeItemId(item))))
                .toList();
    }

    private Map<String, List<String>> itemFeatureGates(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, List<String>> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String itemId = normalizeItemId(String.valueOf(entry.getKey()));
            List<String> features = featureList(entry.getValue());
            if (!itemId.isEmpty() && !features.isEmpty()) {
                result.put(itemId, features);
            }
        }
        return result;
    }

    private List<String> featureList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(value -> String.valueOf(value).trim())
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
        String feature = raw == null ? "" : String.valueOf(raw).trim();
        return feature.isEmpty() ? List.of() : List.of(feature);
    }

    private boolean requiredFeaturesEnabled(List<String> features) {
        if (features == null || features.isEmpty()) {
            return true;
        }
        for (String feature : features) {
            if (!SfxFeatureSwitch.requirementEnabled(plugin, feature)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeItemId(String itemId) {
        return itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
    }

    private static int integer(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    private static String string(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("required string value missing");
        }
        return String.valueOf(raw);
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry == null) {
                continue;
            }
            String text = String.valueOf(entry).trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }

    private static List<String> requiredStringList(Map<?, ?> map, String key) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException("required list value missing: " + key);
        }
        List<String> values = stringList(map.get(key));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("required list value empty: " + key);
        }
        return values;
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
