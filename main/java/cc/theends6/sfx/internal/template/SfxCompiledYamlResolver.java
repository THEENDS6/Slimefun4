package cc.theends6.sfx.internal.template;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxCompiledYamlResolver {
    private static final String COMPILED_DIRECTORY = "content/compiled";

    private SfxCompiledYamlResolver() {
    }

    public static YamlConfiguration loadMerged(JavaPlugin plugin, String resourcePath) {
        File baseFile = new File(plugin.getDataFolder(), resourcePath);
        YamlConfiguration base = baseFile.isFile()
                ? YamlConfiguration.loadConfiguration(baseFile)
                : new YamlConfiguration();
        Map<String, Object> mergedMap = sectionToMap(base);
        File compiledRoot = new File(plugin.getDataFolder(), COMPILED_DIRECTORY + "/" + compiledDirectory(resourcePath));
        if (!compiledRoot.isDirectory()) {
            return base;
        }
        List<File> files = listYamlFiles(compiledRoot);
        for (File file : files) {
            YamlConfiguration compiled = YamlConfiguration.loadConfiguration(file);
            mergeMap(mergedMap, sectionToMap(compiled));
        }
        YamlConfiguration merged = new YamlConfiguration();
        applyMap(merged, mergedMap);
        return merged;
    }

    private static List<File> listYamlFiles(File root) {
        File[] children = root.listFiles();
        if (children == null) {
            return List.of();
        }
        return java.util.Arrays.stream(children)
                .flatMap(file -> {
                    if (file.isDirectory()) {
                        return listYamlFiles(file).stream();
                    }
                    String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                    if (name.startsWith("_")) {
                        return java.util.stream.Stream.empty();
                    }
                    return name.endsWith(".yml") || name.endsWith(".yaml")
                            ? java.util.stream.Stream.of(file)
                            : java.util.stream.Stream.empty();
                })
                .sorted(Comparator.comparing(File::getPath))
                .toList();
    }

    private static String compiledDirectory(String resourcePath) {
        String normalized = resourcePath.replace('\\', '/');
        if (normalized.endsWith(".yml")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.endsWith(".yaml")) {
            return normalized.substring(0, normalized.length() - 5);
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static void mergeMap(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object existing = target.get(entry.getKey());
            Object incoming = entry.getValue();
            if (existing instanceof ConfigurationSection existingSection) {
                existing = sectionToMap(existingSection);
            }
            if (incoming instanceof ConfigurationSection incomingSection) {
                incoming = sectionToMap(incomingSection);
            }
            if (existing instanceof Map<?, ?> existingMap && incoming instanceof Map<?, ?> incomingMap) {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) existingMap);
                mergeMap(merged, (Map<String, Object>) incomingMap);
                target.put(entry.getKey(), merged);
            } else {
                target.put(entry.getKey(), incoming);
            }
        }
    }

    private static Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            map.put(key, value instanceof ConfigurationSection child ? sectionToMap(child) : value);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static void applyMap(ConfigurationSection section, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> childMap) {
                ConfigurationSection child = section.createSection(entry.getKey());
                applyMap(child, (Map<String, Object>) childMap);
            } else {
                section.set(entry.getKey(), value);
            }
        }
    }
}
