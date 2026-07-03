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
        if (!validateCompiledOnlyRuntime(plugin)) {
            return;
        }
        plugin.syncBundledLanguages();
        SfxPluginStorageModule.initialize(plugin);
        if (plugin.compileTemplatesOnStartup()) {
            plugin.compileContentTemplates();
        }
        plugin.bootstrapContent();
        SfxPluginRuntimeModule.initialize(plugin);
        SfxPluginServices services = SfxPluginServiceModule.create(plugin);
        SfxPluginStartupModule.start(plugin, services);
    }

    static void disable(SlimeFunXPlugin plugin) {
        SfxPluginShutdown.disable(plugin);
    }

    private static boolean validateCompiledOnlyRuntime(SlimeFunXPlugin plugin) {
        if (!plugin.getConfig().getBoolean("content.runtime.compiled-only", true)) {
            return true;
        }
        String[] forbiddenFallbackFlags = {
                "content.runtime.allow-java-ui-fallback",
                "content.runtime.allow-java-recipe-fallback",
                "content.runtime.allow-legacy-machine-scan",
                "machines.framework.allow-legacy-candidate-scan"
        };
        boolean valid = true;
        for (String path : forbiddenFallbackFlags) {
            if (plugin.getConfig().getBoolean(path, false)) {
                plugin.getLogger().severe("SlimeFunX compiled-only runtime forbids " + path
                        + "=true. Disable this Java fallback flag or set content.runtime.compiled-only=false for migration debugging.");
                valid = false;
            }
        }
        if (!valid) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
        return valid;
    }
}
