package cc.theends6.sfx.addons.basic.electric;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.api.machine.runtime.SfxElectricRecipe;
import cc.theends6.sfx.api.machine.runtime.SfxElectricRecipeProvider;
import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;


final class SfxBalancedIngotPulverizerRecipeProvider implements SfxElectricRecipeProvider {
    private static final int NORMAL_SECONDS = 2;
    private static final List<String> CARAT_GOLD_IDS = List.of(
            "sf:gold_4k", "sf:gold_6k", "sf:gold_8k", "sf:gold_10k", "sf:gold_12k",
            "sf:gold_14k", "sf:gold_16k", "sf:gold_18k", "sf:gold_20k", "sf:gold_22k", "sf:gold_24k");

    private final List<SfxElectricRecipe> recipes;

    SfxBalancedIngotPulverizerRecipeProvider() {
        List<SfxElectricRecipe> result = new ArrayList<>();
        addVanilla(result, "iron", Material.IRON_INGOT, "sf:iron_dust");
        addVanilla(result, "gold", Material.GOLD_INGOT, "sf:gold_dust");
        addVanilla(result, "copper", Material.COPPER_INGOT, "sf:copper_dust");
        addSfx(result, "tin", "sf:tin_ingot", "sf:tin_dust");
        addSfx(result, "silver", "sf:silver_ingot", "sf:silver_dust");
        addSfx(result, "aluminum", "sf:aluminum_ingot", "sf:aluminum_dust");
        addSfx(result, "lead", "sf:lead_ingot", "sf:lead_dust");
        addSfx(result, "zinc", "sf:zinc_ingot", "sf:zinc_dust");
        addSfx(result, "magnesium", "sf:magnesium_ingot", "sf:magnesium_dust");
        for (int tier = 0; tier < CARAT_GOLD_IDS.size(); tier++) {
            int dust = tier + 1;
            int seconds = NORMAL_SECONDS + tier;
            result.add(new SfxElectricRecipe(
                    "sfx:electric_ingot_pulverizer:carat_gold:" + (4 + tier * 2) + "k",
                    SfxRecipeSlot.sfx(CARAT_GOLD_IDS.get(tier), 1),
                    SfxElectricStack.sfx("sf:gold_dust", dust),
                    seconds));
        }
        recipes = List.copyOf(result);
    }

    @Override
    public List<SfxElectricRecipe> recipes() {
        return recipes;
    }

    private static void addVanilla(List<SfxElectricRecipe> recipes, String key, Material input, String output) {
        recipes.add(new SfxElectricRecipe(
                "sfx:electric_ingot_pulverizer:" + key,
                SfxRecipeSlot.vanilla(input, 1),
                SfxElectricStack.sfx(output, 1),
                NORMAL_SECONDS));
    }

    private static void addSfx(List<SfxElectricRecipe> recipes, String key, String input, String output) {
        recipes.add(new SfxElectricRecipe(
                "sfx:electric_ingot_pulverizer:" + key,
                SfxRecipeSlot.sfx(input, 1),
                SfxElectricStack.sfx(output, 1),
                NORMAL_SECONDS));
    }
}
