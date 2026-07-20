package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.addon.SfxAddonResources;
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
    private final SfxAddonResources resources;
    private final SfxAddonDomainRegistries.Views domains;
    private final SfxStagedAddonRegistration staging;
    private boolean committed;

    SfxAddonRegistrationSession(String addonId, SfxApi api, DefaultSfxFeatureRegistry features,
                                DefaultSfxBehaviorRegistry behaviors,
                                DefaultSfxComponentOverrideRegistry overrides,
                                SfxAddonResources resources,
                                SfxAddonDomainRegistries.Views domains,
                                FileConfiguration addonConfig) {
        this.addonId = Objects.requireNonNull(addonId, "addonId");
        if (!(api instanceof SfxApiImpl implementation)) {
            throw new IllegalStateException("Addon registration requires the default SFX API implementation");
        }
        this.api = implementation;
        this.features = Objects.requireNonNull(features, "features");
        this.behaviors = Objects.requireNonNull(behaviors, "behaviors");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.staging = new SfxStagedAddonRegistration(addonId);
        this.featureRegistrar = staging.features(new SfxFeatureRegistrarView(addonId, features, addonConfig), addonConfig);
        this.behaviorRegistrar = staging.behaviors(behaviors.registrarFor(addonId));
        this.overrideRegistrar = staging.overrides(overrides.registrarFor(addonId));
        this.resources = staging.resources(Objects.requireNonNull(resources, "resources"));
        this.domains = staging.domains(Objects.requireNonNull(domains, "domains"));
        this.scopedApi = new SfxScopedAddonApi(api,
                staging.items(implementation.internalItemRegistry().registrarFor(addonId)),
                staging.machines(implementation.internalManualMachines().registrarFor(addonId)));
    }

    SfxApi api() { return scopedApi; }
    SfxFeatureRegistrar features() { return featureRegistrar; }
    SfxBehaviorRegistrar behaviors() { return behaviorRegistrar; }
    SfxComponentOverrideRegistrar overrides() { return overrideRegistrar; }
    SfxAddonResources resources() { return resources; }
    SfxAddonDomainRegistries.Views domains() { return domains; }

    void commit() {
        staging.commit();
        committed = true;
    }

    void rollback() {
        staging.rollbackPending();
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
