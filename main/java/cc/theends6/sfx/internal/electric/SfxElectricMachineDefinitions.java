package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        SfxElectricRecipeProvider gpsTransmitterRecipes = SfxGpsElectricMachineProviders.transmitter();
        SfxElectricRecipeProvider geoMinerRecipes = SfxGpsElectricMachineProviders.geoExtractor(false);
        SfxElectricRecipeProvider oilPumpRecipes = SfxGpsElectricMachineProviders.geoExtractor(true);
        boolean sfxAutoBrewer = plugin.getConfig().getBoolean("electric-machines.sfx-extensions.auto-brewer.enabled", true);
        SfxElectricRecipeProvider autoBrewerRecipes = sfxAutoBrewer
                ? new SfxAdvancedAutoBrewerRecipeProvider(plugin)
                : new SfxAutoBrewerRecipeProvider();
        SfxElectricRecipeProvider vanillaAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.vanilla(virtualContainers, items);
        SfxElectricRecipeProvider enhancedAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.enhanced(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:enhanced_crafting_table")));
        SfxElectricRecipeProvider armorAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.armor(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:armor_forge")));
        Map<String, SfxElectricRecipeProvider> specialProviders = new LinkedHashMap<>();
        specialProviders.put("sf:vanilla_furnace", furnaceRecipes);
        specialProviders.put("sf:classic_ore_grinder", grinderRecipes);
        specialProviders.put("sf:electric_smeltery", electricSmelteryRecipes);
        specialProviders.put("sf:auto_brewer", autoBrewerRecipes);
        specialProviders.put("sf:auto_brewer_legacy", autoBrewerRecipes);
        specialProviders.put("sf:vanilla_auto_crafter", vanillaAutoCrafterRecipes);
        specialProviders.put("sf:enhanced_auto_crafter", enhancedAutoCrafterRecipes);
        specialProviders.put("sf:armor_auto_crafter", armorAutoCrafterRecipes);
        specialProviders.put("sf:auto_enchanter", autoEnchanterRecipes);
        specialProviders.put("sf:auto_disenchanter", autoDisenchanterRecipes);
        specialProviders.put("sf:book_binder", bookBinderRecipes);
        specialProviders.put("sf:auto_anvil", autoAnvilRecipes);
        specialProviders.put("sf:auto_anvil_2", autoAnvil2Recipes);
        specialProviders.put("sf:produce_collector", produceCollectorRecipes);
        specialProviders.put("sf:auto_breeder", autoBreederRecipes);
        specialProviders.put("sf:animal_growth_accelerator", animalGrowthRecipes);
        specialProviders.put("sf:crop_growth_accelerator", cropGrowthRecipes);
        specialProviders.put("sf:crop_growth_accelerator_2", cropGrowth2Recipes);
        specialProviders.put("sf:tree_growth_accelerator", treeGrowthRecipes);
        specialProviders.put("sf:xp_collector", expCollectorRecipes);
        specialProviders.put("sf:gps_transmitter", gpsTransmitterRecipes);
        specialProviders.put("sf:geo_miner", geoMinerRecipes);
        specialProviders.put("sf:oil_pump", oilPumpRecipes);
        specialProviders.put("sf:fluid_pump", fluidPumpRecipes);
        specialProviders.put("sf:iron_golem_assembler", ironGolemAssemblerRecipes);
        specialProviders.put("sf:wither_assembler", witherAssemblerRecipes);

        register(result, config, "sf:electric_furnace", staticRecipes, specialProviders);
        register(result, config, "sf:electric_furnace_2", staticRecipes, specialProviders);
        register(result, config, "sf:electric_furnace_3", staticRecipes, specialProviders);
        register(result, config, "sf:electric_ore_grinder", staticRecipes, specialProviders);
        register(result, config, "sf:electric_ore_grinder_2", staticRecipes, specialProviders);
        register(result, config, "sf:electric_ore_grinder_3", staticRecipes, specialProviders);

        register(result, config, "sf:auto_drier", staticRecipes);
        if (sfxAutoBrewer) {
            register(result, config, "sf:auto_brewer", staticRecipes, specialProviders);
            register(result, config, "sf:auto_brewer_2", staticRecipes, specialProviders);
        } else {
            register(result, config, "sf:auto_brewer", "sf:auto_brewer#legacy", staticRecipes, specialProviders);
        }

        register(result, config, "sf:vanilla_auto_crafter", staticRecipes, specialProviders);
        register(result, config, "sf:enhanced_auto_crafter", staticRecipes, specialProviders);
        register(result, config, "sf:armor_auto_crafter", staticRecipes, specialProviders);

        register(result, config, "sf:electric_ingot_factory", staticRecipes);
        register(result, config, "sf:electric_ingot_factory_2", staticRecipes);
        register(result, config, "sf:electric_ingot_factory_3", staticRecipes);

        register(result, config, "sf:carbon_press", staticRecipes);
        register(result, config, "sf:carbon_press_2", staticRecipes);
        register(result, config, "sf:carbon_press_3", staticRecipes);

        register(result, config, "sf:electric_press", staticRecipes);
        register(result, config, "sf:electric_press_2", staticRecipes);

        register(result, config, "sf:electrified_crucible", staticRecipes);
        register(result, config, "sf:electrified_crucible_2", staticRecipes);
        register(result, config, "sf:electrified_crucible_3", staticRecipes);

        register(result, config, "sf:freezer", staticRecipes);
        register(result, config, "sf:freezer_2", staticRecipes);
        register(result, config, "sf:freezer_3", staticRecipes);

        register(result, config, "sf:heated_pressure_chamber", staticRecipes);
        register(result, config, "sf:heated_pressure_chamber_2", staticRecipes);

        register(result, config, "sf:food_fabricator", staticRecipes);
        register(result, config, "sf:food_fabricator_2", staticRecipes);
        register(result, config, "sf:food_composter", staticRecipes);
        register(result, config, "sf:food_composter_2", staticRecipes);

        register(result, config, "sf:refinery", staticRecipes);

        register(result, config, "sf:electric_ingot_pulverizer", staticRecipes);
        register(result, config, "sf:electric_smeltery", staticRecipes, specialProviders);
        register(result, config, "sf:electric_smeltery_2", staticRecipes, specialProviders);

        register(result, config, "sf:auto_enchanter", staticRecipes, specialProviders);
        register(result, config, "sf:auto_enchanter_2", staticRecipes, specialProviders);
        register(result, config, "sf:auto_disenchanter", staticRecipes, specialProviders);
        register(result, config, "sf:auto_disenchanter_2", staticRecipes, specialProviders);
        register(result, config, "sf:book_binder", staticRecipes, specialProviders);
        register(result, config, "sf:auto_anvil", staticRecipes, specialProviders);
        register(result, config, "sf:auto_anvil_2", staticRecipes, specialProviders);

        register(result, config, "sf:produce_collector", staticRecipes, specialProviders);
        register(result, config, "sf:auto_breeder", staticRecipes, specialProviders);
        register(result, config, "sf:animal_growth_accelerator", staticRecipes, specialProviders);
        register(result, config, "sf:crop_growth_accelerator", staticRecipes, specialProviders);
        register(result, config, "sf:crop_growth_accelerator_2", staticRecipes, specialProviders);
        register(result, config, "sf:tree_growth_accelerator", staticRecipes, specialProviders);
        register(result, config, "sf:xp_collector", staticRecipes, specialProviders);

        register(result, config, "sf:gps_transmitter", staticRecipes, specialProviders);
        register(result, config, "sf:gps_transmitter_2", staticRecipes, specialProviders);
        register(result, config, "sf:gps_transmitter_3", staticRecipes, specialProviders);
        register(result, config, "sf:gps_transmitter_4", staticRecipes, specialProviders);
        register(result, config, "sf:geo_miner", staticRecipes, specialProviders);
        register(result, config, "sf:oil_pump", staticRecipes, specialProviders);

        register(result, config, "sf:fluid_pump", staticRecipes, specialProviders);
        register(result, config, "sf:iron_golem_assembler", staticRecipes, specialProviders);
        register(result, config, "sf:wither_assembler", staticRecipes, specialProviders);

        register(result, config, "sf:electric_gold_pan", staticRecipes);
        register(result, config, "sf:electric_gold_pan_2", staticRecipes);
        register(result, config, "sf:electric_gold_pan_3", staticRecipes);

        register(result, config, "sf:electric_dust_washer", staticRecipes);
        register(result, config, "sf:electric_dust_washer_2", staticRecipes);
        register(result, config, "sf:electric_dust_washer_3", staticRecipes);
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

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, SfxElectricRecipeYamlLoader staticRecipes) {
        registry.register(config.create(id, id, staticRecipes));
    }

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, SfxElectricRecipeYamlLoader staticRecipes, Map<String, SfxElectricRecipeProvider> specialProviders) {
        registry.register(config.create(id, id, staticRecipes, specialProviders));
    }

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, String compiledEntryId, SfxElectricRecipeYamlLoader staticRecipes, Map<String, SfxElectricRecipeProvider> specialProviders) {
        registry.register(config.create(id, compiledEntryId, staticRecipes, specialProviders));
    }

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, String compiledEntryId, SfxElectricRecipeProvider provider) {
        registry.register(config.create(id, compiledEntryId, provider));
    }
}
