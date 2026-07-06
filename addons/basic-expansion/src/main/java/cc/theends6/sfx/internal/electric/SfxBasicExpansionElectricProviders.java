package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRules;

public final class SfxBasicExpansionElectricProviders {
    private SfxBasicExpansionElectricProviders() {
    }

    public static void register(SfxAddonContext context) {
        context.behaviors().registerElectricSpecialProvider("sfx:advanced_auto_brewer",
                (plugin, items, blockData) -> new SfxAdvancedAutoBrewerRecipeProvider(plugin));
        context.behaviors().registerElectricSpecialProvider("sfx:produce_collector",
                (plugin, items, blockData) -> SfxAreaElectricMachineProviders.produceCollector(true));
        context.behaviors().registerElectricSpecialProvider("sfx:auto_breeder",
                (plugin, items, blockData) -> SfxAreaElectricMachineProviders.autoBreeder(true));
        context.behaviors().registerElectricSpecialProvider("sfx:animal_growth_accelerator",
                (plugin, items, blockData) -> SfxAreaElectricMachineProviders.animalGrowthAccelerator(4000));
        context.behaviors().registerElectricSpecialProvider("sfx:crop_growth_accelerator",
                (plugin, items, blockData) -> SfxAreaElectricMachineProviders.cropGrowthAccelerator(blockData, 3, 20, true));
        context.behaviors().registerElectricSpecialProvider("sfx:crop_growth_accelerator_2",
                (plugin, items, blockData) -> SfxAreaElectricMachineProviders.cropGrowthAccelerator(blockData, 4, 30, true));
        context.behaviors().registerElectricSpecialProvider("sfx:tree_growth_accelerator",
                (plugin, items, blockData) -> SfxAreaElectricMachineProviders.treeGrowthAccelerator(true));
        context.behaviors().registerElectricSpecialProvider("sfx:xp_collector",
                (plugin, items, blockData) -> SfxAreaElectricMachineProviders.expCollector(true, areaRules(context).xpFlaskEnergyCost()));
        context.behaviors().registerElectricSpecialProvider("sfx:fluid_pump",
                (plugin, items, blockData) -> SfxAreaElectricMachineProviders.fluidPump(areaRules(context)));
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
