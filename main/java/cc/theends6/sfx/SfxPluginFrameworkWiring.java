package cc.theends6.sfx;

import cc.theends6.sfx.internal.machine.SfxMachineBuiltinEffectHooks;
import cc.theends6.sfx.internal.machine.SfxMachineCategory;
import cc.theends6.sfx.internal.machine.SfxMachineDomainEffectHooks;
import cc.theends6.sfx.internal.machine.SfxMachineFrameworkCatalog;

/** Registers machine definitions/effects once all domain services are constructed. */
final class SfxPluginFrameworkWiring {
    private SfxPluginFrameworkWiring() {
    }

    static Stats wire(SlimeFunXPlugin plugin) {
        int domainEffectHooks = SfxMachineDomainEffectHooks.register(
                plugin.machineRuntime,
                plugin.gpsService,
                plugin.androidService,
                plugin.ancientAltarService,
                plugin.energyService,
                plugin.cargoService,
                plugin.spawnerService,
                plugin.infusedHopperService,
                plugin.hologramProjectorService,
                plugin.blockPlacerService,
                plugin.industrialMinerService,
                plugin.decorationService);
        int frameworkCatalogExtras = SfxMachineFrameworkCatalog.registerDefinitions(plugin.machineRuntime, plugin.api.itemRegistry().items(),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.BASIC, plugin.basicMachineBlockListener::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.ELECTRIC, plugin.electricMachineService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.CONFIGURABLE, plugin.configurableMachineService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.ENERGY, plugin.energyService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.CARGO, plugin.cargoService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.GPS, plugin.gpsService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.ANDROID, plugin.androidService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.SPECIAL, plugin.decorationService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.SPECIAL, plugin.ancientAltarService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.SPECIAL, plugin.spawnerService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.SPECIAL, plugin.blockPlacerService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.SPECIAL, plugin.infusedHopperService::supportsType),
                SfxMachineFrameworkCatalog.Candidate.of(SfxMachineCategory.SPECIAL, plugin.hologramProjectorService::supportsType));
        plugin.machineRuntime.ensureDefaultProcessors();
        int builtinEffectHooks = SfxMachineBuiltinEffectHooks.registerDefaults(plugin.machineRuntime);
        int unboundDeclaredEffects = plugin.machineRuntime.unboundDeclaredEffectNames().size();
        return new Stats(domainEffectHooks, frameworkCatalogExtras, builtinEffectHooks, unboundDeclaredEffects);
    }

    record Stats(int domainEffectHooks, int frameworkCatalogExtras, int builtinEffectHooks, int unboundDeclaredEffects) {
        boolean valid() { return unboundDeclaredEffects == 0; }
    }
}
