package cc.theends6.sfx.internal.feature;

import cc.theends6.sfx.api.feature.SfxFeature;
import cc.theends6.sfx.api.feature.SfxFeatureRegistrar;
import java.util.Collection;
import java.util.Optional;

public final class SfxFeatureRegistrarView implements SfxFeatureRegistrar {
    private final String addonId;
    private final DefaultSfxFeatureRegistry registry;

    public SfxFeatureRegistrarView(String addonId, DefaultSfxFeatureRegistry registry) {
        this.addonId = addonId;
        this.registry = registry;
    }

    @Override
    public void registerBoolean(String featureId, String configPath, boolean defaultEnabled) {
        registry.registerBoolean(addonId, featureId, configPath, defaultEnabled);
    }

    @Override
    public Optional<SfxFeature> feature(String id) {
        return registry.feature(id);
    }

    @Override
    public Collection<SfxFeature> features() {
        return registry.features();
    }

    @Override
    public boolean enabled(String id) {
        return registry.enabled(id);
    }
}
