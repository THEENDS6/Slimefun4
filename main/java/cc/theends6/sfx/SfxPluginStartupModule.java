package cc.theends6.sfx;

import cc.theends6.sfx.internal.command.SfxCommand;
import java.util.Objects;
import org.bukkit.command.PluginCommand;




final class SfxPluginStartupModule {
    private SfxPluginStartupModule() {
    }

    static void start(SlimeFunXPlugin plugin, SfxPluginServices services) {
        SfxPluginListenerWiring.register(plugin, services.manualMachineService(), services.placeableBlockListener());
        plugin.decorationService.start();
        plugin.ancientAltarService.start();
        plugin.infusedHopperService.start();
        plugin.hologramProjectorService.rebuildIndex();
        plugin.radiationService.start();
        plugin.androidService.start();
        registerCommand(plugin);
        logStartupSummary(plugin, services.frameworkStats());
    }

    private static void registerCommand(SlimeFunXPlugin plugin) {
        SfxCommand command = new SfxCommand(plugin, plugin.api);
        PluginCommand pluginCommand = Objects.requireNonNull(plugin.getCommand("slimefunx"), "plugin.yml missing /slimefunx command");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }

    private static void logStartupSummary(SlimeFunXPlugin plugin, SfxPluginFrameworkWiring.Stats frameworkStats) {
        plugin.getLogger().info("SFX enabled. Registered " + plugin.api.itemRegistry().items().size()
                + " item definitions and " + plugin.api.manualMachines().machines().size() + " manual machines. "
                + "Registered " + plugin.listenerRegistrar.registered().size() + " listeners through sfx-core. "
                + "Machine framework definitions: " + plugin.machineRuntime.definitionCount() + " (" + frameworkStats.frameworkCatalogExtras() + " catalog extras), "
                + plugin.machineRuntime.capabilityDeclarationCount() + " capabilities, "
                + plugin.machineRuntime.policyRefCount() + " policy refs, "
                + plugin.machineRuntime.effectCount() + " phase effects, "
                + plugin.machineRuntime.effectHookCount() + " bound effect hooks (" + frameworkStats.builtinEffectHooks() + " built-in defaults, " + frameworkStats.domainEffectHooks() + " domain hooks, " + frameworkStats.genericEffectHooks() + " generic fallbacks), "
                + plugin.machineRuntime.phaseObserverCount() + " phase observers, "
                + plugin.machineRuntime.unboundDeclaredEffectNames().size() + " unbound declared effects. "
                + "Loaded " + plugin.blockDataService.anchorCount() + " block anchors and "
                + plugin.blockDataService.instanceCount() + " block instances.");
    }
}
