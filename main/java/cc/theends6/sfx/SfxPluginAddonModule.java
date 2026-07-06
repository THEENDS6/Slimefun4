package cc.theends6.sfx;

import cc.theends6.sfx.internal.addon.SfxAddonManager;

final class SfxPluginAddonModule {
    private SfxPluginAddonModule() {
    }

    static void loadAddons(SlimeFunXPlugin plugin) {
        if (plugin.addonManager != null) {
            plugin.addonManager.close();
        }
        plugin.featureRegistry.clear();
        plugin.behaviorRegistry.clear();
        plugin.addonManager = new SfxAddonManager(plugin, plugin.getLogger(), plugin.api, plugin.featureRegistry, plugin.behaviorRegistry);
        plugin.addonManager.loadBundledAddons(plugin.getConfig().getBoolean("addons.basic-expansion.enabled", true));
        plugin.addonManager.loadExternalAddons(new java.io.File(plugin.getDataFolder(), "addons"));
    }
}
