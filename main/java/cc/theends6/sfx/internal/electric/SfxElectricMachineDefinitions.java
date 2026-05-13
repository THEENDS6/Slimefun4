package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxElectricMachineDefinitions {
    private SfxElectricMachineDefinitions() {
    }

    static SfxElectricMachineRegistry create(JavaPlugin plugin, SfxItems items, DefaultManualMachineRegistry manualMachines, SfxBlockDataService blockData) {
        SfxElectricMachineRegistry result = new SfxElectricMachineRegistry();
        SfxElectricRecipeProvider furnaceRecipes = new SfxVanillaFurnaceRecipeProvider(plugin, 4);
        SfxElectricRecipeProvider grinderRecipes = new SfxClassicOreGrinderRecipeProvider(
                manualMachines.recipesFor("sf:grind_stone"),
                manualMachines.recipesFor("sf:ore_crusher"),
                4);
        SfxElectricRecipeProvider autoDrierRecipes = SfxLegacyElectricRecipeProviders.autoDrier();
        SfxElectricRecipeProvider ingotFactoryRecipes = SfxLegacyElectricRecipeProviders.electricIngotFactory();
        SfxElectricRecipeProvider carbonPressRecipes = SfxLegacyElectricRecipeProviders.carbonPress();
        SfxElectricRecipeProvider electricPressRecipes = SfxLegacyElectricRecipeProviders.electricPress();
        SfxElectricRecipeProvider crucibleRecipes = SfxLegacyElectricRecipeProviders.electrifiedCrucible();
        SfxElectricRecipeProvider freezerRecipes = SfxLegacyElectricRecipeProviders.freezer();
        SfxElectricRecipeProvider heatedPressureRecipes = SfxLegacyElectricRecipeProviders.heatedPressureChamber();
        SfxElectricRecipeProvider foodFabricatorRecipes = SfxLegacyElectricRecipeProviders.foodFabricator();
        SfxElectricRecipeProvider foodComposterRecipes = SfxLegacyElectricRecipeProviders.foodComposter();
        SfxElectricRecipeProvider goldPanRecipes = SfxLegacyElectricRecipeProviders.electricGoldPan();
        SfxElectricRecipeProvider dustWasherRecipes = SfxLegacyElectricRecipeProviders.electricDustWasher();
        SfxElectricRecipeProvider refineryRecipes = SfxLegacyElectricRecipeProviders.refinery();
        SfxElectricRecipeProvider ingotPulverizerRecipes = new SfxElectricIngotPulverizerRecipeProvider();
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

        result.register(new SfxElectricMachineDefinition("sf:electric_furnace", "Electric Furnace", 1, 1280, 4, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace_2", "Electric Furnace - II", 2, 2560, 6, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace_3", "Electric Furnace - III", 4, 5120, 10, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder", "Electric Ore Grinder", 1, 2560, 12, Material.IRON_PICKAXE, grinderRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder_2", "Electric Ore Grinder - II", 4, 10240, 30, Material.IRON_PICKAXE, grinderRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder_3", "Electric Ore Grinder - III", 10, 20480, 90, Material.IRON_PICKAXE, grinderRecipes));

        result.register(new SfxElectricMachineDefinition("sf:auto_drier", "Auto Drier", 1, buffer(1280), 10, Material.FLINT_AND_STEEL, autoDrierRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electric_ingot_factory", "Electric Ingot Factory", 1, buffer(512), 8, Material.FLINT_AND_STEEL, ingotFactoryRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ingot_factory_2", "Electric Ingot Factory - II", 2, buffer(1024), 14, Material.FLINT_AND_STEEL, ingotFactoryRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ingot_factory_3", "Electric Ingot Factory - III", 8, buffer(4096), 40, Material.FLINT_AND_STEEL, ingotFactoryRecipes));

        result.register(new SfxElectricMachineDefinition("sf:carbon_press", "Carbon Press", 1, buffer(1280), 20, Material.DIAMOND_PICKAXE, carbonPressRecipes));
        result.register(new SfxElectricMachineDefinition("sf:carbon_press_2", "Carbon Press - II", 3, buffer(4096), 50, Material.DIAMOND_PICKAXE, carbonPressRecipes));
        result.register(new SfxElectricMachineDefinition("sf:carbon_press_3", "Carbon Press - III", 15, buffer(16384), 180, Material.DIAMOND_PICKAXE, carbonPressRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electric_press", "Electric Press", 1, buffer(1280), 16, Material.IRON_HOE, electricPressRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_press_2", "Electric Press - II", 3, buffer(2560), 40, Material.IRON_HOE, electricPressRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electrified_crucible", "Electrified Crucible", 1, buffer(1024), adjustedEnergy(48, crucibleEnergyMultiplier), Material.FLINT_AND_STEEL, crucibleRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electrified_crucible_2", "Electrified Crucible - II", 2, buffer(2048), adjustedEnergy(80, crucibleEnergyMultiplier), Material.FLINT_AND_STEEL, crucibleRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electrified_crucible_3", "Electrified Crucible - III", 4, buffer(4096), adjustedEnergy(120, crucibleEnergyMultiplier), Material.FLINT_AND_STEEL, crucibleRecipes));

        result.register(new SfxElectricMachineDefinition("sf:freezer", "Freezer", 1, buffer(256), 18, Material.GOLDEN_PICKAXE, freezerRecipes));
        result.register(new SfxElectricMachineDefinition("sf:freezer_2", "Freezer - II", 2, buffer(256), 30, Material.GOLDEN_PICKAXE, freezerRecipes));
        result.register(new SfxElectricMachineDefinition("sf:freezer_3", "Freezer - III", 3, buffer(256), 42, Material.GOLDEN_PICKAXE, freezerRecipes));

        result.register(new SfxElectricMachineDefinition("sf:heated_pressure_chamber", "Heated Pressure Chamber", 1, buffer(1024), 10, Material.FLINT_AND_STEEL, heatedPressureRecipes));
        result.register(new SfxElectricMachineDefinition("sf:heated_pressure_chamber_2", "Heated Pressure Chamber - II", 5, buffer(4096), 44, Material.FLINT_AND_STEEL, heatedPressureRecipes));

        result.register(new SfxElectricMachineDefinition("sf:food_fabricator", "Food Fabricator", 1, buffer(256), 14, Material.GOLDEN_HOE, foodFabricatorRecipes));
        result.register(new SfxElectricMachineDefinition("sf:food_fabricator_2", "Food Fabricator - II", 6, buffer(512), 48, Material.GOLDEN_HOE, foodFabricatorRecipes));
        result.register(new SfxElectricMachineDefinition("sf:food_composter", "Food Composter", 1, buffer(256), 16, Material.GOLDEN_HOE, foodComposterRecipes));
        result.register(new SfxElectricMachineDefinition("sf:food_composter_2", "Food Composter - II", 10, buffer(512), 52, Material.GOLDEN_HOE, foodComposterRecipes));

        result.register(new SfxElectricMachineDefinition("sf:refinery", "Refinery", 1, buffer(256), 32, Material.PISTON, refineryRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electric_ingot_pulverizer", "Electric Ingot Pulverizer", 1, buffer(512), 14, Material.IRON_PICKAXE, ingotPulverizerRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_smeltery", "Electric Smeltery", 1, buffer(512), 20, Material.FLINT_AND_STEEL, electricSmelteryRecipes, SfxElectricMachineDefinition.SIX_INPUT_SLOTS, new int[]{24, 25}));
        result.register(new SfxElectricMachineDefinition("sf:electric_smeltery_2", "Electric Smeltery - II", 3, buffer(512), 40, Material.FLINT_AND_STEEL, electricSmelteryRecipes, SfxElectricMachineDefinition.SIX_INPUT_SLOTS, new int[]{24, 25}));

        result.register(new SfxElectricMachineDefinition("sf:auto_enchanter", "Auto Enchanter", 1, buffer(128), 18, Material.GOLDEN_CHESTPLATE, autoEnchanterRecipes));
        result.register(new SfxElectricMachineDefinition("sf:auto_enchanter_2", "Auto Enchanter - II", 3, buffer(1028), 48, Material.GOLDEN_CHESTPLATE, autoEnchanterRecipes));
        result.register(new SfxElectricMachineDefinition("sf:auto_disenchanter", "Auto Disenchanter", 1, buffer(128), 18, Material.DIAMOND_CHESTPLATE, autoDisenchanterRecipes));
        result.register(new SfxElectricMachineDefinition("sf:auto_disenchanter_2", "Auto Disenchanter - II", 3, buffer(1028), 48, Material.DIAMOND_CHESTPLATE, autoDisenchanterRecipes));
        result.register(new SfxElectricMachineDefinition("sf:book_binder", "Book Binder", 1, buffer(128), 16, Material.IRON_CHESTPLATE, bookBinderRecipes));
        result.register(new SfxElectricMachineDefinition("sf:auto_anvil", "Auto Anvil", 1, buffer(128), 24, Material.IRON_PICKAXE, autoAnvilRecipes));
        result.register(new SfxElectricMachineDefinition("sf:auto_anvil_2", "Auto Anvil - II", 1, buffer(128), 32, Material.IRON_PICKAXE, autoAnvil2Recipes));

        result.register(new SfxElectricMachineDefinition("sf:produce_collector", "Produce Collector", 1, buffer(512), 32, Material.BUCKET, produceCollectorRecipes));
        result.register(new SfxElectricMachineDefinition("sf:auto_breeder", "Auto Breeder", 1, buffer(1024), 60, Material.WHEAT, autoBreederRecipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS, SfxElectricMachineMenuStyle.SIMPLE_IO));
        result.register(new SfxElectricMachineDefinition("sf:animal_growth_accelerator", "Animal Growth Accelerator", 1, buffer(1024), 14, Material.WHEAT, animalGrowthRecipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS, SfxElectricMachineMenuStyle.SIMPLE_IO));
        result.register(new SfxElectricMachineDefinition("sf:crop_growth_accelerator", "Crop Growth Accelerator", 1, cropGrowthCapacity, cropGrowthEnergy, Material.BONE_MEAL, cropGrowthRecipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS, SfxElectricMachineMenuStyle.SIMPLE_IO));
        result.register(new SfxElectricMachineDefinition("sf:crop_growth_accelerator_2", "Crop Growth Accelerator - II", 1, cropGrowthCapacity, cropGrowth2Energy, Material.BONE_MEAL, cropGrowth2Recipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS, SfxElectricMachineMenuStyle.SIMPLE_IO));
        result.register(new SfxElectricMachineDefinition("sf:tree_growth_accelerator", "Tree Growth Accelerator", 1, treeGrowthCapacity, treeGrowthEnergy, Material.OAK_SAPLING, treeGrowthRecipes, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineDefinition.NO_OUTPUT_SLOTS, SfxElectricMachineMenuStyle.SIMPLE_IO));
        result.register(new SfxElectricMachineDefinition("sf:xp_collector", "EXP Collector", 1, buffer(1024), 20, Material.EXPERIENCE_BOTTLE, expCollectorRecipes, SfxElectricMachineDefinition.NO_INPUT_SLOTS, SfxElectricMachineDefinition.SIMPLE_IO_SLOTS, SfxElectricMachineMenuStyle.SIMPLE_IO));

        result.register(new SfxElectricMachineDefinition("sf:electric_gold_pan", "Electric Gold Pan", 1, buffer(128), 2, Material.DIAMOND_SHOVEL, goldPanRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_gold_pan_2", "Electric Gold Pan - II", 3, buffer(256), 4, Material.DIAMOND_SHOVEL, goldPanRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_gold_pan_3", "Electric Gold Pan - III", 10, buffer(512), 14, Material.DIAMOND_SHOVEL, goldPanRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electric_dust_washer", "Electric Dust Washer", 1, buffer(256), 6, Material.GOLDEN_SHOVEL, dustWasherRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_dust_washer_2", "Electric Dust Washer - II", 2, buffer(512), 10, Material.GOLDEN_SHOVEL, dustWasherRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_dust_washer_3", "Electric Dust Washer - III", 10, buffer(1024), 30, Material.GOLDEN_SHOVEL, dustWasherRecipes));
        return result;
    }

    private static int buffer(int classicBuffer) {
        return Math.max(0, classicBuffer * 20);
    }

    private static int adjustedEnergy(int classicEnergy, double multiplier) {
        return Math.max(1, (int) Math.round(classicEnergy * multiplier));
    }
}
