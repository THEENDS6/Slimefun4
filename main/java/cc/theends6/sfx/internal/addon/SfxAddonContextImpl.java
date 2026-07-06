package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.feature.SfxFeatureRegistrar;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxAddonContextImpl implements SfxAddonContext {
    private final JavaPlugin plugin;
    private final SfxApi api;
    private final SfxFeatureRegistrar features;
    private final SfxBehaviorRegistrar behaviors;

    SfxAddonContextImpl(JavaPlugin plugin, SfxApi api, SfxFeatureRegistrar features, SfxBehaviorRegistrar behaviors) {
        this.plugin = plugin;
        this.api = api;
        this.features = features;
        this.behaviors = behaviors;
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
    public boolean configBoolean(String path, boolean fallback) {
        return plugin == null ? fallback : plugin.getConfig().getBoolean(path, fallback);
    }

    @Override
    public int configInt(String path, int fallback) {
        return plugin == null ? fallback : plugin.getConfig().getInt(path, fallback);
    }

    @Override
    public double configDouble(String path, double fallback) {
        return plugin == null ? fallback : plugin.getConfig().getDouble(path, fallback);
    }

    @Override
    public String configString(String path, String fallback) {
        return plugin == null ? fallback : plugin.getConfig().getString(path, fallback);
    }
}
