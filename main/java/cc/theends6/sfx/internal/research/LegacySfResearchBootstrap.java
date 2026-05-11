package cc.theends6.sfx.internal.research;

public final class LegacySfResearchBootstrap {
    private LegacySfResearchBootstrap() {
    }

    public static void register(SfxResearchRegistry registry) {
        registry.clear();

        register(registry, "portable_crafter", "Portable Crafter", 1, 1, "sf:portable_crafter");
        register(registry, "portable_dustbin", "Portable Dustbin", 2, 2, "sf:portable_dustbin");
        register(registry, "ender_backpack", "Ender Backpack", 4, 9, "sf:ender_backpack");
        register(registry, "magic_eye_of_ender", "Magic Eye of Ender", 4, 11, "sf:magic_eye_of_ender");
        register(registry, "grappling_hook", "Grappling Hook", 15, 25, "sf:grappling_hook");
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

        register(registry, "backpacks", "Backpacks", 15, 105,
                "sf:small_backpack", "sf:medium_backpack", "sf:large_backpack");
        register(registry, "woven_backpack", "Woven Backpack", 19, 106, "sf:woven_backpack");
        register(registry, "gilded_backpack", "Gilded Backpack", 22, 108, "sf:gilded_backpack");
        register(registry, "bound_backpack", "Soulbound Storage", 22, 120, "sf:bound_backpack");
        register(registry, "cooler", "Portable Beverages", 24, 150, "sf:cooler");
        register(registry, "radiant_backpack", "Radiant Backpack", 25, 242, "sf:radiant_backpack");
        register(registry, "restored_backpack", "Restored Backpack", 20, 121, "sf:restored_backpack");

        register(registry, "bound_weapons", "Soulbound Weapons", 29, 125,
                "sf:soulbound_sword", "sf:soulbound_bow", "sf:soulbound_trident");
        register(registry, "bound_tools", "Soulbound Tools", 29, 126,
                "sf:soulbound_pickaxe", "sf:soulbound_axe", "sf:soulbound_shovel", "sf:soulbound_hoe");
        register(registry, "bound_armor", "Soulbound Armor", 29, 127,
                "sf:soulbound_helmet", "sf:soulbound_chestplate", "sf:soulbound_leggings", "sf:soulbound_boots");
        register(registry, "special_elytras", "Special Elytras", 30, 229, "sf:soulbound_elytra");
    }

    private static void register(SfxResearchRegistry registry, String id, String name, int cost, int order, String... itemIds) {
        registry.register(SfxResearchDefinition.of(id, name, cost, order, itemIds));
    }
}
