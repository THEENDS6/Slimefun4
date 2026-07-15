package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.internal.behavior.DefaultSfxBehaviorRegistry;
import cc.theends6.sfx.internal.feature.DefaultSfxFeatureRegistry;
import cc.theends6.sfx.internal.feature.SfxFeatureRegistrarView;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxAddonManager implements AutoCloseable {
    private static final String BASIC_EXPANSION_ID = "sfx:basic_expansion";
    private static final String BASIC_EXPANSION_RESOURCE = "bundled-addons/sfx-basic-expansion.jar";
    private static final String CONTENT_EXPANSION_RESOURCE = "bundled-addons/sfx-content-expansion.jar";
    private static final String EXAMPLE_ADDON_ID = "sfx:example";
    private static final String EXAMPLE_ADDON_RESOURCE = "bundled-addons/sfx-example-addon.jar";
    private static final String ADDON_MANIFEST = "addon.yml";
    private static final String LEGACY_ADDON_MANIFEST = "sfx-addon.yml";
    private static final int ADDON_API_VERSION = 1;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final SfxApi api;
    private final DefaultSfxFeatureRegistry features;
    private final DefaultSfxBehaviorRegistry behaviors;
    private final Map<String, SfxAddon> loaded = new LinkedHashMap<>();
    private final Map<String, File> externalJars = new LinkedHashMap<>();
    private final List<Closeable> externalLoaders = new ArrayList<>();

    public SfxAddonManager(JavaPlugin plugin, Logger logger, SfxApi api, DefaultSfxFeatureRegistry features, DefaultSfxBehaviorRegistry behaviors) {
        this.plugin = plugin;
        this.logger = logger;
        this.api = api;
        this.features = features;
        this.behaviors = behaviors;
    }

    public void loadBundledAddons() {
        loadBundledAddons(true, true);
    }

    public void loadBundledAddons(boolean basicExpansionEnabled) {
        loadBundledAddons(basicExpansionEnabled, true);
    }

    public void loadBundledAddons(boolean basicExpansionEnabled, boolean exampleAddonEnabled) {
        if (!basicExpansionEnabled) {
            if (logger != null) {
                logger.info("Skipped disabled bundled SFX addon " + BASIC_EXPANSION_ID);
            }
        } else {
            File addonJar = prepareBundledAddonJar(BASIC_EXPANSION_RESOURCE);
            loadAddonJar(addonJar, "bundled", false);
        }
        File contentAddonJar = prepareBundledAddonJar(CONTENT_EXPANSION_RESOURCE);
        loadAddonJar(contentAddonJar, "bundled", false);
        if (!exampleAddonEnabled) {
            if (logger != null) {
                logger.info("Skipped disabled bundled SFX addon " + EXAMPLE_ADDON_ID);
            }
        } else {
            File exampleAddonJar = prepareBundledAddonJar(EXAMPLE_ADDON_RESOURCE);
            loadAddonJar(exampleAddonJar, "bundled", false);
        }
    }

    public void loadExternalAddons(File addonsDir) {
        if (addonsDir == null) {
            return;
        }
        if (!addonsDir.isDirectory()) {
            if (!addonsDir.mkdirs() && logger != null) {
                logger.warning("Failed to create SFX addon directory: " + addonsDir.getPath());
            }
            return;
        }
        File[] files = addonsDir.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
        if (files != null && files.length > 0) {
            java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
            for (File file : files) {
                loadAddonJar(file, "external", true);
            }
        }
        loadConfigAddons(addonsDir);
    }

    private File prepareBundledAddonJar(String resourcePath) {
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled addon jar resource " + resourcePath);
            }
            File addonsDir = new File(plugin.getDataFolder(), "addons");
            if (!addonsDir.isDirectory() && !addonsDir.mkdirs()) {
                throw new IOException("Failed to create addon directory " + addonsDir.getPath());
            }
            File tempJar = File.createTempFile("sfx-bundled-addon-", ".jar", addonsDir);
            Files.copy(input, tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            YamlConfiguration manifest = readManifest(tempJar);
            String id = manifest.getString("id", BASIC_EXPANSION_ID).trim();
            File addonDirectory = new File(addonsDir, folderName(id));
            if (!addonDirectory.isDirectory() && !addonDirectory.mkdirs()) {
                throw new IOException("Failed to create bundled addon directory " + addonDirectory.getPath());
            }
            String jarName = manifest.getString("java.jar", "sfx-basic-expansion.jar").trim();
            File addonJar = new File(addonsDir, jarName);
            replaceFileIfChanged(tempJar, addonJar);
            if (!tempJar.delete() && tempJar.exists() && logger != null) {
                logger.warning("Failed to delete temporary bundled addon jar " + tempJar.getPath());
            }
            deleteStaleDirectoryJars(addonDirectory);
            expandDefaultResources(addonJar, addonDirectory);
            return addonJar;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to prepare bundled SFX addon " + resourcePath + ": " + exception.getMessage(), exception);
        }
    }

    private void replaceFileIfChanged(File source, File target) throws IOException {
        if (target.isFile() && sameFileContent(source, target)) {
            return;
        }
        Files.createDirectories(target.toPath().getParent());
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean sameFileContent(File left, File right) throws IOException {
        if (!left.isFile() || !right.isFile() || left.length() != right.length()) {
            return false;
        }
        byte[] leftBytes = Files.readAllBytes(left.toPath());
        byte[] rightBytes = Files.readAllBytes(right.toPath());
        return java.util.Arrays.equals(leftBytes, rightBytes);
    }

    private void deleteStaleDirectoryJars(File addonDirectory) throws IOException {
        File[] staleJars = addonDirectory.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
        if (staleJars == null || staleJars.length == 0) {
            return;
        }
        for (File staleJar : staleJars) {
            Files.deleteIfExists(staleJar.toPath());
        }
    }

    private void expandDefaultResources(File addonJar, File addonDirectory) throws IOException {
        try (JarFile jar = new JarFile(addonJar)) {
            for (JarEntry entry : jar.stream()
                    .filter(candidate -> !candidate.isDirectory())
                    .sorted(SfxAddonJarResources.byEntryName())
                    .toList()) {
                String name = entry.getName().replace('\\', '/');
                if (!isExtractableDefaultResource(name)) {
                    continue;
                }
                File target = new File(addonDirectory, name);
                if (target.isFile()) {
                    continue;
                }
                Files.createDirectories(target.toPath().getParent());
                try (InputStream input = jar.getInputStream(entry)) {
                    Files.copy(input, target.toPath());
                }
            }
        }
    }

    private static boolean isExtractableDefaultResource(String name) {
        return name.equals(ADDON_MANIFEST)
                || name.equals(LEGACY_ADDON_MANIFEST)
                || name.equals("config.yml")
                || name.startsWith("content/")
                || name.startsWith("lang/");
    }

    private void loadConfigAddons(File addonsDir) {
        File[] directories = addonsDir.listFiles(File::isDirectory);
        if (directories == null || directories.length == 0) {
            return;
        }
        java.util.Arrays.sort(directories, java.util.Comparator.comparing(File::getName));
        for (File directory : directories) {
            File manifestFile = manifestFile(directory);
            if (!manifestFile.isFile()) {
                continue;
            }
            try {
                YamlConfiguration manifest = YamlConfiguration.loadConfiguration(manifestFile);
                if (!manifest.getBoolean("enabled", true)) {
                    if (logger != null) {
                        logger.info("Skipped disabled SFX config addon " + directory.getName());
                    }
                    continue;
                }
                String id = manifest.getString("id", directory.getName()).trim();
                if (isLoaded(id)) {
                    continue;
                }
                String mainClassName = manifest.getString("main", "").trim();
                if (!mainClassName.isEmpty()) {
                    if (logger != null) {
                        logger.warning("Ignoring Java SFX addon descriptor in config directory " + directory.getName()
                                + ". Java addons must be installed as jar files directly under " + addonsDir.getPath() + ".");
                    }
                    continue;
                }
                String name = manifest.getString("name", id).trim();
                validateManifest(manifest, manifestFile.getPath(), false);
                load(new ConfigAddon(id, name, manifestFeatures(manifest)), "config", directory, id);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Failed to load SFX config addon " + directory.getName() + ": " + exception.getMessage(), exception);
            }
        }
    }

    private synchronized boolean isLoaded(String id) {
        return id != null && loaded.containsKey(id);
    }

    private static File manifestFile(File directory) {
        File manifest = new File(directory, ADDON_MANIFEST);
        if (manifest.isFile()) {
            return manifest;
        }
        return new File(directory, LEGACY_ADDON_MANIFEST);
    }

    private List<ManifestFeature> manifestFeatures(YamlConfiguration manifest) {
        List<ManifestFeature> result = new ArrayList<>();
        for (Map<?, ?> raw : manifest.getMapList("features")) {
            String id = string(raw.get("id"));
            String config = string(raw.get("config"));
            Object enabled = raw.containsKey("default-enabled") ? raw.get("default-enabled") : raw.get("enabled");
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Config addon feature id must not be blank.");
            }
            if (config == null || config.isBlank()) {
                throw new IllegalArgumentException("Config addon feature " + id + " config path must not be blank.");
            }
            result.add(new ManifestFeature(id.trim(), config.trim(), booleanValue(enabled, true)));
        }
        return List.copyOf(result);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public void loadAll(List<SfxAddon> addons) {
        loadAll(addons, AddonSource.EXTERNAL);
    }

    private void loadAll(List<SfxAddon> addons, AddonSource source) {
        List<SfxAddon> snapshot = new ArrayList<>(addons == null ? List.of() : addons);
        for (SfxAddon addon : snapshot) {
            String id = addon == null ? "addon" : addon.id();
            File directory = new File(new File(plugin.getDataFolder(), "addons"), folderName(id));
            load(addon, source.description, directory, id);
        }
    }

    public synchronized List<SfxAddon> loadedAddons() {
        return Collections.unmodifiableList(new ArrayList<>(loaded.values()));
    }

    public synchronized List<File> externalAddonJars() {
        return Collections.unmodifiableList(new ArrayList<>(externalJars.values()));
    }

    private void loadAddonJar(File file, String source, boolean exposeJarResources) {
        try {
            if (exposeJarResources) {
                rejectInternalApiReferences(file);
            }
            URLClassLoader loader = new URLClassLoader(new URL[] {file.toURI().toURL()}, SfxAddon.class.getClassLoader());
            try {
                YamlConfiguration manifest = readManifest(file);
                validateManifest(manifest, file.getPath(), true);
                if (!manifest.getBoolean("enabled", true)) {
                    loader.close();
                    if (logger != null) {
                        logger.info("Skipped disabled SFX addon jar " + file.getName());
                    }
                    return;
                }
                String id = manifest.getString("id", "").trim();
                if (!id.isEmpty() && isLoaded(id)) {
                    loader.close();
                    return;
                }
                String mainClassName = manifest.getString("main", "").trim();
                if (mainClassName.isEmpty()) {
                    throw new IllegalStateException(ADDON_MANIFEST + " is missing main");
                }
                Class<?> mainClass = Class.forName(mainClassName, true, loader);
                if (!SfxAddon.class.isAssignableFrom(mainClass)) {
                    throw new IllegalStateException("Main class does not implement " + SfxAddon.class.getName() + ": " + mainClassName);
                }
                SfxAddon addon = (SfxAddon) mainClass.getDeclaredConstructor().newInstance();
                File addonDirectory = new File(new File(plugin.getDataFolder(), "addons"), folderName(id));
                if (!addonDirectory.isDirectory() && !addonDirectory.mkdirs()) {
                    throw new IOException("Failed to create addon data directory " + addonDirectory);
                }
                expandDefaultResources(file, addonDirectory);
                load(addon, source, addonDirectory, id);
                synchronized (this) {
                    if (exposeJarResources) {
                        externalJars.put(addon.id(), file);
                    }
                    externalLoaders.add(loader);
                }
            } catch (Throwable throwable) {
                loader.close();
                throw throwable;
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to load SFX addon jar " + file.getName() + ": " + throwable.getMessage(), throwable);
        }
    }

    private static YamlConfiguration readManifest(File file) throws IOException {
        try (JarFile jar = new JarFile(file)) {
            JarEntry manifestEntry = jar.getJarEntry(ADDON_MANIFEST);
            if (manifestEntry == null) {
                manifestEntry = jar.getJarEntry(LEGACY_ADDON_MANIFEST);
            }
            if (manifestEntry == null) {
                throw new IllegalStateException("Missing " + ADDON_MANIFEST);
            }
            try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(manifestEntry), StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        }
    }

    private static void rejectInternalApiReferences(File file) throws IOException {
        try (JarFile jar = new JarFile(file)) {
            for (JarEntry entry : jar.stream().filter(candidate -> candidate.getName().endsWith(".class")).toList()) {
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                String constantPool = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (constantPool.contains("cc/theends6/sfx/internal/")) {
                    throw new IllegalStateException("Addon class " + entry.getName()
                            + " references unsupported cc.theends6.sfx.internal implementation classes");
                }
            }
        }
    }

    private static String folderName(String addonId) {
        String id = addonId == null || addonId.isBlank() ? "addon" : addonId.trim();
        int namespace = id.indexOf(':');
        if (namespace >= 0 && namespace + 1 < id.length()) {
            id = id.substring(namespace + 1);
        }
        return id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private void load(SfxAddon addon, String source, File dataDirectory, String manifestId) {
        if (addon == null || addon.id() == null || addon.id().isBlank()) {
            throw new IllegalArgumentException("SFX addon id must not be blank.");
        }
        if (manifestId != null && !manifestId.equals(addon.id())) {
            throw new IllegalStateException("Addon manifest id " + manifestId
                    + " does not match Java addon id " + addon.id());
        }
        synchronized (this) {
            if (loaded.containsKey(addon.id())) {
                throw new IllegalStateException("Duplicate SFX addon id: " + addon.id());
            }
        }
        org.bukkit.configuration.file.FileConfiguration addonConfig = SfxAddonContextImpl.loadConfig(dataDirectory);
        addon.onLoad(new SfxAddonContextImpl(plugin, api,
                new SfxFeatureRegistrarView(addon.id(), features, addonConfig), behaviors, dataDirectory, addonConfig));
        synchronized (this) {
            loaded.put(addon.id(), addon);
        }
        if (logger != null) {
            logger.info("Loaded " + source + " SFX addon " + addon.id() + " (" + addon.name() + ")");
        }
    }

    @Override
    public void close() {
        List<SfxAddon> addons;
        List<Closeable> loaders;
        synchronized (this) {
            addons = new ArrayList<>(loaded.values());
            loaders = new ArrayList<>(externalLoaders);
            externalLoaders.clear();
            externalJars.clear();
            loaded.clear();
        }
        Collections.reverse(addons);
        for (SfxAddon addon : addons) {
            try {
                addon.onDisable();
            } catch (Throwable exception) {
                if (logger != null) {
                    logger.warning("Failed to disable SFX addon " + addon.id() + ": " + exception.getMessage());
                }
            }
        }
        for (Closeable loader : loaders) {
            try {
                loader.close();
            } catch (IOException exception) {
                if (logger != null) {
                    logger.warning("Failed to close SFX addon classloader: " + exception.getMessage());
                }
            }
        }
    }

    private static void validateManifest(YamlConfiguration manifest, String source, boolean javaAddon) {
        String id = manifest.getString("id", "").trim();
        if (!id.matches("[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*")) {
            throw new IllegalStateException("Invalid addon id in " + source
                    + "; expected lowercase namespace:name, got " + id);
        }
        int apiVersion = manifest.getInt("api-version", ADDON_API_VERSION);
        if (apiVersion != ADDON_API_VERSION) {
            throw new IllegalStateException("Unsupported addon api-version " + apiVersion + " in " + source
                    + "; runtime supports " + ADDON_API_VERSION);
        }
        if (javaAddon && manifest.getString("main", "").isBlank()) {
            throw new IllegalStateException(source + " is missing main");
        }
    }

    private enum AddonSource {
        EXTERNAL("external");

        private final String description;

        AddonSource(String description) {
            this.description = description;
        }
    }

    private record ConfigAddon(String id, String name, List<ManifestFeature> features) implements SfxAddon {
        private ConfigAddon {
            features = List.copyOf(Objects.requireNonNull(features, "features"));
        }

        @Override
        public void onLoad(cc.theends6.sfx.api.addon.SfxAddonContext context) {
            for (ManifestFeature feature : features) {
                context.features().registerBoolean(feature.id(), feature.configPath(), feature.defaultEnabled());
            }
        }
    }

    private record ManifestFeature(String id, String configPath, boolean defaultEnabled) {
    }
}
