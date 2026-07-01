package cc.theends6.sfx.internal.diagnostics;

import org.bukkit.plugin.java.JavaPlugin;

public final class SfxValidationDiagnostics {
    public static final String PREFIX = "[SFX-VALIDATION] ";
    private static final String ROOT = "diagnostics.validation";

    private SfxValidationDiagnostics() {
    }

    public static boolean enabled(JavaPlugin plugin, String key) {
        if (plugin == null || key == null || key.isBlank()) {
            return false;
        }
        return plugin.getConfig().getBoolean(ROOT + ".enabled", false)
                && plugin.getConfig().getBoolean(ROOT + "." + key, false);
    }

    public static void log(JavaPlugin plugin, String key, String message) {
        if (enabled(plugin, key)) {
            plugin.getLogger().info(PREFIX + key + " " + message);
        }
    }
}
