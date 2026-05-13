package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxElectricMachineDefinitions {
    private SfxElectricMachineDefinitions() {
    }

    static SfxElectricMachineRegistry create(JavaPlugin plugin, DefaultManualMachineRegistry manualMachines) {
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

        result.register(new SfxElectricMachineDefinition("sf:electric_furnace", "Electric Furnace", 1, 1280, 4, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace_2", "Electric Furnace - II", 2, 2560, 6, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace_3", "Electric Furnace - III", 4, 5120, 10, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder", "Electric Ore Grinder", 1, 2560, 12, Material.IRON_PICKAXE, grinderRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder_2", "Electric Ore Grinder - II", 4, 10240, 30, Material.IRON_PICKAXE, grinderRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder_3", "Electric Ore Grinder - III", 10, 20480, 90, Material.IRON_PICKAXE, grinderRecipes));

        result.register(new SfxElectricMachineDefinition("sf:auto_drier", "Auto Drier", 1, 1280, 10, Material.FLINT_AND_STEEL, autoDrierRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electric_ingot_factory", "Electric Ingot Factory", 1, 512, 8, Material.FLINT_AND_STEEL, ingotFactoryRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ingot_factory_2", "Electric Ingot Factory - II", 2, 1024, 14, Material.FLINT_AND_STEEL, ingotFactoryRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ingot_factory_3", "Electric Ingot Factory - III", 8, 4096, 40, Material.FLINT_AND_STEEL, ingotFactoryRecipes));

        result.register(new SfxElectricMachineDefinition("sf:carbon_press", "Carbon Press", 1, 1280, 20, Material.DIAMOND_PICKAXE, carbonPressRecipes));
        result.register(new SfxElectricMachineDefinition("sf:carbon_press_2", "Carbon Press - II", 3, 4096, 50, Material.DIAMOND_PICKAXE, carbonPressRecipes));
        result.register(new SfxElectricMachineDefinition("sf:carbon_press_3", "Carbon Press - III", 15, 16384, 180, Material.DIAMOND_PICKAXE, carbonPressRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electric_press", "Electric Press", 1, 1280, 16, Material.IRON_HOE, electricPressRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_press_2", "Electric Press - II", 3, 2560, 40, Material.IRON_HOE, electricPressRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electrified_crucible", "Electrified Crucible", 1, 1024, 48, Material.FLINT_AND_STEEL, crucibleRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electrified_crucible_2", "Electrified Crucible - II", 2, 2048, 80, Material.FLINT_AND_STEEL, crucibleRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electrified_crucible_3", "Electrified Crucible - III", 4, 4096, 120, Material.FLINT_AND_STEEL, crucibleRecipes));

        result.register(new SfxElectricMachineDefinition("sf:freezer", "Freezer", 1, 256, 18, Material.GOLDEN_PICKAXE, freezerRecipes));
        result.register(new SfxElectricMachineDefinition("sf:freezer_2", "Freezer - II", 2, 256, 30, Material.GOLDEN_PICKAXE, freezerRecipes));
        result.register(new SfxElectricMachineDefinition("sf:freezer_3", "Freezer - III", 3, 256, 42, Material.GOLDEN_PICKAXE, freezerRecipes));

        result.register(new SfxElectricMachineDefinition("sf:heated_pressure_chamber", "Heated Pressure Chamber", 1, 1024, 10, Material.FLINT_AND_STEEL, heatedPressureRecipes));
        result.register(new SfxElectricMachineDefinition("sf:heated_pressure_chamber_2", "Heated Pressure Chamber - II", 5, 4096, 44, Material.FLINT_AND_STEEL, heatedPressureRecipes));

        result.register(new SfxElectricMachineDefinition("sf:food_fabricator", "Food Fabricator", 1, 256, 14, Material.GOLDEN_HOE, foodFabricatorRecipes));
        result.register(new SfxElectricMachineDefinition("sf:food_fabricator_2", "Food Fabricator - II", 6, 512, 48, Material.GOLDEN_HOE, foodFabricatorRecipes));
        result.register(new SfxElectricMachineDefinition("sf:food_composter", "Food Composter", 1, 256, 16, Material.GOLDEN_HOE, foodComposterRecipes));
        result.register(new SfxElectricMachineDefinition("sf:food_composter_2", "Food Composter - II", 10, 512, 52, Material.GOLDEN_HOE, foodComposterRecipes));

        result.register(new SfxElectricMachineDefinition("sf:refinery", "Refinery", 1, 256, 32, Material.PISTON, refineryRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electric_gold_pan", "Electric Gold Pan", 1, 128, 2, Material.DIAMOND_SHOVEL, goldPanRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_gold_pan_2", "Electric Gold Pan - II", 3, 256, 4, Material.DIAMOND_SHOVEL, goldPanRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_gold_pan_3", "Electric Gold Pan - III", 10, 512, 14, Material.DIAMOND_SHOVEL, goldPanRecipes));

        result.register(new SfxElectricMachineDefinition("sf:electric_dust_washer", "Electric Dust Washer", 1, 256, 6, Material.GOLDEN_SHOVEL, dustWasherRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_dust_washer_2", "Electric Dust Washer - II", 2, 512, 10, Material.GOLDEN_SHOVEL, dustWasherRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_dust_washer_3", "Electric Dust Washer - III", 10, 1024, 30, Material.GOLDEN_SHOVEL, dustWasherRecipes));
        return result;
    }
}
