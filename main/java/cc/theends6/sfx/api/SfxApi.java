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

    default SfxLocalizationView localization() {
        throw new UnsupportedOperationException("This SfxApi implementation does not expose localization.");
    }

    SfxGuide guide();

    SfxFeatureRegistry features();

    SfxBehaviorRegistry behaviors();

    SfxManualMachineRegistry manualMachines();

    default SfxMachineRuntime machineRuntime() {
        throw new UnsupportedOperationException("This SfxApi implementation does not expose the machine runtime API.");
    }

    default SfxServerActiveClock activeClock() {
        throw new UnsupportedOperationException("This SfxApi implementation does not expose the server active clock.");
    }

    default SfxInventoryPowerRouter powerRouter() {
        throw new UnsupportedOperationException("This SfxApi implementation does not expose the inventory power router.");
    }

    default SfxWorldActionService worldActions() {
        throw new UnsupportedOperationException("This SfxApi implementation does not expose world actions.");
    }

    default SfxProtectionService protection() {
        throw new UnsupportedOperationException("This SfxApi implementation does not expose protection checks.");
    }

    default SfxAddonRuntime addonRuntime() {
        throw new UnsupportedOperationException("This SfxApi implementation does not expose the addon runtime.");
    }
}
