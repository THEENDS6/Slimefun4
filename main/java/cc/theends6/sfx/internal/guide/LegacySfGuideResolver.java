package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class LegacySfGuideResolver {
    private static final Map<String, SfxItemCategory> VIRTUAL_CATEGORIES = createVirtualCategories();
    private static final Map<String, String> EXACT_CATEGORY_BY_ITEM = createExactCategoryByItem();
    private static final Map<String, String> SOURCE_CATEGORY_FALLBACKS = createSourceCategoryFallbacks();

    private LegacySfGuideResolver() {
    }

    static List<SfxItemCategory> visibleCategories(DefaultSfxItemRegistry registry, GuideMode mode) {
        List<SfxItemCategory> categories = new ArrayList<>();
        for (SfxItemCategory category : registry.categories()) {
            if (category.id().startsWith("sf:")) {
                continue;
            }
            if ("sfx:internal".equals(category.id())) {
                continue;
            }
            if (!category.hidden() || mode == GuideMode.CHEAT) {
                categories.add(category);
            }
        }
        for (SfxItemCategory category : VIRTUAL_CATEGORIES.values()) {
            if (!visibleItemsInCategory(registry, category.id()).isEmpty()) {
                categories.add(category);
            }
        }
        categories.sort(Comparator.comparingInt(SfxItemCategory::order).thenComparing(SfxItemCategory::id));
        return categories;
    }

    static Optional<SfxItemCategory> resolveCategory(DefaultSfxItemRegistry registry, String categoryId) {
        SfxItemCategory virtual = VIRTUAL_CATEGORIES.get(categoryId);
        if (virtual != null) {
            return Optional.of(virtual);
        }
        return registry.category(categoryId).filter(category -> !category.id().startsWith("sf:"));
    }

    static List<SfxItemDefinition> visibleItemsInCategory(DefaultSfxItemRegistry registry, String categoryId) {
        if (!VIRTUAL_CATEGORIES.containsKey(categoryId)) {
            return registry.visibleItemsInCategory(categoryId).stream().toList();
        }
        return registry.items().stream()
                .filter(item -> !item.hidden())
                .filter(LegacySfGuideResolver::isLegacySlimefunItem)
                .filter(item -> categoryId.equals(resolveLegacyGuideCategory(item)))
                .toList();
    }

    private static boolean isLegacySlimefunItem(SfxItemDefinition item) {
        return item.flags().contains("legacy-sf") || item.id().startsWith("sf:");
    }

    private static String resolveLegacyGuideCategory(SfxItemDefinition item) {
        String id = item.id();
        if (isEnderTalismanId(id)) {
            return "guide:sf:ender_talismans";
        }
        if (isTalismanId(id)) {
            return "guide:sf:talismans";
        }
        String exact = EXACT_CATEGORY_BY_ITEM.get(item.id());
        if (exact != null) {
            return exact;
        }
        if (id.endsWith("_xmas")) {
            return "guide:sf:christmas";
        }
        if (id.endsWith("_valentine")) {
            return "guide:sf:valentines_day";
        }
        if (id.endsWith("_halloween")) {
            return "guide:sf:halloween";
        }
        if ("sf:birthday_cake".equals(id)) {
            return "guide:sf:birthday";
        }
        return SOURCE_CATEGORY_FALLBACKS.getOrDefault(item.categoryId(), "guide:sf:misc");
    }


    private static boolean isEnderTalismanId(String id) {
        return "sf:ender_talisman".equals(id) || (id.startsWith("sf:ender_") && id.endsWith("_talisman"));
    }

    private static boolean isTalismanId(String id) {
        return "sf:common_talisman".equals(id) || (id.endsWith("_talisman") && !isEnderTalismanId(id));
    }

    private static Map<String, String> createExactCategoryByItem() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("sf:portable_crafter", "guide:sf:useful_items");
        map.put("sf:portable_dustbin", "guide:sf:useful_items");
        map.put("sf:ender_backpack", "guide:sf:magical_gadgets");
        map.put("sf:magic_eye_of_ender", "guide:sf:magical_gadgets");
        map.put("sf:broken_spawner", "guide:sf:magical_items");
        map.put("sf:infernal_bonemeal", "guide:sf:magical_gadgets");
        map.put("sf:tape_measure", "guide:sf:useful_items");
        map.put("sf:gold_pan", "guide:sf:tools");
        map.put("sf:nether_gold_pan", "guide:sf:tools");
        map.put("sf:parachute", "guide:sf:technical_gadgets");
        map.put("sf:grappling_hook", "guide:sf:tools");
        map.put("sf:solar_helmet", "guide:sf:technical_gadgets");
        map.put("sf:cloth", "guide:sf:misc");
        map.put("sf:reinforced_cloth", "guide:sf:technical_components");
        map.put("sf:night_vision_goggles", "guide:sf:technical_gadgets");
        map.put("sf:elytra_cap", "guide:sf:magical_armor");
        map.put("sf:farmer_shoes", "guide:sf:magical_armor");
        map.put("sf:infused_magnet", "guide:sf:magical_gadgets");
        map.put("sf:rag", "guide:sf:useful_items");
        map.put("sf:bandage", "guide:sf:useful_items");
        map.put("sf:splint", "guide:sf:useful_items");
        map.put("sf:vitamins", "guide:sf:useful_items");
        map.put("sf:medicine", "guide:sf:useful_items");
        map.put("sf:magical_zombie_pills", "guide:sf:magical_gadgets");
        map.put("sf:flask_of_knowledge", "guide:sf:magical_gadgets");
        map.put("sf:filled_flask_of_knowledge", "guide:sf:magical_gadgets");
        map.put("sf:small_backpack", "guide:sf:useful_items");
        map.put("sf:medium_backpack", "guide:sf:useful_items");
        map.put("sf:large_backpack", "guide:sf:useful_items");
        map.put("sf:woven_backpack", "guide:sf:useful_items");
        map.put("sf:gilded_backpack", "guide:sf:useful_items");
        map.put("sf:radiant_backpack", "guide:sf:useful_items");
        map.put("sf:bound_backpack", "guide:sf:magical_gadgets");
        map.put("sf:cooler", "guide:sf:useful_items");
        map.put("sf:duralumin_jetpack", "guide:sf:technical_gadgets");
        map.put("sf:solder_jetpack", "guide:sf:technical_gadgets");
        map.put("sf:billon_jetpack", "guide:sf:technical_gadgets");
        map.put("sf:steel_jetpack", "guide:sf:technical_gadgets");
        map.put("sf:damascus_steel_jetpack", "guide:sf:technical_gadgets");
        map.put("sf:reinforced_alloy_jetpack", "guide:sf:technical_gadgets");
        map.put("sf:carbonado_jetpack", "guide:sf:technical_gadgets");
        map.put("sf:armored_jetpack", "guide:sf:technical_gadgets");
        map.put("sf:duralumin_jetboots", "guide:sf:technical_gadgets");
        map.put("sf:solder_jetboots", "guide:sf:technical_gadgets");
        map.put("sf:billon_jetboots", "guide:sf:technical_gadgets");
        map.put("sf:steel_jetboots", "guide:sf:technical_gadgets");
        map.put("sf:damascus_steel_jetboots", "guide:sf:technical_gadgets");
        map.put("sf:reinforced_alloy_jetboots", "guide:sf:technical_gadgets");
        map.put("sf:carbonado_jetboots", "guide:sf:technical_gadgets");
        map.put("sf:armored_jetboots", "guide:sf:technical_gadgets");
        map.put("sf:duralumin_multi_tool", "guide:sf:technical_gadgets");
        map.put("sf:solder_multi_tool", "guide:sf:technical_gadgets");
        map.put("sf:billon_multi_tool", "guide:sf:technical_gadgets");
        map.put("sf:steel_multi_tool", "guide:sf:technical_gadgets");
        map.put("sf:damascus_steel_multi_tool", "guide:sf:technical_gadgets");
        map.put("sf:reinforced_alloy_multi_tool", "guide:sf:technical_gadgets");
        map.put("sf:carbonado_multi_tool", "guide:sf:technical_gadgets");
        map.put("sf:fortune_cookie", "guide:sf:food");
        map.put("sf:diet_cookie", "guide:sf:food");
        map.put("sf:magic_sugar", "guide:sf:food");
        map.put("sf:monster_jerky", "guide:sf:food");
        map.put("sf:apple_juice", "guide:sf:food");
        map.put("sf:melon_juice", "guide:sf:food");
        map.put("sf:carrot_juice", "guide:sf:food");
        map.put("sf:pumpkin_juice", "guide:sf:food");
        map.put("sf:sweet_berry_juice", "guide:sf:food");
        map.put("sf:glow_berry_juice", "guide:sf:food");
        map.put("sf:golden_apple_juice", "guide:sf:food");
        map.put("sf:beef_jerky", "guide:sf:food");
        map.put("sf:pork_jerky", "guide:sf:food");
        map.put("sf:chicken_jerky", "guide:sf:food");
        map.put("sf:mutton_jerky", "guide:sf:food");
        map.put("sf:rabbit_jerky", "guide:sf:food");
        map.put("sf:fish_jerky", "guide:sf:food");
        map.put("sf:kelp_cookie", "guide:sf:food");
        map.put("sf:christmas_milk", "guide:sf:christmas");
        map.put("sf:christmas_chocolate_milk", "guide:sf:christmas");
        map.put("sf:christmas_egg_nog", "guide:sf:christmas");
        map.put("sf:christmas_apple_cider", "guide:sf:christmas");
        map.put("sf:christmas_cookie", "guide:sf:christmas");
        map.put("sf:christmas_fruit_cake", "guide:sf:christmas");
        map.put("sf:christmas_apple_pie", "guide:sf:christmas");
        map.put("sf:christmas_hot_chocolate", "guide:sf:christmas");
        map.put("sf:christmas_cake", "guide:sf:christmas");
        map.put("sf:christmas_caramel", "guide:sf:christmas");
        map.put("sf:christmas_caramel_apple", "guide:sf:christmas");
        map.put("sf:christmas_chocolate_apple", "guide:sf:christmas");
        map.put("sf:christmas_present", "guide:sf:christmas");
        map.put("sf:easter_egg", "guide:sf:easter");
        map.put("sf:easter_apple_pie", "guide:sf:easter");
        map.put("sf:grandmas_walking_stick", "guide:sf:weapons");
        map.put("sf:grandpas_walking_stick", "guide:sf:weapons");
        map.put("sf:sword_of_beheading", "guide:sf:weapons");
        map.put("sf:blade_of_vampires", "guide:sf:weapons");
        map.put("sf:seismic_axe", "guide:sf:weapons");
        map.put("sf:explosive_bow", "guide:sf:weapons");
        map.put("sf:icy_bow", "guide:sf:weapons");
        map.put("sf:smelters_pickaxe", "guide:sf:tools");
        map.put("sf:lumber_axe", "guide:sf:tools");
        map.put("sf:pickaxe_of_containment", "guide:sf:tools");
        map.put("sf:explosive_pickaxe", "guide:sf:tools");
        map.put("sf:explosive_shovel", "guide:sf:tools");
        map.put("sf:pickaxe_of_the_seeker", "guide:sf:tools");
        map.put("sf:cobalt_pickaxe", "guide:sf:tools");
        map.put("sf:pickaxe_of_vein_mining", "guide:sf:tools");
        map.put("sf:climbing_pick", "guide:sf:tools");
        map.put("sf:glowstone_helmet", "guide:sf:magical_armor");
        map.put("sf:glowstone_chestplate", "guide:sf:magical_armor");
        map.put("sf:glowstone_leggings", "guide:sf:magical_armor");
        map.put("sf:glowstone_boots", "guide:sf:magical_armor");
        map.put("sf:rainbow_leather", "guide:sf:magical_items");
        map.put("sf:rainbow_helmet", "guide:sf:magical_armor");
        map.put("sf:rainbow_chestplate", "guide:sf:magical_armor");
        map.put("sf:rainbow_leggings", "guide:sf:magical_armor");
        map.put("sf:rainbow_boots", "guide:sf:magical_armor");
        map.put("sf:ender_helmet", "guide:sf:magical_armor");
        map.put("sf:ender_chestplate", "guide:sf:magical_armor");
        map.put("sf:ender_leggings", "guide:sf:magical_armor");
        map.put("sf:ender_boots", "guide:sf:magical_armor");
        map.put("sf:slime_helmet", "guide:sf:magical_armor");
        map.put("sf:slime_chestplate", "guide:sf:magical_armor");
        map.put("sf:slime_leggings", "guide:sf:magical_armor");
        map.put("sf:slime_boots", "guide:sf:magical_armor");
        map.put("sf:cactus_helmet", "guide:sf:armor");
        map.put("sf:cactus_chestplate", "guide:sf:armor");
        map.put("sf:cactus_leggings", "guide:sf:armor");
        map.put("sf:cactus_boots", "guide:sf:armor");
        map.put("sf:damascus_steel_helmet", "guide:sf:armor");
        map.put("sf:damascus_steel_chestplate", "guide:sf:armor");
        map.put("sf:damascus_steel_leggings", "guide:sf:armor");
        map.put("sf:damascus_steel_boots", "guide:sf:armor");
        map.put("sf:reinforced_alloy_helmet", "guide:sf:armor");
        map.put("sf:reinforced_alloy_chestplate", "guide:sf:armor");
        map.put("sf:reinforced_alloy_leggings", "guide:sf:armor");
        map.put("sf:reinforced_alloy_boots", "guide:sf:armor");
        map.put("sf:scuba_helmet", "guide:sf:armor");
        map.put("sf:hazmat_leggings", "guide:sf:armor");
        map.put("sf:rubber_boots", "guide:sf:armor");
        map.put("sf:gilded_iron_helmet", "guide:sf:armor");
        map.put("sf:gilded_iron_chestplate", "guide:sf:armor");
        map.put("sf:gilded_iron_leggings", "guide:sf:armor");
        map.put("sf:gilded_iron_boots", "guide:sf:armor");
        map.put("sf:gold_12k_helmet", "guide:sf:armor");
        map.put("sf:gold_12k_chestplate", "guide:sf:armor");
        map.put("sf:gold_12k_leggings", "guide:sf:armor");
        map.put("sf:gold_12k_boots", "guide:sf:armor");
        map.put("sf:slime_steel_helmet", "guide:sf:magical_armor");
        map.put("sf:slime_steel_chestplate", "guide:sf:magical_armor");
        map.put("sf:slime_steel_leggings", "guide:sf:magical_armor");
        map.put("sf:slime_steel_boots", "guide:sf:magical_armor");
        map.put("sf:boots_of_the_stomper", "guide:sf:magical_armor");
        map.put("sf:bee_helmet", "guide:sf:magical_armor");
        map.put("sf:bee_wings", "guide:sf:magical_armor");
        map.put("sf:bee_leggings", "guide:sf:magical_armor");
        map.put("sf:bee_boots", "guide:sf:magical_armor");
        map.put("sf:magic_lump_1", "guide:sf:magical_items");
        map.put("sf:magic_lump_2", "guide:sf:magical_items");
        map.put("sf:magic_lump_3", "guide:sf:magical_items");
        map.put("sf:ender_lump_1", "guide:sf:magical_items");
        map.put("sf:ender_lump_2", "guide:sf:magical_items");
        map.put("sf:ender_lump_3", "guide:sf:magical_items");
        map.put("sf:magical_book_cover", "guide:sf:magical_items");
        map.put("sf:magical_glass", "guide:sf:magical_items");
        map.put("sf:synthetic_shulker_shell", "guide:sf:magical_items");
        map.put("sf:basic_circuit_board", "guide:sf:technical_components");
        map.put("sf:advanced_circuit_board", "guide:sf:technical_components");
        map.put("sf:wheat_flour", "guide:sf:misc");
        map.put("sf:steel_plate", "guide:sf:misc");
        map.put("sf:battery", "guide:sf:technical_components");
        map.put("sf:carbon", "guide:sf:resources");
        map.put("sf:compressed_carbon", "guide:sf:resources");
        map.put("sf:carbon_chunk", "guide:sf:resources");
        map.put("sf:steel_thruster", "guide:sf:technical_components");
        map.put("sf:power_crystal", "guide:sf:technical_components");
        map.put("sf:chain", "guide:sf:misc");
        map.put("sf:hook", "guide:sf:misc");
        map.put("sf:sifted_ore", "guide:sf:misc");
        map.put("sf:stone_chunk", "guide:sf:misc");
        map.put("sf:lava_crystal", "guide:sf:magical_items");
        map.put("sf:salt", "guide:sf:misc");
        map.put("sf:cheese", "guide:sf:misc");
        map.put("sf:butter", "guide:sf:misc");
        map.put("sf:duct_tape", "guide:sf:misc");
        map.put("sf:heavy_cream", "guide:sf:misc");
        map.put("sf:crushed_ore", "guide:sf:misc");
        map.put("sf:pulverized_ore", "guide:sf:misc");
        map.put("sf:pure_ore_cluster", "guide:sf:misc");
        map.put("sf:small_uranium", "guide:sf:misc");
        map.put("sf:tiny_uranium", "guide:sf:misc");
        map.put("sf:solar_panel", "guide:sf:technical_components");
        map.put("sf:plastic_sheet", "guide:sf:technical_components");
        map.put("sf:magnet", "guide:sf:technical_components");
        map.put("sf:necrotic_skull", "guide:sf:magical_items");
        map.put("sf:essence_of_afterlife", "guide:sf:magical_gadgets");
        map.put("sf:strange_nether_goo", "guide:sf:magical_items");
        map.put("sf:electro_magnet", "guide:sf:technical_components");
        map.put("sf:heating_coil", "guide:sf:technical_components");
        map.put("sf:cooling_unit", "guide:sf:technical_components");
        map.put("sf:electric_motor", "guide:sf:technical_components");
        map.put("sf:cargo_motor", "guide:sf:cargo");
        map.put("sf:scroll_of_dimensional_teleposition", "guide:sf:magical_gadgets");
        map.put("sf:tome_of_knowledge_sharing", "guide:sf:magical_gadgets");
        map.put("sf:hardened_glass", "guide:sf:technical_components");
        map.put("sf:wither_proof_obsidian", "guide:sf:technical_components");
        map.put("sf:wither_proof_glass", "guide:sf:technical_components");
        map.put("sf:reinforced_plate", "guide:sf:misc");
        map.put("sf:ancient_pedestal", "guide:sf:magical_gadgets");
        map.put("sf:ancient_altar", "guide:sf:magical_gadgets");
        map.put("sf:copper_wire", "guide:sf:technical_components");
        map.put("sf:crafting_motor", "guide:sf:cargo");
        map.put("sf:rainbow_wool", "guide:sf:magical_gadgets");
        map.put("sf:rainbow_glass", "guide:sf:magical_gadgets");
        map.put("sf:rainbow_clay", "guide:sf:magical_gadgets");
        map.put("sf:rainbow_glass_pane", "guide:sf:magical_gadgets");
        map.put("sf:rainbow_concrete", "guide:sf:magical_gadgets");
        map.put("sf:rainbow_glazed_terracotta", "guide:sf:magical_gadgets");
        map.put("sf:rainbow_wool_xmas", "guide:sf:christmas");
        map.put("sf:rainbow_glass_xmas", "guide:sf:christmas");
        map.put("sf:rainbow_clay_xmas", "guide:sf:christmas");
        map.put("sf:rainbow_glass_pane_xmas", "guide:sf:christmas");
        map.put("sf:rainbow_concrete_xmas", "guide:sf:christmas");
        map.put("sf:rainbow_glazed_terracotta_xmas", "guide:sf:christmas");
        map.put("sf:rainbow_wool_valentine", "guide:sf:valentines_day");
        map.put("sf:rainbow_glass_valentine", "guide:sf:valentines_day");
        map.put("sf:rainbow_clay_valentine", "guide:sf:valentines_day");
        map.put("sf:rainbow_glass_pane_valentine", "guide:sf:valentines_day");
        map.put("sf:rainbow_concrete_valentine", "guide:sf:valentines_day");
        map.put("sf:rainbow_glazed_terracotta_valentine", "guide:sf:valentines_day");
        map.put("sf:rainbow_wool_halloween", "guide:sf:halloween");
        map.put("sf:rainbow_glass_halloween", "guide:sf:halloween");
        map.put("sf:rainbow_clay_halloween", "guide:sf:halloween");
        map.put("sf:rainbow_glass_pane_halloween", "guide:sf:halloween");
        map.put("sf:rainbow_concrete_halloween", "guide:sf:halloween");
        map.put("sf:rainbow_glazed_terracotta_halloween", "guide:sf:halloween");
        map.put("sf:copper_ingot", "guide:sf:resources");
        map.put("sf:tin_ingot", "guide:sf:resources");
        map.put("sf:silver_ingot", "guide:sf:resources");
        map.put("sf:aluminum_ingot", "guide:sf:resources");
        map.put("sf:lead_ingot", "guide:sf:resources");
        map.put("sf:zinc_ingot", "guide:sf:resources");
        map.put("sf:magnesium_ingot", "guide:sf:resources");
        map.put("sf:steel_ingot", "guide:sf:resources");
        map.put("sf:bronze_ingot", "guide:sf:resources");
        map.put("sf:duralumin_ingot", "guide:sf:resources");
        map.put("sf:billon_ingot", "guide:sf:resources");
        map.put("sf:brass_ingot", "guide:sf:resources");
        map.put("sf:aluminum_brass_ingot", "guide:sf:resources");
        map.put("sf:aluminum_bronze_ingot", "guide:sf:resources");
        map.put("sf:corinthian_bronze_ingot", "guide:sf:resources");
        map.put("sf:solder_ingot", "guide:sf:resources");
        map.put("sf:damascus_steel_ingot", "guide:sf:resources");
        map.put("sf:hardened_metal_ingot", "guide:sf:resources");
        map.put("sf:reinforced_alloy_ingot", "guide:sf:resources");
        map.put("sf:ferrosilicon", "guide:sf:resources");
        map.put("sf:gilded_iron", "guide:sf:resources");
        map.put("sf:redstone_alloy", "guide:sf:resources");
        map.put("sf:nickel_ingot", "guide:sf:resources");
        map.put("sf:cobalt_ingot", "guide:sf:resources");
        map.put("sf:gold_4k", "guide:sf:resources");
        map.put("sf:gold_6k", "guide:sf:resources");
        map.put("sf:gold_8k", "guide:sf:resources");
        map.put("sf:gold_10k", "guide:sf:resources");
        map.put("sf:gold_12k", "guide:sf:resources");
        map.put("sf:gold_14k", "guide:sf:resources");
        map.put("sf:gold_16k", "guide:sf:resources");
        map.put("sf:gold_18k", "guide:sf:resources");
        map.put("sf:gold_20k", "guide:sf:resources");
        map.put("sf:gold_22k", "guide:sf:resources");
        map.put("sf:gold_24k", "guide:sf:resources");
        map.put("sf:iron_dust", "guide:sf:resources");
        map.put("sf:gold_dust", "guide:sf:resources");
        map.put("sf:tin_dust", "guide:sf:resources");
        map.put("sf:copper_dust", "guide:sf:resources");
        map.put("sf:silver_dust", "guide:sf:resources");
        map.put("sf:aluminum_dust", "guide:sf:resources");
        map.put("sf:lead_dust", "guide:sf:resources");
        map.put("sf:zinc_dust", "guide:sf:resources");
        map.put("sf:magnesium_dust", "guide:sf:resources");
        map.put("sf:sulfate", "guide:sf:resources");
        map.put("sf:silicon", "guide:sf:resources");
        map.put("sf:gold_24k_block", "guide:sf:food");
        map.put("sf:synthetic_diamond", "guide:sf:resources");
        map.put("sf:synthetic_emerald", "guide:sf:resources");
        map.put("sf:synthetic_sapphire", "guide:sf:resources");
        map.put("sf:carbonado", "guide:sf:resources");
        map.put("sf:raw_carbonado", "guide:sf:resources");
        map.put("sf:uranium", "guide:sf:resources");
        map.put("sf:neptunium", "guide:sf:resources");
        map.put("sf:plutonium", "guide:sf:resources");
        map.put("sf:boosted_uranium", "guide:sf:resources");
        map.put("sf:common_talisman", "guide:sf:magical_items");
        map.put("sf:staff_elemental", "guide:sf:magical_gadgets");
        map.put("sf:enhanced_crafting_table", "guide:sf:basic_machines");
        map.put("sf:grind_stone", "guide:sf:basic_machines");
        map.put("sf:armor_forge", "guide:sf:basic_machines");
        map.put("sf:makeshift_smeltery", "guide:sf:basic_machines");
        map.put("sf:smeltery", "guide:sf:basic_machines");
        map.put("sf:ore_crusher", "guide:sf:basic_machines");
        map.put("sf:pressure_chamber", "guide:sf:basic_machines");
        map.put("sf:magic_workbench", "guide:sf:basic_machines");
        map.put("sf:ore_washer", "guide:sf:basic_machines");
        map.put("sf:table_saw", "guide:sf:basic_machines");
        map.put("sf:juicer", "guide:sf:basic_machines");
        map.put("sf:automated_panning_machine", "guide:sf:basic_machines");
        map.put("sf:industrial_miner", "guide:sf:basic_machines");
        map.put("sf:advanced_industrial_miner", "guide:sf:basic_machines");
        map.put("sf:composter", "guide:sf:basic_machines");
        map.put("sf:crucible", "guide:sf:basic_machines");
        map.put("sf:output_chest", "guide:sf:basic_machines");
        map.put("sf:ignition_chamber", "guide:sf:basic_machines");
        map.put("sf:hologram_projector", "guide:sf:technical_gadgets");
        map.put("sf:block_placer", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_2", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_3", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_4", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_5", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_6", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_7", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_8", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_9", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_10", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnace_11", "guide:sf:basic_machines");
        map.put("sf:reinforced_furnace", "guide:sf:basic_machines");
        map.put("sf:carbonado_edged_furnace", "guide:sf:basic_machines");
        map.put("sf:soulbound_sword", "guide:sf:weapons");
        map.put("sf:soulbound_bow", "guide:sf:weapons");
        map.put("sf:soulbound_pickaxe", "guide:sf:tools");
        map.put("sf:soulbound_axe", "guide:sf:tools");
        map.put("sf:soulbound_shovel", "guide:sf:tools");
        map.put("sf:soulbound_hoe", "guide:sf:tools");
        map.put("sf:soulbound_trident", "guide:sf:weapons");
        map.put("sf:soulbound_helmet", "guide:sf:magical_armor");
        map.put("sf:soulbound_chestplate", "guide:sf:magical_armor");
        map.put("sf:soulbound_leggings", "guide:sf:magical_armor");
        map.put("sf:soulbound_boots", "guide:sf:magical_armor");
        map.put("sf:blank_rune", "guide:sf:magical_items");
        map.put("sf:solar_generator", "guide:sf:electricity");
        map.put("sf:solar_generator_2", "guide:sf:electricity");
        map.put("sf:solar_generator_3", "guide:sf:electricity");
        map.put("sf:solar_generator_4", "guide:sf:electricity");
        map.put("sf:coal_generator", "guide:sf:electricity");
        map.put("sf:coal_generator_2", "guide:sf:electricity");
        map.put("sf:lava_generator", "guide:sf:electricity");
        map.put("sf:lava_generator_2", "guide:sf:electricity");
        map.put("sf:electric_furnace", "guide:sf:electricity");
        map.put("sf:electric_furnace_2", "guide:sf:electricity");
        map.put("sf:electric_furnace_3", "guide:sf:electricity");
        map.put("sf:electric_ore_grinder", "guide:sf:electricity");
        map.put("sf:electric_ore_grinder_2", "guide:sf:electricity");
        map.put("sf:electric_ore_grinder_3", "guide:sf:electricity");
        map.put("sf:electric_ingot_pulverizer", "guide:sf:electricity");
        map.put("sf:auto_drier", "guide:sf:electricity");
        map.put("sf:auto_enchanter", "guide:sf:electricity");
        map.put("sf:auto_enchanter_2", "guide:sf:electricity");
        map.put("sf:auto_disenchanter", "guide:sf:electricity");
        map.put("sf:auto_disenchanter_2", "guide:sf:electricity");
        map.put("sf:auto_anvil", "guide:sf:electricity");
        map.put("sf:auto_anvil_2", "guide:sf:electricity");
        map.put("sf:auto_brewer", "guide:sf:electricity");
        map.put("sf:book_binder", "guide:sf:electricity");
        map.put("sf:bio_reactor", "guide:sf:electricity");
        map.put("sf:multimeter", "guide:sf:technical_gadgets");
        map.put("sf:small_capacitor", "guide:sf:electricity");
        map.put("sf:medium_capacitor", "guide:sf:electricity");
        map.put("sf:big_capacitor", "guide:sf:electricity");
        map.put("sf:large_capacitor", "guide:sf:electricity");
        map.put("sf:carbonado_edged_capacitor", "guide:sf:electricity");
        map.put("sf:energized_capacitor", "guide:sf:electricity");
        map.put("sf:programmable_android", "guide:sf:androids");
        map.put("sf:programmable_android_farmer", "guide:sf:androids");
        map.put("sf:programmable_android_miner", "guide:sf:androids");
        map.put("sf:programmable_android_woodcutter", "guide:sf:androids");
        map.put("sf:programmable_android_butcher", "guide:sf:androids");
        map.put("sf:programmable_android_fisherman", "guide:sf:androids");
        map.put("sf:programmable_android_2", "guide:sf:androids");
        map.put("sf:programmable_android_2_fisherman", "guide:sf:androids");
        map.put("sf:programmable_android_2_farmer", "guide:sf:androids");
        map.put("sf:programmable_android_2_butcher", "guide:sf:androids");
        map.put("sf:programmable_android_3", "guide:sf:androids");
        map.put("sf:programmable_android_3_fisherman", "guide:sf:androids");
        map.put("sf:programmable_android_3_butcher", "guide:sf:androids");
        map.put("sf:gps_transmitter", "guide:sf:gps");
        map.put("sf:gps_transmitter_2", "guide:sf:gps");
        map.put("sf:gps_transmitter_3", "guide:sf:gps");
        map.put("sf:gps_transmitter_4", "guide:sf:gps");
        map.put("sf:gps_marker_tool", "guide:sf:gps");
        map.put("sf:gps_control_panel", "guide:sf:gps");
        map.put("sf:gps_emergency_transmitter", "guide:sf:gps");
        map.put("sf:android_interface_fuel", "guide:sf:androids");
        map.put("sf:android_interface_items", "guide:sf:androids");
        map.put("sf:gps_geo_scanner", "guide:sf:gps");
        map.put("sf:portable_geo_scanner", "guide:sf:gps");
        map.put("sf:geo_miner", "guide:sf:gps");
        map.put("sf:oil_pump", "guide:sf:gps");
        map.put("sf:refinery", "guide:sf:electricity");
        map.put("sf:combustion_reactor", "guide:sf:electricity");
        map.put("sf:android_memory_core", "guide:sf:technical_components");
        map.put("sf:gps_teleporter_pylon", "guide:sf:gps");
        map.put("sf:gps_teleportation_matrix", "guide:sf:gps");
        map.put("sf:gps_activation_device_shared", "guide:sf:gps");
        map.put("sf:gps_activation_device_personal", "guide:sf:gps");
        map.put("sf:portable_teleporter", "guide:sf:gps");
        map.put("sf:elevator_plate", "guide:sf:gps");
        map.put("sf:infused_hopper", "guide:sf:magical_gadgets");
        map.put("sf:heated_pressure_chamber", "guide:sf:electricity");
        map.put("sf:heated_pressure_chamber_2", "guide:sf:electricity");
        map.put("sf:electric_smeltery", "guide:sf:electricity");
        map.put("sf:electric_smeltery_2", "guide:sf:electricity");
        map.put("sf:electric_press", "guide:sf:electricity");
        map.put("sf:electric_press_2", "guide:sf:electricity");
        map.put("sf:electrified_crucible", "guide:sf:electricity");
        map.put("sf:electrified_crucible_2", "guide:sf:electricity");
        map.put("sf:electrified_crucible_3", "guide:sf:electricity");
        map.put("sf:carbon_press", "guide:sf:electricity");
        map.put("sf:carbon_press_2", "guide:sf:electricity");
        map.put("sf:carbon_press_3", "guide:sf:electricity");
        map.put("sf:blistering_ingot", "guide:sf:resources");
        map.put("sf:blistering_ingot_2", "guide:sf:resources");
        map.put("sf:blistering_ingot_3", "guide:sf:resources");
        map.put("sf:energy_regulator", "guide:sf:electricity");
        map.put("sf:energy_connector", "guide:sf:electricity");
        map.put("sf:nether_ice", "guide:sf:resources");
        map.put("sf:enriched_nether_ice", "guide:sf:resources");
        map.put("sf:nether_ice_coolant_cell", "guide:sf:technical_components");
        map.put("sf:cargo_manager", "guide:sf:cargo");
        map.put("sf:auto_breeder", "guide:sf:electricity");
        map.put("sf:produce_collector", "guide:sf:electricity");
        map.put("sf:animal_growth_accelerator", "guide:sf:electricity");
        map.put("sf:crop_growth_accelerator", "guide:sf:electricity");
        map.put("sf:crop_growth_accelerator_2", "guide:sf:electricity");
        map.put("sf:tree_growth_accelerator", "guide:sf:electricity");
        map.put("sf:food_fabricator", "guide:sf:electricity");
        map.put("sf:food_fabricator_2", "guide:sf:electricity");
        map.put("sf:food_composter", "guide:sf:electricity");
        map.put("sf:food_composter_2", "guide:sf:electricity");
        map.put("sf:nuclear_reactor", "guide:sf:electricity");
        map.put("sf:reactor_access_port", "guide:sf:cargo");
        map.put("sf:freezer", "guide:sf:electricity");
        map.put("sf:freezer_2", "guide:sf:electricity");
        map.put("sf:freezer_3", "guide:sf:electricity");
        map.put("sf:electric_gold_pan", "guide:sf:electricity");
        map.put("sf:electric_gold_pan_2", "guide:sf:electricity");
        map.put("sf:electric_gold_pan_3", "guide:sf:electricity");
        map.put("sf:electric_dust_washer", "guide:sf:electricity");
        map.put("sf:electric_dust_washer_2", "guide:sf:electricity");
        map.put("sf:electric_dust_washer_3", "guide:sf:electricity");
        map.put("sf:electric_ingot_factory", "guide:sf:electricity");
        map.put("sf:electric_ingot_factory_2", "guide:sf:electricity");
        map.put("sf:electric_ingot_factory_3", "guide:sf:electricity");
        map.put("sf:fluid_pump", "guide:sf:electricity");
        map.put("sf:charging_bench", "guide:sf:electricity");
        map.put("sf:vanilla_auto_crafter", "guide:sf:cargo");
        map.put("sf:enhanced_auto_crafter", "guide:sf:cargo");
        map.put("sf:armor_auto_crafter", "guide:sf:cargo");
        map.put("sf:iron_golem_assembler", "guide:sf:electricity");
        map.put("sf:wither_assembler", "guide:sf:electricity");
        map.put("sf:elytra_scale", "guide:sf:magical_gadgets");
        map.put("sf:infused_elytra", "guide:sf:magical_gadgets");
        map.put("sf:soulbound_elytra", "guide:sf:magical_gadgets");
        map.put("sf:magnesium_salt", "guide:sf:resources");
        map.put("sf:magnesium_generator", "guide:sf:electricity");
        map.put("sf:reinforced_spawner", "guide:sf:magical_items");
        map.put("sf:can", "guide:sf:misc");
        map.put("sf:restored_backpack", "guide:sf:useful_items");
        map.put("sf:carrot_pie", "guide:sf:easter");
        map.put("sf:hazmat_chestplate", "guide:sf:armor");
        map.put("sf:ender_talisman", "guide:sf:magical_items");
        map.put("sf:anvil_talisman", "guide:sf:magical_items");
        map.put("sf:miner_talisman", "guide:sf:magical_items");
        map.put("sf:farmer_talisman", "guide:sf:magical_items");
        map.put("sf:hunter_talisman", "guide:sf:magical_items");
        map.put("sf:lava_talisman", "guide:sf:magical_items");
        map.put("sf:water_talisman", "guide:sf:magical_items");
        map.put("sf:angel_talisman", "guide:sf:magical_items");
        map.put("sf:fire_talisman", "guide:sf:magical_items");
        map.put("sf:magician_talisman", "guide:sf:magical_items");
        map.put("sf:traveller_talisman", "guide:sf:magical_items");
        map.put("sf:warrior_talisman", "guide:sf:magical_items");
        map.put("sf:knight_talisman", "guide:sf:magical_items");
        map.put("sf:whirlwind_talisman", "guide:sf:magical_items");
        map.put("sf:wizard_talisman", "guide:sf:magical_items");
        map.put("sf:caveman_talisman", "guide:sf:magical_items");
        map.put("sf:wise_talisman", "guide:sf:magical_items");
        map.put("sf:staff_elemental_wind", "guide:sf:magical_gadgets");
        map.put("sf:staff_elemental_fire", "guide:sf:magical_gadgets");
        map.put("sf:staff_elemental_water", "guide:sf:magical_gadgets");
        map.put("sf:staff_elemental_storm", "guide:sf:magical_gadgets");
        map.put("sf:compressor", "guide:sf:basic_machines");
        map.put("sf:ancient_rune_air", "guide:sf:magical_items");
        map.put("sf:ancient_rune_water", "guide:sf:magical_items");
        map.put("sf:ancient_rune_fire", "guide:sf:magical_items");
        map.put("sf:ancient_rune_earth", "guide:sf:magical_items");
        map.put("sf:ancient_rune_ender", "guide:sf:magical_items");
        map.put("sf:ancient_rune_rainbow", "guide:sf:magical_items");
        map.put("sf:ancient_rune_lightning", "guide:sf:magical_items");
        map.put("sf:ancient_rune_soulbound", "guide:sf:magical_items");
        map.put("sf:ancient_rune_enchantment", "guide:sf:magical_items");
        map.put("sf:ancient_rune_villagers", "guide:sf:magical_items");
        map.put("sf:bucket_of_oil", "guide:sf:resources");
        map.put("sf:bucket_of_fuel", "guide:sf:resources");
        map.put("sf:debug_fish", "guide:sf:misc");
        map.put("sf:cargo_node", "guide:sf:cargo");
        map.put("sf:cargo_node_input", "guide:sf:cargo");
        map.put("sf:cargo_node_output", "guide:sf:cargo");
        map.put("sf:cargo_node_output_advanced", "guide:sf:cargo");
        map.put("sf:organic_food", "guide:sf:misc");
        map.put("sf:organic_food_wheat", "guide:sf:misc");
        map.put("sf:organic_food_carrot", "guide:sf:misc");
        map.put("sf:organic_food_potato", "guide:sf:misc");
        map.put("sf:organic_food_seeds", "guide:sf:misc");
        map.put("sf:organic_food_beetroot", "guide:sf:misc");
        map.put("sf:organic_food_melon", "guide:sf:misc");
        map.put("sf:organic_food_apple", "guide:sf:misc");
        map.put("sf:organic_food_sweet_berries", "guide:sf:misc");
        map.put("sf:organic_food_kelp", "guide:sf:misc");
        map.put("sf:organic_food_cocoa", "guide:sf:misc");
        map.put("sf:organic_food_seagrass", "guide:sf:misc");
        map.put("sf:fertilizer", "guide:sf:misc");
        map.put("sf:fertilizer_wheat", "guide:sf:misc");
        map.put("sf:fertilizer_carrot", "guide:sf:misc");
        map.put("sf:fertilizer_potato", "guide:sf:misc");
        map.put("sf:fertilizer_seeds", "guide:sf:misc");
        map.put("sf:fertilizer_beetroot", "guide:sf:misc");
        map.put("sf:fertilizer_melon", "guide:sf:misc");
        map.put("sf:fertilizer_apple", "guide:sf:misc");
        map.put("sf:fertilizer_sweet_berries", "guide:sf:misc");
        map.put("sf:fertilizer_kelp", "guide:sf:misc");
        map.put("sf:fertilizer_cocoa", "guide:sf:misc");
        map.put("sf:fertilizer_seagrass", "guide:sf:misc");
        map.put("sf:xp_collector", "guide:sf:electricity");
        map.put("sf:reactor_collant_cell", "guide:sf:technical_components");
        map.put("sf:netherstar_reactor", "guide:sf:electricity");
        map.put("sf:trash_can_block", "guide:sf:cargo");
        map.put("sf:birthday_cake", "guide:sf:birthday");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> createSourceCategoryFallbacks() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("sf:items", "guide:sf:useful_items");
        map.put("sf:gadgets", "guide:sf:useful_items");
        map.put("sf:backpacks", "guide:sf:useful_items");
        map.put("sf:jetpacks", "guide:sf:technical_gadgets");
        map.put("sf:jetboots", "guide:sf:technical_gadgets");
        map.put("sf:multi_tools", "guide:sf:technical_gadgets");
        map.put("sf:food", "guide:sf:food");
        map.put("sf:christmas", "guide:sf:christmas");
        map.put("sf:easter", "guide:sf:easter");
        map.put("sf:weapons", "guide:sf:weapons");
        map.put("sf:bows", "guide:sf:weapons");
        map.put("sf:tools", "guide:sf:tools");
        map.put("sf:armor", "guide:sf:armor");
        map.put("sf:magical_components", "guide:sf:magical_items");
        map.put("sf:technical_components", "guide:sf:technical_components");
        map.put("sf:rainbow_blocks", "guide:sf:magical_gadgets");
        map.put("sf:seasonal", "guide:sf:christmas");
        map.put("sf:ingots", "guide:sf:resources");
        map.put("sf:alloy_carbon_iron", "guide:sf:resources");
        map.put("sf:alloy_copper_tin", "guide:sf:resources");
        map.put("sf:alloy_copper_aluminum", "guide:sf:resources");
        map.put("sf:alloy_copper_silver", "guide:sf:resources");
        map.put("sf:alloy_copper_zinc", "guide:sf:resources");
        map.put("sf:alloy_aluminum_brass", "guide:sf:resources");
        map.put("sf:alloy_aluminum_bronze", "guide:sf:resources");
        map.put("sf:alloy_gold_silver_copper", "guide:sf:resources");
        map.put("sf:alloy_lead_tin", "guide:sf:resources");
        map.put("sf:alloy_steel_iron_carbon", "guide:sf:resources");
        map.put("sf:alloy_damascus_steel_duralumin_compressed_carbon_aluminium_bronze", "guide:sf:resources");
        map.put("sf:alloy_hardened_metal_corinthian_bronze_solder_billon_damascus_steel", "guide:sf:resources");
        map.put("sf:alloy_iron_silicon", "guide:sf:resources");
        map.put("sf:alloy_iron_gold", "guide:sf:resources");
        map.put("sf:alloy_redstone_ferrosilicon", "guide:sf:resources");
        map.put("sf:alloy_iron_copper", "guide:sf:resources");
        map.put("sf:alloy_nickel_iron_copper", "guide:sf:resources");
        map.put("sf:gold", "guide:sf:resources");
        map.put("sf:dusts", "guide:sf:resources");
        map.put("sf:gems", "guide:sf:resources");
        map.put("sf:talisman", "guide:sf:talismans");
        map.put("sf:staves", "guide:sf:magical_gadgets");
        map.put("sf:multiblocks", "guide:sf:basic_machines");
        map.put("sf:machines", "guide:sf:basic_machines");
        map.put("sf:enhanced_furnaces", "guide:sf:basic_machines");
        map.put("sf:soulbound_items", "guide:sf:magical_gadgets");
        map.put("sf:runes", "guide:sf:magical_items");
        map.put("sf:electricity", "guide:sf:electricity");
        map.put("sf:robots", "guide:sf:androids");
        map.put("sf:gps", "guide:sf:gps");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, SfxItemCategory> createVirtualCategories() {
        Map<String, SfxItemCategory> map = new LinkedHashMap<>();
        map.put("guide:sf:weapons", category("guide:sf:weapons", "&7Weapons", icon(Material.STICK, "&7Grandmas Walking Stick", null, null), 1070));
        map.put("guide:sf:tools", category("guide:sf:tools", "&7Tools", icon(Material.DIAMOND_PICKAXE, "&6Smelter's Pickaxe", null, null), 1080));
        map.put("guide:sf:useful_items", category("guide:sf:useful_items", "&7Useful Items", icon(Material.PLAYER_HEAD, "&eBackpack", "2a3b34862b9afb63cf8d5779966d3fba70af82b04e83f3eaf6449aeba", null), 1000));
        map.put("guide:sf:basic_machines", category("guide:sf:basic_machines", "&7Basic Machines", icon(Material.CRAFTING_TABLE, "&eEnhanced Crafting Table", null, null), 1010));
        map.put("guide:sf:food", category("guide:sf:food", "&7Food", icon(Material.COOKIE, "&6Fortune Cookie", null, null), 1060));
        map.put("guide:sf:armor", category("guide:sf:armor", "&7Armor", icon(Material.IRON_CHESTPLATE, "&bWatered Steel Chestplate", null, null), 1090));
        map.put("guide:sf:magical_items", category("guide:sf:magical_items", "&7Magical Items", icon(Material.ENDER_EYE, "&5Ender Rune", null, null), 1040));
        map.put("guide:sf:talismans", category("guide:sf:talismans", "&7Talismans - &aTier I", icon(Material.EMERALD, "&7Common Talisman", null, null), 1020));
        map.put("guide:sf:ender_talismans", category("guide:sf:ender_talismans", "&7Talismans - &5Tier II", icon(Material.ENDER_EYE, "&5Ender Talisman", null, null), 1030));
        map.put("guide:sf:magical_gadgets", category("guide:sf:magical_gadgets", "&7Magical Gadgets", icon(Material.ELYTRA, "&5Infused Elytra", null, null), 1070));
        map.put("guide:sf:magical_armor", category("guide:sf:magical_armor", "&7Magical Armor", icon(Material.DIAMOND_HELMET, "&5Ender Helmet", null, null), 1080));
        map.put("guide:sf:misc", category("guide:sf:misc", "&7Miscellaneous", icon(Material.BUCKET, "&7Tin Can", null, null), 1090));
        map.put("guide:sf:technical_components", category("guide:sf:technical_components", "&7Technical Components", icon(Material.BLAZE_POWDER, "&6Heating Coil", null, null), 1100));
        map.put("guide:sf:technical_gadgets", category("guide:sf:technical_gadgets", "&7Technical Gadgets", icon(Material.LEATHER_CHESTPLATE, "&9Electric Jetpack &7- &eIV", null, 0x9D9D97), 1110));
        map.put("guide:sf:resources", category("guide:sf:resources", "&7Resources", icon(Material.PLAYER_HEAD, "&bSynthetic Sapphire", "e35032f4d7d01de8ec99d89f8723012d4e74fa73022c4facf1b57c7ff6ff0", null), 1120));
        map.put("guide:sf:electricity", category("guide:sf:electricity", "&bEnergy and Electricity", icon(Material.PLAYER_HEAD, "&2Nuclear Reactor", "fa5de0bc2bfb5cc2d23eb72f96402ada479524dd0de404bc23b6dacee3ffd080", null), 1130));
        map.put("guide:sf:androids", category("guide:sf:androids", "&cProgrammable Androids", icon(Material.PLAYER_HEAD, "&cProgrammable Android", "3503cb7ed845e7a507f569afc647c47ac483771465c9a679a54594c76afba", null), 1140));
        map.put("guide:sf:cargo", category("guide:sf:cargo", "&cCargo Management", icon(Material.PLAYER_HEAD, "&6Cargo Manager", "e510bc85362a130a6ff9d91ff11d6fa46d7d1912a3431f751558ef3c4d9c2", null), 1150));
        map.put("guide:sf:gps", category("guide:sf:gps", "&bGPS-based Machines", icon(Material.PLAYER_HEAD, "&bGPS Transmitter", "b0c9c1a022f40b73f14b4cba37c718c6a533f3a2864b6536d5f456934cc1f", null), 1160));
        map.put("guide:sf:christmas", category("guide:sf:christmas", "&cChristmas", icon(Material.PLAYER_HEAD, "&cChristmas", "215ba31cde2671b8f176de6a9ffd008035f0590d63ee240be6e8921cd2037a45", null), 1170));
        map.put("guide:sf:valentines_day", category("guide:sf:valentines_day", "&dValentine's Day", icon(Material.PLAYER_HEAD, "&dValentine's Day", "55d89431d14bfef2060461b4a3565614dc51115c001fae2508e8684bc0ae6a80", null), 1180));
        map.put("guide:sf:easter", category("guide:sf:easter", "&6Easter", icon(Material.PLAYER_HEAD, "&fEaster Egg", "b2cd5df9d7f1fa8341fcce2f3c118e2f517e4d2d99df2c51d61d93ed7f83e13", null), 1190));
        map.put("guide:sf:birthday", category("guide:sf:birthday", "&a&lTheBusyBiscuit's Birthday", icon(Material.FIREWORK_ROCKET, "&bBirthday Cake", null, null), 1200));
        map.put("guide:sf:halloween", category("guide:sf:halloween", "&6&lHalloween", icon(Material.JACK_O_LANTERN, "&6&lHalloween", null, null), 1210));
        return Collections.unmodifiableMap(map);
    }

    private static SfxItemCategory category(String id, String legacyName, ItemStack icon, int order) {
        return new SfxItemCategory(id, Text.legacy(legacyName), icon, order, false);
    }

    private static ItemStack icon(Material material, String legacyName, String textureHash, Integer colorRgb) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (material == Material.PLAYER_HEAD && textureHash != null) {
                HeadTextures.apply(meta, textureHash);
            }
            if (colorRgb != null && meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leatherMeta) {
                leatherMeta.setColor(org.bukkit.Color.fromRGB(colorRgb));
            }
            meta.displayName(Text.noItalic(Text.legacy(legacyName)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
