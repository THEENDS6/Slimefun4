package cc.theends6.sfx.addons.basic.electric;

import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRules;

public final class SfxBasicExpansionElectricProviders {
    private SfxBasicExpansionElectricProviders() {
    }

    public static void register(SfxAddonContext context) {
        SfxBasicExpansionAreaElectricMachineProviders.bindRuntime(context.api().runtime());
        context.behaviors().registerElectricMachineProvider("sfx:advanced_auto_brewer",
                hook -> new SfxAdvancedAutoBrewerRecipeProvider(hook.plugin()));
        context.behaviors().registerElectricMachineProvider("sfx:produce_collector",
                hook -> SfxBasicExpansionAreaElectricMachineProviders.produceCollector(true));
        context.behaviors().registerElectricMachineProvider("sfx:auto_breeder",
                hook -> SfxBasicExpansionAreaElectricMachineProviders.autoBreeder(true, 20));
        context.behaviors().registerElectricMachineProvider("sfx:animal_growth_accelerator",
                hook -> SfxBasicExpansionAreaElectricMachineProviders.animalGrowthAccelerator(4000, 40));
        context.behaviors().registerElectricMachineProvider("sfx:crop_growth_accelerator",
                hook -> SfxBasicExpansionAreaElectricMachineProviders.cropGrowthAccelerator(hook.areaMachines(), 3, 20, true));
        context.behaviors().registerElectricMachineProvider("sfx:crop_growth_accelerator_2",
                hook -> SfxBasicExpansionAreaElectricMachineProviders.cropGrowthAccelerator(hook.areaMachines(), 4, 30, true));
        context.behaviors().registerElectricMachineProvider("sfx:tree_growth_accelerator",
                hook -> SfxBasicExpansionAreaElectricMachineProviders.treeGrowthAccelerator(true));
        context.behaviors().registerElectricMachineProvider("sfx:xp_collector",
                hook -> SfxBasicExpansionAreaElectricMachineProviders.expCollector(true, areaRules(context).xpFlaskEnergyCost()));
        context.behaviors().registerElectricMachineProvider("sfx:fluid_pump",
                hook -> SfxBasicExpansionAreaElectricMachineProviders.fluidPump(areaRules(context)));
    }

    public static void disable() {
        SfxBasicExpansionAreaElectricMachineProviders.clearCaches();
    }

    private static SfxAreaMachineRules areaRules(SfxAddonContext context) {
        SfxAreaMachineRules defaults = SfxAreaMachineRules.classicDefaults();
        return new SfxAreaMachineRules(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                4000,
                Math.max(0, context.configInt("electric-machines.sfx-balance.xp-collector.flask-energy-cost", defaults.xpFlaskEnergyCost())),
                Math.max(1, context.configInt("electric-machines.sfx-extensions.fluid-pump-optimization.check-interval-ticks", defaults.fluidPumpProbeIntervalTicks())),
                Math.max(1, context.configInt("electric-machines.sfx-extensions.fluid-pump-optimization.water-source-threshold", defaults.waterSourceThreshold())),
                Math.max(1, context.configInt("electric-machines.sfx-extensions.fluid-pump-optimization.lava-source-threshold", defaults.lavaSourceThreshold()))
        );
    }
}
