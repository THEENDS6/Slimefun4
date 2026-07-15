package cc.theends6.sfx.api.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistrar;
import cc.theends6.sfx.api.feature.SfxFeatureRegistrar;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;

public interface SfxAddonContext {
    SfxApi api();

    SfxFeatureRegistrar features();

    SfxBehaviorRegistrar behaviors();

    
    File dataDirectory();

    
    FileConfiguration config();

    boolean configBoolean(String path, boolean fallback);

    int configInt(String path, int fallback);

    double configDouble(String path, double fallback);

    String configString(String path, String fallback);
}
