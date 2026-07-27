package cc.theends6.sfx.api;

import cc.theends6.sfx.api.behavior.SfxBehaviorRegistry;
import cc.theends6.sfx.api.chat.SfxChatInputService;
import cc.theends6.sfx.api.guide.SfxGuide;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import cc.theends6.sfx.api.machine.SfxMachineRuntime;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.api.feature.SfxFeatureRegistry;
import cc.theends6.sfx.api.localization.SfxLocalizationView;
import cc.theends6.sfx.api.time.SfxServerActiveClock;
import cc.theends6.sfx.api.power.SfxInventoryPowerRouter;
import cc.theends6.sfx.api.world.SfxProtectionService;
import cc.theends6.sfx.api.world.SfxWorldActionService;
import cc.theends6.sfx.api.addon.SfxAddonRuntime;

public interface SfxApi {
    SfxRuntime runtime();

    SfxItemRegistry itemRegistry();

    SfxItems items();

    SfxMenus menus();

    SfxChatInputService chatInput();

    SfxLocalizationView localization();

    SfxGuide guide();

    SfxFeatureRegistry features();

    SfxBehaviorRegistry behaviors();

    SfxManualMachineRegistry manualMachines();

    SfxMachineRuntime machineRuntime();

    SfxServerActiveClock activeClock();

    SfxInventoryPowerRouter powerRouter();

    SfxWorldActionService worldActions();

    SfxProtectionService protection();

    cc.theends6.sfx.api.permission.SfxWorldPermissionService permissions();

    SfxAddonRuntime addonRuntime();
}
