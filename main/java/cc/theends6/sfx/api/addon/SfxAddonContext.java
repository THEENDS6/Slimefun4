package cc.theends6.sfx.api.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.feature.SfxFeatureRegistrar;

public interface SfxAddonContext {
    SfxApi api();

    SfxFeatureRegistrar features();

    SfxBehaviorRegistrar behaviors();

    boolean configBoolean(String path, boolean fallback);

    int configInt(String path, int fallback);

    double configDouble(String path, double fallback);

    String configString(String path, String fallback);
}
