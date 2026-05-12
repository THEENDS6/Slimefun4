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
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace", "Electric Furnace", 1, 1280, 4, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace_2", "Electric Furnace - II", 2, 2560, 6, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_furnace_3", "Electric Furnace - III", 4, 5120, 10, Material.FLINT_AND_STEEL, furnaceRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder", "Electric Ore Grinder", 1, 2560, 12, Material.IRON_PICKAXE, grinderRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder_2", "Electric Ore Grinder - II", 4, 10240, 30, Material.IRON_PICKAXE, grinderRecipes));
        result.register(new SfxElectricMachineDefinition("sf:electric_ore_grinder_3", "Electric Ore Grinder - III", 10, 20480, 90, Material.IRON_PICKAXE, grinderRecipes));
        return result;
    }
}
