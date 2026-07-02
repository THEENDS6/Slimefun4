package cc.theends6.sfx.internal.template;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        boolean strictCompiled = compiledOnly(plugin);
        File baseFile = new File(plugin.getDataFolder(), resourcePath);
        YamlConfiguration base = !strictCompiled && baseFile.isFile()
                ? YamlConfiguration.loadConfiguration(baseFile)
                : new YamlConfiguration();
        Map<String, Object> mergedMap = sectionToMap(base);
        List<YamlConfiguration> bundledCompiled = loadBundledCompiled(plugin, resourcePath);
        boolean hasCompiled = !bundledCompiled.isEmpty();
        for (YamlConfiguration compiled : bundledCompiled) {
            mergeMap(mergedMap, sectionToMap(compiled));
        }
        File compiledRoot = new File(plugin.getDataFolder(), COMPILED_DIRECTORY + "/" + compiledDirectory(resourcePath));
        if (compiledRoot.isDirectory()) {
            List<File> files = listYamlFiles(compiledRoot);
            hasCompiled = hasCompiled || !files.isEmpty();
            for (File file : files) {
                YamlConfiguration compiled = YamlConfiguration.loadConfiguration(file);
                mergeMap(mergedMap, sectionToMap(compiled));
            }
        }
        if (!hasCompiled && strictCompiled) {
            throw new IllegalStateException("Compiled-only content runtime is enabled, but no compiled content was found for " + resourcePath);
        }
        YamlConfiguration merged = new YamlConfiguration();
        applyMap(merged, mergedMap);
        return merged;
    }

    public static List<YamlConfiguration> loadBundledCompiledUnder(JavaPlugin plugin, String resourcePrefix) {
        String normalizedPrefix = normalizeResource(resourcePrefix);
        return loadBundledCompiled(plugin).stream()
                .filter(entry -> normalizeResource(entry.target()).startsWith(normalizedPrefix)
                        || normalizeResource(entry.file()).startsWith(normalizedPrefix))
                .map(BundledCompiledEntry::yaml)
                .toList();
    }

    public static List<YamlConfiguration> loadCompiledUnder(JavaPlugin plugin, String resourcePrefix) {
        String normalizedPrefix = normalizeResource(resourcePrefix);
        List<YamlConfiguration> result = new ArrayList<>();
        File compiledRoot = new File(plugin.getDataFolder(), COMPILED_DIRECTORY + "/" + normalizedPrefix);
        if (compiledRoot.isDirectory()) {
            for (File file : listYamlFiles(compiledRoot)) {
                result.add(YamlConfiguration.loadConfiguration(file));
            }
        }
        if (result.isEmpty()) {
            result.addAll(loadBundledCompiledUnder(plugin, normalizedPrefix));
        }
        if (result.isEmpty() && compiledOnly(plugin)) {
            throw new IllegalStateException("Compiled-only content runtime is enabled, but no compiled content was found under " + resourcePrefix);
        }
        return List.copyOf(result);
    }

    private static List<YamlConfiguration> loadBundledCompiled(JavaPlugin plugin, String resourcePath) {
        String normalizedTarget = normalizeResource(resourcePath);
        return loadBundledCompiled(plugin).stream()
                .filter(entry -> normalizeResource(entry.target()).equals(normalizedTarget))
                .map(BundledCompiledEntry::yaml)
                .toList();
    }

    private static List<BundledCompiledEntry> loadBundledCompiled(JavaPlugin plugin) {
        InputStream manifestStream = plugin.getResource(COMPILED_DIRECTORY + "/_manifest.yml");
        if (manifestStream == null) {
            return List.of();
        }
        YamlConfiguration manifest;
        try (InputStream stream = manifestStream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            manifest = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to read bundled SFX compiled manifest: " + ex.getMessage());
            return List.of();
        }
        List<BundledCompiledEntry> result = new ArrayList<>();
        for (Map<?, ?> output : manifest.getMapList("outputs")) {
            String file = string(output.get("file"));
            if (file == null) {
                continue;
            }
            String target = string(output.get("target"));
            String resource = COMPILED_DIRECTORY + "/" + normalizeResource(file);
            try (InputStream stream = plugin.getResource(resource)) {
                if (stream == null) {
                    plugin.getLogger().warning("Bundled SFX compiled resource listed in manifest is missing: " + resource);
                    continue;
                }
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                result.add(new BundledCompiledEntry(file, target == null ? "" : target, yaml));
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to read bundled SFX compiled resource " + resource + ": " + ex.getMessage());
            }
        }
        result.sort(Comparator.comparing(BundledCompiledEntry::file));
        return List.copyOf(result);
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
        String normalized = normalizeResource(resourcePath);
        if (normalized.endsWith(".yml")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        if (normalized.endsWith(".yaml")) {
            return normalized.substring(0, normalized.length() - 5);
        }
        return normalized;
    }

    private static String normalizeResource(String resourcePath) {
        if (resourcePath == null) {
            return "";
        }
        String normalized = resourcePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean compiledOnly(JavaPlugin plugin) {
        return plugin.getConfig().getBoolean("content.runtime.compiled-only", true);
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

    private record BundledCompiledEntry(String file, String target, YamlConfiguration yaml) {
    }
}
