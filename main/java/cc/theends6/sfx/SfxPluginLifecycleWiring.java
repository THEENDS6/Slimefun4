package cc.theends6.sfx;

import cc.theends6.sfx.internal.core.SfxModule;
import cc.theends6.sfx.internal.core.SfxModuleManager;

/** Central lifecycle composition for runtime services. Constructors must not start repeating tasks. */
final class SfxPluginLifecycleWiring {
    private SfxPluginLifecycleWiring() {
    }

    static SfxModuleManager create(SlimeFunXPlugin plugin) {
        SfxModuleManager modules = new SfxModuleManager(plugin.getLogger());
        modules.register(module("floating-text-display", null, () -> plugin.floatingTextDisplayService.shutdown()));
        modules.register(module("virtual-container", null, () -> plugin.virtualContainerService.shutdown()));
        modules.register(module("basic-machine-listener", null, () -> plugin.basicMachineBlockListener.shutdown()));
        modules.register(module("electric-machines", () -> plugin.electricMachineService.start(), () -> plugin.electricMachineService.shutdown()));
        modules.register(module("configurable-machines", () -> plugin.configurableMachineService.start(), () -> plugin.configurableMachineService.shutdown()));
        modules.register(module("technical-gadgets", null, () -> plugin.technicalGadgetService.shutdown()));
        modules.register(module("energy-grid", () -> plugin.energyService.start(), () -> plugin.energyService.shutdown()));
        modules.register(module("cargo-network", () -> plugin.cargoService.start(), () -> plugin.cargoService.shutdown()));
        modules.register(module("decoration", () -> plugin.decorationService.start(), () -> plugin.decorationService.shutdown()));
        modules.register(module("gps", null, () -> plugin.gpsService.shutdown()));
        modules.register(module("android", () -> plugin.androidService.start(), () -> plugin.androidService.shutdown()));
        modules.register(module("ancient-altar", () -> plugin.ancientAltarService.start(), () -> plugin.ancientAltarService.shutdown()));
        modules.register(module("spawner", null, null));
        modules.register(module("infused-hopper", () -> plugin.infusedHopperService.start(), () -> plugin.infusedHopperService.shutdown()));
        modules.register(module("hologram-projector", () -> plugin.hologramProjectorService.rebuildIndex(), () -> plugin.hologramProjectorService.shutdown()));
        modules.register(module("block-placer", null, null));
        modules.register(module("industrial-miner", null, () -> plugin.industrialMinerService.shutdown()));
        modules.register(module("block-persistence-listener", null, () -> plugin.blockPersistenceListener.shutdown()));
        modules.register(module("radiation", () -> plugin.radiationService.start(), () -> plugin.radiationService.shutdown()));
        return modules;
    }

    static void registerListenerLifecycle(SlimeFunXPlugin plugin) {
        if (plugin.moduleManager == null || plugin.backpackListener == null) {
            return;
        }
        plugin.moduleManager.register(module("backpack-listener", null, () -> plugin.backpackListener.shutdown()));
    }

    private static SfxModule module(String name, ThrowingRunnable enable, ThrowingRunnable disable) {
        return new SfxModule() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void enable() throws Exception {
                if (enable != null) {
                    enable.run();
                }
            }

            @Override
            public void disable() throws Exception {
                if (disable != null) {
                    disable.run();
                }
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
