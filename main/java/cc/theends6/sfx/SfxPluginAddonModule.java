package cc.theends6.sfx;

import cc.theends6.sfx.internal.addon.SfxAddonManager;

final class SfxPluginAddonModule {
    private SfxPluginAddonModule() {
    }

    static void unloadAddons(SlimeFunXPlugin plugin) {
        if (plugin.addonManager == null) {
            plugin.api.bindAddonManager(null);
            return;
        }
        plugin.addonManager.close();
        plugin.api.bindAddonManager(null);
        plugin.addonManager = null;
    }

    static void loadAddons(SlimeFunXPlugin plugin) {
        unloadAddons(plugin);
        plugin.featureRegistry.clear();
        plugin.behaviorRegistry.clear();
        plugin.componentOverrideRegistry.clear();
        plugin.addonManager = new SfxAddonManager(plugin, plugin.getLogger(), plugin.api, plugin.featureRegistry,
                plugin.behaviorRegistry, plugin.componentOverrideRegistry);
        plugin.addonManager.loadConfiguredAddons(
                plugin.getConfig().getBoolean("addons.basic-expansion.enabled", true),
                plugin.getConfig().getBoolean("addons.research-expansion.enabled", true),
                plugin.getConfig().getBoolean("addons.example.enabled", true),
                new java.io.File(plugin.getDataFolder(), "addons"));
        plugin.api.bindAddonManager(plugin.addonManager);
    }
}
