package cc.theends6.sfx.internal.feature;

import cc.theends6.sfx.SlimeFunXPlugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxFeatureSwitch {
    private SfxFeatureSwitch() {
    }

    public static boolean requirementEnabled(JavaPlugin plugin, String featureId) {
        if (featureId == null || featureId.isBlank()) {
            return true;
        }
        if (plugin instanceof SlimeFunXPlugin sfx && sfx.api() != null) {
            return sfx.api().features().feature(featureId).isPresent() && sfx.api().features().enabled(featureId);
        }
        return false;
    }
}
