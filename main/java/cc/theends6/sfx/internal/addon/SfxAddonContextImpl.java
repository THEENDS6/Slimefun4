package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.feature.SfxFeatureRegistrar;
import cc.theends6.sfx.api.override.SfxComponentOverrideRegistrar;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxAddonContextImpl implements SfxAddonContext {
    private final JavaPlugin plugin;
    private final SfxApi api;
    private final SfxFeatureRegistrar features;
    private final SfxBehaviorRegistrar behaviors;
    private final SfxComponentOverrideRegistrar overrides;
    private final File dataDirectory;
    private final FileConfiguration config;

    SfxAddonContextImpl(JavaPlugin plugin, SfxApi api, SfxFeatureRegistrar features, SfxBehaviorRegistrar behaviors,
                        SfxComponentOverrideRegistrar overrides,
                        File dataDirectory, FileConfiguration config) {
        this.plugin = plugin;
        this.api = api;
        this.features = features;
        this.behaviors = behaviors;
        this.overrides = overrides;
        this.dataDirectory = dataDirectory;
        this.config = config;
    }

    @Override
    public SfxApi api() {
        return api;
    }

    @Override
    public SfxFeatureRegistrar features() {
        return features;
    }

    @Override
    public SfxBehaviorRegistrar behaviors() {
        return behaviors;
    }

    @Override
    public SfxComponentOverrideRegistrar overrides() {
        return overrides;
    }

    @Override
    public File dataDirectory() {
        return dataDirectory;
    }

    @Override
    public FileConfiguration config() {
        return config;
    }

    @Override
    public boolean configBoolean(String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    @Override
    public int configInt(String path, int fallback) {
        return config.getInt(path, fallback);
    }

    @Override
    public double configDouble(String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    @Override
    public String configString(String path, String fallback) {
        return config.getString(path, fallback);
    }

    static FileConfiguration loadConfig(File dataDirectory) {
        if (dataDirectory == null) {
            return new YamlConfiguration();
        }
        if (!dataDirectory.isDirectory() && !dataDirectory.mkdirs()) {
            throw new IllegalStateException("Failed to create addon data directory " + dataDirectory);
        }
        File file = new File(dataDirectory, "config.yml");
        return YamlConfiguration.loadConfiguration(file);
    }
}
