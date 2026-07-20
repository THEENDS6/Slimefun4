package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddonContext;

final class BasicExpansionFeatureModule implements BasicExpansionModule {
    @Override public void register(SfxAddonContext context) {
        context.features().registerBoolean(SfxBasicExpansionAddon.GPS_TRANSMITTER_STATUS_UI, "gps.sfx-extensions.transmitter-gui.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.ENHANCED_FURNACE_SPEED_FUEL, "plugin-blocks.enhanced-furnace.speed-affects-fuel-consumption", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.JETPACKS_AND_JETBOOTS_REWORK, "technical-gadgets.sfx-extensions.jetpacks-and-jetboots.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.TECHNICAL_GADGET_BALANCE, "technical-gadgets.sfx-balance.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.CROP_GROWTH_ACCELERATOR_BALANCE, "electric-machines.sfx-balance.crop-growth-accelerator", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.TREE_GROWTH_ACCELERATOR_BALANCE, "electric-machines.sfx-balance.tree-growth-accelerator", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.AUTO_BREEDER_BALANCE, "electric-machines.sfx-balance.auto-breeder", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.ANIMAL_GROWTH_ACCELERATOR_BALANCE, "electric-machines.sfx-balance.animal-growth-accelerator", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.PRODUCE_COLLECTOR_BALANCE, "electric-machines.sfx-balance.produce-collector", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.ELECTRIC_ORE_GRINDER_3_BALANCE, "electric-machines.sfx-balance.electric-ore-grinder-3", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.ELECTRIC_INGOT_PULVERIZER_BALANCE, "electric-machines.sfx-balance.electric-ingot-pulverizer", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.XP_COLLECTOR_BALANCE, "electric-machines.sfx-balance.xp-collector.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.FLUID_PUMP_OPTIMIZATION, "electric-machines.sfx-extensions.fluid-pump-optimization.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.AUTO_BREWER, "electric-machines.sfx-extensions.auto-brewer.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.GENERATOR_BALANCE, "energy.generator-balance.use-sfx-balance", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.PAUSE_GENERATORS_WHEN_GRID_FULL, "energy.generator-balance.pause-generators-when-grid-full", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.ENHANCED_MULTIMETER, "tools.multimeter.use-sfx-enhanced", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.ADVANCED_INPUT_INTERFACE, "cargo.sfx-extensions.advanced-input-interface.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.CARGO_GHOST_FILTER_INTERFACE, "cargo.sfx-extensions.ghost-filter-interface.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.RADIATION_REWORK, "radiation.sfx-rework.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.ANDROID_WOODCUTTER_BATCH_REPLANT, "androids.woodcutter.batch-replant-bottom-layer", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.BASIC_CIRCUIT_BOARD_DROP_BALANCE, "entity-drops.basic-circuit-board.enabled", true);
        context.features().registerBoolean(SfxBasicExpansionAddon.INDUSTRIAL_MINER_ACCURACY, "industrial-miner.accuracy.enabled", false);
    }
}
