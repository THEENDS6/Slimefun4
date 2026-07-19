package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.feature.SfxFeatureRegistrar;
import cc.theends6.sfx.api.override.SfxComponentOverrideRegistrar;
import cc.theends6.sfx.api.addon.SfxAddonResources;
import cc.theends6.sfx.api.block.SfxBlockType;
import cc.theends6.sfx.api.container.SfxVirtualContainerType;
import cc.theends6.sfx.api.display.SfxDisplayRegistrar;
import cc.theends6.sfx.api.machine.continuous.SfxContinuousManualMachine;
import cc.theends6.sfx.api.power.SfxPoweredItem;
import cc.theends6.sfx.api.randomtick.SfxRandomTickType;
import cc.theends6.sfx.api.registry.SfxDefinitionRegistry;
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
    private final SfxAddonResources resources;
    private final SfxAddonDomainRegistries.Views domains;
    private final File dataDirectory;
    private final FileConfiguration config;

    SfxAddonContextImpl(JavaPlugin plugin, SfxApi api, SfxFeatureRegistrar features, SfxBehaviorRegistrar behaviors,
                        SfxComponentOverrideRegistrar overrides, SfxAddonResources resources,
                        SfxAddonDomainRegistries.Views domains,
                        File dataDirectory, FileConfiguration config) {
        this.plugin = plugin;
        this.api = api;
        this.features = features;
        this.behaviors = behaviors;
        this.overrides = overrides;
        this.resources = resources;
        this.domains = domains;
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
    public SfxAddonResources resources() {
        return resources;
    }

    @Override public SfxDefinitionRegistry<SfxBlockType<?>> blocks() { return domains.blocks(); }
    @Override public SfxDefinitionRegistry<SfxRandomTickType<?>> randomTicks() { return domains.randomTicks(); }
    @Override public SfxDisplayRegistrar displays() { return domains.displays(); }
    @Override public SfxDefinitionRegistry<SfxVirtualContainerType> containers() { return domains.containers(); }
    @Override public SfxDefinitionRegistry<SfxContinuousManualMachine> continuousMachines() { return domains.continuousMachines(); }
    @Override public SfxDefinitionRegistry<SfxPoweredItem> power() { return domains.poweredItems(); }

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
