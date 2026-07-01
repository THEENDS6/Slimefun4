package cc.theends6.sfx;







final class SfxPluginBootstrap {
    private SfxPluginBootstrap() {
    }

    static void enable(SlimeFunXPlugin plugin) {
        if (!SfxPluginPreflight.prepare(plugin)) {
            return;
        }
        plugin.saveDefaultConfig();
        plugin.syncBundledLanguages();
        SfxPluginStorageModule.initialize(plugin);
        plugin.compileContentTemplates();
        plugin.bootstrapContent();
        SfxPluginRuntimeModule.initialize(plugin);
        SfxPluginServices services = SfxPluginServiceModule.create(plugin);
        SfxPluginStartupModule.start(plugin, services);
    }

    static void disable(SlimeFunXPlugin plugin) {
        SfxPluginShutdown.disable(plugin);
    }
}
