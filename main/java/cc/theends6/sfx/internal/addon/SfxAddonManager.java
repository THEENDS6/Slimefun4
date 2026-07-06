package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.addons.basic.SfxBasicExpansionAddon;
import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.internal.behavior.DefaultSfxBehaviorRegistry;
import cc.theends6.sfx.internal.feature.DefaultSfxFeatureRegistry;
import cc.theends6.sfx.internal.feature.SfxFeatureRegistrarView;
import java.util.ArrayList;
import java.util.Collections;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
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
        loadBundledAddons(true);
    }

    public void loadBundledAddons(boolean basicExpansionEnabled) {
        if (!basicExpansionEnabled) {
            if (logger != null) {
                logger.info("Skipped disabled bundled SFX addon " + SfxBasicExpansionAddon.ID);
            }
            return;
        }
        loadAll(List.of(new SfxBasicExpansionAddon()), AddonSource.BUNDLED);
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
        loadConfigAddons(addonsDir);
        File[] files = addonsDir.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
        if (files == null || files.length == 0) {
            return;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File file : files) {
            loadExternalJar(file);
        }
    }

    private void loadConfigAddons(File addonsDir) {
        File[] directories = addonsDir.listFiles(File::isDirectory);
        if (directories == null || directories.length == 0) {
            return;
        }
        java.util.Arrays.sort(directories, java.util.Comparator.comparing(File::getName));
        for (File directory : directories) {
            File manifestFile = new File(directory, "sfx-addon.yml");
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
                String mainClassName = manifest.getString("main", "").trim();
                if (!mainClassName.isEmpty()) {
                    continue;
                }
                String id = manifest.getString("id", directory.getName()).trim();
                String name = manifest.getString("name", id).trim();
                load(new ConfigAddon(id, name, manifestFeatures(manifest)), "config");
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Failed to load SFX config addon " + directory.getName() + ": " + exception.getMessage(), exception);
            }
        }
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
            load(addon, source.description);
        }
    }

    public synchronized List<SfxAddon> loadedAddons() {
        return Collections.unmodifiableList(new ArrayList<>(loaded.values()));
    }

    public synchronized List<File> externalAddonJars() {
        return Collections.unmodifiableList(new ArrayList<>(externalJars.values()));
    }

    private void loadExternalJar(File file) {
        try {
            URLClassLoader loader = new URLClassLoader(new URL[] {file.toURI().toURL()}, SfxAddon.class.getClassLoader());
            try (JarFile jar = new JarFile(file)) {
                JarEntry manifestEntry = jar.getJarEntry("sfx-addon.yml");
                if (manifestEntry == null) {
                    throw new IllegalStateException("Missing sfx-addon.yml");
                }
                YamlConfiguration manifest;
                try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(manifestEntry), StandardCharsets.UTF_8)) {
                    manifest = YamlConfiguration.loadConfiguration(reader);
                }
                if (!manifest.getBoolean("enabled", true)) {
                    loader.close();
                    if (logger != null) {
                        logger.info("Skipped disabled SFX addon jar " + file.getName());
                    }
                    return;
                }
                String mainClassName = manifest.getString("main", "").trim();
                if (mainClassName.isEmpty()) {
                    throw new IllegalStateException("sfx-addon.yml is missing main");
                }
                Class<?> mainClass = Class.forName(mainClassName, true, loader);
                if (!SfxAddon.class.isAssignableFrom(mainClass)) {
                    throw new IllegalStateException("Main class does not implement " + SfxAddon.class.getName() + ": " + mainClassName);
                }
                SfxAddon addon = (SfxAddon) mainClass.getDeclaredConstructor().newInstance();
                load(addon, "external");
                synchronized (this) {
                    externalJars.put(addon.id(), file);
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

    private void load(SfxAddon addon, String source) {
        if (addon == null || addon.id() == null || addon.id().isBlank()) {
            throw new IllegalArgumentException("SFX addon id must not be blank.");
        }
        synchronized (this) {
            if (loaded.containsKey(addon.id())) {
                throw new IllegalStateException("Duplicate SFX addon id: " + addon.id());
            }
        }
        addon.onLoad(new SfxAddonContextImpl(plugin, api, new SfxFeatureRegistrarView(addon.id(), features), behaviors));
        synchronized (this) {
            loaded.put(addon.id(), addon);
        }
        if (logger != null) {
            logger.info("Loaded " + source + " SFX addon " + addon.id() + " (" + addon.name() + ")");
        }
    }

    @Override
    public synchronized void close() {
        for (Closeable loader : externalLoaders) {
            try {
                loader.close();
            } catch (IOException exception) {
                if (logger != null) {
                    logger.warning("Failed to close SFX addon classloader: " + exception.getMessage());
                }
            }
        }
        externalLoaders.clear();
        externalJars.clear();
        loaded.clear();
    }

    private enum AddonSource {
        BUNDLED("bundled"),
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
