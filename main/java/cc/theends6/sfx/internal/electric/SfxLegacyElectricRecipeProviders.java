package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Tag;

final class SfxLegacyElectricRecipeProviders {
    private SfxLegacyElectricRecipeProviders() {
    }

    static SfxElectricRecipeProvider autoDrier() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        addVanilla(recipes, "auto_drier:rotten_flesh", Material.ROTTEN_FLESH, 1, Material.LEATHER, 1, 6);
        addVanilla(recipes, "auto_drier:wet_sponge", Material.WET_SPONGE, 1, Material.SPONGE, 1, 6);
        addVanilla(recipes, "auto_drier:kelp", Material.KELP, 1, Material.DRIED_KELP, 1, 6);
        addVanilla(recipes, "auto_drier:potion", Material.POTION, 1, Material.GLASS_BOTTLE, 1, 6);
        addVanilla(recipes, "auto_drier:splash_potion", Material.SPLASH_POTION, 1, Material.GLASS_BOTTLE, 1, 6);
        addVanilla(recipes, "auto_drier:lingering_potion", Material.LINGERING_POTION, 1, Material.GLASS_BOTTLE, 1, 6);
        addVanilla(recipes, "auto_drier:water_bucket", Material.WATER_BUCKET, 1, Material.BUCKET, 1, 6);
        addSfx(recipes, "auto_drier:beef_jerky", Material.COOKED_BEEF, 1, "sf:beef_jerky", 1, 6);
        addSfx(recipes, "auto_drier:pork_jerky", Material.COOKED_PORKCHOP, 1, "sf:pork_jerky", 1, 6);
        addSfx(recipes, "auto_drier:chicken_jerky", Material.COOKED_CHICKEN, 1, "sf:chicken_jerky", 1, 6);
        addSfx(recipes, "auto_drier:mutton_jerky", Material.COOKED_MUTTON, 1, "sf:mutton_jerky", 1, 6);
        addSfx(recipes, "auto_drier:rabbit_jerky", Material.COOKED_RABBIT, 1, "sf:rabbit_jerky", 1, 6);
        addSfx(recipes, "auto_drier:cod_jerky", Material.COOKED_COD, 1, "sf:fish_jerky", 1, 6);
        addSfx(recipes, "auto_drier:salmon_jerky", Material.COOKED_SALMON, 1, "sf:fish_jerky", 1, 6);
        addVanillaIfPresent(recipes, "auto_drier:mud", "MUD", 1, Material.CLAY, 1, 6);
        for (Material sapling : Tag.SAPLINGS.getValues()) {
            addVanilla(recipes, "auto_drier:sapling:" + sapling.key(), sapling, 1, Material.STICK, 2, 6);
        }
        for (Material leaves : Tag.LEAVES.getValues()) {
            addVanilla(recipes, "auto_drier:leaves:" + leaves.key(), leaves, 1, Material.STICK, 1, 6);
        }
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider electricIngotFactory() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        addVanillaFromSfx(recipes, "electric_ingot_factory:iron", "sf:iron_dust", 1, Material.IRON_INGOT, 1, 4);
        addSfxFromSfx(recipes, "electric_ingot_factory:gold", "sf:gold_dust", 1, "sf:gold_4k", 1, 4);
        addSfxFromSfx(recipes, "electric_ingot_factory:tin", "sf:tin_dust", 1, "sf:tin_ingot", 1, 4);
        addSfxFromSfx(recipes, "electric_ingot_factory:copper", "sf:copper_dust", 1, "sf:copper_ingot", 1, 4);
        addSfxFromSfx(recipes, "electric_ingot_factory:silver", "sf:silver_dust", 1, "sf:silver_ingot", 1, 4);
        addSfxFromSfx(recipes, "electric_ingot_factory:aluminum", "sf:aluminum_dust", 1, "sf:aluminum_ingot", 1, 4);
        addSfxFromSfx(recipes, "electric_ingot_factory:lead", "sf:lead_dust", 1, "sf:lead_ingot", 1, 4);
        addSfxFromSfx(recipes, "electric_ingot_factory:zinc", "sf:zinc_dust", 1, "sf:zinc_ingot", 1, 4);
        addSfx(recipes, "electric_ingot_factory:silicon", Material.QUARTZ_BLOCK, 1, "sf:silicon", 1, 4);
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider carbonPress() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        addVanilla(recipes, "carbon_press:charcoal", Material.CHARCOAL, 4, Material.COAL, 1, 15);
        addSfx(recipes, "carbon_press:coal", Material.COAL, 8, "sf:carbon", 1, 20);
        addSfx(recipes, "carbon_press:coal_block", Material.COAL_BLOCK, 8, "sf:carbon", 9, 180);
        addSfxFromSfx(recipes, "carbon_press:compressed_carbon", "sf:carbon", 4, "sf:compressed_carbon", 1, 30);
        recipes.add(SfxElectricRecipe.fixedOutputs(
                "sf:carbon_press:raw_carbonado",
                List.of(SfxRecipeSlot.sfx("sf:carbon_chunk"), SfxRecipeSlot.sfx("sf:synthetic_diamond")),
                List.of(SfxElectricStack.sfx("sf:raw_carbonado", 1)),
                60));
        addSfxFromSfx(recipes, "carbon_press:synthetic_diamond", "sf:carbon_chunk", 1, "sf:synthetic_diamond", 1, 60);
        addSfxFromSfx(recipes, "carbon_press:carbonado", "sf:raw_carbonado", 1, "sf:carbonado", 1, 90);
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider electricPress() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        addVanillaFromSfx(recipes, "electric_press:stone_chunk", "sf:stone_chunk", 3, Material.COBBLESTONE, 1, 4);
        addVanilla(recipes, "electric_press:flint", Material.FLINT, 6, Material.COBBLESTONE, 1, 4);
        addVanilla(recipes, "electric_press:glass", Material.GLASS, 1, Material.GLASS_PANE, 3, 5);
        addVanilla(recipes, "electric_press:snowball", Material.SNOWBALL, 4, Material.SNOW_BLOCK, 1, 4);
        addVanilla(recipes, "electric_press:magma_cream", Material.MAGMA_CREAM, 4, Material.MAGMA_BLOCK, 1, 4);
        addVanilla(recipes, "electric_press:slime_ball", Material.SLIME_BALL, 9, Material.SLIME_BLOCK, 1, 4);
        addVanilla(recipes, "electric_press:dried_kelp", Material.DRIED_KELP, 9, Material.DRIED_KELP_BLOCK, 1, 3);
        addVanilla(recipes, "electric_press:bone_meal", Material.BONE_MEAL, 9, Material.BONE_BLOCK, 1, 3);
        addVanilla(recipes, "electric_press:clay_ball", Material.CLAY_BALL, 4, Material.CLAY, 1, 3);
        addVanilla(recipes, "electric_press:brick", Material.BRICK, 4, Material.BRICKS, 1, 3);
        addSfxFromSfx(recipes, "electric_press:copper_wire", "sf:copper_ingot", 1, "sf:copper_wire", 3, 6);
        addSfxFromSfx(recipes, "electric_press:steel_plate", "sf:steel_ingot", 8, "sf:steel_plate", 1, 16);
        addSfxFromSfx(recipes, "electric_press:reinforced_plate", "sf:reinforced_alloy_ingot", 8, "sf:reinforced_plate", 1, 18);
        addSfx(recipes, "electric_press:magic_lump_1", Material.NETHER_WART, 1, "sf:magic_lump_1", 2, 8);
        addSfxFromSfx(recipes, "electric_press:magic_lump_2", "sf:magic_lump_1", 4, "sf:magic_lump_2", 1, 10);
        addSfxFromSfx(recipes, "electric_press:magic_lump_3", "sf:magic_lump_2", 4, "sf:magic_lump_3", 1, 12);
        addSfx(recipes, "electric_press:ender_lump_1", Material.ENDER_EYE, 1, "sf:ender_lump_1", 2, 10);
        addSfxFromSfx(recipes, "electric_press:ender_lump_2", "sf:ender_lump_1", 4, "sf:ender_lump_2", 1, 12);
        addSfxFromSfx(recipes, "electric_press:ender_lump_3", "sf:ender_lump_2", 4, "sf:ender_lump_3", 1, 14);
        addSfxFromSfx(recipes, "electric_press:small_uranium", "sf:tiny_uranium", 9, "sf:small_uranium", 1, 18);
        addSfxFromSfx(recipes, "electric_press:uranium", "sf:small_uranium", 4, "sf:uranium", 1, 24);
        addVanilla(recipes, "electric_press:quartz", Material.QUARTZ, 4, Material.QUARTZ_BLOCK, 1, 4);
        addVanilla(recipes, "electric_press:iron_nugget", Material.IRON_NUGGET, 9, Material.IRON_INGOT, 1, 4);
        addVanilla(recipes, "electric_press:gold_nugget", Material.GOLD_NUGGET, 9, Material.GOLD_INGOT, 1, 4);
        addVanilla(recipes, "electric_press:coal", Material.COAL, 9, Material.COAL_BLOCK, 1, 4);
        addVanilla(recipes, "electric_press:sand", Material.SAND, 4, Material.SANDSTONE, 1, 4);
        addVanilla(recipes, "electric_press:red_sand", Material.RED_SAND, 4, Material.RED_SANDSTONE, 1, 4);
        addVanilla(recipes, "electric_press:iron_ingot", Material.IRON_INGOT, 9, Material.IRON_BLOCK, 1, 5);
        addVanilla(recipes, "electric_press:gold_ingot", Material.GOLD_INGOT, 9, Material.GOLD_BLOCK, 1, 5);
        addVanilla(recipes, "electric_press:redstone", Material.REDSTONE, 9, Material.REDSTONE_BLOCK, 1, 6);
        addVanilla(recipes, "electric_press:lapis", Material.LAPIS_LAZULI, 9, Material.LAPIS_BLOCK, 1, 6);
        addVanilla(recipes, "electric_press:emerald", Material.EMERALD, 9, Material.EMERALD_BLOCK, 1, 8);
        addVanilla(recipes, "electric_press:diamond", Material.DIAMOND, 9, Material.DIAMOND_BLOCK, 1, 8);
        addVanilla(recipes, "electric_press:netherite", Material.NETHERITE_INGOT, 9, Material.NETHERITE_BLOCK, 1, 16);
        addVanillaIfPresent(recipes, "electric_press:amethyst", "AMETHYST_SHARD", 4, Material.AMETHYST_BLOCK, 1, 4);
        addVanillaIfPresent(recipes, "electric_press:copper", "COPPER_INGOT", 9, Material.COPPER_BLOCK, 1, 5);
        addVanillaIfPresent(recipes, "electric_press:raw_iron", "RAW_IRON", 9, Material.RAW_IRON_BLOCK, 1, 5);
        addVanillaIfPresent(recipes, "electric_press:raw_gold", "RAW_GOLD", 9, Material.RAW_GOLD_BLOCK, 1, 5);
        addVanillaIfPresent(recipes, "electric_press:raw_copper", "RAW_COPPER", 9, Material.RAW_COPPER_BLOCK, 1, 5);
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider electrifiedCrucible() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        addVanillaOutputFromTwoVanilla(recipes, "crucible:cobblestone", Material.BUCKET, 1, Material.COBBLESTONE, 16, Material.LAVA_BUCKET, 1, 10);
        addVanillaOutputFromTwoVanilla(recipes, "crucible:netherrack", Material.BUCKET, 1, Material.NETHERRACK, 16, Material.LAVA_BUCKET, 1, 8);
        addVanillaOutputFromTwoVanilla(recipes, "crucible:stone", Material.BUCKET, 1, Material.STONE, 12, Material.LAVA_BUCKET, 1, 8);
        addVanillaOutputFromTwoVanilla(recipes, "crucible:terracotta", Material.BUCKET, 1, Material.TERRACOTTA, 12, Material.LAVA_BUCKET, 1, 8);
        addVanillaOutputFromTwoVanilla(recipes, "crucible:obsidian", Material.BUCKET, 1, Material.OBSIDIAN, 1, Material.LAVA_BUCKET, 1, 10);
        for (Material terracotta : coloredTerracotta()) {
            addVanillaOutputFromTwoVanilla(recipes, "crucible:terracotta:" + terracotta.key(), Material.BUCKET, 1, terracotta, 12, Material.LAVA_BUCKET, 1, 8);
        }
        for (Material leaves : Tag.LEAVES.getValues()) {
            addVanillaOutputFromTwoVanilla(recipes, "crucible:leaves:" + leaves.key(), Material.BUCKET, 1, leaves, 16, Material.WATER_BUCKET, 1, 10);
        }
        addVanillaOutputFromTwoVanilla(recipes, "crucible:blackstone", Material.BUCKET, 1, Material.BLACKSTONE, 8, Material.LAVA_BUCKET, 1, 10);
        addVanillaOutputFromTwoVanilla(recipes, "crucible:basalt", Material.BUCKET, 1, Material.BASALT, 12, Material.LAVA_BUCKET, 1, 10);
        addTwoVanillaIfPresent(recipes, "crucible:cobbled_deepslate", Material.BUCKET, 1, "COBBLED_DEEPSLATE", 12, Material.LAVA_BUCKET, 1, 10);
        addTwoVanillaIfPresent(recipes, "crucible:deepslate", Material.BUCKET, 1, "DEEPSLATE", 10, Material.LAVA_BUCKET, 1, 10);
        addTwoVanillaIfPresent(recipes, "crucible:tuff", Material.BUCKET, 1, "TUFF", 8, Material.LAVA_BUCKET, 1, 10);
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider freezer() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        addVanilla(recipes, "freezer:ice", Material.ICE, 1, Material.PACKED_ICE, 1, 4);
        addVanilla(recipes, "freezer:packed_ice", Material.PACKED_ICE, 1, Material.BLUE_ICE, 1, 6);
        recipes.add(SfxElectricRecipe.fixedOutputs(
                "sf:freezer:water_bucket",
                List.of(SfxRecipeSlot.vanilla(Material.WATER_BUCKET)),
                List.of(SfxElectricStack.vanilla(Material.BUCKET, 1), SfxElectricStack.vanilla(Material.ICE, 1)),
                2));
        recipes.add(SfxElectricRecipe.fixedOutputs(
                "sf:freezer:lava_bucket",
                List.of(SfxRecipeSlot.vanilla(Material.LAVA_BUCKET)),
                List.of(SfxElectricStack.vanilla(Material.BUCKET, 1), SfxElectricStack.vanilla(Material.OBSIDIAN, 1)),
                8));
        addSfx(recipes, "freezer:reactor_coolant_cell", Material.BLUE_ICE, 1, "sf:reactor_coolant_cell", 1, 8);
        addVanilla(recipes, "freezer:snow_block", Material.SNOW_BLOCK, 2, Material.ICE, 1, 6);
        addVanilla(recipes, "freezer:magma_cream", Material.MAGMA_CREAM, 1, Material.SLIME_BALL, 1, 6);
        addVanilla(recipes, "freezer:magma_block", Material.MAGMA_BLOCK, 2, Material.SLIME_BLOCK, 1, 6);
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider heatedPressureChamber() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        recipes.add(SfxElectricRecipe.fixedOutputs(
                "sf:heated_pressure_chamber:plastic_sheet",
                List.of(SfxRecipeSlot.sfx("sf:bucket_of_oil")),
                List.of(SfxElectricStack.vanilla(Material.BUCKET, 1), SfxElectricStack.sfx("sf:plastic_sheet", 8)),
                45));
        recipes.add(SfxElectricRecipe.fixedOutputs("sf:heated_pressure_chamber:blistering_ingot", List.of(SfxRecipeSlot.sfx("sf:gold_24k"), SfxRecipeSlot.sfx("sf:uranium")), List.of(SfxElectricStack.sfx("sf:blistering_ingot", 1)), 30));
        recipes.add(SfxElectricRecipe.fixedOutputs("sf:heated_pressure_chamber:blistering_ingot_2", List.of(SfxRecipeSlot.sfx("sf:blistering_ingot"), SfxRecipeSlot.sfx("sf:carbonado")), List.of(SfxElectricStack.sfx("sf:blistering_ingot_2", 1)), 30));
        recipes.add(SfxElectricRecipe.fixedOutputs("sf:heated_pressure_chamber:blistering_ingot_3", List.of(SfxRecipeSlot.sfx("sf:blistering_ingot_2"), SfxRecipeSlot.vanilla(Material.NETHER_STAR)), List.of(SfxElectricStack.sfx("sf:blistering_ingot_3", 1)), 60));
        recipes.add(SfxElectricRecipe.fixedOutputs("sf:heated_pressure_chamber:boosted_uranium", List.of(SfxRecipeSlot.sfx("sf:plutonium"), SfxRecipeSlot.sfx("sf:uranium")), List.of(SfxElectricStack.sfx("sf:boosted_uranium", 1)), 90));
        recipes.add(SfxElectricRecipe.fixedOutputs("sf:heated_pressure_chamber:enriched_nether_ice", List.of(SfxRecipeSlot.sfx("sf:nether_ice"), SfxRecipeSlot.sfx("sf:plutonium")), List.of(SfxElectricStack.sfx("sf:enriched_nether_ice", 4)), 60));
        addSfxFromSfx(recipes, "heated_pressure_chamber:nether_ice_coolant_cell", "sf:enriched_nether_ice", 1, "sf:nether_ice_coolant_cell", 8, 45);
        recipes.add(SfxElectricRecipe.fixedOutputs("sf:heated_pressure_chamber:magnesium_salt", List.of(SfxRecipeSlot.sfx("sf:magnesium_dust"), SfxRecipeSlot.sfx("sf:salt")), List.of(SfxElectricStack.sfx("sf:magnesium_salt", 1)), 8));
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider foodFabricator() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        addFabricator(recipes, Material.WHEAT, "sf:organic_food_wheat");
        addFabricator(recipes, Material.CARROT, "sf:organic_food_carrot");
        addFabricator(recipes, Material.POTATO, "sf:organic_food_potato");
        addFabricator(recipes, Material.WHEAT_SEEDS, "sf:organic_food_seeds");
        addFabricator(recipes, Material.BEETROOT, "sf:organic_food_beetroot");
        addFabricator(recipes, Material.MELON_SLICE, "sf:organic_food_melon");
        addFabricator(recipes, Material.APPLE, "sf:organic_food_apple");
        addFabricator(recipes, Material.DRIED_KELP, "sf:organic_food_kelp");
        addFabricator(recipes, Material.COCOA_BEANS, "sf:organic_food_cocoa");
        addFabricator(recipes, Material.SWEET_BERRIES, "sf:organic_food_sweet_berries");
        addFabricator(recipes, Material.SEAGRASS, "sf:organic_food_seagrass");
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider foodComposter() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        addSfxFromSfx(recipes, "food_composter:wheat", "sf:organic_food_wheat", 1, "sf:fertilizer_wheat", 2, 30);
        addSfxFromSfx(recipes, "food_composter:carrot", "sf:organic_food_carrot", 1, "sf:fertilizer_carrot", 2, 30);
        addSfxFromSfx(recipes, "food_composter:potato", "sf:organic_food_potato", 1, "sf:fertilizer_potato", 2, 30);
        addSfxFromSfx(recipes, "food_composter:seeds", "sf:organic_food_seeds", 1, "sf:fertilizer_seeds", 2, 30);
        addSfxFromSfx(recipes, "food_composter:beetroot", "sf:organic_food_beetroot", 1, "sf:fertilizer_beetroot", 2, 30);
        addSfxFromSfx(recipes, "food_composter:melon", "sf:organic_food_melon", 1, "sf:fertilizer_melon", 2, 30);
        addSfxFromSfx(recipes, "food_composter:apple", "sf:organic_food_apple", 1, "sf:fertilizer_apple", 2, 30);
        addSfxFromSfx(recipes, "food_composter:kelp", "sf:organic_food_kelp", 1, "sf:fertilizer_kelp", 2, 30);
        addSfxFromSfx(recipes, "food_composter:cocoa", "sf:organic_food_cocoa", 1, "sf:fertilizer_cocoa", 2, 30);
        addSfxFromSfx(recipes, "food_composter:sweet_berries", "sf:organic_food_sweet_berries", 1, "sf:fertilizer_sweet_berries", 2, 30);
        addSfxFromSfx(recipes, "food_composter:seagrass", "sf:organic_food_seagrass", 1, "sf:fertilizer_seagrass", 2, 30);
        return () -> List.copyOf(recipes);
    }


    static SfxElectricRecipeProvider refinery() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        addSfxFromSfx(recipes, "refinery:fuel", "sf:bucket_of_oil", 1, "sf:bucket_of_fuel", 1, 40);
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider electricGoldPan() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        recipes.add(SfxElectricRecipe.randomOutput("sf:electric_gold_pan:gravel", SfxRecipeSlot.vanilla(Material.GRAVEL), weightedOutputs(List.of(
                weighted(SfxElectricStack.vanilla(Material.FLINT, 1), 40),
                weighted(SfxElectricStack.vanilla(Material.CLAY_BALL, 1), 20),
                weighted(SfxElectricStack.sfx("sf:sifted_ore", 1), 35),
                weighted(SfxElectricStack.vanilla(Material.IRON_NUGGET, 1), 5))), 3));
        List<SfxElectricStack> netherOutputs = weightedOutputs(List.of(
                weighted(SfxElectricStack.vanilla(Material.QUARTZ, 1), 50),
                weighted(SfxElectricStack.vanilla(Material.GOLD_NUGGET, 1), 25),
                weighted(SfxElectricStack.vanilla(Material.NETHER_WART, 1), 10),
                weighted(SfxElectricStack.vanilla(Material.BLAZE_POWDER, 1), 8),
                weighted(SfxElectricStack.vanilla(Material.GLOWSTONE_DUST, 1), 5),
                weighted(SfxElectricStack.vanilla(Material.GHAST_TEAR, 1), 2)));
        recipes.add(SfxElectricRecipe.randomOutput("sf:electric_gold_pan:soul_sand", SfxRecipeSlot.vanilla(Material.SOUL_SAND), netherOutputs, 4));
        recipes.add(SfxElectricRecipe.randomOutput("sf:electric_gold_pan:soul_soil", SfxRecipeSlot.vanilla(Material.SOUL_SOIL), netherOutputs, 4));
        return () -> List.copyOf(recipes);
    }

    static SfxElectricRecipeProvider electricDustWasher() {
        List<SfxElectricRecipe> recipes = new ArrayList<>();
        recipes.add(SfxElectricRecipe.randomOutput("sf:electric_dust_washer:sifted_ore", SfxRecipeSlot.sfx("sf:sifted_ore"), List.of(
                SfxElectricStack.sfx("sf:iron_dust", 1),
                SfxElectricStack.sfx("sf:gold_dust", 1),
                SfxElectricStack.sfx("sf:copper_dust", 1),
                SfxElectricStack.sfx("sf:tin_dust", 1),
                SfxElectricStack.sfx("sf:zinc_dust", 1),
                SfxElectricStack.sfx("sf:aluminum_dust", 1),
                SfxElectricStack.sfx("sf:magnesium_dust", 1),
                SfxElectricStack.sfx("sf:lead_dust", 1),
                SfxElectricStack.sfx("sf:silver_dust", 1)), 4));
        addSfxFromSfx(recipes, "electric_dust_washer:pulverized_ore", "sf:pulverized_ore", 1, "sf:pure_ore_cluster", 1, 4);
        addSfx(recipes, "electric_dust_washer:sand", Material.SAND, 1, "sf:salt", 1, 4);
        return () -> List.copyOf(recipes);
    }

    private static void addFabricator(List<SfxElectricRecipe> recipes, Material food, String outputId) {
        recipes.add(SfxElectricRecipe.fixedOutputs(
                "sf:food_fabricator:" + food.key(),
                List.of(SfxRecipeSlot.sfx("sf:can"), SfxRecipeSlot.vanilla(food)),
                List.of(SfxElectricStack.sfx(outputId, 2)),
                12));
    }

    private static void addVanilla(List<SfxElectricRecipe> recipes, String key, Material input, int inputAmount, Material output, int outputAmount, int baseSeconds) {
        recipes.add(new SfxElectricRecipe("sf:" + key, SfxRecipeSlot.vanilla(input, inputAmount), SfxElectricStack.vanilla(output, outputAmount), baseSeconds));
    }

    private static void addVanillaIfPresent(List<SfxElectricRecipe> recipes, String key, String inputMaterial, int inputAmount, Material output, int outputAmount, int baseSeconds) {
        Material input = Material.matchMaterial(inputMaterial);
        if (input != null) {
            addVanilla(recipes, key, input, inputAmount, output, outputAmount, baseSeconds);
        }
    }

    private static void addSfx(List<SfxElectricRecipe> recipes, String key, Material input, int inputAmount, String outputId, int outputAmount, int baseSeconds) {
        recipes.add(new SfxElectricRecipe("sf:" + key, SfxRecipeSlot.vanilla(input, inputAmount), SfxElectricStack.sfx(outputId, outputAmount), baseSeconds));
    }

    private static void addSfxFromSfx(List<SfxElectricRecipe> recipes, String key, String inputId, int inputAmount, String outputId, int outputAmount, int baseSeconds) {
        recipes.add(new SfxElectricRecipe("sf:" + key, SfxRecipeSlot.sfx(inputId, inputAmount), SfxElectricStack.sfx(outputId, outputAmount), baseSeconds));
    }

    private static void addVanillaFromSfx(List<SfxElectricRecipe> recipes, String key, String inputId, int inputAmount, Material output, int outputAmount, int baseSeconds) {
        recipes.add(new SfxElectricRecipe("sf:" + key, SfxRecipeSlot.sfx(inputId, inputAmount), SfxElectricStack.vanilla(output, outputAmount), baseSeconds));
    }

    private static void addVanillaOutputFromTwoVanilla(List<SfxElectricRecipe> recipes, String key, Material firstInput, int firstAmount, Material secondInput, int secondAmount, Material output, int outputAmount, int baseSeconds) {
        recipes.add(SfxElectricRecipe.fixedOutputs(
                "sf:" + key,
                List.of(SfxRecipeSlot.vanilla(firstInput, firstAmount), SfxRecipeSlot.vanilla(secondInput, secondAmount)),
                List.of(SfxElectricStack.vanilla(output, outputAmount)),
                baseSeconds));
    }

    private static void addTwoVanillaIfPresent(List<SfxElectricRecipe> recipes, String key, Material firstInput, int firstAmount, String secondInputName, int secondAmount, Material output, int outputAmount, int baseSeconds) {
        Material secondInput = Material.matchMaterial(secondInputName);
        if (secondInput != null) {
            addVanillaOutputFromTwoVanilla(recipes, key, firstInput, firstAmount, secondInput, secondAmount, output, outputAmount, baseSeconds);
        }
    }

    private static List<Material> coloredTerracotta() {
        return List.of(
                Material.WHITE_TERRACOTTA,
                Material.ORANGE_TERRACOTTA,
                Material.MAGENTA_TERRACOTTA,
                Material.LIGHT_BLUE_TERRACOTTA,
                Material.YELLOW_TERRACOTTA,
                Material.LIME_TERRACOTTA,
                Material.PINK_TERRACOTTA,
                Material.GRAY_TERRACOTTA,
                Material.LIGHT_GRAY_TERRACOTTA,
                Material.CYAN_TERRACOTTA,
                Material.PURPLE_TERRACOTTA,
                Material.BLUE_TERRACOTTA,
                Material.BROWN_TERRACOTTA,
                Material.GREEN_TERRACOTTA,
                Material.RED_TERRACOTTA,
                Material.BLACK_TERRACOTTA);
    }

    private static WeightedStack weighted(SfxElectricStack stack, int weight) {
        return new WeightedStack(stack, weight);
    }

    private static List<SfxElectricStack> weightedOutputs(List<WeightedStack> entries) {
        List<SfxElectricStack> outputs = new ArrayList<>();
        for (WeightedStack entry : entries) {
            for (int index = 0; index < entry.weight(); index++) {
                outputs.add(entry.stack());
            }
        }
        return outputs;
    }

    private record WeightedStack(SfxElectricStack stack, int weight) {
    }
}
