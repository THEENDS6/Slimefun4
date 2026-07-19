package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.feature.SfxFeatureRegistrar;
import cc.theends6.sfx.api.override.SfxComponentOverrideRegistrar;
import cc.theends6.sfx.internal.SfxApiImpl;
import cc.theends6.sfx.internal.behavior.DefaultSfxBehaviorRegistry;
import cc.theends6.sfx.internal.feature.DefaultSfxFeatureRegistry;
import cc.theends6.sfx.internal.feature.SfxFeatureRegistrarView;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;


final class SfxAddonRegistrationSession implements AutoCloseable {
    private final String addonId;
    private final DefaultSfxFeatureRegistry features;
    private final DefaultSfxBehaviorRegistry behaviors;
    private final DefaultSfxComponentOverrideRegistry overrides;
    private final SfxApiImpl api;
    private final SfxApi scopedApi;
    private final SfxFeatureRegistrar featureRegistrar;
    private final SfxBehaviorRegistrar behaviorRegistrar;
    private final SfxComponentOverrideRegistrar overrideRegistrar;
    private boolean committed;

    SfxAddonRegistrationSession(String addonId, SfxApi api, DefaultSfxFeatureRegistry features,
                                DefaultSfxBehaviorRegistry behaviors,
                                DefaultSfxComponentOverrideRegistry overrides,
                                FileConfiguration addonConfig) {
        this.addonId = Objects.requireNonNull(addonId, "addonId");
        if (!(api instanceof SfxApiImpl implementation)) {
            throw new IllegalStateException("Addon registration requires the default SFX API implementation");
        }
        this.api = implementation;
        this.features = Objects.requireNonNull(features, "features");
        this.behaviors = Objects.requireNonNull(behaviors, "behaviors");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.featureRegistrar = new SfxFeatureRegistrarView(addonId, features, addonConfig);
        this.behaviorRegistrar = behaviors.registrarFor(addonId);
        this.overrideRegistrar = overrides.registrarFor(addonId);
        this.scopedApi = new SfxScopedAddonApi(api,
                implementation.internalItemRegistry().registrarFor(addonId),
                implementation.internalManualMachines().registrarFor(addonId));
    }

    SfxApi api() { return scopedApi; }
    SfxFeatureRegistrar features() { return featureRegistrar; }
    SfxBehaviorRegistrar behaviors() { return behaviorRegistrar; }
    SfxComponentOverrideRegistrar overrides() { return overrideRegistrar; }

    void commit() {
        committed = true;
    }

    void rollback() {
        features.removeOwner(addonId);
        behaviors.removeOwner(addonId);
        api.internalItemRegistry().removeOwner(addonId);
        api.internalManualMachines().removeOwner(addonId);
        overrides.removeOwner(addonId);
    }

    @Override
    public void close() {
        if (!committed) {
            rollback();
        }
    }
}
