package cc.theends6.sfx.addons.basic.electric;

import cc.theends6.sfx.api.machine.runtime.SfxElectricRecipe;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public final class SfxIngotPulverizerBalanceSmokeCli {
    private static final List<String> CARAT_GOLD_IDS = List.of(
            "sf:gold_4k", "sf:gold_6k", "sf:gold_8k", "sf:gold_10k", "sf:gold_12k",
            "sf:gold_14k", "sf:gold_16k", "sf:gold_18k", "sf:gold_20k", "sf:gold_22k", "sf:gold_24k");

    private SfxIngotPulverizerBalanceSmokeCli() {
    }

    public static void main(String[] args) {
        List<SfxElectricRecipe> recipes = new SfxBalancedIngotPulverizerRecipeProvider().recipes();
        require(recipes.size() == 20, "expected 9 normal ingots and 11 carat-gold recipes");
        Set<String> inputs = new HashSet<>();
        for (SfxElectricRecipe recipe : recipes) {
            String input = recipe.input().isSfxItem()
                    ? recipe.input().sfxItemId()
                    : recipe.input().material().name();
            require(inputs.add(input), "duplicate pulverizer input " + input);
        }
        for (int tier = 0; tier < CARAT_GOLD_IDS.size(); tier++) {
            String input = CARAT_GOLD_IDS.get(tier);
            SfxElectricRecipe recipe = recipes.stream()
                    .filter(candidate -> candidate.input().isSfxItem()
                            && input.equals(candidate.input().sfxItemId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("missing carat-gold input " + input));
            require(recipe.output().isSfxItem() && "sf:gold_dust".equals(recipe.output().itemId()),
                    input + " must output gold dust");
            require(recipe.output().amount() == tier + 1,
                    input + " must fully refund " + (tier + 1) + " gold dust");
            require(recipe.baseTicks() == tier + 2,
                    input + " must take " + (tier + 2) + " seconds");
        }
        recipes.stream()
                .filter(recipe -> !recipe.input().isSfxItem() || !CARAT_GOLD_IDS.contains(recipe.input().sfxItemId()))
                .forEach(recipe -> require(recipe.baseTicks() == 2,
                        recipe.key() + " must take two seconds"));
        System.out.println("Validated Basic Expansion ingot pulverizer: 20 unique recipes, lossless K-gold recovery.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
