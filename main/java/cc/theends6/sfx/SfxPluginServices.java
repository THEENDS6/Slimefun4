package cc.theends6.sfx;

import cc.theends6.sfx.internal.block.SfxPlaceableBlockListener;
import cc.theends6.sfx.internal.machine.ManualMachineService;

record SfxPluginServices(
        ManualMachineService manualMachineService,
        SfxPlaceableBlockListener placeableBlockListener,
        SfxPluginFrameworkWiring.Stats frameworkStats
) {
}
