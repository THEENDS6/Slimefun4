package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxVanillaFurnaceRecipeProvider implements SfxElectricRecipeProvider {
    private final List<SfxElectricRecipe> recipes;

    public SfxVanillaFurnaceRecipeProvider(JavaPlugin plugin, int baseTicks) {
        Map<Material, SfxElectricRecipe> collected = new LinkedHashMap<>();
        Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof FurnaceRecipe furnaceRecipe)) {
                continue;
            }
            ItemStack result = furnaceRecipe.getResult();
            if (result.getType().isAir()) {
                continue;
            }
            for (Material input : resolveChoices(furnaceRecipe)) {
                collected.putIfAbsent(input, new SfxElectricRecipe(
                        furnaceKey(furnaceRecipe, input),
                        SfxRecipeSlot.vanilla(input),
                        SfxElectricStack.vanilla(result.getType(), result.getAmount()),
                        baseTicks));
            }
        }
        this.recipes = List.copyOf(collected.values());
    }

    @Override
    public List<SfxElectricRecipe> recipes() {
        return recipes;
    }

    private List<Material> resolveChoices(FurnaceRecipe recipe) {
        RecipeChoice choice = recipe.getInputChoice();
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            return materialChoice.getChoices();
        }
        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            return exactChoice.getChoices().stream().map(ItemStack::getType).distinct().toList();
        }
        ItemStack legacy = recipe.getInput();
        return legacy == null || legacy.getType().isAir() ? List.of() : List.of(legacy.getType());
    }

    private String furnaceKey(FurnaceRecipe recipe, Material input) {
        NamespacedKey key = recipe.getKey();
        String raw = key == null ? "minecraft:furnace" : key.asString();
        return raw + ":" + input.key();
    }
}
