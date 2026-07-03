package cc.theends6.sfx.internal.template;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxCompiledYamlResolver {
    private static final String COMPILED_DIRECTORY = "content/compiled";
    private static final Set<String> WARNED_LOCAL_COMPILED_ROOTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> FORBIDDEN_COMPILED_KEYS = Set.of(
            "menu-style",
            "layout",
            "tag-fuels",
            "expand",
            "id-prefix",
            "input-prefix",
            "input-amount",
            "profile",
            "@cf",
            "@copyfrom",
            "@d",
            "@define",
            "@g",
            "@global",
            "@io",
            "@isoutput",
            "@it",
            "@istemplate",
            "@mi",
            "@mergeinto",
            "@ot",
            "@outputtarget",
            "@p",
            "@project"
    );

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
        if (compiledRoot.isDirectory() && localCompiledIsCurrent(plugin)) {
            List<File> files = listYamlFiles(compiledRoot);
            hasCompiled = hasCompiled || !files.isEmpty();
            for (File file : files) {
                YamlConfiguration compiled = YamlConfiguration.loadConfiguration(file);
                validateCompiledShape(compiled, "local " + file.getAbsolutePath());
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
        if (compiledRoot.isDirectory() && localCompiledIsCurrent(plugin)) {
            for (File file : listYamlFiles(compiledRoot)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                validateCompiledShape(yaml, "local " + file.getAbsolutePath());
                result.add(yaml);
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
        YamlConfiguration manifest = loadBundledManifest(plugin);
        if (manifest == null) {
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
                validateCompiledShape(yaml, "bundled " + resource);
                result.add(new BundledCompiledEntry(file, target == null ? "" : target, yaml));
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to read bundled SFX compiled resource " + resource + ": " + ex.getMessage());
            }
        }
        result.sort(Comparator.comparing(BundledCompiledEntry::file));
        return List.copyOf(result);
    }

    private static boolean localCompiledIsCurrent(JavaPlugin plugin) {
        File localRoot = new File(plugin.getDataFolder(), COMPILED_DIRECTORY);
        if (!localRoot.isDirectory()) {
            return false;
        }
        File manifestFile = new File(localRoot, "_manifest.yml");
        if (!manifestFile.isFile()) {
            warnLocalCompiledIgnored(plugin, "missing _manifest.yml");
            return false;
        }
        YamlConfiguration localManifest;
        try {
            localManifest = YamlConfiguration.loadConfiguration(manifestFile);
        } catch (Exception ex) {
            warnLocalCompiledIgnored(plugin, "unreadable _manifest.yml: " + ex.getMessage());
            return false;
        }
        YamlConfiguration bundledManifest = loadBundledManifest(plugin);
        if (bundledManifest == null) {
            return true;
        }
        for (String key : List.of("compiler", "compiler-version", "template-hash", "content-version")) {
            String localValue = string(localManifest.get(key));
            String bundledValue = string(bundledManifest.get(key));
            if (!Objects.equals(localValue, bundledValue)) {
                warnLocalCompiledIgnored(plugin, key + " mismatch (local=" + localValue + ", bundled=" + bundledValue + ")");
                return false;
            }
        }
        return localCompiledOutputsMatch(plugin, localRoot, localManifest);
    }

    private static boolean localCompiledOutputsMatch(JavaPlugin plugin, File localRoot, YamlConfiguration manifest) {
        Set<String> expectedFiles = new LinkedHashSet<>();
        Path rootPath = localRoot.toPath().toAbsolutePath().normalize();
        for (Map<?, ?> output : manifest.getMapList("outputs")) {
            String file = normalizeResource(string(output.get("file")));
            String expectedSha = string(output.get("sha256"));
            if (file == null || file.isBlank()) {
                warnLocalCompiledIgnored(plugin, "_manifest.yml output missing file");
                return false;
            }
            if (expectedSha == null || expectedSha.isBlank()) {
                warnLocalCompiledIgnored(plugin, "_manifest.yml output missing sha256 for " + file);
                return false;
            }
            expectedFiles.add(file);
            Path outputPath = rootPath.resolve(file).normalize();
            if (!outputPath.startsWith(rootPath)) {
                warnLocalCompiledIgnored(plugin, "_manifest.yml output escapes compiled root: " + file);
                return false;
            }
            File outputFile = outputPath.toFile();
            if (!outputFile.isFile()) {
                warnLocalCompiledIgnored(plugin, "_manifest.yml output missing from local compiled content: " + file);
                return false;
            }
            String actualSha;
            try {
                actualSha = sha256(outputPath);
            } catch (IllegalStateException ex) {
                warnLocalCompiledIgnored(plugin, ex.getMessage());
                return false;
            }
            if (!expectedSha.equalsIgnoreCase(actualSha)) {
                warnLocalCompiledIgnored(plugin, "_manifest.yml sha256 mismatch for " + file);
                return false;
            }
        }
        for (File file : listYamlFiles(localRoot)) {
            String relative = normalizeResource(rootPath.relativize(file.toPath().toAbsolutePath().normalize()).toString());
            if (!expectedFiles.contains(relative)) {
                warnLocalCompiledIgnored(plugin, "local compiled output is not listed in _manifest.yml: " + relative);
                return false;
            }
        }
        return true;
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot hash compiled output " + file + ": " + ex.getMessage(), ex);
        }
    }

    private static YamlConfiguration loadBundledManifest(JavaPlugin plugin) {
        InputStream manifestStream = plugin.getResource(COMPILED_DIRECTORY + "/_manifest.yml");
        if (manifestStream == null) {
            return null;
        }
        try (InputStream stream = manifestStream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to read bundled SFX compiled manifest: " + ex.getMessage());
            return null;
        }
    }

    private static void warnLocalCompiledIgnored(JavaPlugin plugin, String reason) {
        String root = new File(plugin.getDataFolder(), COMPILED_DIRECTORY).getAbsolutePath();
        String key = root + "|" + reason;
        if (WARNED_LOCAL_COMPILED_ROOTS.add(key)) {
            plugin.getLogger().warning("Ignoring local SFX compiled content at " + root + ": " + reason
                    + ". Recompile templates or remove the stale directory; bundled compiled content will be used when available.");
        }
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
                    String name = file.getName().toLowerCase(Locale.ROOT);
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

    private static void validateCompiledShape(YamlConfiguration yaml, String sourceName) {
        validateCompiledNode(sectionToMap(yaml), sourceName, "$");
    }

    private static void validateCompiledNode(Object value, String sourceName, String path) {
        if (value instanceof ConfigurationSection section) {
            validateCompiledNode(sectionToMap(section), sourceName, path);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalized = key.trim().toLowerCase(Locale.ROOT);
                String childPath = path.equals("$") ? key : path + "." + key;
                if (normalized.startsWith("@") || FORBIDDEN_COMPILED_KEYS.contains(normalized)) {
                    throw new IllegalStateException("Compiled SFX content " + sourceName
                            + " contains template or layout helper key at " + childPath + ": " + key);
                }
                validateCompiledNode(entry.getValue(), sourceName, childPath);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                validateCompiledNode(list.get(i), sourceName, path + "[" + i + "]");
            }
            return;
        }
        if (value instanceof String text && text.contains("${")) {
            throw new IllegalStateException("Compiled SFX content " + sourceName
                    + " contains unresolved template placeholder at " + path);
        }
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
