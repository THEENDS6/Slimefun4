package cc.theends6.sfx;

import cc.theends6.sfx.internal.block.SfxPlaceableBlockListener;
import cc.theends6.sfx.internal.energy.SfxMultimeterListener;
import cc.theends6.sfx.internal.listener.SfxAncientRuneEffectListener;
import cc.theends6.sfx.internal.listener.SfxArmorEffectListener;
import cc.theends6.sfx.internal.listener.SfxBackpackListener;
import cc.theends6.sfx.internal.listener.SfxGuideListener;
import cc.theends6.sfx.internal.listener.SfxItemUseDispatcher;
import cc.theends6.sfx.internal.listener.SfxLegacyCombatToolListener;
import cc.theends6.sfx.internal.listener.SfxLegacyFoodListener;
import cc.theends6.sfx.internal.listener.SfxLegacyUtilityListener;
import cc.theends6.sfx.internal.listener.SfxPlayerProfileListener;
import cc.theends6.sfx.internal.listener.SfxResearchFireworksListener;
import cc.theends6.sfx.internal.listener.SfxSoulboundListener;
import cc.theends6.sfx.internal.listener.SfxTalismanListener;
import cc.theends6.sfx.internal.listener.SfxVanillaGuardListener;
import cc.theends6.sfx.internal.machine.ManualMachineService;
import cc.theends6.sfx.internal.machine.SfxManualMachineDeployListener;
import cc.theends6.sfx.internal.machine.SfxManualMachineListener;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayListener;

/** Central listener composition. Keeps the bootstrap class from knowing every listener constructor. */
final class SfxPluginListenerWiring {
    private SfxPluginListenerWiring() {
    }

    static void register(SlimeFunXPlugin plugin, ManualMachineService manualMachineService, SfxPlaceableBlockListener placeableBlockListener) {
        plugin.backpackListener = new SfxBackpackListener(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.playerDataService, plugin.researchService);
        SfxLegacyUtilityListener utilityListener = new SfxLegacyUtilityListener(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.legacyItemBehaviorConfig, plugin.blockDataService, plugin.radiationService, plugin.playerDataService, plugin.researchService);
        SfxLegacyCombatToolListener combatToolListener = new SfxLegacyCombatToolListener(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.legacyItemBehaviorConfig, plugin.blockDataService);
        SfxLegacyFoodListener foodListener = new SfxLegacyFoodListener(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization);
        SfxTalismanListener talismanListener = new SfxTalismanListener(plugin, plugin.api.runtime(), plugin.api.items(), plugin.researchService, plugin.legacyItemBehaviorConfig.talismans());

        plugin.listenerRegistrar.register(plugin.api.menus());
        plugin.listenerRegistrar.register(new SfxFloatingTextDisplayListener(plugin.floatingTextDisplayService));
        plugin.listenerRegistrar.register(new SfxPlayerProfileListener(plugin.playerDataService));
        plugin.listenerRegistrar.register(plugin.radiationService);
        plugin.listenerRegistrar.register(new SfxGuideListener(plugin, plugin.api.items(), plugin.api.guide()));
        plugin.listenerRegistrar.register(new SfxItemUseDispatcher(plugin.api.items(), plugin.backpackListener, utilityListener, combatToolListener, foodListener, plugin.researchService, plugin.localization));
        plugin.listenerRegistrar.register(new SfxManualMachineListener(manualMachineService, plugin.api.items()));
        plugin.listenerRegistrar.register(new SfxManualMachineDeployListener(plugin, plugin.api.internalManualMachines(), plugin.localization, plugin.blockDataService));
        plugin.listenerRegistrar.register(new SfxMultimeterListener(plugin, plugin.api.items(), plugin.localization, plugin.blockDataService, plugin.electricMachineService, plugin.configurableMachineService, plugin.energyService));
        plugin.listenerRegistrar.register(placeableBlockListener);
        plugin.listenerRegistrar.register(plugin.blockPersistenceListener);
        plugin.listenerRegistrar.register(plugin.basicMachineBlockListener);
        plugin.listenerRegistrar.register(plugin.electricMachineService);
        plugin.listenerRegistrar.register(plugin.configurableMachineService);
        plugin.listenerRegistrar.register(plugin.energyService);
        plugin.listenerRegistrar.register(plugin.energyService.electricMenuListener());
        plugin.listenerRegistrar.register(plugin.virtualContainerService);
        plugin.listenerRegistrar.register(plugin.cargoService);
        plugin.listenerRegistrar.register(plugin.decorationService);
        plugin.listenerRegistrar.register(plugin.gpsService);
        plugin.listenerRegistrar.register(plugin.androidService);
        plugin.listenerRegistrar.register(plugin.ancientAltarService);
        plugin.listenerRegistrar.register(plugin.blockPlacerService);
        plugin.listenerRegistrar.register(plugin.infusedHopperService);
        plugin.listenerRegistrar.register(plugin.hologramProjectorService);
        plugin.listenerRegistrar.register(plugin.industrialMinerService);
        plugin.listenerRegistrar.register(plugin.technicalGadgetService);
        plugin.listenerRegistrar.register(plugin.backpackListener);
        plugin.listenerRegistrar.register(utilityListener);
        plugin.listenerRegistrar.register(combatToolListener);
        plugin.listenerRegistrar.register(foodListener);
        plugin.listenerRegistrar.register(talismanListener);
        plugin.listenerRegistrar.register(new SfxAncientRuneEffectListener(plugin, plugin.api.runtime(), plugin.api.items()));
        plugin.listenerRegistrar.register(new SfxSoulboundListener(plugin, plugin.api.items(), plugin.researchService));
        plugin.listenerRegistrar.register(new SfxResearchFireworksListener());
        plugin.listenerRegistrar.register(new SfxVanillaGuardListener(plugin, plugin.api.items()));
        plugin.listenerRegistrar.register(new SfxArmorEffectListener(plugin.api.items()));
    }
}
