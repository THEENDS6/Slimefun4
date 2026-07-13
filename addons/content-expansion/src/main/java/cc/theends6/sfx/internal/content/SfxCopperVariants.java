package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.electric.SfxElectricRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class SfxCopperVariants {
    static final String LEGACY_COPPER_INGOT = "sf:copper_ingot";
    static final String EXPOSED_COPPER_INGOT = "sfx:exposed_copper_ingot";
    static final String WEATHERED_COPPER_INGOT = "sfx:weathered_copper_ingot";
    static final String OXIDIZED_COPPER_INGOT = "sfx:oxidized_copper_ingot";
    static final String COPPER_DUST = "sf:copper_dust";
    static final String EXPOSED_COPPER_DUST = "sfx:exposed_copper_dust";
    static final String WEATHERED_COPPER_DUST = "sfx:weathered_copper_dust";
    static final String OXIDIZED_COPPER_DUST = "sfx:oxidized_copper_dust";

    private static final Map<Material, Material> WAXED = new LinkedHashMap<>();
    private static final Map<Material, Material> UNWAXED = new LinkedHashMap<>();
    private static final Map<Material, Material> PREVIOUS_OXIDATION = new LinkedHashMap<>();
    private static final Map<Material, Material> FINAL_OXIDATION = new LinkedHashMap<>();
    private static final Map<Material, Double> COPPER_VALUES = new LinkedHashMap<>();
    private static final Map<Material, Material> STRIPPED = new LinkedHashMap<>();

    static {
        pair(Material.COPPER_BLOCK, Material.WAXED_COPPER_BLOCK, Material.EXPOSED_COPPER, 9.0D, Material.OXIDIZED_COPPER);
        pair(Material.EXPOSED_COPPER, Material.WAXED_EXPOSED_COPPER, Material.WEATHERED_COPPER, 9.0D, Material.OXIDIZED_COPPER);
        pair(Material.WEATHERED_COPPER, Material.WAXED_WEATHERED_COPPER, Material.OXIDIZED_COPPER, 9.0D, Material.OXIDIZED_COPPER);
        pair(Material.OXIDIZED_COPPER, Material.WAXED_OXIDIZED_COPPER, null, 9.0D, Material.OXIDIZED_COPPER);
        previous(Material.EXPOSED_COPPER, Material.COPPER_BLOCK);
        previous(Material.WEATHERED_COPPER, Material.EXPOSED_COPPER);
        previous(Material.OXIDIZED_COPPER, Material.WEATHERED_COPPER);

        copperFamily("CUT_COPPER", "EXPOSED_CUT_COPPER", "WEATHERED_CUT_COPPER", "OXIDIZED_CUT_COPPER", 4.0D);
        copperFamily("CUT_COPPER_STAIRS", "EXPOSED_CUT_COPPER_STAIRS", "WEATHERED_CUT_COPPER_STAIRS", "OXIDIZED_CUT_COPPER_STAIRS", 6.0D);
        copperFamily("CUT_COPPER_SLAB", "EXPOSED_CUT_COPPER_SLAB", "WEATHERED_CUT_COPPER_SLAB", "OXIDIZED_CUT_COPPER_SLAB", 2.0D);
        copperFamily("CHISELED_COPPER", "EXPOSED_CHISELED_COPPER", "WEATHERED_CHISELED_COPPER", "OXIDIZED_CHISELED_COPPER", 4.0D);
        copperFamily("COPPER_GRATE", "EXPOSED_COPPER_GRATE", "WEATHERED_COPPER_GRATE", "OXIDIZED_COPPER_GRATE", 2.25D);
        copperFamily("COPPER_BULB", "EXPOSED_COPPER_BULB", "WEATHERED_COPPER_BULB", "OXIDIZED_COPPER_BULB", 3.0D);
        copperFamily("COPPER_DOOR", "EXPOSED_COPPER_DOOR", "WEATHERED_COPPER_DOOR", "OXIDIZED_COPPER_DOOR", 2.0D);
        copperFamily("COPPER_TRAPDOOR", "EXPOSED_COPPER_TRAPDOOR", "WEATHERED_COPPER_TRAPDOOR", "OXIDIZED_COPPER_TRAPDOOR", 3.0D);

        strippedFamily("OAK");
        strippedFamily("SPRUCE");
        strippedFamily("BIRCH");
        strippedFamily("JUNGLE");
        strippedFamily("ACACIA");
        strippedFamily("DARK_OAK");
        strippedFamily("MANGROVE");
        strippedFamily("CHERRY");
        stripped("CRIMSON_STEM", "STRIPPED_CRIMSON_STEM");
        stripped("CRIMSON_HYPHAE", "STRIPPED_CRIMSON_HYPHAE");
        stripped("WARPED_STEM", "STRIPPED_WARPED_STEM");
        stripped("WARPED_HYPHAE", "STRIPPED_WARPED_HYPHAE");
        stripped("BAMBOO_BLOCK", "STRIPPED_BAMBOO_BLOCK");
    }

    private SfxCopperVariants() {
    }

    static SfxElectricStack waxed(SfxItems items, SfxElectricStack stack) {
        if (stack == null || stack.isSfxItem() || stack.hasSnapshot()) {
            return null;
        }
        Material waxed = WAXED.get(stack.material());
        return waxed == null ? null : SfxElectricStack.vanilla(waxed, stack.amount());
    }

    static SfxElectricStack cutOrScrape(SfxItems items, SfxElectricStack stack) {
        if (stack == null || stack.hasSnapshot()) {
            return null;
        }
        if (stack.isSfxItem()) {
            if (EXPOSED_COPPER_INGOT.equals(stack.itemId())) {
                return SfxElectricStack.vanilla(Material.COPPER_INGOT, stack.amount());
            }
            String previous = previousCopperItem(stack.itemId());
            return previous == null ? null : SfxElectricStack.sfx(previous, stack.amount());
        }
        Material unwaxed = UNWAXED.get(stack.material());
        if (unwaxed != null) {
            return SfxElectricStack.vanilla(unwaxed, stack.amount());
        }
        Material previous = PREVIOUS_OXIDATION.get(stack.material());
        if (previous != null) {
            return SfxElectricStack.vanilla(previous, stack.amount());
        }
        Material stripped = STRIPPED.get(stack.material());
        if (stripped != null) {
            return SfxElectricStack.vanilla(stripped, stack.amount());
        }
        return null;
    }

    static List<SfxElectricRecipe> cuttingRecipes() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        UNWAXED.forEach((input, output) -> recipes.add(vanillaCuttingRecipe("unwax", input, output)));
        PREVIOUS_OXIDATION.forEach((input, output) -> recipes.add(vanillaCuttingRecipe("scrape", input, output)));
        STRIPPED.forEach((input, output) -> recipes.add(vanillaCuttingRecipe("strip", input, output)));
        recipes.add(new SfxElectricRecipe(
                "sfx:cut_exposed_ingot",
                SfxRecipeSlot.sfx(EXPOSED_COPPER_INGOT),
                SfxElectricStack.vanilla(Material.COPPER_INGOT, 1),
                120));
        recipes.add(sfxCuttingRecipe("weathered_ingot", WEATHERED_COPPER_INGOT, EXPOSED_COPPER_INGOT));
        recipes.add(sfxCuttingRecipe("oxidized_ingot", OXIDIZED_COPPER_INGOT, WEATHERED_COPPER_INGOT));
        return List.copyOf(recipes);
    }

    static List<SfxElectricRecipe> waxingRecipes() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        WAXED.forEach((input, output) -> recipes.add(new SfxElectricRecipe(
                "sfx:wax_" + input.name().toLowerCase(java.util.Locale.ROOT),
                SfxRecipeSlot.vanilla(input),
                SfxElectricStack.vanilla(output, 1),
                80)));
        return List.copyOf(recipes);
    }

    static SfxElectricStack oxidizedProduct(SfxItems items, SfxElectricStack stack, boolean finalStage) {
        if (stack == null) {
            return null;
        }
        if (stack.isSfxItem()) {
            String next = finalStage ? finalCopperItem(stack.itemId()) : nextCopperItem(stack.itemId());
            return next == null ? null : SfxElectricStack.sfx(next, stack.amount());
        }
        if (stack.hasSnapshot()) {
            return null;
        }
        if (stack.material() == Material.COPPER_INGOT) {
            return SfxElectricStack.sfx(finalStage ? OXIDIZED_COPPER_INGOT : EXPOSED_COPPER_INGOT, stack.amount());
        }
        Material next = finalStage ? FINAL_OXIDATION.get(stack.material()) : nextOxidation(stack.material());
        return next == null ? null : SfxElectricStack.vanilla(next, stack.amount());
    }

    static double copperValue(SfxElectricStack stack) {
        if (stack == null) {
            return 0.0D;
        }
        if (stack.isSfxItem()) {
            String id = stack.itemId();
            if (LEGACY_COPPER_INGOT.equals(id) || EXPOSED_COPPER_INGOT.equals(id) || WEATHERED_COPPER_INGOT.equals(id) || OXIDIZED_COPPER_INGOT.equals(id)) {
                return stack.amount();
            }
            if (COPPER_DUST.equals(id) || EXPOSED_COPPER_DUST.equals(id) || WEATHERED_COPPER_DUST.equals(id) || OXIDIZED_COPPER_DUST.equals(id)) {
                return stack.amount();
            }
            return 0.0D;
        }
        if (stack.material() == Material.COPPER_INGOT) {
            return stack.amount();
        }
        Double value = COPPER_VALUES.get(stack.material());
        return value == null ? 0.0D : value * stack.amount();
    }

    private static Material nextOxidation(Material material) {
        for (Map.Entry<Material, Material> entry : PREVIOUS_OXIDATION.entrySet()) {
            if (entry.getValue() == material) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String nextCopperItem(String id) {
        return switch (id) {
            case LEGACY_COPPER_INGOT -> EXPOSED_COPPER_INGOT;
            case EXPOSED_COPPER_INGOT -> WEATHERED_COPPER_INGOT;
            case WEATHERED_COPPER_INGOT -> OXIDIZED_COPPER_INGOT;
            case COPPER_DUST -> EXPOSED_COPPER_DUST;
            case EXPOSED_COPPER_DUST -> WEATHERED_COPPER_DUST;
            case WEATHERED_COPPER_DUST -> OXIDIZED_COPPER_DUST;
            default -> null;
        };
    }

    private static String finalCopperItem(String id) {
        return switch (id) {
            case LEGACY_COPPER_INGOT, EXPOSED_COPPER_INGOT, WEATHERED_COPPER_INGOT, OXIDIZED_COPPER_INGOT -> OXIDIZED_COPPER_INGOT;
            case COPPER_DUST, EXPOSED_COPPER_DUST, WEATHERED_COPPER_DUST, OXIDIZED_COPPER_DUST -> OXIDIZED_COPPER_DUST;
            default -> null;
        };
    }

    private static String previousCopperItem(String id) {
        return switch (id) {
            case WEATHERED_COPPER_INGOT -> EXPOSED_COPPER_INGOT;
            case OXIDIZED_COPPER_INGOT -> WEATHERED_COPPER_INGOT;
            default -> null;
        };
    }

    private static SfxElectricRecipe vanillaCuttingRecipe(String operation, Material input, Material output) {
        return new SfxElectricRecipe(
                "sfx:cut_" + operation + "_" + input.name().toLowerCase(java.util.Locale.ROOT),
                SfxRecipeSlot.vanilla(input),
                SfxElectricStack.vanilla(output, 1),
                120);
    }

    private static SfxElectricRecipe sfxCuttingRecipe(String key, String input, String output) {
        return new SfxElectricRecipe("sfx:cut_" + key, SfxRecipeSlot.sfx(input), SfxElectricStack.sfx(output, 1), 120);
    }

    private static void strippedFamily(String wood) {
        stripped(wood + "_LOG", "STRIPPED_" + wood + "_LOG");
        stripped(wood + "_WOOD", "STRIPPED_" + wood + "_WOOD");
    }

    private static void stripped(String input, String output) {
        Material inputMaterial = Material.matchMaterial(input);
        Material outputMaterial = Material.matchMaterial(output);
        if (inputMaterial != null && outputMaterial != null) {
            STRIPPED.put(inputMaterial, outputMaterial);
        }
    }

    private static void copperFamily(String base, String exposed, String weathered, String oxidized, double value) {
        Material b = Material.matchMaterial(base);
        Material e = Material.matchMaterial(exposed);
        Material w = Material.matchMaterial(weathered);
        Material o = Material.matchMaterial(oxidized);
        if (b == null || e == null || w == null || o == null) {
            return;
        }
        pair(b, Material.matchMaterial("WAXED_" + base), e, value, o);
        pair(e, Material.matchMaterial("WAXED_" + exposed), w, value, o);
        pair(w, Material.matchMaterial("WAXED_" + weathered), o, value, o);
        pair(o, Material.matchMaterial("WAXED_" + oxidized), null, value, o);
        previous(e, b);
        previous(w, e);
        previous(o, w);
    }

    private static void pair(Material base, Material waxed, Material next, double copperValue, Material finalStage) {
        if (base == null) {
            return;
        }
        COPPER_VALUES.put(base, copperValue);
        if (finalStage != null) {
            FINAL_OXIDATION.put(base, finalStage);
        }
        if (waxed != null) {
            WAXED.put(base, waxed);
            UNWAXED.put(waxed, base);
            COPPER_VALUES.put(waxed, copperValue);
            if (finalStage != null) {
                FINAL_OXIDATION.put(waxed, finalStage);
            }
        }
    }

    private static void previous(Material current, Material previous) {
        if (current != null && previous != null) {
            PREVIOUS_OXIDATION.put(current, previous);
        }
    }
}
