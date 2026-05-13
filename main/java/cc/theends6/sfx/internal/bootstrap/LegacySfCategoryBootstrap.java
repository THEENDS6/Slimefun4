package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemRegistry;
import org.bukkit.Material;

final class LegacySfCategoryBootstrap {
    private LegacySfCategoryBootstrap() {
    }

    static void register(SfxItemRegistry registry) {
        LegacySfBootstrapSupport.registerCategory(registry, "sf:items", "&fItems", Material.PLAYER_HEAD, "&6Portable Crafter", "72ec4a4bd8a58f8361f8a0303e2199d33d624ea5f92f7cb3414fee95e2d861", null, 1000);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:gadgets", "&fGadgets", Material.BOWL, "&6Gold Pan", null, null, 1010);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:backpacks", "&fBackpacks", Material.PLAYER_HEAD, "&eSmall Backpack", "40cb1e67b512ab2d4bf3d7ace0eaaf61c32cd4681ddc3987ceb326706a33fa", null, 1020);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:jetpacks", "&fJetpacks", Material.LEATHER_CHESTPLATE, "&9Electric Jetpack &7- &eI", null, 0x9D9D97, 1030);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:jetboots", "&fJetboots", Material.LEATHER_BOOTS, "&9Jet Boots &7- &eI", null, 0x9D9D97, 1040);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:multi_tools", "&fMulti Tools", Material.SHEARS, "&9Multi Tool &7- &eI", null, null, 1050);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:food", "&fFood", Material.COOKIE, "&6Fortune Cookie", null, null, 1060);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:christmas", "&fChristmas", Material.POTION, "&6Glass of Milk", null, 0xF9FFFE, 1070);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:easter", "&fEaster", Material.PLAYER_HEAD, "&fEaster Egg", "b2cd5df9d7f1fa8341fcce2f3c118e2f517e4d2d99df2c51d61d93ed7f83e13", null, 1080);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:weapons", "&fWeapons", Material.STICK, "&7Grandmas Walking Stick", null, null, 1090);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:bows", "&fBows", Material.BOW, "&cExplosive Bow", null, null, 1100);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:tools", "&fTools", Material.DIAMOND_PICKAXE, "&6Smelter's Pickaxe", null, null, 1110);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:armor", "&fArmor", Material.LEATHER_HELMET, "&e&lGlowstone Helmet", null, 0xFED83D, 1120);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:magical_components", "&fMagical components", Material.GOLD_NUGGET, "&6Magical Lump &7- &eI", null, null, 1130);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:technical_components", "&fTechnical components", Material.PLAYER_HEAD, "&cHeating Coil", "7e3bc4893ba41a3f73ee28174cdf4fef6b145e41fe6c82cb7be8d8e9771a5", null, 1140);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:resources", "&fResources", Material.BUCKET, "&fBucket of Oil", null, null, 1145);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:rainbow_blocks", "&fRainbow blocks", Material.WHITE_WOOL, "&5Rainbow Wool", null, null, 1150);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:seasonal", "&fSeasonal", Material.WHITE_WOOL, "&5Rainbow Wool &7(Christmas)", null, null, 1160);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:ingots", "&fIngots", Material.BRICK, "&bCopper Ingot", null, null, 1170);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_carbon_iron", "&fAlloy (Carbon + Iron)", Material.IRON_INGOT, "&bSteel Ingot", null, null, 1180);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_copper_tin", "&fAlloy (Copper + Tin)", Material.BRICK, "&bBronze Ingot", null, null, 1190);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_copper_aluminum", "&fAlloy (Copper + Aluminum)", Material.IRON_INGOT, "&bDuralumin Ingot", null, null, 1200);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_copper_silver", "&fAlloy (Copper + Silver)", Material.IRON_INGOT, "&bBillon Ingot", null, null, 1210);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_copper_zinc", "&fAlloy (Copper + Zinc)", Material.GOLD_INGOT, "&bBrass Ingot", null, null, 1220);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_aluminum_brass", "&fAlloy (Aluminum + Brass)", Material.GOLD_INGOT, "&bAluminum Brass Ingot", null, null, 1230);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_aluminum_bronze", "&fAlloy (Aluminum + Bronze)", Material.GOLD_INGOT, "&bAluminum Bronze Ingot", null, null, 1240);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_gold_silver_copper", "&fAlloy (Gold + Silver + Copper)", Material.GOLD_INGOT, "&bCorinthian Bronze Ingot", null, null, 1250);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_lead_tin", "&fAlloy (Lead + Tin)", Material.IRON_INGOT, "&bSolder Ingot", null, null, 1260);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_steel_iron_carbon", "&fAlloy (Steel + Iron + Carbon)", Material.IRON_INGOT, "&bDamascus Steel Ingot", null, null, 1270);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_damascus_steel_duralumin_compressed_carbon_aluminium_bronze", "&fAlloy (Damascus Steel + Duralumin + Compressed Carbon + Aluminium Bronze)", Material.IRON_INGOT, "&b&lHardened Metal", null, null, 1280);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_hardened_metal_corinthian_bronze_solder_billon_damascus_steel", "&fAlloy (Hardened Metal + Corinthian Bronze + Solder + Billon + Damascus Steel)", Material.IRON_INGOT, "&b&lReinforced Alloy Ingot", null, null, 1290);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_iron_silicon", "&fAlloy (Iron + Silicon)", Material.IRON_INGOT, "&bFerrosilicon", null, null, 1300);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_iron_gold", "&fAlloy (Iron + Gold)", Material.GOLD_INGOT, "&6&lGilded Iron", null, null, 1310);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_redstone_ferrosilicon", "&fAlloy (Redstone + Ferrosilicon)", Material.BRICK, "&cRedstone Alloy Ingot", null, null, 1320);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_iron_copper", "&fAlloy (Iron + Copper)", Material.IRON_INGOT, "&bNickel Ingot", null, null, 1330);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:alloy_nickel_iron_copper", "&fAlloy (Nickel + Iron + Copper)", Material.IRON_INGOT, "&9Cobalt Ingot", null, null, 1340);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:gold", "&fGold", Material.GOLD_INGOT, "&fGold Ingot &7(4-Carat)", null, null, 1350);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:dusts", "&fDusts", Material.GUNPOWDER, "&6Iron Dust", null, null, 1360);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:gems", "&fGems", Material.DIAMOND, "&bSynthetic Diamond", null, null, 1370);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:talisman", "&fTalisman", Material.EMERALD, "&6Common Talisman", null, null, 1380);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:staves", "&fStaves", Material.STICK, "&6Elemental Staff", null, null, 1390);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:multiblocks", "&fMultiblocks", Material.CRAFTING_TABLE, "&eEnhanced Crafting Table", null, null, 1400);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:machines", "&fMachines", Material.CAULDRON, "&aComposter", null, null, 1410);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:enhanced_furnaces", "&fEnhanced Furnaces", Material.FURNACE, "&7Enhanced Furnace - &eI", null, null, 1420);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:soulbound_items", "&fSoulbound Items", Material.DIAMOND_SWORD, "&cSoulbound Sword", null, null, 1430);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:runes", "&fRunes", Material.FIREWORK_STAR, "&fBlank Rune", null, 0x1D1D21, 1440);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:electricity", "&fElectricity", Material.DAYLIGHT_DETECTOR, "&bSolar Generator", null, null, 1450);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:cargo", "&fCargo", Material.HOPPER, "&6Cargo Node", null, null, 1455);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:robots", "&fRobots", Material.PLAYER_HEAD, "&cProgrammable Android &7(Normal)", "3503cb7ed845e7a507f569afc647c47ac483771465c9a679a54594c76afba", null, 1460);
        LegacySfBootstrapSupport.registerCategory(registry, "sf:gps", "&fGPS", Material.PLAYER_HEAD, "&bGPS Transmitter", "b0c9c1a022f40b73f14b4cba37c718c6a533f3a2864b6536d5f456934cc1f", null, 1470);
    }
}
