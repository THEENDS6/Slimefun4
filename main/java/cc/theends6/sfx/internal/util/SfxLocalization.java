package cc.theends6.sfx.internal.util;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.internal.addon.SfxAddonJarResources;
import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.api.behavior.SfxLocalizedListContext;
import cc.theends6.sfx.api.behavior.SfxLocalizedListPostProcessor;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxLocalization {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");

    private final JavaPlugin plugin;
    private final Set<String> warnedMissingPaths = ConcurrentHashMap.newKeySet();
    private YamlConfiguration bundled;
    private YamlConfiguration addon;
    private YamlConfiguration custom;

    public SfxLocalization(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        reload();
    }

    public void reload() {
        String language = language();
        this.bundled = loadBundled(language);
        this.addon = loadAddonOverlay(language);
        this.custom = loadCustom(language);
    }

    public String language() {
        return plugin.getConfig().getString("language", "zh-CN");
    }

    public boolean has(String path) {
        return lookup(path) != null;
    }

    public String text(String path) {
        return requiredText(path);
    }

    public String requiredText(String path) {
        String value = lookup(path);
        if (value != null) {
            return value;
        }
        warnMissing(path);
        return path;
    }

    public String text(String path, Map<String, ?> placeholders) {
        return applyPlaceholders(requiredText(path), placeholders);
    }

    public Component component(String path) {
        return render(requiredText(path));
    }

    public Component component(String path, Map<String, ?> placeholders) {
        return render(applyPlaceholders(requiredText(path), placeholders));
    }

    public Component categoryName(String categoryId) {
        String path = "categories." + sanitize(categoryId) + ".name";
        return render(requiredText(path));
    }

    public Component itemName(String itemId) {
        String path = "items." + sanitize(itemId) + ".name";
        return render(requiredText(path));
    }

    public Component researchName(String researchId) {
        String path = "researches." + sanitize(researchId) + ".name";
        return render(requiredText(path));
    }

    public List<Component> itemLore(String itemId) {
        List<String> lines = requiredList("items." + sanitize(itemId) + ".lore");
        List<Component> localized = new ArrayList<>();
        for (String line : lines) {
            localized.add(render(line));
        }
        return localized;
    }

    public List<Component> recipeNote(String itemId, int index) {
        String path = "recipes." + sanitize(itemId) + "." + index + ".note";
        String value = lookup(path);
        if (value == null) {
            return List.of();
        }
        return List.of(render(value));
    }

    public List<String> list(String path) {
        List<String> fromCustom = stringList(custom, path);
        if (!fromCustom.isEmpty()) {
            return postProcessList(path, fromCustom);
        }
        List<String> fromAddon = stringList(addon, path);
        if (!fromAddon.isEmpty()) {
            return postProcessList(path, fromAddon);
        }
        List<String> fromBundled = stringList(bundled, path);
        if (!fromBundled.isEmpty()) {
            return postProcessList(path, fromBundled);
        }
        List<String> indexed = indexedList(custom, path);
        if (!indexed.isEmpty()) {
            return postProcessList(path, indexed);
        }
        indexed = indexedList(addon, path);
        if (!indexed.isEmpty()) {
            return postProcessList(path, indexed);
        }
        return postProcessList(path, indexedList(bundled, path));
    }

    public List<String> requiredList(String path) {
        List<String> values = list(path);
        if (!values.isEmpty()) {
            return values;
        }
        String value = lookup(path);
        if (value != null) {
            return List.of(value);
        }
        warnMissing(path);
        return List.of(path);
    }

    private void warnMissing(String path) {
        if (path != null && warnedMissingPaths.add(path)) {
            plugin.getLogger().warning("Missing language key: " + path + " (displaying the key text)");
        }
    }

    private List<String> postProcessList(String path, List<String> values) {
        if (!(plugin instanceof SlimeFunXPlugin sfx)) {
            return values;
        }
        List<String> current = values;
        SfxLocalizedListContext context = new LocalizedListContext(path);
        for (SfxLocalizedListPostProcessor processor : sfx.api().behaviors().localizedListPostProcessors()) {
            List<String> provided = processor.apply(context, current);
            if (provided != null) {
                current = provided;
            }
        }
        return current;
    }

    private List<String> rawList(String path) {
        List<String> indexed = indexedList(custom, path);
        if (!indexed.isEmpty()) {
            return indexed;
        }
        indexed = indexedList(addon, path);
        if (!indexed.isEmpty()) {
            return indexed;
        }
        return indexedList(bundled, path);
    }

    private String rawText(String path) {
        String result = string(custom, path);
        if (result != null) {
            return result;
        }
        result = string(addon, path);
        if (result != null) {
            return result;
        }
        return string(bundled, path);
    }

    public Map<String, String> sectionStrings(String path) {
        Map<String, String> values = new LinkedHashMap<>();
        mergeSection(values, bundled, path);
        mergeSection(values, addon, path);
        mergeSection(values, custom, path);
        return values;
    }

    private void mergeSection(Map<String, String> values, YamlConfiguration configuration, String path) {
        if (configuration == null) {
            return;
        }
        ConfigurationSection section = section(configuration, path);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                values.put(key, value);
            }
        }
    }

    private String lookup(String path) {
        if (custom != null) {
            String value = string(custom, path);
            if (value != null) {
                return value;
            }
        }
        if (addon != null) {
            String value = string(addon, path);
            if (value != null) {
                return value;
            }
        }
        if (bundled != null) {
            String value = string(bundled, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static List<String> indexedList(YamlConfiguration configuration, String path) {
        if (configuration == null) {
            return List.of();
        }
        ConfigurationSection section = section(configuration, path);
        if (section == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int i = 0;
        while (section.contains(String.valueOf(i))) {
            String value = section.getString(String.valueOf(i));
            if (value != null) {
                values.add(value);
            }
            i++;
        }
        return values;
    }

    private static String string(YamlConfiguration configuration, String path) {
        Object value = compoundPathValue(configuration, path);
        return value instanceof String string ? string : null;
    }

    private static List<String> stringList(YamlConfiguration configuration, String path) {
        Object value = compoundPathValue(configuration, path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry != null) {
                result.add(String.valueOf(entry));
            }
        }
        return result;
    }

    private static ConfigurationSection section(YamlConfiguration configuration, String path) {
        Object value = compoundPathValue(configuration, path);
        return value instanceof ConfigurationSection section ? section : null;
    }

    private static Object compoundPathValue(YamlConfiguration configuration, String path) {
        if (configuration == null || path == null || path.isBlank()) {
            return null;
        }
        Object direct = configuration.get(path);
        if (direct != null) {
            return direct;
        }
        return compoundPathValue(configuration, path.split("\\."), 0);
    }

    private static Object compoundPathValue(ConfigurationSection section, String[] parts, int index) {
        if (section == null || index >= parts.length) {
            return section;
        }
        Map<String, Object> values = section.getValues(false);
        for (int end = parts.length; end > index; end--) {
            String key = String.join(".", java.util.Arrays.copyOfRange(parts, index, end));
            Object value = values.get(key);
            if (value == null) {
                continue;
            }
            if (end == parts.length) {
                return value;
            }
            if (value instanceof ConfigurationSection child) {
                Object nested = compoundPathValue(child, parts, end);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static String applyPlaceholders(String input, Map<String, ?> placeholders) {
        if (input == null || input.isBlank() || placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        Matcher matcher = PLACEHOLDER.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object replacement = placeholders.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement == null ? matcher.group(0) : String.valueOf(replacement)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private final class LocalizedListContext implements SfxLocalizedListContext {
        private final String path;

        private LocalizedListContext(String path) {
            this.path = path;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public List<String> rawList(String path) {
            return SfxLocalization.this.rawList(path);
        }

        @Override
        public String rawText(String path) {
            return SfxLocalization.this.rawText(path);
        }

        @Override
        public String applyPlaceholders(String value, Map<String, String> placeholders) {
            return SfxLocalization.applyPlaceholders(value, placeholders);
        }
    }

    private static Component render(String input) {
        if (input == null) {
            return Component.empty();
        }
        return Text.renderFlexible(input);
    }

    private static String sanitize(String input) {
        return Objects.requireNonNull(input, "input").toLowerCase().replace(':', '.').replace('/', '.');
    }

    private YamlConfiguration loadBundled(String language) {
        String resourcePath = "lang/" + language + ".yml";
        var stream = plugin.getResource(resourcePath);
        if (stream == null && !"zh-CN".equals(language)) {
            stream = plugin.getResource("lang/zh-CN.yml");
        }
        if (stream == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private YamlConfiguration loadCustom(String language) {
        File file = new File(new File(plugin.getDataFolder(), "lang"), language + ".yml");
        if (!file.isFile()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private YamlConfiguration loadAddonOverlay(String language) {
        YamlConfiguration merged = new YamlConfiguration();
        Set<String> loadedAddonFolders = loadedAddonFolders();
        loadBundledAddonOverlay(merged, language, loadedAddonFolders);
        loadExternalAddonJarOverlay(merged, language);
        loadDataFolderAddonOverlay(merged, new File(plugin.getDataFolder(), "content/addons"), language, loadedAddonFolders);
        loadDataFolderAddonOverlay(merged, new File(plugin.getDataFolder(), "addons"), language, loadedAddonFolders);
        return merged;
    }

    private void loadBundledAddonOverlay(YamlConfiguration merged, String language, Set<String> loadedAddonFolders) {
        if (loadedAddonFolders.isEmpty()) {
            return;
        }
        try {
            Path location = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(location)) {
                loadBundledAddonOverlayFromDirectory(merged, location, language, loadedAddonFolders);
            } else if (Files.isRegularFile(location)) {
                loadBundledAddonOverlayFromJar(merged, location, language, loadedAddonFolders);
            }
        } catch (URISyntaxException | IOException ex) {
            plugin.getLogger().warning("Failed to load bundled addon language overlay: " + ex.getMessage());
        }
    }

    private void loadBundledAddonOverlayFromDirectory(YamlConfiguration merged, Path location, String language, Set<String> loadedAddonFolders) throws IOException {
        Path addonRoot = location.resolve("content/addons");
        if (!Files.isDirectory(addonRoot)) {
            return;
        }
        try (var stream = Files.walk(addonRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(language + ".yml"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/lang/"))
                    .filter(path -> loadedAddonFolders.contains(addonRoot.relativize(path).getName(0).toString()))
                    .sorted()
                    .forEach(path -> mergeYaml(merged, YamlConfiguration.loadConfiguration(path.toFile())));
        }
    }

    private void loadBundledAddonOverlayFromJar(YamlConfiguration merged, Path location, String language, Set<String> loadedAddonFolders) throws IOException {
        try (JarFile jar = new JarFile(location.toFile())) {
            jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith("content/addons/"))
                    .filter(entry -> entry.getName().endsWith("/lang/" + language + ".yml"))
                    .filter(entry -> loadedAddonFolders.contains(addonFolderFromBundledEntry(entry.getName())))
                    .sorted(Comparator.comparing(java.util.jar.JarEntry::getName))
                    .forEach(entry -> {
                        try (var stream = jar.getInputStream(entry);
                             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                            mergeYaml(merged, YamlConfiguration.loadConfiguration(reader));
                        } catch (IOException ex) {
                            plugin.getLogger().warning("Failed to load addon language resource " + entry.getName() + ": " + ex.getMessage());
                        }
                    });
        }
    }

    private void loadDataFolderAddonOverlay(YamlConfiguration merged, File root, String language, Set<String> loadedAddonFolders) {
        if (!root.isDirectory()) {
            return;
        }
        File[] addons = root.listFiles(File::isDirectory);
        if (addons == null) {
            return;
        }
        java.util.Arrays.sort(addons, Comparator.comparing(File::getName));
        for (File addonDirectory : addons) {
            if (!loadedAddonFolders.contains(addonDirectory.getName())) {
                continue;
            }
            File file = new File(new File(addonDirectory, "lang"), language + ".yml");
            if (file.isFile()) {
                mergeYaml(merged, YamlConfiguration.loadConfiguration(file));
            }
        }
    }

    private Set<String> loadedAddonFolders() {
        if (!(plugin instanceof SlimeFunXPlugin sfx) || sfx.addonManager() == null) {
            return Set.of();
        }
        Set<String> folders = new HashSet<>();
        for (SfxAddon addon : sfx.addonManager().loadedAddons()) {
            String id = addon.id();
            if (id == null || id.isBlank()) {
                continue;
            }
            folders.add(id);
            folders.add(id.replace(':', '_'));
            int namespace = id.indexOf(':');
            if (namespace >= 0 && namespace + 1 < id.length()) {
                folders.add(id.substring(namespace + 1));
            }
        }
        return folders;
    }

    private static String addonFolderFromBundledEntry(String entryName) {
        if (entryName == null || !entryName.startsWith("content/addons/")) {
            return "";
        }
        String remainder = entryName.substring("content/addons/".length());
        int slash = remainder.indexOf('/');
        return slash < 0 ? remainder : remainder.substring(0, slash);
    }

    private void loadExternalAddonJarOverlay(YamlConfiguration merged, String language) {
        if (!(plugin instanceof SlimeFunXPlugin sfx) || sfx.addonManager() == null) {
            return;
        }
        for (File addonJar : sfx.addonManager().externalAddonJars()) {
            loadExternalAddonJarOverlay(merged, addonJar, language);
        }
    }

    private void loadExternalAddonJarOverlay(YamlConfiguration merged, File addonJar, String language) {
        if (addonJar == null || !addonJar.isFile()) {
            return;
        }
        try (JarFile jar = new JarFile(addonJar)) {
            jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> SfxAddonJarResources.isLanguageEntry(entry.getName(), language))
                    .sorted(SfxAddonJarResources.byEntryName())
                    .forEach(entry -> {
                        try (var stream = jar.getInputStream(entry);
                             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                            mergeYaml(merged, YamlConfiguration.loadConfiguration(reader));
                        } catch (IOException ex) {
                            plugin.getLogger().warning("Failed to load addon language resource " + entry.getName() + ": " + ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to load addon language overlay from " + addonJar.getName() + ": " + ex.getMessage());
        }
    }

    private static void mergeYaml(YamlConfiguration target, YamlConfiguration source) {
        for (String key : source.getKeys(true)) {
            Object value = source.get(key);
            if (!(value instanceof ConfigurationSection)) {
                target.set(key, value);
            }
        }
    }
}
