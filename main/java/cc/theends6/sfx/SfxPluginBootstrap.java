package cc.theends6.sfx;

/**
 * Thin boot/shutdown coordinator for SlimeFunX.
 *
 * <p>The entry layer is intentionally split into preflight, storage/core, runtime, service construction,
 * listener wiring/startup and shutdown modules. This class only preserves the top-level order.</p>
 */
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
