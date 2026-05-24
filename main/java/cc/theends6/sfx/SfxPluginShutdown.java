package cc.theends6.sfx;

/** Ordered service shutdown kept outside plugin/bootstrap startup code. */
final class SfxPluginShutdown {
    private SfxPluginShutdown() {
    }

    static void disable(SlimeFunXPlugin plugin) {
        if (plugin.api != null) {
            plugin.api.menus().closeAll();
        }

        if (plugin.moduleManager != null) {
            plugin.moduleManager.disableAllReverse();
            plugin.moduleManager = null;
        } else {
            disableLegacyFallback(plugin);
        }

        if (plugin.playerDataService != null) {
            plugin.playerDataService.shutdown();
        }
        if (plugin.blockDataService != null) {
            plugin.blockDataService.shutdown();
        }
        if (plugin.machineRuntime != null) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.clearDefaultRuntime(plugin.machineRuntime);
            plugin.machineRuntime.clear();
        }
        if (plugin.packetEventsLoaded) {
            try {
                plugin.invokePacketEventsApi("terminate");
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Failed to terminate PacketEvents cleanly: " + throwable.getMessage());
            }
            plugin.packetEventsLoaded = false;
            plugin.packetEventsApi = null;
        }
    }

    private static void disableLegacyFallback(SlimeFunXPlugin plugin) {
        if (plugin.blockPersistenceListener != null) {
            plugin.blockPersistenceListener.shutdown();
        }
        if (plugin.backpackListener != null) {
            plugin.backpackListener.shutdown();
        }
        if (plugin.basicMachineBlockListener != null) {
            plugin.basicMachineBlockListener.shutdown();
        }
        if (plugin.electricMachineService != null) {
            plugin.electricMachineService.shutdown();
        }
        if (plugin.configurableMachineService != null) {
            plugin.configurableMachineService.shutdown();
        }
        if (plugin.cargoService != null) {
            plugin.cargoService.shutdown();
        }
        if (plugin.gpsService != null) {
            plugin.gpsService.shutdown();
        }
        if (plugin.androidService != null) {
            plugin.androidService.shutdown();
        }
        if (plugin.decorationService != null) {
            plugin.decorationService.shutdown();
        }
        if (plugin.ancientAltarService != null) {
            plugin.ancientAltarService.shutdown();
        }
        if (plugin.infusedHopperService != null) {
            plugin.infusedHopperService.shutdown();
        }
        if (plugin.industrialMinerService != null) {
            plugin.industrialMinerService.shutdown();
        }
        if (plugin.virtualContainerService != null) {
            plugin.virtualContainerService.shutdown();
        }
        if (plugin.energyService != null) {
            plugin.energyService.shutdown();
        }
        if (plugin.technicalGadgetService != null) {
            plugin.technicalGadgetService.shutdown();
        }
        if (plugin.floatingTextDisplayService != null) {
            plugin.floatingTextDisplayService.shutdown();
        }
        if (plugin.radiationService != null) {
            plugin.radiationService.shutdown();
        }
    }
}
