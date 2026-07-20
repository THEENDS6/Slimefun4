package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.internal.behavior.DefaultSfxBehaviorRegistry;
import cc.theends6.sfx.internal.SfxApiImpl;
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
    private static final String RESEARCH_EXPANSION_RESOURCE = "bundled-addons/sfx-research-expansion.jar";
    private static final String EXAMPLE_ADDON_ID = "sfx:example";
    private static final String EXAMPLE_ADDON_RESOURCE = "bundled-addons/sfx-example-addon.jar";
    private static final String ADDON_MANIFEST = "addon.yml";
    private static final String LEGACY_ADDON_MANIFEST = "sfx-addon.yml";
    private static final int ADDON_API_VERSION = 2;
    private static final java.util.Set<Integer> SUPPORTED_ADDON_API_VERSIONS = java.util.Set.of(1, 2);

    private final JavaPlugin plugin;
    private final Logger logger;
    private final SfxApi api;
    private final DefaultSfxFeatureRegistry features;
    private final DefaultSfxBehaviorRegistry behaviors;
    private final DefaultSfxComponentOverrideRegistry componentOverrides;
    private final SfxAddonResourceRegistry addonResources;
    private final SfxAddonDomainRegistries addonDomains = new SfxAddonDomainRegistries();
    private final Map<String, SfxAddon> loaded = new LinkedHashMap<>();
    private final Map<String, File> externalJars = new LinkedHashMap<>();
    private final Map<String, File> resourceJars = new LinkedHashMap<>();
    private final List<Closeable> externalLoaders = new ArrayList<>();

    public SfxAddonManager(JavaPlugin plugin, Logger logger, SfxApi api, DefaultSfxFeatureRegistry features,
                           DefaultSfxBehaviorRegistry behaviors,
                           DefaultSfxComponentOverrideRegistry componentOverrides) {
        this.plugin = plugin;
        this.logger = logger;
        this.api = api;
        this.features = features;
        this.behaviors = behaviors;
        this.componentOverrides = Objects.requireNonNull(componentOverrides, "componentOverrides");
        this.addonResources = new SfxAddonResourceRegistry(plugin);
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
        File researchAddonJar = prepareBundledAddonJar(RESEARCH_EXPANSION_RESOURCE);
        loadAddonJar(researchAddonJar, "bundled", false);
        if (!exampleAddonEnabled) {
            if (logger != null) {
                logger.info("Skipped disabled bundled SFX addon " + EXAMPLE_ADDON_ID);
            }
        } else {
            File exampleAddonJar = prepareBundledAddonJar(EXAMPLE_ADDON_RESOURCE);
            loadAddonJar(exampleAddonJar, "bundled", false);
        }
    }

    
    public void loadConfiguredAddons(boolean basicExpansionEnabled, boolean researchExpansionEnabled,
                                     boolean exampleAddonEnabled, File addonsDir) {
        List<AddonJarCandidate> candidates = new ArrayList<>();
        if (basicExpansionEnabled) {
            candidates.add(new AddonJarCandidate(prepareBundledAddonJar(BASIC_EXPANSION_RESOURCE), "bundled", false));
        } else if (logger != null) {
            logger.info("Skipped disabled bundled SFX addon " + BASIC_EXPANSION_ID);
        }
        candidates.add(new AddonJarCandidate(prepareBundledAddonJar(CONTENT_EXPANSION_RESOURCE), "bundled", false));
        if (researchExpansionEnabled) {
            candidates.add(new AddonJarCandidate(prepareBundledAddonJar(RESEARCH_EXPANSION_RESOURCE), "bundled", false));
        } else if (logger != null) {
            logger.info("Skipped disabled bundled SFX addon sfx:research_expansion");
        }
        if (exampleAddonEnabled) {
            candidates.add(new AddonJarCandidate(prepareBundledAddonJar(EXAMPLE_ADDON_RESOURCE), "bundled", false));
        } else if (logger != null) {
            logger.info("Skipped disabled bundled SFX addon " + EXAMPLE_ADDON_ID);
        }

        if (addonsDir != null && !addonsDir.isDirectory() && !addonsDir.mkdirs()) {
            throw new IllegalStateException("Failed to create SFX addon directory: " + addonsDir.getPath());
        }
        if (addonsDir != null) {
            File[] files = addonsDir.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
            if (files != null) {
                java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
                java.util.Set<String> bundledPaths = candidates.stream()
                        .map(candidate -> candidate.file().getAbsolutePath())
                        .collect(java.util.stream.Collectors.toSet());
                java.util.Set<String> bundledJarNames = java.util.Set.of(
                        "sfx-basic-expansion.jar",
                        "sfx-content-expansion.jar",
                        "sfx-research-expansion.jar",
                        "sfx-example-addon.jar");
                for (File file : files) {
                    if (!bundledPaths.contains(file.getAbsolutePath())
                            && !bundledJarNames.contains(file.getName().toLowerCase(java.util.Locale.ROOT))) {
                        candidates.add(new AddonJarCandidate(file, "external", true));
                    }
                }
            }
        }

        List<AddonJarCandidate> accepted = new ArrayList<>();
        java.util.Set<String> discoveredAddonIds = new java.util.LinkedHashSet<>();
        for (AddonJarCandidate candidate : candidates) {
            if (preflightAddonJar(candidate.file(), discoveredAddonIds)) {
                accepted.add(candidate);
            }
        }
        for (AddonJarCandidate candidate : sortByDependencies(accepted)) {
            loadAddonJar(candidate.file(), candidate.source(), candidate.exposeJarResources());
        }
        loadConfigAddons(addonsDir);
        componentOverrides.validateImplementations();
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
            List<AddonJarCandidate> accepted = new ArrayList<>();
            java.util.Set<String> discoveredAddonIds = new java.util.LinkedHashSet<>();
            for (File file : files) {
                if (preflightAddonJar(file, discoveredAddonIds)) {
                    accepted.add(new AddonJarCandidate(file, "external", true));
                }
            }
            for (AddonJarCandidate candidate : sortByDependencies(accepted)) {
                loadAddonJar(candidate.file(), candidate.source(), candidate.exposeJarResources());
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

    public synchronized List<File> addonResourceJars() {
        return Collections.unmodifiableList(new ArrayList<>(resourceJars.values()));
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
                reserveManifestOverrides(id, manifest);
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
                    resourceJars.put(addon.id(), file);
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
        boolean callbackStarted = false;
        try (SfxAddonRegistrationSession registration = new SfxAddonRegistrationSession(
                addon.id(), api, features, behaviors, componentOverrides,
                addonResources.view(addon.id()), addonDomains.views(addon.id()), addonConfig)) {
            callbackStarted = true;
            SfxAddonContextImpl context = new SfxAddonContextImpl(plugin, registration.api(), registration.features(),
                    registration.behaviors(), registration.overrides(), registration.resources(),
                    registration.domains(),
                    dataDirectory, addonConfig);
            addon.onRegister(context);
            registration.commit();
            addon.onEnable(context);
        } catch (Throwable throwable) {
            unregisterAll(addon.id());
            if (callbackStarted) {
                try {
                    addon.onDisable();
                } catch (Throwable cleanupFailure) {
                    throwable.addSuppressed(cleanupFailure);
                }
            }
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Addon registration failed for " + addon.id(), throwable);
        }
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
            resourceJars.clear();
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
            unregisterAll(addon.id());
        }
        componentOverrides.clear();
        addonResources.clear();
        addonDomains.clear();
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

    private void unregisterAll(String addonId) {
        features.removeOwner(addonId);
        behaviors.removeOwner(addonId);
        componentOverrides.removeOwner(addonId);
        addonResources.unregisterAll(addonId);
        addonDomains.removeOwner(addonId);
        if (api instanceof SfxApiImpl implementation) {
            implementation.internalItemRegistry().removeOwner(addonId);
            implementation.internalManualMachines().removeOwner(addonId);
        }
    }

    public java.util.Optional<cc.theends6.sfx.api.block.SfxBlockType<?>> blockType(String id) {
        return addonDomains.block(id);
    }

    public java.util.Collection<cc.theends6.sfx.api.randomtick.SfxRandomTickType<?>> randomTickTypes() {
        return addonDomains.randomTickTypes();
    }

    public java.util.Optional<cc.theends6.sfx.api.display.SfxDisplayCategory> displayCategory(String id) {
        return addonDomains.displayCategory(id);
    }

    public java.util.Optional<cc.theends6.sfx.api.display.SfxDisplayType> displayType(String id) {
        return addonDomains.displayType(id);
    }

    public java.util.Optional<cc.theends6.sfx.api.container.SfxVirtualContainerType> containerType(String id) {
        return addonDomains.containerType(id);
    }

    public java.util.Optional<cc.theends6.sfx.api.machine.continuous.SfxContinuousManualMachine> continuousMachine(String id) {
        return addonDomains.continuousMachine(id);
    }

    public java.util.Optional<cc.theends6.sfx.api.power.SfxPoweredItem> poweredItem(String id) {
        return addonDomains.poweredItem(id);
    }

    private static void validateManifest(YamlConfiguration manifest, String source, boolean javaAddon) {
        String id = manifest.getString("id", "").trim();
        if (!id.matches("[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*")) {
            throw new IllegalStateException("Invalid addon id in " + source
                    + "; expected lowercase namespace:name, got " + id);
        }
        int apiVersion = manifest.getInt("api-version", 1);
        if (!SUPPORTED_ADDON_API_VERSIONS.contains(apiVersion)) {
            throw new IllegalStateException("Unsupported addon api-version " + apiVersion + " in " + source
                    + "; runtime supports " + SUPPORTED_ADDON_API_VERSIONS);
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
        public void onRegister(cc.theends6.sfx.api.addon.SfxAddonContext context) {
            for (ManifestFeature feature : features) {
                context.features().registerBoolean(feature.id(), feature.configPath(), feature.defaultEnabled());
            }
        }
    }

    private record ManifestFeature(String id, String configPath, boolean defaultEnabled) {
    }

    private boolean preflightAddonJar(File file, java.util.Set<String> discoveredAddonIds) {
        try {
            YamlConfiguration manifest = readManifest(file);
            validateManifest(manifest, file.getPath(), true);
            if (!manifest.getBoolean("enabled", true)) {
                if (logger != null) {
                    logger.info("Skipped disabled SFX addon jar " + file.getName());
                }
                return false;
            }
            String addonId = manifest.getString("id", "").trim();
            if (!discoveredAddonIds.add(addonId)) {
                if (logger != null) {
                    logger.warning("Skipped duplicate SFX addon id " + addonId + " from " + file.getName());
                }
                return false;
            }
            reserveManifestOverrides(addonId, manifest);
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect SFX addon jar " + file.getName(), exception);
        }
    }

    private void reserveManifestOverrides(String addonId, YamlConfiguration manifest) {
        for (Map<?, ?> raw : manifest.getMapList("overrides")) {
            String target = string(raw.get("target"));
            Object versionRaw = raw.containsKey("contract-version") ? raw.get("contract-version") : 1;
            int contractVersion = versionRaw instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(versionRaw));
            componentOverrides.claim(addonId, target, contractVersion);
        }
    }

    private record AddonJarCandidate(File file, String source, boolean exposeJarResources) {
    }

    private List<AddonJarCandidate> sortByDependencies(List<AddonJarCandidate> candidates) {
        java.util.Set<String> alreadyLoaded;
        synchronized (this) {
            alreadyLoaded = java.util.Set.copyOf(loaded.keySet());
        }
        Map<String, ManifestOrder> manifests = new LinkedHashMap<>();
        Map<String, AddonJarCandidate> byId = new LinkedHashMap<>();
        for (AddonJarCandidate candidate : candidates) {
            try {
                YamlConfiguration manifest = readManifest(candidate.file());
                String id = manifest.getString("id", "").trim();
                byId.put(id, candidate);
                manifests.put(id, new ManifestOrder(
                        stringList(manifest, "depends"),
                        stringList(manifest, "soft-depends"),
                        stringList(manifest, "load-after"),
                        stringList(manifest, "conflicts")));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to inspect addon dependency metadata from "
                        + candidate.file().getName(), exception);
            }
        }

        for (Map.Entry<String, ManifestOrder> entry : manifests.entrySet()) {
            java.util.LinkedHashSet<String> declaredOrder = new java.util.LinkedHashSet<>(entry.getValue().depends());
            declaredOrder.addAll(entry.getValue().softDepends());
            declaredOrder.addAll(entry.getValue().loadAfter());
            if (declaredOrder.contains(entry.getKey())) {
                throw new IllegalStateException("Addon " + entry.getKey() + " cannot depend on or load after itself");
            }
            for (String dependency : entry.getValue().depends()) {
                if (!manifests.containsKey(dependency) && !alreadyLoaded.contains(dependency)) {
                    throw new IllegalStateException("Addon " + entry.getKey() + " requires missing addon " + dependency);
                }
            }
            for (String conflict : entry.getValue().conflicts()) {
                if (manifests.containsKey(conflict) || alreadyLoaded.contains(conflict)) {
                    throw new IllegalStateException("Addon conflict: " + entry.getKey() + " conflicts with " + conflict);
                }
            }
        }

        Map<String, java.util.Set<String>> outgoing = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        manifests.keySet().forEach(id -> {
            outgoing.put(id, new java.util.LinkedHashSet<>());
            indegree.put(id, 0);
        });
        for (Map.Entry<String, ManifestOrder> entry : manifests.entrySet()) {
            String addonId = entry.getKey();
            java.util.LinkedHashSet<String> predecessors = new java.util.LinkedHashSet<>(entry.getValue().depends());
            predecessors.addAll(entry.getValue().softDepends());
            predecessors.addAll(entry.getValue().loadAfter());
            for (String predecessor : predecessors) {
                if (!manifests.containsKey(predecessor) || predecessor.equals(addonId)) {
                    continue;
                }
                if (outgoing.get(predecessor).add(addonId)) {
                    indegree.put(addonId, indegree.get(addonId) + 1);
                }
            }
        }

        java.util.ArrayDeque<String> ready = new java.util.ArrayDeque<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) ready.add(id);
        });
        List<AddonJarCandidate> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            result.add(byId.get(id));
            for (String dependent : outgoing.get(id)) {
                int remaining = indegree.computeIfPresent(dependent, (ignored, degree) -> degree - 1);
                if (remaining == 0) ready.addLast(dependent);
            }
        }
        if (result.size() != candidates.size()) {
            List<String> cycle = dependencyCycle(manifests);
            throw new IllegalStateException("Addon dependency cycle: " + String.join(" -> ", cycle));
        }
        return List.copyOf(result);
    }

    private static List<String> stringList(YamlConfiguration manifest, String path) {
        return manifest.getStringList(path).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static List<String> dependencyCycle(Map<String, ManifestOrder> manifests) {
        Map<String, Integer> state = new LinkedHashMap<>();
        List<String> path = new ArrayList<>();
        for (String id : manifests.keySet()) {
            List<String> cycle = dependencyCycleFrom(id, manifests, state, path);
            if (!cycle.isEmpty()) return cycle;
        }
        return List.copyOf(manifests.keySet());
    }

    private static List<String> dependencyCycleFrom(String id, Map<String, ManifestOrder> manifests,
                                                    Map<String, Integer> state, List<String> path) {
        int current = state.getOrDefault(id, 0);
        if (current == 2) return List.of();
        if (current == 1) {
            int start = path.indexOf(id);
            List<String> cycle = new ArrayList<>(path.subList(Math.max(0, start), path.size()));
            cycle.add(id);
            return List.copyOf(cycle);
        }
        state.put(id, 1);
        path.add(id);
        ManifestOrder order = manifests.get(id);
        java.util.LinkedHashSet<String> predecessors = new java.util.LinkedHashSet<>(order.depends());
        predecessors.addAll(order.softDepends());
        predecessors.addAll(order.loadAfter());
        for (String predecessor : predecessors) {
            if (!manifests.containsKey(predecessor)) continue;
            List<String> cycle = dependencyCycleFrom(predecessor, manifests, state, path);
            if (!cycle.isEmpty()) return cycle;
        }
        path.remove(path.size() - 1);
        state.put(id, 2);
        return List.of();
    }

    private record ManifestOrder(List<String> depends, List<String> softDepends,
                                 List<String> loadAfter, List<String> conflicts) {
    }
}
