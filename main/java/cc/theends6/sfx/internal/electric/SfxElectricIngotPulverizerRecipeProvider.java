package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

final class SfxElectricIngotPulverizerRecipeProvider implements SfxElectricRecipeProvider {
    private final List<SfxElectricRecipe> recipes;

    SfxElectricIngotPulverizerRecipeProvider() {
        List<SfxElectricRecipe> result = new ArrayList<>();
        addVanillaToSfx(result, "iron", Material.IRON_INGOT, "sf:iron_dust");
        addVanillaToSfx(result, "gold", Material.GOLD_INGOT, "sf:gold_dust");
        Material copper = Material.matchMaterial("COPPER_INGOT");
        if (copper != null) {
            addVanillaToSfx(result, "copper_vanilla", copper, "sf:copper_dust");
        }
        addSfxToSfx(result, "tin", "sf:tin_ingot", "sf:tin_dust");
        addSfxToSfx(result, "copper", "sf:copper_ingot", "sf:copper_dust");
        addSfxToSfx(result, "silver", "sf:silver_ingot", "sf:silver_dust");
        addSfxToSfx(result, "aluminum", "sf:aluminum_ingot", "sf:aluminum_dust");
        addSfxToSfx(result, "lead", "sf:lead_ingot", "sf:lead_dust");
        addSfxToSfx(result, "zinc", "sf:zinc_ingot", "sf:zinc_dust");
        addSfxToSfx(result, "magnesium", "sf:magnesium_ingot", "sf:magnesium_dust");
        recipes = List.copyOf(result);
    }

    private static void addVanillaToSfx(List<SfxElectricRecipe> recipes, String key, Material input, String output) {
        recipes.add(new SfxElectricRecipe("sf:electric_ingot_pulverizer:" + key, SfxRecipeSlot.vanilla(input), SfxElectricStack.sfx(output, 1), 3));
    }

    private static void addSfxToSfx(List<SfxElectricRecipe> recipes, String key, String input, String output) {
        recipes.add(new SfxElectricRecipe("sf:electric_ingot_pulverizer:" + key, SfxRecipeSlot.sfx(input), SfxElectricStack.sfx(output, 1), 3));
    }

    @Override
    public List<SfxElectricRecipe> recipes() {
        return recipes;
    }
}
