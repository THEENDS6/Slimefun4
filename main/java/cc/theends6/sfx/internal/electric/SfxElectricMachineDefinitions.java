package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
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
        boolean allowSoulSoil = plugin.getConfig().getBoolean("configurable-machines.wither-assembler.allow-soul-soil", true);
        SfxElectricAssemblerSpec ironGolemAssemblerSpec = new SfxElectricAssemblerSpec(Material.CARVED_PUMPKIN, 1, Set.of(Material.IRON_BLOCK), 4);
        SfxElectricAssemblerSpec witherAssemblerSpec = new SfxElectricAssemblerSpec(
                Material.WITHER_SKELETON_SKULL,
                3,
                allowSoulSoil ? Set.of(Material.SOUL_SAND, Material.SOUL_SOIL) : Set.of(Material.SOUL_SAND),
                4);
        SfxElectricRecipeProvider ironGolemAssemblerRecipes = SfxAreaElectricMachineProviders.assembler(
                "iron_golem",
                EntityType.IRON_GOLEM,
                Material.CARVED_PUMPKIN,
                1,
                Set.of(Material.IRON_BLOCK),
                4,
                30 * 20);
        SfxElectricRecipeProvider witherAssemblerRecipes = SfxAreaElectricMachineProviders.assembler(
                "wither",
                EntityType.WITHER,
                Material.WITHER_SKELETON_SKULL,
                3,
                allowSoulSoil ? Set.of(Material.SOUL_SAND, Material.SOUL_SOIL) : Set.of(Material.SOUL_SAND),
                4,
                30 * 20);
        boolean sfxAutoBrewer = plugin.getConfig().getBoolean("electric-machines.sfx-extensions.auto-brewer.enabled", true);
        SfxElectricRecipeProvider autoBrewerRecipes = sfxAutoBrewer
                ? new SfxAdvancedAutoBrewerRecipeProvider(plugin)
                : new SfxAutoBrewerRecipeProvider();
        SfxElectricRecipeProvider vanillaAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.vanilla(virtualContainers, items);
        SfxElectricRecipeProvider enhancedAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.enhanced(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:enhanced_crafting_table")));
        SfxElectricRecipeProvider armorAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.armor(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:armor_forge")));

        double crucibleEnergyMultiplier = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true)
                ? Math.max(1.0D, plugin.getConfig().getDouble("energy.generator-balance.electrified-crucible-consumption-multiplier", 1.5D))
                : 1.0D;
        boolean cropGrowthSfxBalance = plugin.getConfig().getBoolean("electric-machines.sfx-balance.crop-growth-accelerator", true);
        boolean treeGrowthSfxBalance = plugin.getConfig().getBoolean("electric-machines.sfx-balance.tree-growth-accelerator", true);
        int cropGrowthCapacity = cropGrowthSfxBalance ? buffer(2048) : buffer(1024);
        int treeGrowthCapacity = treeGrowthSfxBalance ? buffer(2048) : buffer(1024);
        int cropGrowthEnergy = cropGrowthSfxBalance ? 100 : 50;
        int cropGrowth2Energy = cropGrowthSfxBalance ? 120 : 60;
        int treeGrowthEnergy = treeGrowthSfxBalance ? 96 : 24;

        register(result, config, new SfxElectricMachineDefinition("sf:electric_furnace", "Electric Furnace", 1, 1280, 4, Material.FLINT_AND_STEEL, furnaceRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_furnace_2", "Electric Furnace - II", 2, 2560, 6, Material.FLINT_AND_STEEL, furnaceRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_furnace_3", "Electric Furnace - III", 4, 5120, 10, Material.FLINT_AND_STEEL, furnaceRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_ore_grinder", "Electric Ore Grinder", 1, 2560, 12, Material.IRON_PICKAXE, grinderRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_ore_grinder_2", "Electric Ore Grinder - II", 4, 10240, 30, Material.IRON_PICKAXE, grinderRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_ore_grinder_3", "Electric Ore Grinder - III", 10, 20480, 90, Material.IRON_PICKAXE, grinderRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:auto_drier", "Auto Drier", 1, buffer(1280), 10, Material.FLINT_AND_STEEL, autoDrierRecipes));
        if (sfxAutoBrewer) {
            register(result, config, new SfxElectricMachineDefinition("sf:auto_brewer", "Auto Brewer", 1, buffer(512), 12, Material.BREWING_STAND, autoBrewerRecipes, SfxElectricMachineDefinition.AUTO_BREWER_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
            register(result, config, new SfxElectricMachineDefinition("sf:auto_brewer_2", "Auto Brewer II", 5, buffer(1024), 50, Material.BREWING_STAND, autoBrewerRecipes, SfxElectricMachineDefinition.AUTO_BREWER_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        } else {
            register(result, config, new SfxElectricMachineDefinition("sf:auto_brewer", "Auto Brewer", 1, buffer(256), 12, Material.FISHING_ROD, autoBrewerRecipes, "sf:auto_brewer#legacy"));
        }

        register(result, config, new SfxElectricMachineDefinition("sf:vanilla_auto_crafter", "Auto-Crafter (Vanilla)", 1, 5120, 32, Material.CRAFTING_TABLE, vanillaAutoCrafterRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:enhanced_auto_crafter", "Auto-Crafter (Enhanced)", 1, 5120, 32, Material.CRAFTING_TABLE, enhancedAutoCrafterRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:armor_auto_crafter", "Auto-Crafter (Armor Forge)", 1, 5120, 64, Material.ANVIL, armorAutoCrafterRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));

        register(result, config, new SfxElectricMachineDefinition("sf:electric_ingot_factory", "Electric Ingot Factory", 1, buffer(512), 8, Material.FLINT_AND_STEEL, ingotFactoryRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_ingot_factory_2", "Electric Ingot Factory - II", 2, buffer(1024), 14, Material.FLINT_AND_STEEL, ingotFactoryRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_ingot_factory_3", "Electric Ingot Factory - III", 8, buffer(4096), 40, Material.FLINT_AND_STEEL, ingotFactoryRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:carbon_press", "Carbon Press", 1, buffer(1280), 20, Material.DIAMOND_PICKAXE, carbonPressRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:carbon_press_2", "Carbon Press - II", 3, buffer(4096), 50, Material.DIAMOND_PICKAXE, carbonPressRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:carbon_press_3", "Carbon Press - III", 15, buffer(16384), 180, Material.DIAMOND_PICKAXE, carbonPressRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:electric_press", "Electric Press", 1, buffer(1280), 16, Material.IRON_HOE, electricPressRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_press_2", "Electric Press - II", 3, buffer(2560), 40, Material.IRON_HOE, electricPressRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:electrified_crucible", "Electrified Crucible", 1, buffer(1024), adjustedEnergy(48, crucibleEnergyMultiplier), Material.FLINT_AND_STEEL, crucibleRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electrified_crucible_2", "Electrified Crucible - II", 2, buffer(2048), adjustedEnergy(80, crucibleEnergyMultiplier), Material.FLINT_AND_STEEL, crucibleRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electrified_crucible_3", "Electrified Crucible - III", 4, buffer(4096), adjustedEnergy(120, crucibleEnergyMultiplier), Material.FLINT_AND_STEEL, crucibleRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:freezer", "Freezer", 1, buffer(256), 18, Material.GOLDEN_PICKAXE, freezerRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:freezer_2", "Freezer - II", 2, buffer(256), 30, Material.GOLDEN_PICKAXE, freezerRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:freezer_3", "Freezer - III", 3, buffer(256), 42, Material.GOLDEN_PICKAXE, freezerRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:heated_pressure_chamber", "Heated Pressure Chamber", 1, buffer(1024), 10, Material.FLINT_AND_STEEL, heatedPressureRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:heated_pressure_chamber_2", "Heated Pressure Chamber - II", 5, buffer(4096), 44, Material.FLINT_AND_STEEL, heatedPressureRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:food_fabricator", "Food Fabricator", 1, buffer(256), 14, Material.GOLDEN_HOE, foodFabricatorRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:food_fabricator_2", "Food Fabricator - II", 6, buffer(512), 48, Material.GOLDEN_HOE, foodFabricatorRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:food_composter", "Food Composter", 1, buffer(256), 16, Material.GOLDEN_HOE, foodComposterRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:food_composter_2", "Food Composter - II", 10, buffer(512), 52, Material.GOLDEN_HOE, foodComposterRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:refinery", "Refinery", 1, buffer(256), 32, Material.PISTON, refineryRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:electric_ingot_pulverizer", "Electric Ingot Pulverizer", 1, buffer(512), 14, Material.IRON_PICKAXE, ingotPulverizerRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_smeltery", "Electric Smeltery", 1, buffer(512), 20, Material.FLINT_AND_STEEL, electricSmelteryRecipes, SfxElectricMachineDefinition.SIX_INPUT_SLOTS, new int[]{24, 25}));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_smeltery_2", "Electric Smeltery - II", 3, buffer(512), 40, Material.FLINT_AND_STEEL, electricSmelteryRecipes, SfxElectricMachineDefinition.SIX_INPUT_SLOTS, new int[]{24, 25}));

        register(result, config, new SfxElectricMachineDefinition("sf:auto_enchanter", "Auto Enchanter", 1, buffer(128), 18, Material.GOLDEN_CHESTPLATE, autoEnchanterRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:auto_enchanter_2", "Auto Enchanter - II", 3, buffer(1028), 48, Material.GOLDEN_CHESTPLATE, autoEnchanterRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:auto_disenchanter", "Auto Disenchanter", 1, buffer(128), 18, Material.DIAMOND_CHESTPLATE, autoDisenchanterRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:auto_disenchanter_2", "Auto Disenchanter - II", 3, buffer(1028), 48, Material.DIAMOND_CHESTPLATE, autoDisenchanterRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:book_binder", "Book Binder", 1, buffer(128), 16, Material.IRON_CHESTPLATE, bookBinderRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:auto_anvil", "Auto Anvil", 1, buffer(128), 24, Material.IRON_PICKAXE, autoAnvilRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:auto_anvil_2", "Auto Anvil - II", 1, buffer(128), 32, Material.IRON_PICKAXE, autoAnvil2Recipes));

        register(result, config, new SfxElectricMachineDefinition("sf:produce_collector", "Produce Collector", 1, buffer(512), 32, Material.BUCKET, produceCollectorRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:auto_breeder", "Auto Breeder", 1, buffer(1024), 60, Material.WHEAT, autoBreederRecipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:animal_growth_accelerator", "Animal Growth Accelerator", 1, buffer(1024), 14, Material.WHEAT, animalGrowthRecipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:crop_growth_accelerator", "Crop Growth Accelerator", 1, cropGrowthCapacity, cropGrowthEnergy, Material.BONE_MEAL, cropGrowthRecipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:crop_growth_accelerator_2", "Crop Growth Accelerator - II", 1, cropGrowthCapacity, cropGrowth2Energy, Material.BONE_MEAL, cropGrowth2Recipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:tree_growth_accelerator", "Tree Growth Accelerator", 1, treeGrowthCapacity, treeGrowthEnergy, Material.OAK_SAPLING, treeGrowthRecipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:xp_collector", "EXP Collector", 1, buffer(1024), 20, Material.EXPERIENCE_BOTTLE, expCollectorRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS));
        SfxElectricRecipeProvider gpsTransmitterRecipes = SfxGpsElectricMachineProviders.transmitter();
        SfxElectricRecipeProvider geoMinerRecipes = SfxGpsElectricMachineProviders.geoExtractor(false);
        SfxElectricRecipeProvider oilPumpRecipes = SfxGpsElectricMachineProviders.geoExtractor(true);

        register(result, config, new SfxElectricMachineDefinition("sf:gps_transmitter", "GPS Transmitter", 1, 320, 2, Material.COMPASS, gpsTransmitterRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:gps_transmitter_2", "Advanced GPS Transmitter", 1, 1280, 6, Material.COMPASS, gpsTransmitterRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:gps_transmitter_3", "Carbonado GPS Transmitter", 1, 5120, 22, Material.COMPASS, gpsTransmitterRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:gps_transmitter_4", "Energized GPS Transmitter", 1, 20480, 92, Material.COMPASS, gpsTransmitterRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:geo_miner", "GEO Miner", 1, 512, 48, Material.DIAMOND_PICKAXE, geoMinerRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.GEO_MINER_OUTPUT_SLOTS));
        register(result, config, new SfxElectricMachineDefinition("sf:oil_pump", "Oil Pump", 1, 512, 24, Material.BUCKET, oilPumpRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:fluid_pump", "Fluid Pump", 1, 512, 8, Material.BUCKET, fluidPumpRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:iron_golem_assembler", "Iron Golem Assembler", 1, 81920, 75, Material.CARVED_PUMPKIN, ironGolemAssemblerRecipes, SfxElectricMachineDefinition.ASSEMBLER_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS, ironGolemAssemblerSpec));
        register(result, config, new SfxElectricMachineDefinition("sf:wither_assembler", "Wither Assembler", 1, 81920, 150, Material.WITHER_SKELETON_SKULL, witherAssemblerRecipes, SfxElectricMachineDefinition.ASSEMBLER_INPUT_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS, witherAssemblerSpec));

        register(result, config, new SfxElectricMachineDefinition("sf:electric_gold_pan", "Electric Gold Pan", 1, buffer(128), 2, Material.DIAMOND_SHOVEL, goldPanRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_gold_pan_2", "Electric Gold Pan - II", 3, buffer(256), 4, Material.DIAMOND_SHOVEL, goldPanRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_gold_pan_3", "Electric Gold Pan - III", 10, buffer(512), 14, Material.DIAMOND_SHOVEL, goldPanRecipes));

        register(result, config, new SfxElectricMachineDefinition("sf:electric_dust_washer", "Electric Dust Washer", 1, buffer(256), 6, Material.GOLDEN_SHOVEL, dustWasherRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_dust_washer_2", "Electric Dust Washer - II", 2, buffer(512), 10, Material.GOLDEN_SHOVEL, dustWasherRecipes));
        register(result, config, new SfxElectricMachineDefinition("sf:electric_dust_washer_3", "Electric Dust Washer - III", 10, buffer(1024), 30, Material.GOLDEN_SHOVEL, dustWasherRecipes));
        return result;
    }


    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, SfxElectricMachineDefinition fallback) {
        registry.register(config.apply(fallback));
    }

    private static int buffer(int classicBuffer) {
        return Math.max(0, classicBuffer * 20);
    }

    private static int adjustedEnergy(int classicEnergy, double multiplier) {
        return Math.max(1, (int) Math.round(classicEnergy * multiplier));
    }
}
