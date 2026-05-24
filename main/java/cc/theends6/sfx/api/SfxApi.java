package cc.theends6.sfx.api;

import cc.theends6.sfx.api.guide.SfxGuide;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import cc.theends6.sfx.api.machine.SfxMachineRuntime;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;

public interface SfxApi {
    SfxRuntime runtime();

    SfxItemRegistry itemRegistry();

    SfxItems items();

    SfxMenus menus();

    SfxGuide guide();

    SfxManualMachineRegistry manualMachines();

    SfxMachineRuntime machineRuntime();
}
