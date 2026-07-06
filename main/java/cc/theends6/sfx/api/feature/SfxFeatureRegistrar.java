package cc.theends6.sfx.api.feature;

public interface SfxFeatureRegistrar extends SfxFeatureRegistry {
    void registerBoolean(String featureId, String configPath, boolean defaultEnabled);
}
