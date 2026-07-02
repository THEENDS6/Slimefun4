package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import java.util.List;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxElectricMachineDefinitions {
    private SfxElectricMachineDefinitions() {
    }

    static SfxElectricMachineRegistry create(JavaPlugin plugin, SfxItems items, DefaultManualMachineRegistry manualMachines, SfxBlockDataService blockData, SfxVirtualContainerService virtualContainers) {
        SfxElectricMachineRegistry result = new SfxElectricMachineRegistry();
        SfxElectricMachineDefinitionConfig config = SfxElectricMachineDefinitionConfig.load(plugin);
        SfxElectricRecipeYamlLoader staticRecipes = SfxElectricRecipeYamlLoader.load(plugin);
        SfxElectricRecipeProvider furnaceRecipes = new SfxVanillaFurnaceRecipeProvider(plugin, 4);
        SfxElectricRecipeProvider grinderRecipes = new SfxClassicOreGrinderRecipeProvider(
                manualMachines.recipesFor("sf:grind_stone"),
                manualMachines.recipesFor("sf:ore_crusher"),
                4);
        SfxElectricRecipeProvider autoDrierRecipes = staticRecipes.provider("auto_drier");
        SfxElectricRecipeProvider ingotFactoryRecipes = staticRecipes.provider("electric_ingot_factory");
        SfxElectricRecipeProvider carbonPressRecipes = staticRecipes.provider("carbon_press");
        SfxElectricRecipeProvider electricPressRecipes = staticRecipes.provider("electric_press");
        SfxElectricRecipeProvider crucibleRecipes = staticRecipes.provider("electrified_crucible");
        SfxElectricRecipeProvider freezerRecipes = staticRecipes.provider("freezer");
        SfxElectricRecipeProvider heatedPressureRecipes = staticRecipes.provider("heated_pressure_chamber");
        SfxElectricRecipeProvider foodFabricatorRecipes = staticRecipes.provider("food_fabricator");
        SfxElectricRecipeProvider foodComposterRecipes = staticRecipes.provider("food_composter");
        SfxElectricRecipeProvider goldPanRecipes = staticRecipes.provider("electric_gold_pan");
        SfxElectricRecipeProvider dustWasherRecipes = staticRecipes.provider("electric_dust_washer");
        SfxElectricRecipeProvider refineryRecipes = staticRecipes.provider("refinery");
        SfxElectricRecipeProvider ingotPulverizerRecipes = staticRecipes.provider("electric_ingot_pulverizer");
        SfxElectricRecipeProvider electricSmelteryRecipes = new SfxElectricSmelteryRecipeProvider(manualMachines.recipesFor("sf:smeltery"), 6);
        SfxElectricRecipeProvider autoEnchanterRecipes = SfxSpecialElectricRecipeProviders.autoEnchanter(plugin, items);
        SfxElectricRecipeProvider autoDisenchanterRecipes = SfxSpecialElectricRecipeProviders.autoDisenchanter(plugin, items);
        SfxElectricRecipeProvider bookBinderRecipes = SfxSpecialElectricRecipeProviders.bookBinder(plugin);
        SfxElectricRecipeProvider autoAnvilRecipes = SfxSpecialElectricRecipeProviders.autoAnvil(plugin, items, 10);
        SfxElectricRecipeProvider autoAnvil2Recipes = SfxSpecialElectricRecipeProviders.autoAnvil(plugin, items, 25);
        SfxElectricRecipeProvider produceCollectorRecipes = SfxAreaElectricMachineProviders.produceCollector();
        SfxElectricRecipeProvider autoBreederRecipes = SfxAreaElectricMachineProviders.autoBreeder();
        SfxElectricRecipeProvider animalGrowthRecipes = SfxAreaElectricMachineProviders.animalGrowthAccelerator();
        SfxElectricRecipeProvider cropGrowthRecipes = SfxAreaElectricMachineProviders.cropGrowthAccelerator(blockData, 3, 20);
        SfxElectricRecipeProvider cropGrowth2Recipes = SfxAreaElectricMachineProviders.cropGrowthAccelerator(blockData, 4, 30);
        SfxElectricRecipeProvider treeGrowthRecipes = SfxAreaElectricMachineProviders.treeGrowthAccelerator();
        SfxElectricRecipeProvider expCollectorRecipes = SfxAreaElectricMachineProviders.expCollector();
        SfxElectricRecipeProvider fluidPumpRecipes = SfxAreaElectricMachineProviders.fluidPump();
        SfxElectricAssemblerSpec ironGolemAssemblerSpec = config.assemblerSpec("sf:iron_golem_assembler");
        SfxElectricAssemblerSpec witherAssemblerSpec = config.assemblerSpec("sf:wither_assembler");
        SfxElectricRecipeProvider ironGolemAssemblerRecipes = assemblerProvider("iron_golem", EntityType.IRON_GOLEM, ironGolemAssemblerSpec);
        SfxElectricRecipeProvider witherAssemblerRecipes = assemblerProvider("wither", EntityType.WITHER, witherAssemblerSpec);
        boolean sfxAutoBrewer = plugin.getConfig().getBoolean("electric-machines.sfx-extensions.auto-brewer.enabled", true);
        SfxElectricRecipeProvider autoBrewerRecipes = sfxAutoBrewer
                ? new SfxAdvancedAutoBrewerRecipeProvider(plugin)
                : new SfxAutoBrewerRecipeProvider();
        SfxElectricRecipeProvider vanillaAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.vanilla(virtualContainers, items);
        SfxElectricRecipeProvider enhancedAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.enhanced(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:enhanced_crafting_table")));
        SfxElectricRecipeProvider armorAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.armor(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:armor_forge")));

        register(result, config, "sf:electric_furnace", furnaceRecipes);
        register(result, config, "sf:electric_furnace_2", furnaceRecipes);
        register(result, config, "sf:electric_furnace_3", furnaceRecipes);
        register(result, config, "sf:electric_ore_grinder", grinderRecipes);
        register(result, config, "sf:electric_ore_grinder_2", grinderRecipes);
        register(result, config, "sf:electric_ore_grinder_3", grinderRecipes);

        register(result, config, "sf:auto_drier", autoDrierRecipes);
        if (sfxAutoBrewer) {
            register(result, config, "sf:auto_brewer", autoBrewerRecipes);
            register(result, config, "sf:auto_brewer_2", autoBrewerRecipes);
        } else {
            register(result, config, "sf:auto_brewer", "sf:auto_brewer#legacy", autoBrewerRecipes);
        }

        register(result, config, "sf:vanilla_auto_crafter", vanillaAutoCrafterRecipes);
        register(result, config, "sf:enhanced_auto_crafter", enhancedAutoCrafterRecipes);
        register(result, config, "sf:armor_auto_crafter", armorAutoCrafterRecipes);

        register(result, config, "sf:electric_ingot_factory", ingotFactoryRecipes);
        register(result, config, "sf:electric_ingot_factory_2", ingotFactoryRecipes);
        register(result, config, "sf:electric_ingot_factory_3", ingotFactoryRecipes);

        register(result, config, "sf:carbon_press", carbonPressRecipes);
        register(result, config, "sf:carbon_press_2", carbonPressRecipes);
        register(result, config, "sf:carbon_press_3", carbonPressRecipes);

        register(result, config, "sf:electric_press", electricPressRecipes);
        register(result, config, "sf:electric_press_2", electricPressRecipes);

        register(result, config, "sf:electrified_crucible", crucibleRecipes);
        register(result, config, "sf:electrified_crucible_2", crucibleRecipes);
        register(result, config, "sf:electrified_crucible_3", crucibleRecipes);

        register(result, config, "sf:freezer", freezerRecipes);
        register(result, config, "sf:freezer_2", freezerRecipes);
        register(result, config, "sf:freezer_3", freezerRecipes);

        register(result, config, "sf:heated_pressure_chamber", heatedPressureRecipes);
        register(result, config, "sf:heated_pressure_chamber_2", heatedPressureRecipes);

        register(result, config, "sf:food_fabricator", foodFabricatorRecipes);
        register(result, config, "sf:food_fabricator_2", foodFabricatorRecipes);
        register(result, config, "sf:food_composter", foodComposterRecipes);
        register(result, config, "sf:food_composter_2", foodComposterRecipes);

        register(result, config, "sf:refinery", refineryRecipes);

        register(result, config, "sf:electric_ingot_pulverizer", ingotPulverizerRecipes);
        register(result, config, "sf:electric_smeltery", electricSmelteryRecipes);
        register(result, config, "sf:electric_smeltery_2", electricSmelteryRecipes);

        register(result, config, "sf:auto_enchanter", autoEnchanterRecipes);
        register(result, config, "sf:auto_enchanter_2", autoEnchanterRecipes);
        register(result, config, "sf:auto_disenchanter", autoDisenchanterRecipes);
        register(result, config, "sf:auto_disenchanter_2", autoDisenchanterRecipes);
        register(result, config, "sf:book_binder", bookBinderRecipes);
        register(result, config, "sf:auto_anvil", autoAnvilRecipes);
        register(result, config, "sf:auto_anvil_2", autoAnvil2Recipes);

        register(result, config, "sf:produce_collector", produceCollectorRecipes);
        register(result, config, "sf:auto_breeder", autoBreederRecipes);
        register(result, config, "sf:animal_growth_accelerator", animalGrowthRecipes);
        register(result, config, "sf:crop_growth_accelerator", cropGrowthRecipes);
        register(result, config, "sf:crop_growth_accelerator_2", cropGrowth2Recipes);
        register(result, config, "sf:tree_growth_accelerator", treeGrowthRecipes);
        register(result, config, "sf:xp_collector", expCollectorRecipes);

        SfxElectricRecipeProvider gpsTransmitterRecipes = SfxGpsElectricMachineProviders.transmitter();
        SfxElectricRecipeProvider geoMinerRecipes = SfxGpsElectricMachineProviders.geoExtractor(false);
        SfxElectricRecipeProvider oilPumpRecipes = SfxGpsElectricMachineProviders.geoExtractor(true);
        register(result, config, "sf:gps_transmitter", gpsTransmitterRecipes);
        register(result, config, "sf:gps_transmitter_2", gpsTransmitterRecipes);
        register(result, config, "sf:gps_transmitter_3", gpsTransmitterRecipes);
        register(result, config, "sf:gps_transmitter_4", gpsTransmitterRecipes);
        register(result, config, "sf:geo_miner", geoMinerRecipes);
        register(result, config, "sf:oil_pump", oilPumpRecipes);

        register(result, config, "sf:fluid_pump", fluidPumpRecipes);
        register(result, config, "sf:iron_golem_assembler", ironGolemAssemblerRecipes);
        register(result, config, "sf:wither_assembler", witherAssemblerRecipes);

        register(result, config, "sf:electric_gold_pan", goldPanRecipes);
        register(result, config, "sf:electric_gold_pan_2", goldPanRecipes);
        register(result, config, "sf:electric_gold_pan_3", goldPanRecipes);

        register(result, config, "sf:electric_dust_washer", dustWasherRecipes);
        register(result, config, "sf:electric_dust_washer_2", dustWasherRecipes);
        register(result, config, "sf:electric_dust_washer_3", dustWasherRecipes);
        return result;
    }

    private static SfxElectricRecipeProvider assemblerProvider(String key, EntityType entityType, SfxElectricAssemblerSpec spec) {
        return SfxAreaElectricMachineProviders.assembler(
                key,
                entityType,
                spec.headMaterial(),
                spec.headAmount(),
                spec.bodyMaterials(),
                spec.bodyAmount(),
                30 * 20);
    }

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, SfxElectricRecipeProvider provider) {
        register(registry, config, id, id, provider);
    }

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, String compiledEntryId, SfxElectricRecipeProvider provider) {
        registry.register(config.create(id, compiledEntryId, provider));
    }
}
