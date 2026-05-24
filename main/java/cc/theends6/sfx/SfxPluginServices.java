package cc.theends6.sfx;

import cc.theends6.sfx.internal.block.SfxPlaceableBlockListener;
import cc.theends6.sfx.internal.machine.ManualMachineService;
import cc.theends6.sfx.internal.core.SfxModuleManager;

record SfxPluginServices(
        ManualMachineService manualMachineService,
        SfxPlaceableBlockListener placeableBlockListener,
        SfxPluginFrameworkWiring.Stats frameworkStats,
        SfxModuleManager moduleManager
) {
}
