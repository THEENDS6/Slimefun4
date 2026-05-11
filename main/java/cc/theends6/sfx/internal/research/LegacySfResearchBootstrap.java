package cc.theends6.sfx.internal.research;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import java.util.Collection;
import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class LegacySfResearchBootstrap {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private LegacySfResearchBootstrap() {
    }

    public static void register(SfxResearchRegistry registry, Collection<SfxItemDefinition> items) {
        registry.clear();

        register(registry, "walking_sticks", "Walking Sticks", 1, 0, "sf:grandmas_walking_stick", "sf:grandpas_walking_stick");
        register(registry, "portable_crafter", "Portable Crafter", 1, 1, "sf:portable_crafter");
        register(registry, "fortune_cookie", "Fortune Cookie", 1, 2, "sf:fortune_cookie");
        register(registry, "portable_dustbin", "Portable Dustbin", 2, 4, "sf:portable_dustbin");
        register(registry, "meat_jerky", "Jerkys", 2, 5, "sf:beef_jerky", "sf:fish_jerky", "sf:rabbit_jerky", "sf:mutton_jerky", "sf:chicken_jerky", "sf:pork_jerky");
        register(registry, "armor_forge", "Armor Crafting", 2, 6, "sf:armor_forge");
        register(registry, "glowstone_armor", "Glowstone Armor", 3, 7, "sf:glowstone_helmet", "sf:glowstone_chestplate", "sf:glowstone_leggings", "sf:glowstone_boots");
        register(registry, "lumps", "Lumps and Magic", 3, 8, "sf:magic_lump_1", "sf:magic_lump_2", "sf:magic_lump_3", "sf:ender_lump_1", "sf:ender_lump_2", "sf:ender_lump_3");
        register(registry, "ender_backpack", "Ender Backpack", 4, 9, "sf:ender_backpack");
        register(registry, "ender_armor", "Ender Armor", 4, 10, "sf:ender_helmet", "sf:ender_chestplate", "sf:ender_leggings", "sf:ender_boots");
        register(registry, "magic_eye_of_ender", "Magic Eye of Ender", 4, 11, "sf:magic_eye_of_ender");
        register(registry, "magic_sugar", "Magic Sugar", 4, 12, "sf:magic_sugar");
        register(registry, "monster_jerky", "Monster Jerky", 5, 13, "sf:monster_jerky");
        register(registry, "slime_armor", "Slime Armor", 5, 14, "sf:slime_helmet", "sf:slime_chestplate", "sf:slime_leggings", "sf:slime_boots");
        register(registry, "sword_of_beheading", "Sword of Beheading", 6, 15, "sf:sword_of_beheading");
        register(registry, "basic_circuit_board", "Electric Work", 8, 16, "sf:basic_circuit_board");
        register(registry, "advanced_circuit_board", "Advanced Electricity", 9, 17, "sf:advanced_circuit_board");
        register(registry, "smeltery", "Hot Smelting", 10, 18, "sf:smeltery");
        register(registry, "steel", "Steel Age", 11, 19, "sf:steel_ingot");
        register(registry, "misc_power_items", "Important Power-Related Items", 12, 20, "sf:sulfate", "sf:power_crystal");
        register(registry, "battery", "Your first Battery", 10, 21, "sf:battery");
        register(registry, "steel_plate", "Steel Plating", 14, 22, "sf:steel_plate");
        register(registry, "steel_thruster", "Steel Thruster", 14, 23, "sf:steel_thruster");
        register(registry, "parachute", "Parachute", 15, 24, "sf:parachute");
        register(registry, "grappling_hook", "Grappling Hook", 15, 25, "sf:grappling_hook", "sf:hook", "sf:chain");
        register(registry, "jetpacks", "Jetpacks", 22, 26, "sf:duralumin_jetpack", "sf:billon_jetpack", "sf:solder_jetpack", "sf:steel_jetpack", "sf:damascus_steel_jetpack", "sf:reinforced_alloy_jetpack", "sf:carbonado_jetpack", "sf:armored_jetpack");
        register(registry, "multitools", "Multi Tools", 18, 27, "sf:duralumin_multi_tool", "sf:solder_multi_tool", "sf:billon_multi_tool", "sf:steel_multi_tool", "sf:damascus_steel_multi_tool", "sf:reinforced_alloy_multi_tool", "sf:carbonado_multi_tool");
        register(registry, "solar_panel_and_helmet", "Solar Power", 17, 28, "sf:solar_panel", "sf:solar_helmet");
        register(registry, "elemental_staff", "Elemental Staves", 17, 29, "sf:staff_elemental", "sf:staff_elemental_wind", "sf:staff_elemental_fire", "sf:staff_elemental_water", "sf:staff_elemental_storm");
        register(registry, "grind_stone", "Grind Stone", 4, 30, "sf:grind_stone");
        register(registry, "cactus_armor", "Cactus Suit", 5, 31, "sf:cactus_boots", "sf:cactus_chestplate", "sf:cactus_helmet", "sf:cactus_leggings");
        register(registry, "gold_pan", "Gold Pan", 5, 32, "sf:gold_pan");
        register(registry, "magical_book_cover", "Magical Book Binding", 5, 33, "sf:magical_book_cover");
        register(registry, "slimefun_metals", "New Metals", 6, 34, "sf:copper_ingot", "sf:tin_ingot", "sf:silver_ingot", "sf:lead_ingot", "sf:aluminum_ingot", "sf:zinc_ingot", "sf:magnesium_ingot");
        register(registry, "ore_crusher", "Ore Doubling", 6, 35, "sf:ore_crusher");
        register(registry, "bronze", "Bronze Creation", 8, 36, "sf:bronze_ingot");
        register(registry, "alloys", "Advanced Alloys", 12, 37, "sf:billon_ingot", "sf:duralumin_ingot", "sf:aluminum_brass_ingot", "sf:aluminum_bronze_ingot", "sf:solder_ingot", "sf:corinthian_bronze_ingot", "sf:brass_ingot");
        register(registry, "compressor_and_carbon", "Carbon Creation", 9, 38, "sf:compressor", "sf:carbon");
        register(registry, "gilded_iron_armor", "Gilded Iron Armor", 16, 40, "sf:gilded_iron_helmet", "sf:gilded_iron_chestplate", "sf:gilded_iron_leggings", "sf:gilded_iron_boots");
        register(registry, "synthetic_diamond", "Synthetic Diamonds", 10, 41, "sf:compressed_carbon", "sf:carbon_chunk", "sf:synthetic_diamond");
        register(registry, "pressure_chamber", "Pressure Chamber", 14, 42, "sf:pressure_chamber");
        register(registry, "synthetic_sapphire", "Synthetic Sapphires", 16, 43, "sf:synthetic_sapphire");
        register(registry, "damascus_steel", "Damascus Steel", 17, 45, "sf:damascus_steel_ingot");
        register(registry, "damascus_steel_armor", "Damascus Steel Armor", 18, 46, "sf:damascus_steel_helmet", "sf:damascus_steel_chestplate", "sf:damascus_steel_leggings", "sf:damascus_steel_boots");
        register(registry, "reinforced_alloy", "Reinforced Alloy", 22, 47, "sf:hardened_metal_ingot", "sf:reinforced_alloy_ingot");
        register(registry, "carbonado", "Black Diamonds", 26, 48, "sf:raw_carbonado", "sf:carbonado");
        register(registry, "magic_workbench", "Magic Workbench", 12, 50, "sf:magic_workbench");
        register(registry, "reinforced_armor", "Reinforced Armor", 26, 52, "sf:reinforced_alloy_helmet", "sf:reinforced_alloy_chestplate", "sf:reinforced_alloy_leggings", "sf:reinforced_alloy_boots");
        register(registry, "ore_washer", "Ore Washer", 5, 53, "sf:ore_washer", "sf:stone_chunk", "sf:sifted_ore");
        register(registry, "gold_carats", "Pure Gold", 7, 54, "sf:gold_4k", "sf:gold_6k", "sf:gold_8k", "sf:gold_10k", "sf:gold_12k", "sf:gold_14k", "sf:gold_16k", "sf:gold_18k", "sf:gold_20k", "sf:gold_22k", "sf:gold_24k");
        register(registry, "silicon", "Silicon Valley", 12, 55, "sf:silicon", "sf:ferrosilicon");
        register(registry, "smelters_pickaxe", "Smelters Pickaxe", 17, 57, "sf:smelters_pickaxe");

        register(registry, "common_talisman", "Common Talisman", 14, 58, "sf:common_talisman");
        register(registry, "anvil_talisman", "Talisman of the Anvil", 18, 59, "sf:anvil_talisman");
        register(registry, "miner_talisman", "Talisman of the Miner", 18, 60, "sf:miner_talisman");
        register(registry, "hunter_talisman", "Talisman of the Hunter", 18, 61, "sf:hunter_talisman");
        register(registry, "lava_talisman", "Talisman of the Lava Walker", 18, 62, "sf:lava_talisman");
        register(registry, "water_talisman", "Talisman of the Water Breather", 18, 63, "sf:water_talisman");
        register(registry, "angel_talisman", "Talisman of the Angel", 18, 64, "sf:angel_talisman");
        register(registry, "fire_talisman", "Talisman of the Firefighter", 18, 65, "sf:fire_talisman");
        register(registry, "magician_talisman", "Talisman of the Magician", 20, 68, "sf:magician_talisman");
        register(registry, "traveller_talisman", "Talisman of the Traveller", 20, 69, "sf:traveller_talisman");
        register(registry, "warrior_talisman", "Talisman of the Warrior", 20, 70, "sf:warrior_talisman");
        register(registry, "knight_talisman", "Talisman of the Knight", 20, 71, "sf:knight_talisman");
        register(registry, "whirlwind_talisman", "Talisman of the Whirlwind", 19, 75, "sf:whirlwind_talisman");
        register(registry, "wizard_talisman", "Talisman of the Wizard", 22, 76, "sf:wizard_talisman");
        register(registry, "farmer_talisman", "Talisman of the Farmer", 18, 280, "sf:farmer_talisman");
        register(registry, "caveman_talisman", "Talisman of the Caveman", 20, 267, "sf:caveman_talisman");
        register(registry, "wise_talisman", "Talisman of the Wise", 20, 271, "sf:wise_talisman");
        register(registry, "ender_talismans", "Ender Talismans", 28, 112,
                "sf:ender_talisman",
                "sf:ender_anvil_talisman",
                "sf:ender_miner_talisman",
                "sf:ender_hunter_talisman",
                "sf:ender_lava_talisman",
                "sf:ender_water_talisman",
                "sf:ender_angel_talisman",
                "sf:ender_fire_talisman",
                "sf:ender_magician_talisman",
                "sf:ender_traveller_talisman",
                "sf:ender_warrior_talisman",
                "sf:ender_knight_talisman",
                "sf:ender_whirlwind_talisman",
                "sf:ender_wizard_talisman",
                "sf:ender_farmer_talisman",
                "sf:ender_caveman_talisman",
                "sf:ender_wise_talisman");

        register(registry, "lumber_axe", "Lumber Axe", 21, 77, "sf:lumber_axe");
        register(registry, "hazmat_suit", "Hazmat Suit", 21, 79, "sf:scuba_helmet", "sf:hazmat_chestplate", "sf:hazmat_leggings", "sf:hazmat_boots");
        register(registry, "uranium", "Radioactive", 30, 80, "sf:tiny_uranium", "sf:small_uranium", "sf:uranium");
        register(registry, "redstone_alloy", "Redstone Alloy", 16, 84, "sf:redstone_alloy");
        register(registry, "first_aid", "First Aid", 2, 86, "sf:cloth", "sf:rag", "sf:bandage", "sf:splint", "sf:tin_can", "sf:vitamins", "sf:medicine");
        register(registry, "gold_armor", "Shiny Armor", 13, 87, "sf:gold_12k_helmet", "sf:gold_12k_chestplate", "sf:gold_12k_leggings", "sf:gold_12k_boots");
        register(registry, "night_vision_googles", "Night Vision Goggles", 10, 89, "sf:night_vision_goggles");
        register(registry, "pickaxe_of_containment", "Pickaxe of Containment", 14, 90, "sf:pickaxe_of_containment", "sf:broken_spawner");
        register(registry, "table_saw", "Table Saw", 4, 92, "sf:table_saw");
        register(registry, "slime_steel_armor", "Slimy Steel Armor", 27, 93, "sf:slime_steel_helmet", "sf:slime_steel_chestplate", "sf:slime_steel_leggings", "sf:slime_steel_boots");
        register(registry, "blade_of_vampires", "Blade of Vampires", 26, 94, "sf:blade_of_vampires");
        register(registry, "composter", "Composting Dirt", 3, 99, "sf:composter");
        register(registry, "farmer_shoes", "Farmer Shoes", 4, 100, "sf:farmer_shoes");
        register(registry, "explosive_tools", "Explosive Tools", 30, 101, "sf:explosive_pickaxe", "sf:explosive_shovel");
        register(registry, "automated_panning_machine", "Automated Gold Pan", 17, 102, "sf:automated_panning_machine");
        register(registry, "boots_of_the_stomper", "Boots of the Stomper", 19, 103, "sf:boots_of_the_stomper");
        register(registry, "pickaxe_of_the_seeker", "Pickaxe of the Seeker", 19, 104, "sf:pickaxe_of_the_seeker");

        register(registry, "backpacks", "Backpacks", 15, 105, "sf:small_backpack", "sf:medium_backpack", "sf:large_backpack");
        register(registry, "woven_backpack", "Woven Backpack", 19, 106, "sf:woven_backpack");
        register(registry, "gilded_backpack", "Gilded Backpack", 22, 108, "sf:gilded_backpack");
        register(registry, "bound_backpack", "Soulbound Storage", 22, 120, "sf:bound_backpack");
        register(registry, "cooler", "Portable Beverages", 24, 150, "sf:cooler");
        register(registry, "radiant_backpack", "Radiant Backpack", 25, 242, "sf:radiant_backpack");

        register(registry, "bound_weapons", "Soulbound Weapons", 29, 125, "sf:soulbound_sword", "sf:soulbound_bow", "sf:soulbound_trident");
        register(registry, "bound_tools", "Soulbound Tools", 29, 126, "sf:soulbound_pickaxe", "sf:soulbound_axe", "sf:soulbound_shovel", "sf:soulbound_hoe");
        register(registry, "bound_armor", "Soulbound Armor", 29, 127, "sf:soulbound_helmet", "sf:soulbound_chestplate", "sf:soulbound_leggings", "sf:soulbound_boots");
        register(registry, "special_elytras", "Special Elytras", 30, 229, "sf:infused_elytra", "sf:soulbound_elytra");

        register(registry, "repaired_spawner", "Repairing Spawners", 15, 130, "sf:repaired_spawner");
        register(registry, "enhanced_furnace", "Enhanced Furnace", 7, 132, "sf:enhanced_furnace", "sf:enhanced_furnace_2");
        register(registry, "more_enhanced_furnaces", "Better Furnaces", 18, 133, "sf:enhanced_furnace_3", "sf:enhanced_furnace_4", "sf:enhanced_furnace_5", "sf:enhanced_furnace_6", "sf:enhanced_furnace_7");
        register(registry, "high_tier_enhanced_furnaces", "High Tier Furnace", 29, 134, "sf:enhanced_furnace_8", "sf:enhanced_furnace_9", "sf:enhanced_furnace_10", "sf:enhanced_furnace_11");
        register(registry, "reinforced_furnace", "Reinforced Furnace", 32, 135, "sf:reinforced_furnace");
        register(registry, "carbonado_furnace", "Carbonado Edged Furnace", 35, 136, "sf:carbonado_edged_furnace");
        register(registry, "electric_motor", "Heating up", 32, 137, "sf:electro_magnet", "sf:electric_motor", "sf:heating_coil");
        register(registry, "scroll_of_dimensional_teleposition", "Turning things around", 38, 142, "sf:scroll_of_dimensional_teleposition");
        register(registry, "special_bows", "Robin Hood", 22, 143, "sf:explosive_bow", "sf:icy_bow");
        register(registry, "tome_of_knowledge_sharing", "Sharing with friends", 30, 144, "sf:tome_of_knowledge_sharing");
        register(registry, "flask_of_knowledge", "XP Storage", 13, 145, "sf:flask_of_knowledge");
        register(registry, "hardened_glass", "Withstanding Explosions", 15, 146, "sf:reinforced_plate", "sf:hardened_glass");
        register(registry, "ancient_altar", "Ancient Altar", 15, 151, "sf:ancient_pedestal", "sf:ancient_altar");
        register(registry, "wither_proof_obsidian", "Wither-Proof Obsidian", 21, 152, "sf:wither_proof_obsidian");
        register(registry, "ancient_runes", "Elemental Runes", 15, 155, "sf:blank_rune", "sf:earth_rune", "sf:water_rune", "sf:air_rune", "sf:fire_rune");
        register(registry, "special_runes", "Purple Runes", 18, 156, "sf:ender_rune", "sf:rainbow_rune");
        register(registry, "infernal_bonemeal", "Infernal Bonemeal", 12, 157, "sf:infernal_bonemeal");
        register(registry, "infused_hopper", "Infused Hopper", 22, 159, "sf:infused_hopper");
        register(registry, "duct_tape", "Duct Tape", 14, 161, "sf:duct_tape");
        register(registry, "hologram_projector", "Holograms", 36, 166, "sf:hologram_projector");
        register(registry, "capacitors", "Tier 1 Capacitors", 25, 167, "sf:small_capacitor", "sf:medium_capacitor", "sf:big_capacitor");
        register(registry, "high_tier_capacitors", "Tier 2 Capacitors", 32, 168, "sf:large_capacitor", "sf:carbonado_edged_capacitor");
        register(registry, "solar_generators", "Solar Power Plant", 14, 169, "sf:solar_generator");
        register(registry, "electric_furnaces", "Powered Furnace", 15, 170, "sf:electric_furnace");
        register(registry, "electric_ore_grinding", "Crushing and Grinding", 20, 171, "sf:electric_ore_grinder", "sf:electric_ingot_pulverizer");
        register(registry, "heated_pressure_chamber", "Heated Pressure Chamber", 22, 172, "sf:heated_pressure_chamber");
        register(registry, "coal_generator", "Coal Generator", 14, 173, "sf:coal_generator");
        register(registry, "bio_reactor", "Bio-Reactor", 18, 173, "sf:bio_reactor");
        register(registry, "auto_enchanting", "Automatic Enchanting and Disenchanting", 24, 174, "sf:auto_enchanter", "sf:auto_disenchanter");
        register(registry, "auto_anvil", "Automatic Anvil", 34, 175, "sf:auto_anvil", "sf:auto_anvil_2");
        register(registry, "multimeter", "Power Measurement", 10, 176, "sf:multimeter");
        register(registry, "gps_setup", "Basic GPS Setup", 28, 177, "sf:gps_transmitter", "sf:gps_control_panel", "sf:gps_marker_tool");
        register(registry, "gps_emergency_transmitter", "GPS Emergency Waypoint", 30, 178, "sf:gps_emergency_transmitter");
        register(registry, "geo_scanner", "GEO-Scans", 30, 181, "sf:gps_geo_scanner", "sf:portable_geo_scanner");
        register(registry, "teleporter", "Teleporter Base Components", 42, 183, "sf:gps_teleportation_matrix", "sf:gps_teleporter_pylon");
        register(registry, "teleporter_activation_plates", "Teleporter Activation", 36, 184, "sf:gps_activation_device_personal", "sf:gps_activation_device_shared");
        register(registry, "portable_teleporter", "Teleportation from Anywhere", 42, 278, "sf:portable_teleporter");
        register(registry, "energy_regulator", "Energy Networks 101", 6, 190, "sf:energy_regulator");
        register(registry, "cargo_basics", "Cargo Basics", 30, 205, "sf:cargo_motor", "sf:cargo_manager", "sf:cargo_connector_node");
        register(registry, "cargo_nodes", "Cargo Setup", 30, 206, "sf:cargo_input_node", "sf:cargo_output_node");
        register(registry, "electric_ingot_machines", "Electric Ingot Fabrication", 18, 207, "sf:electric_gold_pan", "sf:electric_dust_washer", "sf:electric_ingot_factory");
        register(registry, "medium_tier_electric_ingot_machines", "Fast Ingot Fabrication", 25, 208, "sf:electric_gold_pan_2", "sf:electric_dust_washer_2", "sf:electric_ingot_factory_2", "sf:electric_ore_grinder_2");
        register(registry, "high_tier_electric_ingot_machines", "Super Fast Ingot Fabrication", 32, 209, "sf:electric_gold_pan_3", "sf:electric_dust_washer_3", "sf:electric_ingot_factory_3", "sf:electric_ore_grinder_3");
        register(registry, "electric_smeltery", "Electric Smeltery", 28, 219, "sf:electric_smeltery");
        register(registry, "better_electric_furnace", "Upgraded Electric Furnace", 20, 220, "sf:electric_furnace_2", "sf:electric_furnace_3");
        register(registry, "electric_crucible", "Electrified Crucible", 26, 230, "sf:electrified_crucible");
        register(registry, "advanced_electric_smeltery", "Advanced Electric Smeltery", 28, 232, "sf:electric_smeltery_2");
        register(registry, "charging_bench", "Charging Bench", 8, 250, "sf:charging_bench");
        register(registry, "nether_gold_pan", "Nether Gold Pan", 8, 251, "sf:nether_gold_pan");
        register(registry, "electric_press", "Electric Press", 16, 252, "sf:electric_press", "sf:electric_press_2");
        register(registry, "makeshift_smeltery", "Improvised Smeltery", 6, 255, "sf:makeshift_smeltery");
        register(registry, "magical_zombie_pills", "De-Zombification", 22, 257, "sf:magical_zombie_pills");
        register(registry, "tape_measure", "Tape Measure", 7, 261, "sf:tape_measure");
        register(registry, "climbing_pick", "Block Raider", 20, 265, "sf:climbing_pick");
        register(registry, "energy_connectors", "Wired Connections", 12, 269, "sf:energy_connector");
        register(registry, "book_binder", "Enchantment Book Binding", 26, 272, "sf:book_binder");

        registerRemaining(registry, items);
    }

    private static void registerRemaining(SfxResearchRegistry registry, Collection<SfxItemDefinition> items) {
        for (SfxItemDefinition item : items) {
            if (item.hidden() || !item.giveable()) {
                continue;
            }
            if (!item.flags().contains("legacy-sf")) {
                continue;
            }
            if (registry.byItemId(item.id()).isPresent()) {
                continue;
            }
            String name = PLAIN.serialize(item.name());
            register(registry, autoResearchId(item.id()), name, fallbackCost(item), 10_000 + Math.max(0, item.order()), item.id());
        }
    }

    private static String autoResearchId(String itemId) {
        return "auto_" + itemId.toLowerCase(Locale.ROOT).replace(':', '_').replace('/', '_').replace('.', '_').replace('-', '_');
    }

    private static int fallbackCost(SfxItemDefinition item) {
        String category = item.categoryId() == null ? "" : item.categoryId();
        if (category.contains("electricity") || category.contains("android") || category.contains("cargo") || category.contains("gps")) {
            return 30;
        }
        if (item.flags().contains("talisman") || item.id().contains("soulbound") || item.id().contains("jetpack") || item.id().contains("jetboots")) {
            return 22;
        }
        if (category.contains("machines") || category.contains("multiblock")) {
            return 12;
        }
        return 8;
    }

    private static void register(SfxResearchRegistry registry, String id, String name, int cost, int order, String... itemIds) {
        registry.register(SfxResearchDefinition.of(id, name, cost, order, itemIds));
    }
}
