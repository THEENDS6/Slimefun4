package cc.theends6.sfx;

import cc.theends6.sfx.internal.core.SfxModule;
import cc.theends6.sfx.internal.core.SfxModuleManager;
import java.util.List;


final class SfxPluginLifecycleWiring {
    private SfxPluginLifecycleWiring() {
    }

    static SfxModuleManager create(SlimeFunXPlugin plugin) {
        SfxModuleManager modules = new SfxModuleManager(plugin.getLogger());
        modules.register(module("floating-text-display", () -> plugin.floatingTextDisplayService.start(), () -> plugin.floatingTextDisplayService.shutdown()));
        modules.register(module("virtual-container", () -> plugin.virtualContainerService.start(), () -> plugin.virtualContainerService.shutdown()));
        modules.register(module("basic-machine-listener", () -> plugin.basicMachineBlockListener.start(), () -> plugin.basicMachineBlockListener.shutdown()));
        modules.register(module("electric-machines", List.of("virtual-container", "floating-text-display", "basic-machine-listener"), () -> plugin.electricMachineService.start(), () -> plugin.electricMachineService.shutdown()));
        modules.register(module("configurable-machines", List.of("floating-text-display"), () -> plugin.configurableMachineService.start(), () -> plugin.configurableMachineService.shutdown()));
        modules.register(module("technical-gadgets", () -> plugin.technicalGadgetService.start(), () -> plugin.technicalGadgetService.shutdown()));
        modules.register(module("energy-grid", List.of("electric-machines", "configurable-machines", "floating-text-display"), () -> plugin.energyService.start(), () -> plugin.energyService.shutdown()));
        modules.register(module("cargo-network", List.of("virtual-container", "electric-machines"), () -> plugin.cargoService.start(), () -> plugin.cargoService.shutdown()));
        modules.register(module("decoration", () -> plugin.decorationService.start(), () -> plugin.decorationService.shutdown()));
        modules.register(module("gps", null, () -> plugin.gpsService.shutdown()));
        modules.register(module("android", () -> plugin.androidService.start(), () -> plugin.androidService.shutdown()));
        modules.register(module("ancient-altar", () -> plugin.ancientAltarService.start(), () -> plugin.ancientAltarService.shutdown()));
        modules.register(module("spawner", null, null));
        modules.register(module("infused-hopper", () -> plugin.infusedHopperService.start(), () -> plugin.infusedHopperService.shutdown()));
        modules.register(module("hologram-projector", List.of("floating-text-display"), () -> plugin.hologramProjectorService.rebuildIndex(), () -> plugin.hologramProjectorService.shutdown()));
        modules.register(module("block-placer", null, null));
        modules.register(module("industrial-miner", null, () -> plugin.industrialMinerService.shutdown()));
        modules.register(module("block-persistence-listener", List.of("cargo-network", "energy-grid", "virtual-container"), () -> plugin.blockPersistenceListener.start(), () -> plugin.blockPersistenceListener.shutdown()));
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
        return module(name, List.of(), enable, disable);
    }

    private static SfxModule module(String name, List<String> dependsOn, ThrowingRunnable enable, ThrowingRunnable disable) {
        List<String> dependencies = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        return new SfxModule() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<String> dependsOn() {
                return dependencies;
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
