package cc.theends6.sfx;

import cc.theends6.sfx.internal.machine.SfxMachinePhaseLedger;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxWorldMutationBridge;




final class SfxPluginRuntimeModule {
    private SfxPluginRuntimeModule() {
    }

    static void initialize(SlimeFunXPlugin plugin) {
        plugin.machineRuntime = new SfxMachineRuntimeEngine();
        SfxWorldMutationBridge.bindDefaultRuntime(plugin.machineRuntime);
        plugin.machinePhaseLedger = new SfxMachinePhaseLedger();
        plugin.machineRuntime.registerPhaseObserver(plugin.machinePhaseLedger);
    }
}
