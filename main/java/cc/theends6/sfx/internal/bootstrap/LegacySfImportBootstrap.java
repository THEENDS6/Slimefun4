package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.Text;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

public final class LegacySfImportBootstrap {
    private static final Map<String, Map<String, Integer>> LEGACY_ENCHANTMENTS = createLegacyEnchantments();

    private LegacySfImportBootstrap() {}

    public static void register(SfxItemRegistry registry) {
        registry.registerCategory(new SfxItemCategory("sf:items", Text.legacy("&fItems"), icon(Material.PLAYER_HEAD, Text.legacy("&6Portable Crafter"), "72ec4a4bd8a58f8361f8a0303e2199d33d624ea5f92f7cb3414fee95e2d861", null), 1000, false));
        registry.registerCategory(new SfxItemCategory("sf:gadgets", Text.legacy("&fGadgets"), icon(Material.BOWL, Text.legacy("&6Gold Pan"), null, null), 1010, false));
        registry.registerCategory(new SfxItemCategory("sf:backpacks", Text.legacy("&fBackpacks"), icon(Material.PLAYER_HEAD, Text.legacy("&eSmall Backpack"), "40cb1e67b512ab2d4bf3d7ace0eaaf61c32cd4681ddc3987ceb326706a33fa", null), 1020, false));
        registry.registerCategory(new SfxItemCategory("sf:jetpacks", Text.legacy("&fJetpacks"), icon(Material.LEATHER_CHESTPLATE, Text.legacy("&9Electric Jetpack &7- &eI"), null, 0x9D9D97), 1030, false));
        registry.registerCategory(new SfxItemCategory("sf:jetboots", Text.legacy("&fJetboots"), icon(Material.LEATHER_BOOTS, Text.legacy("&9Jet Boots &7- &eI"), null, 0x9D9D97), 1040, false));
        registry.registerCategory(new SfxItemCategory("sf:multi_tools", Text.legacy("&fMulti Tools"), icon(Material.SHEARS, Text.legacy("&9Multi Tool &7- &eI"), null, null), 1050, false));
        registry.registerCategory(new SfxItemCategory("sf:food", Text.legacy("&fFood"), icon(Material.COOKIE, Text.legacy("&6Fortune Cookie"), null, null), 1060, false));
        registry.registerCategory(new SfxItemCategory("sf:christmas", Text.legacy("&fChristmas"), icon(Material.POTION, Text.legacy("&6Glass of Milk"), null, 0xF9FFFE), 1070, false));
        registry.registerCategory(new SfxItemCategory("sf:easter", Text.legacy("&fEaster"), icon(Material.PLAYER_HEAD, Text.legacy("&fEaster Egg"), "b2cd5df9d7f1fa8341fcce2f3c118e2f517e4d2d99df2c51d61d93ed7f83e13", null), 1080, false));
        registry.registerCategory(new SfxItemCategory("sf:weapons", Text.legacy("&fWeapons"), icon(Material.STICK, Text.legacy("&7Grandmas Walking Stick"), null, null), 1090, false));
        registry.registerCategory(new SfxItemCategory("sf:bows", Text.legacy("&fBows"), icon(Material.BOW, Text.legacy("&cExplosive Bow"), null, null), 1100, false));
        registry.registerCategory(new SfxItemCategory("sf:tools", Text.legacy("&fTools"), icon(Material.DIAMOND_PICKAXE, Text.legacy("&6Smelter's Pickaxe"), null, null), 1110, false));
        registry.registerCategory(new SfxItemCategory("sf:armor", Text.legacy("&fArmor"), icon(Material.LEATHER_HELMET, Text.legacy("&e&lGlowstone Helmet"), null, 0xFED83D), 1120, false));
        registry.registerCategory(new SfxItemCategory("sf:magical_components", Text.legacy("&fMagical components"), icon(Material.GOLD_NUGGET, Text.legacy("&6Magical Lump &7- &eI"), null, null), 1130, false));
        registry.registerCategory(new SfxItemCategory("sf:technical_components", Text.legacy("&fTechnical components"), icon(Material.ACTIVATOR_RAIL, Text.legacy("&bBasic Circuit Board"), null, null), 1140, false));
        registry.registerCategory(new SfxItemCategory("sf:resources", Text.legacy("&fResources"), icon(Material.BUCKET, Text.legacy("&fBucket of Oil"), null, null), 1145, false));
        registry.registerCategory(new SfxItemCategory("sf:rainbow_blocks", Text.legacy("&fRainbow blocks"), icon(Material.WHITE_WOOL, Text.legacy("&5Rainbow Wool"), null, null), 1150, false));
        registry.registerCategory(new SfxItemCategory("sf:seasonal", Text.legacy("&fSeasonal"), icon(Material.WHITE_WOOL, Text.legacy("&5Rainbow Wool &7(Christmas)"), null, null), 1160, false));
        registry.registerCategory(new SfxItemCategory("sf:ingots", Text.legacy("&fIngots"), icon(Material.BRICK, Text.legacy("&bCopper Ingot"), null, null), 1170, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_carbon_iron", Text.legacy("&fAlloy (Carbon + Iron)"), icon(Material.IRON_INGOT, Text.legacy("&bSteel Ingot"), null, null), 1180, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_copper_tin", Text.legacy("&fAlloy (Copper + Tin)"), icon(Material.BRICK, Text.legacy("&bBronze Ingot"), null, null), 1190, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_copper_aluminum", Text.legacy("&fAlloy (Copper + Aluminum)"), icon(Material.IRON_INGOT, Text.legacy("&bDuralumin Ingot"), null, null), 1200, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_copper_silver", Text.legacy("&fAlloy (Copper + Silver)"), icon(Material.IRON_INGOT, Text.legacy("&bBillon Ingot"), null, null), 1210, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_copper_zinc", Text.legacy("&fAlloy (Copper + Zinc)"), icon(Material.GOLD_INGOT, Text.legacy("&bBrass Ingot"), null, null), 1220, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_aluminum_brass", Text.legacy("&fAlloy (Aluminum + Brass)"), icon(Material.GOLD_INGOT, Text.legacy("&bAluminum Brass Ingot"), null, null), 1230, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_aluminum_bronze", Text.legacy("&fAlloy (Aluminum + Bronze)"), icon(Material.GOLD_INGOT, Text.legacy("&bAluminum Bronze Ingot"), null, null), 1240, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_gold_silver_copper", Text.legacy("&fAlloy (Gold + Silver + Copper)"), icon(Material.GOLD_INGOT, Text.legacy("&bCorinthian Bronze Ingot"), null, null), 1250, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_lead_tin", Text.legacy("&fAlloy (Lead + Tin)"), icon(Material.IRON_INGOT, Text.legacy("&bSolder Ingot"), null, null), 1260, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_steel_iron_carbon", Text.legacy("&fAlloy (Steel + Iron + Carbon)"), icon(Material.IRON_INGOT, Text.legacy("&bDamascus Steel Ingot"), null, null), 1270, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_damascus_steel_duralumin_compressed_carbon_aluminium_bronze", Text.legacy("&fAlloy (Damascus Steel + Duralumin + Compressed Carbon + Aluminium Bronze)"), icon(Material.IRON_INGOT, Text.legacy("&b&lHardened Metal"), null, null), 1280, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_hardened_metal_corinthian_bronze_solder_billon_damascus_steel", Text.legacy("&fAlloy (Hardened Metal + Corinthian Bronze + Solder + Billon + Damascus Steel)"), icon(Material.IRON_INGOT, Text.legacy("&b&lReinforced Alloy Ingot"), null, null), 1290, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_iron_silicon", Text.legacy("&fAlloy (Iron + Silicon)"), icon(Material.IRON_INGOT, Text.legacy("&bFerrosilicon"), null, null), 1300, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_iron_gold", Text.legacy("&fAlloy (Iron + Gold)"), icon(Material.GOLD_INGOT, Text.legacy("&6&lGilded Iron"), null, null), 1310, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_redstone_ferrosilicon", Text.legacy("&fAlloy (Redstone + Ferrosilicon)"), icon(Material.BRICK, Text.legacy("&cRedstone Alloy Ingot"), null, null), 1320, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_iron_copper", Text.legacy("&fAlloy (Iron + Copper)"), icon(Material.IRON_INGOT, Text.legacy("&bNickel Ingot"), null, null), 1330, false));
        registry.registerCategory(new SfxItemCategory("sf:alloy_nickel_iron_copper", Text.legacy("&fAlloy (Nickel + Iron + Copper)"), icon(Material.IRON_INGOT, Text.legacy("&9Cobalt Ingot"), null, null), 1340, false));
        registry.registerCategory(new SfxItemCategory("sf:gold", Text.legacy("&fGold"), icon(Material.GOLD_INGOT, Text.legacy("&fGold Ingot &7(4-Carat)"), null, null), 1350, false));
        registry.registerCategory(new SfxItemCategory("sf:dusts", Text.legacy("&fDusts"), icon(Material.GUNPOWDER, Text.legacy("&6Iron Dust"), null, null), 1360, false));
        registry.registerCategory(new SfxItemCategory("sf:gems", Text.legacy("&fGems"), icon(Material.DIAMOND, Text.legacy("&bSynthetic Diamond"), null, null), 1370, false));
        registry.registerCategory(new SfxItemCategory("sf:talisman", Text.legacy("&fTalisman"), icon(Material.EMERALD, Text.legacy("&6Common Talisman"), null, null), 1380, false));
        registry.registerCategory(new SfxItemCategory("sf:staves", Text.legacy("&fStaves"), icon(Material.STICK, Text.legacy("&6Elemental Staff"), null, null), 1390, false));
        registry.registerCategory(new SfxItemCategory("sf:multiblocks", Text.legacy("&fMultiblocks"), icon(Material.CRAFTING_TABLE, Text.legacy("&eEnhanced Crafting Table"), null, null), 1400, false));
        registry.registerCategory(new SfxItemCategory("sf:machines", Text.legacy("&fMachines"), icon(Material.CAULDRON, Text.legacy("&aComposter"), null, null), 1410, false));
        registry.registerCategory(new SfxItemCategory("sf:enhanced_furnaces", Text.legacy("&fEnhanced Furnaces"), icon(Material.FURNACE, Text.legacy("&7Enhanced Furnace - &eI"), null, null), 1420, false));
        registry.registerCategory(new SfxItemCategory("sf:soulbound_items", Text.legacy("&fSoulbound Items"), icon(Material.DIAMOND_SWORD, Text.legacy("&cSoulbound Sword"), null, null), 1430, false));
        registry.registerCategory(new SfxItemCategory("sf:runes", Text.legacy("&fRunes"), icon(Material.FIREWORK_STAR, Text.legacy("&fBlank Rune"), null, 0x1D1D21), 1440, false));
        registry.registerCategory(new SfxItemCategory("sf:electricity", Text.legacy("&fElectricity"), icon(Material.DAYLIGHT_DETECTOR, Text.legacy("&bSolar Generator"), null, null), 1450, false));
        registry.registerCategory(new SfxItemCategory("sf:cargo", Text.legacy("&fCargo"), icon(Material.HOPPER, Text.legacy("&6Cargo Node"), null, null), 1455, false));
        registry.registerCategory(new SfxItemCategory("sf:robots", Text.legacy("&fRobots"), icon(Material.PLAYER_HEAD, Text.legacy("&cProgrammable Android &7(Normal)"), "3503cb7ed845e7a507f569afc647c47ac483771465c9a679a54594c76afba", null), 1460, false));
        registry.registerCategory(new SfxItemCategory("sf:gps", Text.legacy("&fGPS"), icon(Material.PLAYER_HEAD, Text.legacy("&bGPS Transmitter"), "b0c9c1a022f40b73f14b4cba37c718c6a533f3a2864b6536d5f456934cc1f", null), 1470, false));

        registerLegacyItem(registry, "sf:portable_crafter", "sf:items", Material.PLAYER_HEAD, "&6Portable Crafter", "72ec4a4bd8a58f8361f8a0303e2199d33d624ea5f92f7cb3414fee95e2d861", null, new String[] {"&a&oA portable Crafting Table", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:portable_dustbin", "sf:items", Material.PLAYER_HEAD, "&6Portable Dustbin", "32d41042ce99147cc38cac9e46741576e7ee791283e6fac8d3292cae2935f1f", null, new String[] {"&fYour portable Item-Destroyer", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:ender_backpack", "sf:items", Material.PLAYER_HEAD, "&6Ender Backpack", "2a3b34862b9afb63cf8d5779966d3fba70af82b04e83f3eaf6449aeba", null, new String[] {"&a&oA portable Ender Chest", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:magic_eye_of_ender", "sf:items", Material.ENDER_EYE, "&6&lMagic Eye of Ender", null, null, new String[] {"&4&lRequires full Ender Armor", "", "&7&eRight Click&7 to shoot an Ender Pearl"});
        registerLegacyItem(registry, "sf:broken_spawner", "sf:items", Material.SPAWNER, "&cBroken Spawner", null, null, new String[] {"&7Type: &b<Type>", "", "&cFractured, must be repaired in an Ancient Altar"});
        registerLegacyItem(registry, "sf:reinforced_spawner", "sf:items", Material.SPAWNER, "&bReinforced Spawner", null, null, new String[] {"&7Type: &b<Type>"});
        registerLegacyItem(registry, "sf:infernal_bonemeal", "sf:items", Material.BONE_MEAL, "&4Infernal Bonemeal", null, null, new String[] {"", "&cSpeeds up the Growth of", "&cNether Warts as well"});
        registerLegacyItem(registry, "sf:tape_measure", "sf:items", Material.PLAYER_HEAD, "&6Tape Measure", "180d5c43a6cf5bb7769fd0c8240e1e70d2ae38ef9d78a1db401aca6a2cb36f65", null, new String[] {"", "&eCrouch & Right Click &7to set an anchor", "&eRight Click &7to measure"});
        registerLegacyItem(registry, "sf:gold_pan", "sf:gadgets", Material.BOWL, "&6Gold Pan", null, null, new String[] {"", "&eRight Click&7 to collect resources", "&7from Gravel"});
        registerLegacyItem(registry, "sf:nether_gold_pan", "sf:gadgets", Material.BOWL, "&4Nether Gold Pan", null, null, new String[] {"", "&eRight Click&7 to collect resources", "&7from Soul Sand"});
        registerLegacyItem(registry, "sf:parachute", "sf:gadgets", Material.LEATHER_CHESTPLATE, "&f&lParachute", null, 0xF9FFFE, new String[] {"", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:grappling_hook", "sf:gadgets", Material.LEAD, "&6Grappling Hook", null, null, new String[] {"", "&eRight Click&7 to use"});
        registerLegacyItem(registry, "sf:solar_helmet", "sf:gadgets", Material.IRON_HELMET, "&bSolar Helmet", null, null, new String[] {"", "&a&oCharges held Items and Armor"});
        registerLegacyItem(registry, "sf:cloth", "sf:gadgets", Material.PAPER, "&bCloth", null, null, new String[] {});
        registerLegacyItem(registry, "sf:reinforced_cloth", "sf:gadgets", Material.PAPER, "&bReinforced Cloth", null, null, new String[] {"", "&fThis cloth has been reinforced", "&fwith &bLead &fto protect against", "&fradioactive substances"});
        registerLegacyItem(registry, "sf:can", "sf:gadgets", Material.PLAYER_HEAD, "&fTin Can", "94da97f080e395b842c4cc82a840823d4dbd8ca688a206853e5783e4bfdc012", null, new String[] {});
        registerLegacyItem(registry, "sf:night_vision_goggles", "sf:gadgets", Material.LEATHER_HELMET, "&aNight Vision Goggles", null, 0x1D1D21, new String[] {"", "&9+ Night Vision"});
        registerLegacyItem(registry, "sf:elytra_cap", "sf:gadgets", Material.LEATHER_HELMET, "&5Elytra Cap", null, 0x8932B8, new String[] {"", "&7This helmet will protect you from", "&7crashing while flying with an elytra."});
        registerLegacyItem(registry, "sf:farmer_shoes", "sf:gadgets", Material.LEATHER_BOOTS, "&eFarmer Shoes", null, 0xFED83D, new String[] {"", "&6&oPrevents you from trampling your Crops"});
        registerLegacyItem(registry, "sf:infused_magnet", "sf:gadgets", Material.PLAYER_HEAD, "&aInfused Magnet", "aba8ebc4c6a81730947499bf7e1d5e73fed6c1bb2c051e96d35eb16d24610e7", null, new String[] {"", "&fMagical infused Magnets", "&fattract nearby Items", "&fas long as it is somewhere in", "&fyour Inventory", "", "&7Hold &eShift&7 to pick up nearby Items"});
        registerLegacyItem(registry, "sf:rag", "sf:gadgets", Material.PAPER, "&cRag", null, null, new String[] {"", "&aLevel I - Medical Supply", "", "&fRestores 2 Hearts", "&fExtinguishes Fire", "", "&eRight Click&7 to use"});
        registerLegacyItem(registry, "sf:bandage", "sf:gadgets", Material.PAPER, "&cBandage", null, null, new String[] {"", "&aLevel II - Medical Supply", "", "&fRestores 4 Hearts", "&fExtinguishes Fire", "", "&eRight Click&7 to use"});
        registerLegacyItem(registry, "sf:splint", "sf:gadgets", Material.STICK, "&cSplint", null, null, new String[] {"", "&aLevel I - Medical Supply", "", "&fRestores 2 Hearts", "", "&eRight Click&7 to use"});
        registerLegacyItem(registry, "sf:vitamins", "sf:gadgets", Material.NETHER_WART, "&cVitamins", null, null, new String[] {"", "&aLevel III - Medical Supply", "", "&fRestores 4 Hearts", "&fExtinguishes Fire", "&fCures Poison/Wither/Radiation", "", "&eRight Click&7 to use"});
        registerLegacyItem(registry, "sf:medicine", "sf:gadgets", Material.POTION, "&cMedicine", null, 0xB02E26, new String[] {"", "&aLevel III - Medical Supply", "", "&fRestores 4 Hearts", "&fExtinguishes Fire", "&fCures Poison/Wither/Radiation"});
        registerLegacyItem(registry, "sf:magical_zombie_pills", "sf:gadgets", Material.NETHER_WART, "&6Magical Zombie Pills", null, null, new String[] {"", "&eRight Click &7a Zombified Villager", "&eor &7a Zombified Piglin to", "&7instantly cure it from its curse"});
        registerLegacyItem(registry, "sf:flask_of_knowledge", "sf:gadgets", Material.GLASS_BOTTLE, "&cFlask of Knowledge", null, null, new String[] {"", "&fAllows you to store some of", "&fyour Experience in a Bottle", "&7Cost: &a1 Level"});
        registerLegacyItem(registry, "sf:filled_flask_of_knowledge", "sf:gadgets", Material.EXPERIENCE_BOTTLE, "&aFlask of Knowledge", null, null, new String[] {});
        registerLegacyItem(registry, "sf:small_backpack", "sf:backpacks", Material.PLAYER_HEAD, "&eSmall Backpack", "40cb1e67b512ab2d4bf3d7ace0eaaf61c32cd4681ddc3987ceb326706a33fa", null, new String[] {"", "&7Size: &e9", "&7ID: <ID>", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:medium_backpack", "sf:backpacks", Material.PLAYER_HEAD, "&eBackpack", "40cb1e67b512ab2d4bf3d7ace0eaaf61c32cd4681ddc3987ceb326706a33fa", null, new String[] {"", "&7Size: &e18", "&7ID: <ID>", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:large_backpack", "sf:backpacks", Material.PLAYER_HEAD, "&eLarge Backpack", "40cb1e67b512ab2d4bf3d7ace0eaaf61c32cd4681ddc3987ceb326706a33fa", null, new String[] {"", "&7Size: &e27", "&7ID: <ID>", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:woven_backpack", "sf:backpacks", Material.PLAYER_HEAD, "&eWoven Backpack", "40cb1e67b512ab2d4bf3d7ace0eaaf61c32cd4681ddc3987ceb326706a33fa", null, new String[] {"", "&7Size: &e36", "&7ID: <ID>", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:gilded_backpack", "sf:backpacks", Material.PLAYER_HEAD, "&eGilded Backpack", "40cb1e67b512ab2d4bf3d7ace0eaaf61c32cd4681ddc3987ceb326706a33fa", null, new String[] {"", "&7Size: &e45", "&7ID: <ID>", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:radiant_backpack", "sf:backpacks", Material.PLAYER_HEAD, "&eRadiant Backpack", "40cb1e67b512ab2d4bf3d7ace0eaaf61c32cd4681ddc3987ceb326706a33fa", null, new String[] {"", "&7Size: &e54 (Double chest)", "&7ID: <ID>", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:bound_backpack", "sf:backpacks", Material.PLAYER_HEAD, "&cSoulbound Backpack", "2a3b34862b9afb63cf8d5779966d3fba70af82b04e83f3eaf6449aeba", null, new String[] {"", "&7Size: &e36", "&7ID: <ID>", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:cooler", "sf:backpacks", Material.PLAYER_HEAD, "&bCooler", "d4c1572584eb5de229de9f5a4f779d0aacbaffd33bcb33eb4536a6a2bc6a1", null, new String[] {"&fAllows you to store Juices/Smoothies", "&fand automatically consumes them when you are hungry", "&fand you have this in your Inventory", "", "&7Size: &e27", "&7ID: <ID>", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:restored_backpack", "sf:backpacks", Material.PLAYER_HEAD, "&eRestored Backpack", "9c3681bf8a2738232fb305597f7e2a34a3a5c1356705249e9a365b0bcd04705a", null, new String[] {"", "&7Retrieve your lost items", "&7ID: <ID>", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:duralumin_jetpack", "sf:jetpacks", Material.LEATHER_CHESTPLATE, "&9Electric Jetpack &7- &eI", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bDuralumin", "&8⇨ &e⚡ &70 / 20 J", "&8⇨ &7Thrust: &c0.35", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:solder_jetpack", "sf:jetpacks", Material.LEATHER_CHESTPLATE, "&9Electric Jetpack &7- &eII", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bSolder", "&8⇨ &e⚡ &70 / 30 J", "&8⇨ &7Thrust: &c0.4", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:billon_jetpack", "sf:jetpacks", Material.LEATHER_CHESTPLATE, "&9Electric Jetpack &7- &eIII", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bBillon", "&8⇨ &e⚡ &70 / 45 J", "&8⇨ &7Thrust: &c0.45", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:steel_jetpack", "sf:jetpacks", Material.LEATHER_CHESTPLATE, "&9Electric Jetpack &7- &eIV", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bSteel", "&8⇨ &e⚡ &70 / 60 J", "&8⇨ &7Thrust: &c0.5", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:damascus_steel_jetpack", "sf:jetpacks", Material.LEATHER_CHESTPLATE, "&9Electric Jetpack &7- &eV", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bDamascus Steel", "&8⇨ &e⚡ &70 / 75 J", "&8⇨ &7Thrust: &c0.55", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:reinforced_alloy_jetpack", "sf:jetpacks", Material.LEATHER_CHESTPLATE, "&9Electric Jetpack &7- &eVI", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bReinforced Alloy", "&8⇨ &e⚡ &70 / 100 J", "&8⇨ &7Thrust: &c0.6", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:carbonado_jetpack", "sf:jetpacks", Material.LEATHER_CHESTPLATE, "&9Electric Jetpack &7- &eVII", null, 0x1D1D21, new String[] {"", "&8⇨ &7Material: &bCarbonado", "&8⇨ &e⚡ &70 / 150 J", "&8⇨ &7Thrust: &c0.7", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:armored_jetpack", "sf:jetpacks", Material.IRON_CHESTPLATE, "&9Armored Jetpack", null, null, new String[] {"&8⇨ &7Material: &bSteel", "", "&8⇨ &e⚡ &70 / 50 J", "&8⇨ &7Thrust: &c0.5", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:duralumin_jetboots", "sf:jetboots", Material.LEATHER_BOOTS, "&9Jet Boots &7- &eI", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bDuralumin", "&8⇨ &e⚡ &70 / 20 J", "&8⇨ &b⚡ &7Speed: &b0.35x", "&8⇨ &7Accuracy: &c50%", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:solder_jetboots", "sf:jetboots", Material.LEATHER_BOOTS, "&9Jet Boots &7- &eII", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bSolder", "&8⇨ &e⚡ &70 / 30 J", "&8⇨ &b⚡ &7Speed: &b0.4x", "&8⇨ &7Accuracy: &660%", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:billon_jetboots", "sf:jetboots", Material.LEATHER_BOOTS, "&9Jet Boots &7- &eIII", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bBillon", "&8⇨ &e⚡ &70 / 40 J", "&8⇨ &b⚡ &7Speed: &b0.45x", "&8⇨ &7Accuracy: &665%", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:steel_jetboots", "sf:jetboots", Material.LEATHER_BOOTS, "&9Jet Boots &7- &eIV", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bSteel", "&8⇨ &e⚡ &70 / 50 J", "&8⇨ &b⚡ &7Speed: &b0.5x", "&8⇨ &7Accuracy: &e70%", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:damascus_steel_jetboots", "sf:jetboots", Material.LEATHER_BOOTS, "&9Jet Boots &7- &eV", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bDamascus Steel", "&8⇨ &e⚡ &70 / 75 J", "&8⇨ &b⚡ &7Speed: &b0.55x", "&8⇨ &7Accuracy: &a75%", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:reinforced_alloy_jetboots", "sf:jetboots", Material.LEATHER_BOOTS, "&9Jet Boots &7- &eVI", null, 0x9D9D97, new String[] {"", "&8⇨ &7Material: &bReinforced Alloy", "&8⇨ &e⚡ &70 / 100 J", "&8⇨ &b⚡ &7Speed: &b0.6x", "&8⇨ &7Accuracy: &c80%", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:carbonado_jetboots", "sf:jetboots", Material.LEATHER_BOOTS, "&9Jet Boots &7- &eVII", null, 0x1D1D21, new String[] {"", "&8⇨ &7Material: &bCarbonado", "&8⇨ &e⚡ &70 / 125 J", "&8⇨ &b⚡ &7Speed: &b0.7x", "&8⇨ &7Accuracy: &c99.9%", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:armored_jetboots", "sf:jetboots", Material.IRON_BOOTS, "&9Armored Jet Boots", null, null, new String[] {"", "&8⇨ &7Material: &bSteel", "&8⇨ &e⚡ &70 / 50 J", "&8⇨ &b⚡ &7Speed: &b0.45x", "&8⇨ &7Accuracy: &e70%", "", "&eCrouch&7 to use"});
        registerLegacyItem(registry, "sf:duralumin_multi_tool", "sf:multi_tools", Material.SHEARS, "&9Multi Tool &7- &eI", null, null, new String[] {"", "&8⇨ &7Material: &bDuralumin", "&8⇨ &e⚡ &70 / 20 J", "", "&eRight Click&7 to use", "&eCrouch & Right Click&7 to switch modes"});
        registerLegacyItem(registry, "sf:solder_multi_tool", "sf:multi_tools", Material.SHEARS, "&9Multi Tool &7- &eII", null, null, new String[] {"", "&8⇨ &7Material: &bSolder", "&8⇨ &e⚡ &70 / 30 J", "", "&eRight Click&7 to use", "&eCrouch & Right Click&7 to switch modes"});
        registerLegacyItem(registry, "sf:billon_multi_tool", "sf:multi_tools", Material.SHEARS, "&9Multi Tool &7- &eIII", null, null, new String[] {"", "&8⇨ &7Material: &bBillon", "&8⇨ &e⚡ &70 / 40 J", "", "&eRight Click&7 to use", "&eCrouch & Right Click&7 to switch modes"});
        registerLegacyItem(registry, "sf:steel_multi_tool", "sf:multi_tools", Material.SHEARS, "&9Multi Tool &7- &eIV", null, null, new String[] {"", "&8⇨ &7Material: &bSteel", "&8⇨ &e⚡ &70 / 50 J", "", "&eRight Click&7 to use", "&eCrouch & Right Click&7 to switch modes"});
        registerLegacyItem(registry, "sf:damascus_steel_multi_tool", "sf:multi_tools", Material.SHEARS, "&9Multi Tool &7- &eV", null, null, new String[] {"", "&8⇨ &7Material: &bDamascus Steel", "&8⇨ &e⚡ &70 / 60 J", "", "&eRight Click&7 to use", "&eCrouch & Right Click&7 to switch modes"});
        registerLegacyItem(registry, "sf:reinforced_alloy_multi_tool", "sf:multi_tools", Material.SHEARS, "&9Multi Tool &7- &eVI", null, null, new String[] {"", "&8⇨ &7Material: &bReinforced Alloy", "&8⇨ &e⚡ &70 / 75 J", "", "&eRight Click&7 to use", "&eCrouch & Right Click&7 to switch modes"});
        registerLegacyItem(registry, "sf:carbonado_multi_tool", "sf:multi_tools", Material.SHEARS, "&9Multi Tool &7- &eVII", null, null, new String[] {"", "&8⇨ &7Material: &bCarbonado", "&8⇨ &e⚡ &70 / 100 J", "", "&eRight Click&7 to use", "&eCrouch & Right Click&7 to switch modes"});
        registerLegacyItem(registry, "sf:fortune_cookie", "sf:food", Material.COOKIE, "&6Fortune Cookie", null, null, new String[] {"", "&a&oTells you stuff about your Future :o"});
        registerLegacyItem(registry, "sf:diet_cookie", "sf:food", Material.COOKIE, "&6Diet Cookie", null, null, new String[] {"", "&aA very &olightweight &f&acookie."});
        registerLegacyItem(registry, "sf:magic_sugar", "sf:food", Material.SUGAR, "&6Magic Sugar", null, null, new String[] {"", "&a&oFeel the Power of Hermes!"});
        registerLegacyItem(registry, "sf:monster_jerky", "sf:food", Material.ROTTEN_FLESH, "&6Monster Jerky", null, null, new String[] {"", "&a&oNo longer hungry"});
        registerLegacyItem(registry, "sf:apple_juice", "sf:food", Material.POTION, "&cApple Juice", null, 0xB02E26, new String[] {"", "&7&oRestores &b&o3.0 &7&oHunger"});
        registerLegacyItem(registry, "sf:melon_juice", "sf:food", Material.POTION, "&cMelon Juice", null, 0xB02E26, new String[] {"", "&7&oRestores &b&o3.0 &7&oHunger"});
        registerLegacyItem(registry, "sf:carrot_juice", "sf:food", Material.POTION, "&6Carrot Juice", null, 0xF9801D, new String[] {"", "&7&oRestores &b&o3.0 &7&oHunger"});
        registerLegacyItem(registry, "sf:pumpkin_juice", "sf:food", Material.POTION, "&6Pumpkin Juice", null, 0xF9801D, new String[] {"", "&7&oRestores &b&o3.0 &7&oHunger"});
        registerLegacyItem(registry, "sf:sweet_berry_juice", "sf:food", Material.POTION, "&cSweet Berry Juice", null, 0xB02E26, new String[] {"", "&7&oRestores &b&o3.0 &7&oHunger"});
        registerLegacyItem(registry, "sf:glow_berry_juice", "sf:food", Material.POTION, "&6Glow Berry Juice", null, 0xF9801D, new String[] {"", "&7&oRestores &b&o3.0 &7&oHunger"});
        registerLegacyItem(registry, "sf:golden_apple_juice", "sf:food", Material.POTION, "&bGolden Apple Juice", null, 0xFED83D, new String[] {});
        registerLegacyItem(registry, "sf:beef_jerky", "sf:food", Material.COOKED_BEEF, "&6Beef Jerky", null, null, new String[] {"", "&fExtra saturating!"});
        registerLegacyItem(registry, "sf:pork_jerky", "sf:food", Material.COOKED_PORKCHOP, "&6Pork Jerky", null, null, new String[] {"", "&fExtra saturating!"});
        registerLegacyItem(registry, "sf:chicken_jerky", "sf:food", Material.COOKED_CHICKEN, "&6Chicken Jerky", null, null, new String[] {"", "&fExtra saturating!"});
        registerLegacyItem(registry, "sf:mutton_jerky", "sf:food", Material.COOKED_MUTTON, "&6Mutton Jerky", null, null, new String[] {"", "&fExtra saturating!"});
        registerLegacyItem(registry, "sf:rabbit_jerky", "sf:food", Material.COOKED_RABBIT, "&6Rabbit Jerky", null, null, new String[] {"", "&fExtra saturating!"});
        registerLegacyItem(registry, "sf:fish_jerky", "sf:food", Material.COOKED_COD, "&6Fish Jerky", null, null, new String[] {"", "&fExtra saturating!"});
        registerLegacyItem(registry, "sf:kelp_cookie", "sf:food", Material.COOKIE, "&2Kelp Cookie", null, null, new String[] {});
        registerLegacyItem(registry, "sf:christmas_milk", "sf:christmas", Material.POTION, "&6Glass of Milk", null, 0xF9FFFE, new String[] {"", "&7&oRestores &b&o2.5 &7&oHunger"});
        registerLegacyItem(registry, "sf:christmas_chocolate_milk", "sf:christmas", Material.POTION, "&6Chocolate Milk", null, 0x8E3C2E, new String[] {"", "&7&oRestores &b&o6.0 &7&oHunger"});
        registerLegacyItem(registry, "sf:christmas_egg_nog", "sf:christmas", Material.POTION, "&aEgg Nog", null, 0x474F52, new String[] {"", "&7&oRestores &b&o3.5 &7&oHunger"});
        registerLegacyItem(registry, "sf:christmas_apple_cider", "sf:christmas", Material.POTION, "&cApple Cider", null, 0xB02E26, new String[] {"", "&7&oRestores &b&o7.0 &7&oHunger"});
        registerLegacyItem(registry, "sf:christmas_cookie", "sf:christmas", Material.COOKIE, "&aC&ch&ar&ci&as&ct&am&ca&as &cC&ao&co&ak&ci&ae", null, null, new String[] {});
        registerLegacyItem(registry, "sf:christmas_fruit_cake", "sf:christmas", Material.PUMPKIN_PIE, "&aF&cr&au&ci&at &cC&aa&ck&ae", null, null, new String[] {});
        registerLegacyItem(registry, "sf:christmas_apple_pie", "sf:christmas", Material.PUMPKIN_PIE, "&fApple Pie", null, null, new String[] {});
        registerLegacyItem(registry, "sf:christmas_hot_chocolate", "sf:christmas", Material.POTION, "&6Hot Chocolate", null, 0x8E3C2E, new String[] {"", "&7&oRestores &b&o7.0 &7&oHunger"});
        registerLegacyItem(registry, "sf:christmas_cake", "sf:christmas", Material.PUMPKIN_PIE, "&aC&ch&ar&ci&as&ct&am&ca&as &cC&aa&ck&ae", null, null, new String[] {});
        registerLegacyItem(registry, "sf:christmas_caramel", "sf:christmas", Material.BRICK, "&6Caramel", null, null, new String[] {});
        registerLegacyItem(registry, "sf:christmas_caramel_apple", "sf:christmas", Material.APPLE, "&6Caramel Apple", null, null, new String[] {});
        registerLegacyItem(registry, "sf:christmas_chocolate_apple", "sf:christmas", Material.APPLE, "&6Chocolate Apple", null, null, new String[] {});
        registerLegacyItem(registry, "sf:christmas_present", "sf:christmas", Material.PLAYER_HEAD, "&aC&ch&ar&ci&as&ct&am&ca&as &cP&ar&ce&as&ce&an&ct", "6cef9aa14e884773eac134a4ee8972063f466de678363cf7b1a21a85b7", null, new String[] {"&7From: &cTheBusyBiscuit", "&7To: &eYou", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:easter_egg", "sf:easter", Material.PLAYER_HEAD, "&fEaster Egg", "b2cd5df9d7f1fa8341fcce2f3c118e2f517e4d2d99df2c51d61d93ed7f83e13", null, new String[] {"&dHappy Easter! Have a surprise.", "", "&eRight Click&7 to open"});
        registerLegacyItem(registry, "sf:carrot_pie", "sf:easter", Material.PUMPKIN_PIE, "&6Carrot Pie", null, null, new String[] {});
        registerLegacyItem(registry, "sf:easter_apple_pie", "sf:easter", Material.PUMPKIN_PIE, "&fApple Pie", null, null, new String[] {});
        registerLegacyItem(registry, "sf:grandmas_walking_stick", "sf:weapons", Material.STICK, "&7Grandmas Walking Stick", null, null, new String[] {});
        registerLegacyItem(registry, "sf:grandpas_walking_stick", "sf:weapons", Material.STICK, "&7Grandpas Walking Stick", null, null, new String[] {});
        registerLegacyItem(registry, "sf:sword_of_beheading", "sf:weapons", Material.IRON_SWORD, "&6Sword of Beheading", null, null, new String[] {"&7Beheading II", "", "&fHas a chance to behead Mobs", "&f(even a higher chance for Wither Skeletons)"});
        registerLegacyItem(registry, "sf:blade_of_vampires", "sf:weapons", Material.GOLDEN_SWORD, "&cBlade of Vampires", null, null, new String[] {"&7Life Steal I", "", "&fEverytime you attack something", "&fyou have a 45% chance to", "&frecover 2 Hearts of your Health"});
        registerLegacyItem(registry, "sf:seismic_axe", "sf:weapons", Material.IRON_AXE, "&aSeismic Axe", null, null, new String[] {"", "&7&oA portable Earthquake...", "", "&eRight Click&7 to use"});
        registerLegacyItem(registry, "sf:explosive_bow", "sf:bows", Material.BOW, "&cExplosive Bow", null, null, new String[] {"&fAny Arrows fired using this Bow", "&fwill launch hit enemys into the air"});
        registerLegacyItem(registry, "sf:icy_bow", "sf:bows", Material.BOW, "&bIcy Bow", null, null, new String[] {"&fAny Arrows fired using this Bow", "&fwill prevent hit enemys from moving", "&ffor 2 seconds"});
        registerLegacyItem(registry, "sf:smelters_pickaxe", "sf:tools", Material.DIAMOND_PICKAXE, "&6Smelter's Pickaxe", null, null, new String[] {"&c&lAuto-Smelting", "", "&9Works with Fortune"});
        registerLegacyItem(registry, "sf:lumber_axe", "sf:tools", Material.DIAMOND_AXE, "&6Lumber Axe", null, null, new String[] {"&a&oCuts down the whole Tree..."});
        registerLegacyItem(registry, "sf:pickaxe_of_containment", "sf:tools", Material.IRON_PICKAXE, "&cPickaxe of Containment", null, null, new String[] {"", "&9Can pickup Spawners"});
        registerLegacyItem(registry, "sf:explosive_pickaxe", "sf:tools", Material.DIAMOND_PICKAXE, "&eExplosive Pickaxe", null, null, new String[] {"", "&fAllows you to mine a good bit", "&fof Blocks at once...", "", "&9Works with Fortune"});
        registerLegacyItem(registry, "sf:explosive_shovel", "sf:tools", Material.DIAMOND_SHOVEL, "&eExplosive Shovel", null, null, new String[] {"", "&fAllows you to mine a good bit", "&fof diggable Blocks at once..."});
        registerLegacyItem(registry, "sf:pickaxe_of_the_seeker", "sf:tools", Material.DIAMOND_PICKAXE, "&aPickaxe of the Seeker", null, null, new String[] {"&fWill always point you to the nearest Ore", "&fbut might get damaged when doing it", "", "&7&eRight Click&7 to be pointed to the nearest Ore"});
        registerLegacyItem(registry, "sf:cobalt_pickaxe", "sf:tools", Material.IRON_PICKAXE, "&9Cobalt Pickaxe", null, null, new String[] {});
        registerLegacyItem(registry, "sf:pickaxe_of_vein_mining", "sf:tools", Material.DIAMOND_PICKAXE, "&ePickaxe of Vein Mining", null, null, new String[] {"", "&fThis Pickaxe will dig out", "&fwhole Veins of Ores..."});
        registerLegacyItem(registry, "sf:climbing_pick", "sf:tools", Material.IRON_PICKAXE, "&bClimbing Pick", null, null, new String[] {"", "&fAllows you to climb certain surfaces", "&fby right-clicking.", "&fEnchant this pick with Efficiency to", "&fclimb even faster!"});
        registerLegacyItem(registry, "sf:glowstone_helmet", "sf:armor", Material.LEATHER_HELMET, "&e&lGlowstone Helmet", null, 0xFED83D, new String[] {"", "&a&oShining like the sun!", "", "&9+ Night Vision"});
        registerLegacyItem(registry, "sf:glowstone_chestplate", "sf:armor", Material.LEATHER_CHESTPLATE, "&e&lGlowstone Chestplate", null, 0xFED83D, new String[] {"", "&a&oShining like the sun!", "", "&9+ Night Vision"});
        registerLegacyItem(registry, "sf:glowstone_leggings", "sf:armor", Material.LEATHER_LEGGINGS, "&e&lGlowstone Leggings", null, 0xFED83D, new String[] {"", "&a&oShining like the sun!", "", "&9+ Night Vision"});
        registerLegacyItem(registry, "sf:glowstone_boots", "sf:armor", Material.LEATHER_BOOTS, "&e&lGlowstone Boots", null, 0xFED83D, new String[] {"", "&a&oShining like the sun!", "", "&9+ Night Vision"});
        registerLegacyItem(registry, "sf:rainbow_leather", "sf:armor", Material.RABBIT_HIDE, "&dRainbow Leather", null, 0xC74EBD, new String[] {"", "&fCan be used to craft rainbow armor"});
        registerLegacyItem(registry, "sf:rainbow_helmet", "sf:armor", Material.LEATHER_HELMET, "&d&lRainbow Helmet", null, 0xC74EBD, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:rainbow_chestplate", "sf:armor", Material.LEATHER_CHESTPLATE, "&d&lRainbow Chestplate", null, 0xC74EBD, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:rainbow_leggings", "sf:armor", Material.LEATHER_LEGGINGS, "&d&lRainbow Leggings", null, 0xC74EBD, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:rainbow_boots", "sf:armor", Material.LEATHER_BOOTS, "&d&lRainbow Boots", null, 0xC74EBD, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:ender_helmet", "sf:armor", Material.LEATHER_HELMET, "&5&lEnder Helmet", null, 0x1C1970, new String[] {"", "&a&oSometimes its here, sometimes there!"});
        registerLegacyItem(registry, "sf:ender_chestplate", "sf:armor", Material.LEATHER_CHESTPLATE, "&5&lEnder Chestplate", null, 0x1C1970, new String[] {"", "&a&oSometimes its here, sometimes there!"});
        registerLegacyItem(registry, "sf:ender_leggings", "sf:armor", Material.LEATHER_LEGGINGS, "&5&lEnder Leggings", null, 0x1C1970, new String[] {"", "&a&oSometimes its here, sometimes there!"});
        registerLegacyItem(registry, "sf:ender_boots", "sf:armor", Material.LEATHER_BOOTS, "&5&lEnder Boots", null, 0x1C1970, new String[] {"", "&a&oSometimes its here, sometimes there!", "", "&9+ No Enderpearl Damage"});
        registerLegacyItem(registry, "sf:slime_helmet", "sf:armor", Material.LEATHER_HELMET, "&a&lSlime Helmet", null, 0x80C71F, new String[] {"", "&a&oBouncy Feeling"});
        registerLegacyItem(registry, "sf:slime_chestplate", "sf:armor", Material.LEATHER_CHESTPLATE, "&a&lSlime Chestplate", null, 0x80C71F, new String[] {"", "&a&oBouncy Feeling"});
        registerLegacyItem(registry, "sf:slime_leggings", "sf:armor", Material.LEATHER_LEGGINGS, "&a&lSlime Leggings", null, 0x80C71F, new String[] {"", "&a&oBouncy Feeling", "", "&9+ Speed"});
        registerLegacyItem(registry, "sf:slime_boots", "sf:armor", Material.LEATHER_BOOTS, "&a&lSlime Boots", null, 0x80C71F, new String[] {"", "&a&oBouncy Feeling", "", "&9+ Jump Boost", "&9+ No Fall Damage"});
        registerLegacyItem(registry, "sf:cactus_helmet", "sf:armor", Material.LEATHER_HELMET, "&2Cactus Helmet", null, 0x5E7C16, new String[] {});
        registerLegacyItem(registry, "sf:cactus_chestplate", "sf:armor", Material.LEATHER_CHESTPLATE, "&2Cactus Chestplate", null, 0x5E7C16, new String[] {});
        registerLegacyItem(registry, "sf:cactus_leggings", "sf:armor", Material.LEATHER_LEGGINGS, "&2Cactus Leggings", null, 0x5E7C16, new String[] {});
        registerLegacyItem(registry, "sf:cactus_boots", "sf:armor", Material.LEATHER_BOOTS, "&2Cactus Boots", null, 0x5E7C16, new String[] {});
        registerLegacyItem(registry, "sf:damascus_steel_helmet", "sf:armor", Material.IRON_HELMET, "&7Damascus Steel Helmet", null, null, new String[] {});
        registerLegacyItem(registry, "sf:damascus_steel_chestplate", "sf:armor", Material.IRON_CHESTPLATE, "&7Damascus Steel Chestplate", null, null, new String[] {});
        registerLegacyItem(registry, "sf:damascus_steel_leggings", "sf:armor", Material.IRON_LEGGINGS, "&7Damascus Steel Leggings", null, null, new String[] {});
        registerLegacyItem(registry, "sf:damascus_steel_boots", "sf:armor", Material.IRON_BOOTS, "&7Damascus Steel Boots", null, null, new String[] {});
        registerLegacyItem(registry, "sf:reinforced_alloy_helmet", "sf:armor", Material.IRON_HELMET, "&bReinforced Helmet", null, null, new String[] {});
        registerLegacyItem(registry, "sf:reinforced_alloy_chestplate", "sf:armor", Material.IRON_CHESTPLATE, "&bReinforced Chestplate", null, null, new String[] {});
        registerLegacyItem(registry, "sf:reinforced_alloy_leggings", "sf:armor", Material.IRON_LEGGINGS, "&bReinforced Leggings", null, null, new String[] {});
        registerLegacyItem(registry, "sf:reinforced_alloy_boots", "sf:armor", Material.IRON_BOOTS, "&bReinforced Boots", null, null, new String[] {});
        registerLegacyItem(registry, "sf:scuba_helmet", "sf:armor", Material.LEATHER_HELMET, "&cScuba Helmet", null, 0xF9801D, new String[] {"", "&7Allows you to breathe underwater"});
        registerLegacyItem(registry, "sf:hazmat_chestplate", "sf:armor", Material.LEATHER_CHESTPLATE, "&cHazmat Suit", null, 0xF9801D, new String[] {"", "&7Allows you to walk through fire and lava"});
        registerLegacyItem(registry, "sf:hazmat_leggings", "sf:armor", Material.LEATHER_LEGGINGS, "&cHazmat Suit Leggings", null, 0xF9801D, new String[] {"", "&6Full set effects:", "&e- Radiation immunity", "&e- Bee Sting protection"});
        registerLegacyItem(registry, "sf:rubber_boots", "sf:armor", Material.LEATHER_BOOTS, "&cHazmat Boots", null, 0x1D1D21, new String[] {"", "&6Full set effects:", "&e- Radiation immunity", "&e- Bee Sting protection"});
        registerLegacyItem(registry, "sf:gilded_iron_helmet", "sf:armor", Material.GOLDEN_HELMET, "&6Gilded Iron Helmet", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gilded_iron_chestplate", "sf:armor", Material.GOLDEN_CHESTPLATE, "&6Gilded Iron Chestplate", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gilded_iron_leggings", "sf:armor", Material.GOLDEN_LEGGINGS, "&6Gilded Iron Leggings", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gilded_iron_boots", "sf:armor", Material.GOLDEN_BOOTS, "&6Gilded Iron Boots", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_12k_helmet", "sf:armor", Material.GOLDEN_HELMET, "&6Golden Helmet &7(12-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_12k_chestplate", "sf:armor", Material.GOLDEN_CHESTPLATE, "&6Golden Chestplate &7(12-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_12k_leggings", "sf:armor", Material.GOLDEN_LEGGINGS, "&6Golden Leggings &7(12-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_12k_boots", "sf:armor", Material.GOLDEN_BOOTS, "&6Golden Boots &7(12-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:slime_steel_helmet", "sf:armor", Material.IRON_HELMET, "&a&lSlime Helmet", null, null, new String[] {"&7&oReinforced", "", "&a&oBouncy Feeling"});
        registerLegacyItem(registry, "sf:slime_steel_chestplate", "sf:armor", Material.IRON_CHESTPLATE, "&a&lSlime Chestplate", null, null, new String[] {"&7&oReinforced", "", "&a&oBouncy Feeling"});
        registerLegacyItem(registry, "sf:slime_steel_leggings", "sf:armor", Material.IRON_LEGGINGS, "&a&lSlime Leggings", null, null, new String[] {"&7&oReinforced", "", "&a&oBouncy Feeling", "", "&9+ Speed"});
        registerLegacyItem(registry, "sf:slime_steel_boots", "sf:armor", Material.IRON_BOOTS, "&a&lSlime Boots", null, null, new String[] {"&7&oReinforced", "", "&a&oBouncy Feeling", "", "&9+ Jump Boost", "&9+ No Fall Damage"});
        registerLegacyItem(registry, "sf:boots_of_the_stomper", "sf:armor", Material.LEATHER_BOOTS, "&bBoots of the Stomper", null, 0x3AB3DA, new String[] {"", "&9All Fall Damage you receive", "&9will be applied to nearby Mobs/Players", "", "&9+ No Fall Damage"});
        registerLegacyItem(registry, "sf:bee_helmet", "sf:armor", Material.GOLDEN_HELMET, "&e&lBee Helmet", null, null, new String[] {" ", "&fBzzzzzzz"});
        registerLegacyItem(registry, "sf:bee_wings", "sf:armor", Material.ELYTRA, "&e&lBee Wings", null, null, new String[] {" ", "&fBzzzzzzz", " ", "&9Activates Slow falling", "&9when approaching the ground"});
        registerLegacyItem(registry, "sf:bee_leggings", "sf:armor", Material.GOLDEN_LEGGINGS, "&e&lBee Leggings", null, null, new String[] {" ", "&fBzzzzzzz"});
        registerLegacyItem(registry, "sf:bee_boots", "sf:armor", Material.GOLDEN_BOOTS, "&e&lBee Boots", null, null, new String[] {"", "&fBzzzzzzz", "", "&9+ Jump Boost", "&9+ No Fall Damage"});
        registerLegacyItem(registry, "sf:magic_lump_1", "sf:magical_components", Material.GOLD_NUGGET, "&6Magical Lump &7- &eI", null, null, new String[] {"", "&c&oTier: I"});
        registerLegacyItem(registry, "sf:magic_lump_2", "sf:magical_components", Material.GOLD_NUGGET, "&6Magical Lump &7- &eII", null, null, new String[] {"", "&c&oTier: II"});
        registerLegacyItem(registry, "sf:magic_lump_3", "sf:magical_components", Material.GOLD_NUGGET, "&6Magical Lump &7- &eIII", null, null, new String[] {"", "&c&oTier: III"});
        registerLegacyItem(registry, "sf:ender_lump_1", "sf:magical_components", Material.GOLD_NUGGET, "&5Ender Lump &7- &eI", null, null, new String[] {"", "&c&oTier: I"});
        registerLegacyItem(registry, "sf:ender_lump_2", "sf:magical_components", Material.GOLD_NUGGET, "&5Ender Lump &7- &eII", null, null, new String[] {"", "&c&oTier: II"});
        registerLegacyItem(registry, "sf:ender_lump_3", "sf:magical_components", Material.GOLD_NUGGET, "&5Ender Lump &7- &eIII", null, null, new String[] {"", "&c&oTier: III"});
        registerLegacyItem(registry, "sf:magical_book_cover", "sf:magical_components", Material.PAPER, "&6Magical Book Cover", null, null, new String[] {"", "&a&oUsed for various Magic Books"});
        registerLegacyItem(registry, "sf:magical_glass", "sf:magical_components", Material.GLASS_PANE, "&6Magical Glass", null, null, new String[] {"", "&a&oUsed for various Magical Gadgets"});
        registerLegacyItem(registry, "sf:synthetic_shulker_shell", "sf:magical_components", Material.SHULKER_SHELL, "&dSynthetic Shulker Shell", null, null, new String[] {"", "&fThis item can be used in a", "&fworkbench like a normal Shulker Shell"});
        registerLegacyItem(registry, "sf:basic_circuit_board", "sf:technical_components", Material.ACTIVATOR_RAIL, "&bBasic Circuit Board", null, null, new String[] {});
        registerLegacyItem(registry, "sf:advanced_circuit_board", "sf:technical_components", Material.POWERED_RAIL, "&bAdvanced Circuit Board", null, null, new String[] {});
        registerLegacyItem(registry, "sf:wheat_flour", "sf:technical_components", Material.SUGAR, "&fWheat Flour", null, null, new String[] {});
        registerLegacyItem(registry, "sf:steel_plate", "sf:technical_components", Material.PAPER, "&7&lSteel Plate", null, null, new String[] {});
        registerLegacyItem(registry, "sf:battery", "sf:technical_components", Material.PLAYER_HEAD, "&6Battery", "6e2dda6ef6185d4dd6ea8684e97d39ba8ab037e25f75cdea6bd29df8eb34ee", null, new String[] {});
        registerLegacyItem(registry, "sf:carbon", "sf:technical_components", Material.PLAYER_HEAD, "&eCarbon", "8b3a095b6b81e6b9853a19324eedf0bb9349417258dd173b8eff87a087aa", null, new String[] {});
        registerLegacyItem(registry, "sf:compressed_carbon", "sf:technical_components", Material.PLAYER_HEAD, "&cCompressed Carbon", "321d495165748d3116f99d6b5bd5d42eb8ba592bcdfad37fd95f9b6c04a3b", null, new String[] {});
        registerLegacyItem(registry, "sf:carbon_chunk", "sf:technical_components", Material.PLAYER_HEAD, "&4Carbon Chunk", "321d495165748d3116f99d6b5bd5d42eb8ba592bcdfad37fd95f9b6c04a3b", null, new String[] {});
        registerLegacyItem(registry, "sf:steel_thruster", "sf:technical_components", Material.BUCKET, "&7&lSteel Thruster", null, null, new String[] {});
        registerLegacyItem(registry, "sf:power_crystal", "sf:technical_components", Material.PLAYER_HEAD, "&c&lPower Crystal", "53c1b036b6e03517b285a811bd85e73f5abfdacc1ddf90dff962e180934e3", null, new String[] {});
        registerLegacyItem(registry, "sf:chain", "sf:technical_components", Material.STRING, "&bChain", null, null, new String[] {});
        registerLegacyItem(registry, "sf:hook", "sf:technical_components", Material.FLINT, "&bHook", null, null, new String[] {});
        registerLegacyItem(registry, "sf:sifted_ore", "sf:technical_components", Material.GUNPOWDER, "&6Sifted Ore", null, null, new String[] {});
        registerLegacyItem(registry, "sf:stone_chunk", "sf:technical_components", Material.PLAYER_HEAD, "&6Stone Chunk", "ce8f5adb14d6c9f6b810d027543f1a8c1f417e2fed993c97bcd89c74f5e2e8", null, new String[] {});
        registerLegacyItem(registry, "sf:lava_crystal", "sf:technical_components", Material.PLAYER_HEAD, "&4Lava Crystal", "a3ad8ee849edf04ed9a26ca3341f6033bd76dcc4231ed1ea63b7565751b27ac", null, new String[] {});
        registerLegacyItem(registry, "sf:salt", "sf:technical_components", Material.SUGAR, "&fSalt", null, null, new String[] {});
        registerLegacyItem(registry, "sf:cheese", "sf:technical_components", Material.PLAYER_HEAD, "&fCheese", "34febbc15d1d4cc62bedc5d7a2b6f0f46cd5b0696a884de75e289e35cbb53a0", null, new String[] {});
        registerLegacyItem(registry, "sf:butter", "sf:technical_components", Material.PLAYER_HEAD, "&fButter", "b66b19f7d635d03473891df33017c549363209a8f6328a8542c213d08525e", null, new String[] {});
        registerLegacyItem(registry, "sf:duct_tape", "sf:technical_components", Material.PLAYER_HEAD, "&8Duct Tape", "b2faaceab6384fff5ed24bb44a4af2f584eb1382729ecd93a5369acfd6654", null, new String[] {"", "&fYou can repair Items using this", "&fin an Auto-Anvil"});
        registerLegacyItem(registry, "sf:heavy_cream", "sf:technical_components", Material.SNOWBALL, "&fHeavy Cream", null, null, new String[] {});
        registerLegacyItem(registry, "sf:crushed_ore", "sf:technical_components", Material.GUNPOWDER, "&6Crushed Ore", null, null, new String[] {});
        registerLegacyItem(registry, "sf:pulverized_ore", "sf:technical_components", Material.GUNPOWDER, "&6Pulverized Ore", null, null, new String[] {});
        registerLegacyItem(registry, "sf:pure_ore_cluster", "sf:technical_components", Material.GUNPOWDER, "&6Pure Ore Cluster", null, null, new String[] {});
        registerLegacyItem(registry, "sf:small_uranium", "sf:technical_components", Material.PLAYER_HEAD, "&cSmall Chunk of Uranium", "c8b29afa6d6dc923e2e1324bf8192750f7bdbddc689632a2b6c18d9fe7a5e", null, new String[] {"", "&a☢&7 Radiation level: &eMODERATE", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:tiny_uranium", "sf:technical_components", Material.PLAYER_HEAD, "&cTiny Pile of Uranium", "c8b29afa6d6dc923e2e1324bf8192750f7bdbddc689632a2b6c18d9fe7a5e", null, new String[] {"", "&a☢&7 Radiation level: &eLOW"});
        registerLegacyItem(registry, "sf:solar_panel", "sf:technical_components", Material.DAYLIGHT_DETECTOR, "&9Photovoltaic Cell", null, null, new String[] {"", "&7Important component for", "&7crafting a &bSolar Generator"});
        registerLegacyItem(registry, "sf:plastic_sheet", "sf:technical_components", Material.PAPER, "&fPlastic Sheet", null, null, new String[] {});
        registerLegacyItem(registry, "sf:magnet", "sf:technical_components", Material.PLAYER_HEAD, "&cMagnet", "aba8ebc4c6a81730947499bf7e1d5e73fed6c1bb2c051e96d35eb16d24610e7", null, new String[] {});
        registerLegacyItem(registry, "sf:necrotic_skull", "sf:technical_components", Material.PLAYER_HEAD, "&cNecrotic Skull", "7953b6c68448e7e6b6bf8fb273d7203acd8e1be19e81481ead51f45de59a8", null, new String[] {});
        registerLegacyItem(registry, "sf:essence_of_afterlife", "sf:technical_components", Material.GUNPOWDER, "&4Essence of Afterlife", null, null, new String[] {});
        registerLegacyItem(registry, "sf:strange_nether_goo", "sf:technical_components", Material.PURPLE_DYE, "&5Strange Nether Goo", null, null, new String[] {"", "&fA strange bio matter that", "&fcan be acquired from", "&fbartering with Piglins"});
        registerLegacyItem(registry, "sf:electro_magnet", "sf:technical_components", Material.PLAYER_HEAD, "&cElectromagnet", "aba8ebc4c6a81730947499bf7e1d5e73fed6c1bb2c051e96d35eb16d24610e7", null, new String[] {});
        registerLegacyItem(registry, "sf:heating_coil", "sf:technical_components", Material.PLAYER_HEAD, "&cHeating Coil", "7e3bc4893ba41a3f73ee28174cdf4fef6b145e41fe6c82cb7be8d8e9771a5", null, new String[] {});
        registerLegacyItem(registry, "sf:cooling_unit", "sf:technical_components", Material.PLAYER_HEAD, "&bCooling Unit", "754bad86c99df780c889a1098f77648ead7385cc1ddb093da5a7d8c4c2ae54d", null, new String[] {});
        registerLegacyItem(registry, "sf:electric_motor", "sf:technical_components", Material.PLAYER_HEAD, "&cElectric Motor", "8cbca012f67e54de9aee72ff424e056c2ae58de5eacc949ab2bcd9683cec", null, new String[] {});
        registerLegacyItem(registry, "sf:cargo_motor", "sf:cargo", Material.PLAYER_HEAD, "&3Cargo Motor", "8e47f99abcd645a3ef1122c9d850a981979f431ba293255c1680e91ab117ed35", null, new String[] {"", "&7Important ingredient for items", "&7related to Cargo Management"});
        registerLegacyItem(registry, "sf:scroll_of_dimensional_teleposition", "sf:technical_components", Material.PAPER, "&6Scroll of Dimensional Teleposition", null, null, new String[] {"", "&cThis Scroll is capable of creating", "&ca temporary black Hole which pulls", "&cnearby Entities into itself and sends", "&cthem into another Dimension where", "&ceverything is turned around", "", "&fIn other words: Makes Entities turn by 180 Degrees"});
        registerLegacyItem(registry, "sf:tome_of_knowledge_sharing", "sf:technical_components", Material.ENCHANTED_BOOK, "&6Tome of Knowledge Sharing", null, null, new String[] {"&7Owner: &bNone", "", "&eRight Click&7 to bind this Tome to yourself", "", "", "&eRight Click&7 to obtain all Researches by", "&7the previously assigned Owner"});
        registerLegacyItem(registry, "sf:hardened_glass", "sf:technical_components", Material.LIGHT_GRAY_STAINED_GLASS, "&7Hardened Glass", null, null, new String[] {"", "&fWithstands Explosions"});
        registerLegacyItem(registry, "sf:wither_proof_obsidian", "sf:technical_components", Material.OBSIDIAN, "&5Wither-Proof Obsidian", null, null, new String[] {"", "&fWithstands Explosions", "&fWithstands Wither Bosses"});
        registerLegacyItem(registry, "sf:wither_proof_glass", "sf:technical_components", Material.PURPLE_STAINED_GLASS, "&5Wither-Proof Glass", null, null, new String[] {"", "&fWithstands Explosions", "&fWithstands Wither Bosses"});
        registerLegacyItem(registry, "sf:reinforced_plate", "sf:technical_components", Material.PAPER, "&7Reinforced Plate", null, null, new String[] {});
        registerLegacyItem(registry, "sf:ancient_pedestal", "sf:technical_components", Material.DISPENSER, "&dAncient Pedestal", null, null, new String[] {"", "&5Part of the Ancient Altar"});
        registerLegacyItem(registry, "sf:ancient_altar", "sf:technical_components", Material.ENCHANTING_TABLE, "&dAncient Altar", null, null, new String[] {"", "&5Multi-Block Altar for", "&5magical Crafting Processes"});
        registerLegacyItem(registry, "sf:copper_wire", "sf:technical_components", Material.STRING, "&6Copper Wire", null, null, new String[] {"", "&6Crucial component in electric modules"});
        registerLegacyItem(registry, "sf:crafting_motor", "sf:technical_components", Material.PLAYER_HEAD, "&6Crafting Motor", "1003620899f1afa271e8e521ecbee2977a06c8529d3f389e8cc04af06d8c7940", null, new String[] {"", "&7Important component of Auto-Crafters"});
        registerLegacyItem(registry, "sf:rainbow_wool", "sf:rainbow_blocks", Material.WHITE_WOOL, "&5Rainbow Wool", null, null, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:rainbow_glass", "sf:rainbow_blocks", Material.WHITE_STAINED_GLASS, "&5Rainbow Glass", null, null, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:rainbow_clay", "sf:rainbow_blocks", Material.WHITE_TERRACOTTA, "&5Rainbow Clay", null, null, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:rainbow_glass_pane", "sf:rainbow_blocks", Material.WHITE_STAINED_GLASS_PANE, "&5Rainbow Glass Pane", null, null, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:rainbow_concrete", "sf:rainbow_blocks", Material.WHITE_CONCRETE, "&5Rainbow Concrete", null, null, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:rainbow_glazed_terracotta", "sf:rainbow_blocks", Material.WHITE_GLAZED_TERRACOTTA, "&5Rainbow Glazed Terracotta", null, null, new String[] {"", "&dCycles through all Colors of the Rainbow!"});
        registerLegacyItem(registry, "sf:rainbow_wool_xmas", "sf:seasonal", Material.WHITE_WOOL, "&5Rainbow Wool &7(Christmas)", null, null, new String[] {"", "&2[Christmas Edition]"});
        registerLegacyItem(registry, "sf:rainbow_glass_xmas", "sf:seasonal", Material.WHITE_STAINED_GLASS, "&5Rainbow Glass &7(Christmas)", null, null, new String[] {"", "&2[Christmas Edition]"});
        registerLegacyItem(registry, "sf:rainbow_clay_xmas", "sf:seasonal", Material.WHITE_TERRACOTTA, "&5Rainbow Clay &7(Christmas)", null, null, new String[] {"", "&2[Christmas Edition]"});
        registerLegacyItem(registry, "sf:rainbow_glass_pane_xmas", "sf:seasonal", Material.WHITE_STAINED_GLASS_PANE, "&5Rainbow Glass Pane &7(Christmas)", null, null, new String[] {"", "&2[Christmas Edition]"});
        registerLegacyItem(registry, "sf:rainbow_concrete_xmas", "sf:seasonal", Material.WHITE_CONCRETE, "&5Rainbow Concrete &7(Christmas)", null, null, new String[] {"", "&2[Christmas Edition]"});
        registerLegacyItem(registry, "sf:rainbow_glazed_terracotta_xmas", "sf:seasonal", Material.WHITE_GLAZED_TERRACOTTA, "&5Rainbow Glazed Terracotta &7(Christmas)", null, null, new String[] {"", "&2[Christmas Edition]"});
        registerLegacyItem(registry, "sf:rainbow_wool_valentine", "sf:seasonal", Material.PINK_WOOL, "&5Rainbow Wool &7(Valentine's Day)", null, null, new String[] {"", "&5[&dValentine's Day Edition&5]"});
        registerLegacyItem(registry, "sf:rainbow_glass_valentine", "sf:seasonal", Material.PINK_STAINED_GLASS, "&5Rainbow Glass &7(Valentine's Day)", null, null, new String[] {"", "&5[&dValentine's Day Edition&5]"});
        registerLegacyItem(registry, "sf:rainbow_clay_valentine", "sf:seasonal", Material.PINK_TERRACOTTA, "&5Rainbow Clay &7(Valentine's Day)", null, null, new String[] {"", "&5[&dValentine's Day Edition&5]"});
        registerLegacyItem(registry, "sf:rainbow_glass_pane_valentine", "sf:seasonal", Material.PINK_STAINED_GLASS_PANE, "&5Rainbow Glass Pane &7(Valentine's Day)", null, null, new String[] {"", "&5[&dValentine's Day Edition&5]"});
        registerLegacyItem(registry, "sf:rainbow_concrete_valentine", "sf:seasonal", Material.PINK_CONCRETE, "&5Rainbow Concrete &7(Valentine's Day)", null, null, new String[] {"", "&5[&dValentine's Day Edition&5]"});
        registerLegacyItem(registry, "sf:rainbow_glazed_terracotta_valentine", "sf:seasonal", Material.PINK_GLAZED_TERRACOTTA, "&5Rainbow Glazed Terracotta &7(Valentine's Day)", null, null, new String[] {"", "&5[&dValentine's Day Edition&5]"});
        registerLegacyItem(registry, "sf:rainbow_wool_halloween", "sf:seasonal", Material.ORANGE_WOOL, "&5Rainbow Wool &7(Halloween)", null, null, new String[] {"", "&c[&6Halloween Edition&c]"});
        registerLegacyItem(registry, "sf:rainbow_glass_halloween", "sf:seasonal", Material.ORANGE_STAINED_GLASS, "&5Rainbow Glass &7(Halloween)", null, null, new String[] {"", "&c[&6Halloween Edition&c]"});
        registerLegacyItem(registry, "sf:rainbow_clay_halloween", "sf:seasonal", Material.ORANGE_TERRACOTTA, "&5Rainbow Clay &7(Halloween)", null, null, new String[] {"", "&c[&6Halloween Edition&c]"});
        registerLegacyItem(registry, "sf:rainbow_glass_pane_halloween", "sf:seasonal", Material.ORANGE_STAINED_GLASS_PANE, "&5Rainbow Glass Pane &7(Halloween)", null, null, new String[] {"", "&c[&6Halloween Edition&c]"});
        registerLegacyItem(registry, "sf:rainbow_concrete_halloween", "sf:seasonal", Material.ORANGE_CONCRETE, "&5Rainbow Concrete &7(Halloween)", null, null, new String[] {"", "&c[&6Halloween Edition&c]"});
        registerLegacyItem(registry, "sf:rainbow_glazed_terracotta_halloween", "sf:seasonal", Material.ORANGE_GLAZED_TERRACOTTA, "&5Rainbow Glazed Terracotta &7(Halloween)", null, null, new String[] {"", "&c[&6Halloween Edition&c]"});
        registerLegacyItem(registry, "sf:copper_ingot", "sf:ingots", Material.BRICK, "&bCopper Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:tin_ingot", "sf:ingots", Material.IRON_INGOT, "&bTin Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:silver_ingot", "sf:ingots", Material.IRON_INGOT, "&bSilver Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:aluminum_ingot", "sf:ingots", Material.IRON_INGOT, "&bAluminum Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:lead_ingot", "sf:ingots", Material.IRON_INGOT, "&bLead Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:zinc_ingot", "sf:ingots", Material.IRON_INGOT, "&bZinc Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:magnesium_ingot", "sf:ingots", Material.IRON_INGOT, "&bMagnesium Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:steel_ingot", "sf:alloy_carbon_iron", Material.IRON_INGOT, "&bSteel Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:bronze_ingot", "sf:alloy_copper_tin", Material.BRICK, "&bBronze Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:duralumin_ingot", "sf:alloy_copper_aluminum", Material.IRON_INGOT, "&bDuralumin Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:billon_ingot", "sf:alloy_copper_silver", Material.IRON_INGOT, "&bBillon Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:brass_ingot", "sf:alloy_copper_zinc", Material.GOLD_INGOT, "&bBrass Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:aluminum_brass_ingot", "sf:alloy_aluminum_brass", Material.GOLD_INGOT, "&bAluminum Brass Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:aluminum_bronze_ingot", "sf:alloy_aluminum_bronze", Material.GOLD_INGOT, "&bAluminum Bronze Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:corinthian_bronze_ingot", "sf:alloy_gold_silver_copper", Material.GOLD_INGOT, "&bCorinthian Bronze Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:solder_ingot", "sf:alloy_lead_tin", Material.IRON_INGOT, "&bSolder Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:damascus_steel_ingot", "sf:alloy_steel_iron_carbon", Material.IRON_INGOT, "&bDamascus Steel Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:hardened_metal_ingot", "sf:alloy_damascus_steel_duralumin_compressed_carbon_aluminium_bronze", Material.IRON_INGOT, "&b&lHardened Metal", null, null, new String[] {});
        registerLegacyItem(registry, "sf:reinforced_alloy_ingot", "sf:alloy_hardened_metal_corinthian_bronze_solder_billon_damascus_steel", Material.IRON_INGOT, "&b&lReinforced Alloy Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:ferrosilicon", "sf:alloy_iron_silicon", Material.IRON_INGOT, "&bFerrosilicon", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gilded_iron", "sf:alloy_iron_gold", Material.GOLD_INGOT, "&6&lGilded Iron", null, null, new String[] {});
        registerLegacyItem(registry, "sf:redstone_alloy", "sf:alloy_redstone_ferrosilicon", Material.BRICK, "&cRedstone Alloy Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:nickel_ingot", "sf:alloy_iron_copper", Material.IRON_INGOT, "&bNickel Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:cobalt_ingot", "sf:alloy_nickel_iron_copper", Material.IRON_INGOT, "&9Cobalt Ingot", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_4k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(4-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_6k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(6-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_8k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(8-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_10k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(10-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_12k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(12-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_14k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(14-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_16k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(16-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_18k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(18-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_20k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(20-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_22k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(22-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_24k", "sf:gold", Material.GOLD_INGOT, "&fGold Ingot &7(24-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:iron_dust", "sf:dusts", Material.GUNPOWDER, "&6Iron Dust", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_dust", "sf:dusts", Material.GLOWSTONE_DUST, "&6Gold Dust", null, null, new String[] {});
        registerLegacyItem(registry, "sf:tin_dust", "sf:dusts", Material.SUGAR, "&6Tin Dust", null, null, new String[] {});
        registerLegacyItem(registry, "sf:copper_dust", "sf:dusts", Material.GLOWSTONE_DUST, "&6Copper Dust", null, null, new String[] {});
        registerLegacyItem(registry, "sf:silver_dust", "sf:dusts", Material.SUGAR, "&6Silver Dust", null, null, new String[] {});
        registerLegacyItem(registry, "sf:aluminum_dust", "sf:dusts", Material.SUGAR, "&6Aluminum Dust", null, null, new String[] {});
        registerLegacyItem(registry, "sf:lead_dust", "sf:dusts", Material.GUNPOWDER, "&6Lead Dust", null, null, new String[] {});
        registerLegacyItem(registry, "sf:zinc_dust", "sf:dusts", Material.SUGAR, "&6Zinc Dust", null, null, new String[] {});
        registerLegacyItem(registry, "sf:magnesium_dust", "sf:dusts", Material.SUGAR, "&6Magnesium", null, null, new String[] {});
        registerLegacyItem(registry, "sf:sulfate", "sf:dusts", Material.GLOWSTONE_DUST, "&6Sulfate", null, null, new String[] {});
        registerLegacyItem(registry, "sf:silicon", "sf:dusts", Material.FIREWORK_STAR, "&6Silicon", null, null, new String[] {});
        registerLegacyItem(registry, "sf:gold_24k_block", "sf:dusts", Material.GOLD_BLOCK, "&fGold Block &7(24-Carat)", null, null, new String[] {});
        registerLegacyItem(registry, "sf:synthetic_diamond", "sf:gems", Material.DIAMOND, "&bSynthetic Diamond", null, null, new String[] {"", "&fThis item can be used in a", "&fworkbench and acts like a normal Diamond"});
        registerLegacyItem(registry, "sf:synthetic_emerald", "sf:gems", Material.EMERALD, "&bSynthetic Emerald", null, null, new String[] {"", "&fThis item can be used to", "&ftrade with Villagers"});
        registerLegacyItem(registry, "sf:synthetic_sapphire", "sf:gems", Material.PLAYER_HEAD, "&bSynthetic Sapphire", "e35032f4d7d01de8ec99d89f8723012d4e74fa73022c4facf1b57c7ff6ff0", null, new String[] {"", "&fThis item can be used in a", "&fworkbench and acts like Lapis Lazuli"});
        registerLegacyItem(registry, "sf:carbonado", "sf:gems", Material.PLAYER_HEAD, "&b&lCarbonado", "12f4b1577f5160c6893172571c4a71d8b321cdceaa032c6e0e3b60e0b328fa", null, new String[] {"", "&7&o\"Black Diamond\""});
        registerLegacyItem(registry, "sf:raw_carbonado", "sf:gems", Material.PLAYER_HEAD, "&bRaw Carbonado", "eb49e6ec10771e899225aea73cd8cf03684f411d1415c7323c93cb9476230", null, new String[] {});
        registerLegacyItem(registry, "sf:uranium", "sf:gems", Material.PLAYER_HEAD, "&4Uranium", "c8b29afa6d6dc923e2e1324bf8192750f7bdbddc689632a2b6c18d9fe7a5e", null, new String[] {"", "&a☢&7 Radiation level: &6HIGH", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:neptunium", "sf:gems", Material.PLAYER_HEAD, "&aNeptunium", "4edea6bfd37e49de43f154fe6fca617d4129e61b95759a3d49a15935a1c2dcf0", null, new String[] {"", "&a☢&7 Radiation level: &6HIGH", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:plutonium", "sf:gems", Material.PLAYER_HEAD, "&7Plutonium", "25cf91b7388665a6d7c1b6026bdb2322c6d278997a44478677cbcc15f76124f", null, new String[] {"", "&a☢&7 Radiation level: &cVERY HIGH", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:boosted_uranium", "sf:gems", Material.PLAYER_HEAD, "&2Boosted Uranium", "6837ca12f222f4787196a17b8ab656985f8404c50767adbcb6e7f14254fee", null, new String[] {"", "&a☢&7 Radiation level: &cVERY HIGH", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:common_talisman", "sf:talisman", Material.EMERALD, "&6Common Talisman", null, null, new String[] {});
        registerLegacyItem(registry, "sf:ender_talisman", "sf:talisman", Material.EMERALD, "&5Ender Talisman", null, null, new String[] {});
        registerLegacyItem(registry, "sf:anvil_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Anvil", null, null, new String[] {"", "&fEach Talisman can prevent", "&f1 Tool from breaking, but will then", "&fbe consumed", "", "&4&lWARNING:", "&4This Talisman does not work on", "&4Tools which are too powerful", "&4due to their complexity"});
        registerLegacyItem(registry, "sf:miner_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Miner", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it has", "&fa 20% chance of doubling", "&fall Ores you mine"});
        registerLegacyItem(registry, "sf:farmer_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Farmer", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it has", "&fa 20% chance of doubling", "&fall crops you harvest"});
        registerLegacyItem(registry, "sf:hunter_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Hunter", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it has", "&fa 20% chance of doubling", "&fall Drops from Mobs you kill"});
        registerLegacyItem(registry, "sf:lava_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Lava Walker", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it will", "&fgive you Fire Resistance", "&fas soon as you touch Lava", "&fbut will then be consumed"});
        registerLegacyItem(registry, "sf:water_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Water Breather", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it will", "&fgive you the ability", "&fto breath underwater as", "&fsoon as you start drowning", "&fbut will then be consumed"});
        registerLegacyItem(registry, "sf:angel_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Angel", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it has a", "&f75% chance to prevent you", "&ffrom taking Fall Damage"});
        registerLegacyItem(registry, "sf:fire_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Firefighter", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it will", "&fgive you Fire Resistance", "&fas soon as you start burning", "&fbut will then be consumed"});
        registerLegacyItem(registry, "sf:magician_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Magician", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it gives", "&fyou a 80% Luck Bonus on Enchanting", "&fYou will sometimes get an Extra Enchantment"});
        registerLegacyItem(registry, "sf:traveller_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Traveller", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it gives", "&fyou a 60% Chance for a decent", "&fSpeed Buff when you start sprinting"});
        registerLegacyItem(registry, "sf:warrior_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Warrior", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it gives", "&fyou Strength III whenever you get hit", "&fbut will then be consumed"});
        registerLegacyItem(registry, "sf:knight_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Knight", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it gives", "&fyou a 30% Chance for 5 Seconds of Regeneration", "&fwhenever You get hit", "&fbut will then be consumed"});
        registerLegacyItem(registry, "sf:whirlwind_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Whirlwind", null, null, new String[] {"", "&fHaving this Talisman", "&fin your Inventory will reflect", "&f60% of any projectiles fired at you.", "&e&oOnly a thrown Trident can pierce", "&e&othrough this layer of protection"});
        registerLegacyItem(registry, "sf:wizard_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Wizard", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your Inventory it allows you to", "&fobtain Fortune Level 4/5 however", "&fit also has a chance to lower the", "&fLevel of some Enchantments on your Item"});
        registerLegacyItem(registry, "sf:caveman_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Caveman", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your inventory it gives", "&fyou a 50% chance for a decent", "&fHaste buff when you mine any ore"});
        registerLegacyItem(registry, "sf:wise_talisman", "sf:talisman", Material.EMERALD, "&aTalisman of the Wise", null, null, new String[] {"", "&fWhile you have this Talisman", "&fin your inventory it gives", "&fyou a 20% chance of doubling", "&fany experience you obtain"});
        registerEnderTalismanVariant(registry, "anvil_talisman", "&aTalisman of the Anvil");
        registerEnderTalismanVariant(registry, "miner_talisman", "&aTalisman of the Miner");
        registerEnderTalismanVariant(registry, "farmer_talisman", "&aTalisman of the Farmer");
        registerEnderTalismanVariant(registry, "hunter_talisman", "&aTalisman of the Hunter");
        registerEnderTalismanVariant(registry, "lava_talisman", "&aTalisman of the Lava Walker");
        registerEnderTalismanVariant(registry, "water_talisman", "&aTalisman of the Water Breather");
        registerEnderTalismanVariant(registry, "angel_talisman", "&aTalisman of the Angel");
        registerEnderTalismanVariant(registry, "fire_talisman", "&aTalisman of the Firefighter");
        registerEnderTalismanVariant(registry, "magician_talisman", "&aTalisman of the Magician");
        registerEnderTalismanVariant(registry, "traveller_talisman", "&aTalisman of the Traveller");
        registerEnderTalismanVariant(registry, "warrior_talisman", "&aTalisman of the Warrior");
        registerEnderTalismanVariant(registry, "knight_talisman", "&aTalisman of the Knight");
        registerEnderTalismanVariant(registry, "whirlwind_talisman", "&aTalisman of the Whirlwind");
        registerEnderTalismanVariant(registry, "wizard_talisman", "&aTalisman of the Wizard");
        registerEnderTalismanVariant(registry, "caveman_talisman", "&aTalisman of the Caveman");
        registerEnderTalismanVariant(registry, "wise_talisman", "&aTalisman of the Wise");
        registerLegacyItem(registry, "sf:staff_elemental", "sf:staves", Material.STICK, "&6Elemental Staff", null, null, new String[] {});
        registerLegacyItem(registry, "sf:staff_elemental_wind", "sf:staves", Material.STICK, "&6Elemental Staff &7- &b&oWind", null, null, new String[] {"", "&7Element: &b&oWind", "", "&eRight Click&7 to launch yourself forward"});
        registerLegacyItem(registry, "sf:staff_elemental_fire", "sf:staves", Material.STICK, "&6Elemental Staff &7- &c&oFire", null, null, new String[] {"", "&7Element: &c&oFire"});
        registerLegacyItem(registry, "sf:staff_elemental_water", "sf:staves", Material.STICK, "&6Elemental Staff &7- &1&oWater", null, null, new String[] {"", "&7Element: &1&oWater", "", "&eRight Click&7 to extinguish yourself"});
        registerLegacyItem(registry, "sf:staff_elemental_storm", "sf:staves", Material.STICK, "&6Elemental Staff &7- &8&oStorm", null, null, new String[] {"", "&7Element: &8&oStorm", "", "&eRight Click&7 to summon a lightning", "&e8 Uses &7left"});
        registerLegacyItem(registry, "sf:enhanced_crafting_table", "sf:multiblocks", Material.CRAFTING_TABLE, "&eEnhanced Crafting Table", null, null, new String[] {"", "&aA regular Crafting Table cannot", "&ahold this massive Amount of Power..."});
        registerLegacyItem(registry, "sf:grind_stone", "sf:multiblocks", Material.DISPENSER, "&bGrind Stone", null, null, new String[] {"", "&aGrinds items down into other items"});
        registerLegacyItem(registry, "sf:armor_forge", "sf:multiblocks", Material.ANVIL, "&6Armor Forge", null, null, new String[] {"", "&aGives you the ability to create powerful armor"});
        registerLegacyItem(registry, "sf:makeshift_smeltery", "sf:multiblocks", Material.BLAST_FURNACE, "&eMakeshift Smeltery", null, null, new String[] {"", "&fImprovised version of the Smeltery", "&fthat only allows you to", "&fsmelt dusts into ingots"});
        registerLegacyItem(registry, "sf:smeltery", "sf:multiblocks", Material.FURNACE, "&6Smeltery", null, null, new String[] {"", "&fA high-temperature furnace", "&fthat allows you to smelt dusts", "&finto ingots and create alloys."});
        registerLegacyItem(registry, "sf:ore_crusher", "sf:multiblocks", Material.DISPENSER, "&bOre Crusher", null, null, new String[] {"", "&aCrushes ores to double them"});
        registerLegacyItem(registry, "sf:compressor", "sf:multiblocks", Material.PISTON, "&bCompressor", null, null, new String[] {"", "&aCompresses Items"});
        registerLegacyItem(registry, "sf:pressure_chamber", "sf:multiblocks", Material.GLASS, "&bPressure Chamber", null, null, new String[] {"", "&aCompresses Items even further"});
        registerLegacyItem(registry, "sf:magic_workbench", "sf:multiblocks", Material.CRAFTING_TABLE, "&6Magic Workbench", null, null, new String[] {"", "&dInfuses Items with magical Energy"});
        registerLegacyItem(registry, "sf:ore_washer", "sf:multiblocks", Material.CAULDRON, "&6Ore Washer", null, null, new String[] {"", "&aWashes Sifted Ore to filter Ores", "&aand gives you small Stone Chunks"});
        registerLegacyItem(registry, "sf:table_saw", "sf:multiblocks", Material.STONECUTTER, "&6Table Saw", null, null, new String[] {"", "&aAllows you to get 8 planks from 1 Log", "&a(Works with all log types)"});
        registerLegacyItem(registry, "sf:juicer", "sf:multiblocks", Material.GLASS_BOTTLE, "&aJuicer", null, null, new String[] {"", "&aAllows you to create delicious Juice"});
        registerLegacyItem(registry, "sf:automated_panning_machine", "sf:multiblocks", Material.BOWL, "&eAutomated Panning Machine", null, null, new String[] {"", "&fA MultiBlock Version of the Gold Pan", "&fand Nether Gold Pan combined in one machine."});
        registerLegacyItem(registry, "sf:industrial_miner", "sf:multiblocks", Material.GOLDEN_PICKAXE, "&bIndustrial Miner", null, null, new String[] {"", "&fThis Multiblock will mine any Ores", "&fin a 7x7 area underneath it.", "&fPlace coal or similar in its chest", "&fto fuel this machine."});
        registerLegacyItem(registry, "sf:advanced_industrial_miner", "sf:multiblocks", Material.DIAMOND_PICKAXE, "&cAdvanced Industrial Miner", null, null, new String[] {"", "&fThis Multiblock will mine any Ores", "&fin a 11x11 area underneath it.", "&fPlace a bucket of fuel or lava in", "&fits chest to fuel this machine.", "", "&a+ Silk Touch"});
        registerLegacyItem(registry, "sf:composter", "sf:machines", Material.CAULDRON, "&aComposter", null, null, new String[] {"", "&a&oCan convert various Materials over Time..."});
        registerLegacyItem(registry, "sf:crucible", "sf:machines", Material.CAULDRON, "&cCrucible", null, null, new String[] {"", "&a&oUsed to smelt Items into Liquids"});
        registerLegacyItem(registry, "sf:output_chest", "sf:machines", Material.CHEST, "&4Output Chest", null, null, new String[] {"", "&c&oA basic machine will try to put", "&c&oitems in this chest if it's placed", "&c&oadjacent to the dispenser."});
        registerLegacyItem(registry, "sf:ignition_chamber", "sf:machines", Material.DROPPER, "&4Automatic Ignition Chamber", null, null, new String[] {"", "&fPrevents the Smeltery from using up fire.", "&fJust fill it up with \"Flint and Steel\"", "&fand place it adjacent to the Smeltery's dispenser"});
        registerLegacyItem(registry, "sf:hologram_projector", "sf:machines", Material.QUARTZ_SLAB, "&bHologram Projector", null, null, new String[] {"", "&fProjects an Editable Hologram"});
        registerLegacyItem(registry, "sf:block_placer", "sf:machines", Material.DISPENSER, "&aBlock Placer", null, null, new String[] {"", "&fAll Blocks in this Dispenser", "&fwill automatically get placed"});
        registerLegacyItem(registry, "sf:enhanced_furnace", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eI", null, null, new String[] {"", "&7Processing Speed: &e1x", "&7Fuel Efficiency: &e1x", "&7Luck Multiplier: &e1x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_2", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eII", null, null, new String[] {"", "&7Processing Speed: &e2x", "&7Fuel Efficiency: &e1x", "&7Luck Multiplier: &e1x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_3", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eIII", null, null, new String[] {"", "&7Processing Speed: &e2x", "&7Fuel Efficiency: &e2x", "&7Luck Multiplier: &e1x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_4", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eIV", null, null, new String[] {"", "&7Processing Speed: &e3x", "&7Fuel Efficiency: &e2x", "&7Luck Multiplier: &e1x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_5", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eV", null, null, new String[] {"", "&7Processing Speed: &e3x", "&7Fuel Efficiency: &e2x", "&7Luck Multiplier: &e2x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_6", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eVI", null, null, new String[] {"", "&7Processing Speed: &e3x", "&7Fuel Efficiency: &e3x", "&7Luck Multiplier: &e2x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_7", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eVII", null, null, new String[] {"", "&7Processing Speed: &e4x", "&7Fuel Efficiency: &e3x", "&7Luck Multiplier: &e2x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_8", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eVIII", null, null, new String[] {"", "&7Processing Speed: &e4x", "&7Fuel Efficiency: &e4x", "&7Luck Multiplier: &e2x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_9", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eIX", null, null, new String[] {"", "&7Processing Speed: &e5x", "&7Fuel Efficiency: &e4x", "&7Luck Multiplier: &e2x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_10", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eX", null, null, new String[] {"", "&7Processing Speed: &e5x", "&7Fuel Efficiency: &e5x", "&7Luck Multiplier: &e2x"});
        registerLegacyItem(registry, "sf:enhanced_furnace_11", "sf:enhanced_furnaces", Material.FURNACE, "&7Enhanced Furnace - &eXI", null, null, new String[] {"", "&7Processing Speed: &e5x", "&7Fuel Efficiency: &e5x", "&7Luck Multiplier: &e3x"});
        registerLegacyItem(registry, "sf:reinforced_furnace", "sf:enhanced_furnaces", Material.FURNACE, "&7Reinforced Furnace", null, null, new String[] {"", "&7Processing Speed: &e10x", "&7Fuel Efficiency: &e10x", "&7Luck Multiplier: &e3x"});
        registerLegacyItem(registry, "sf:carbonado_edged_furnace", "sf:enhanced_furnaces", Material.FURNACE, "&7Carbonado Edged Furnace", null, null, new String[] {"", "&7Processing Speed: &e20x", "&7Fuel Efficiency: &e10x", "&7Luck Multiplier: &e3x"});
        registerLegacyItem(registry, "sf:soulbound_sword", "sf:soulbound_items", Material.DIAMOND_SWORD, "&cSoulbound Sword", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_bow", "sf:soulbound_items", Material.BOW, "&cSoulbound Bow", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_pickaxe", "sf:soulbound_items", Material.DIAMOND_PICKAXE, "&cSoulbound Pickaxe", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_axe", "sf:soulbound_items", Material.DIAMOND_AXE, "&cSoulbound Axe", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_shovel", "sf:soulbound_items", Material.DIAMOND_SHOVEL, "&cSoulbound Shovel", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_hoe", "sf:soulbound_items", Material.DIAMOND_HOE, "&cSoulbound Hoe", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_trident", "sf:soulbound_items", Material.TRIDENT, "&cSoulbound Trident", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_helmet", "sf:soulbound_items", Material.DIAMOND_HELMET, "&cSoulbound Helmet", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_chestplate", "sf:soulbound_items", Material.DIAMOND_CHESTPLATE, "&cSoulbound Chestplate", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_leggings", "sf:soulbound_items", Material.DIAMOND_LEGGINGS, "&cSoulbound Leggings", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_boots", "sf:soulbound_items", Material.DIAMOND_BOOTS, "&cSoulbound Boots", null, null, new String[] {});
        registerLegacyItem(registry, "sf:blank_rune", "sf:runes", Material.FIREWORK_STAR, "&fBlank Rune", null, 0x1D1D21, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_air", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Air", null, 0x3AB3DA, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_water", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Water", null, 0x3C44AA, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_fire", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Fire", null, 0xB02E26, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_earth", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Earth", null, null, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_ender", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Ender", null, 0x8932B8, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_rainbow", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Rainbow", null, 0xC74EBD, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_lightning", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Lightning", null, null, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_soulbound", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Soulbound", null, null, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_enchantment", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Enchantment", null, null, new String[] {});
        registerLegacyItem(registry, "sf:ancient_rune_villagers", "sf:runes", Material.FIREWORK_STAR, "&fAncient Rune Villagers", null, null, new String[] {});
        registerLegacyItem(registry, "sf:solar_generator", "sf:electricity", Material.DAYLIGHT_DETECTOR, "&bSolar Generator", null, null, new String[] {"", "&eBasic Generator", "&8⇨ &e⚡ &70 J Buffer", "&8⇨ &e⚡ &74 J/s"});
        registerLegacyItem(registry, "sf:solar_generator_2", "sf:electricity", Material.DAYLIGHT_DETECTOR, "&cAdvanced Solar Generator", null, null, new String[] {"", "&aMedium Generator", "&8⇨ &e⚡ &70 J Buffer", "&8⇨ &e⚡ &716 J/s"});
        registerLegacyItem(registry, "sf:solar_generator_3", "sf:electricity", Material.DAYLIGHT_DETECTOR, "&4Carbonado Solar Generator", null, null, new String[] {"", "&4End-Game Generator", "&8⇨ &e⚡ &70 J Buffer", "&8⇨ &e⚡ &764 J/s"});
        registerLegacyItem(registry, "sf:solar_generator_4", "sf:electricity", Material.DAYLIGHT_DETECTOR, "&eEnergized Solar Generator", null, null, new String[] {"", "&9Works at Night", "", "&4End-Game Generator", "&8⇨ &e⚡ &70 J Buffer", "&8⇨ &e⚡ &7256 J/s (Day)", "&8⇨ &e⚡ &7128 J/s (Night)"});
        registerLegacyItem(registry, "sf:coal_generator", "sf:electricity", Material.PLAYER_HEAD, "&cCoal Generator", "9343ce58da54c79924a2c9331cfc417fe8ccbbea9be45a7ac85860a6c730", null, new String[] {"", "&6Average Generator", "&8⇨ &e⚡ &764 J Buffer", "&8⇨ &e⚡ &716 J/s"});
        registerLegacyItem(registry, "sf:coal_generator_2", "sf:electricity", Material.PLAYER_HEAD, "&cCoal Generator &7(&eII&7)", "9343ce58da54c79924a2c9331cfc417fe8ccbbea9be45a7ac85860a6c730", null, new String[] {"", "&6Advanced Generator", "&8⇨ &e⚡ &7256 J Buffer", "&8⇨ &e⚡ &730 J/s"});
        registerLegacyItem(registry, "sf:lava_generator", "sf:electricity", Material.PLAYER_HEAD, "&4Lava Generator", "9343ce58da54c79924a2c9331cfc417fe8ccbbea9be45a7ac85860a6c730", null, new String[] {"", "&6Average Generator", "&8⇨ &e⚡ &7512 J Buffer", "&8⇨ &e⚡ &720 J/s"});
        registerLegacyItem(registry, "sf:lava_generator_2", "sf:electricity", Material.PLAYER_HEAD, "&4Lava Generator &7(&eII&7)", "9343ce58da54c79924a2c9331cfc417fe8ccbbea9be45a7ac85860a6c730", null, new String[] {"", "&6Advanced Generator", "&8⇨ &e⚡ &71024 J Buffer", "&8⇨ &e⚡ &740 J/s"});
        registerLegacyItem(registry, "sf:electric_furnace", "sf:electricity", Material.FURNACE, "&cElectric Furnace", null, null, new String[] {"", "&eBasic Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &74 J/s"});
        registerLegacyItem(registry, "sf:electric_furnace_2", "sf:electricity", Material.FURNACE, "&cElectric Furnace &7- &eII", null, null, new String[] {"", "&aMedium Machine", "&8⇨ &b⚡ &7Speed: &b2x", "&8⇨ &e⚡ &76 J/s"});
        registerLegacyItem(registry, "sf:electric_furnace_3", "sf:electricity", Material.FURNACE, "&cElectric Furnace &7- &eIII", null, null, new String[] {"", "&aMedium Machine", "&8⇨ &b⚡ &7Speed: &b4x", "&8⇨ &e⚡ &710 J/s"});
        registerLegacyItem(registry, "sf:electric_ore_grinder", "sf:electricity", Material.FURNACE, "&cElectric Ore Grinder", null, null, new String[] {"", "&fWorks as an Ore Crusher and Grind Stone", "", "&6Advanced Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &712 J/s"});
        registerLegacyItem(registry, "sf:electric_ore_grinder_2", "sf:electricity", Material.FURNACE, "&cElectric Ore Grinder &7(&eII&7)", null, null, new String[] {"", "&fWorks as an Ore Crusher and Grind Stone", "", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b4x", "&8⇨ &e⚡ &730 J/s"});
        registerLegacyItem(registry, "sf:electric_ore_grinder_3", "sf:electricity", Material.FURNACE, "&cElectric Ore Grinder &7(&eIII&7)", null, null, new String[] {"", "&fWorks as an Ore Crusher and Grind Stone", "", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b10x", "&8⇨ &e⚡ &790 J/s"});
        registerLegacyItem(registry, "sf:electric_ingot_pulverizer", "sf:electricity", Material.FURNACE, "&cElectric Ingot Pulverizer", null, null, new String[] {"", "&fPulverizes Ingots into Dust", "", "&aMedium Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &714 J/s"});
        registerLegacyItem(registry, "sf:auto_drier", "sf:electricity", Material.SMOKER, "&6Auto Drier", null, null, new String[] {"", "&aMedium Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &710 J/s"});
        registerLegacyItem(registry, "sf:auto_enchanter", "sf:electricity", Material.ENCHANTING_TABLE, "&5Auto Enchanter", null, null, new String[] {"", "&aMedium Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &718 J/s"});
        registerLegacyItem(registry, "sf:auto_enchanter_2", "sf:electricity", Material.ENCHANTING_TABLE, "&5Auto Enchanter &7- &eII", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b3x", "&8⇨ &e⚡ &748 J/s"});
        registerLegacyItem(registry, "sf:auto_disenchanter", "sf:electricity", Material.ENCHANTING_TABLE, "&5Auto Disenchanter", null, null, new String[] {"", "&aMedium Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &718 J/s"});
        registerLegacyItem(registry, "sf:auto_disenchanter_2", "sf:electricity", Material.ENCHANTING_TABLE, "&5Auto Disenchanter &7- &eII", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b3x", "&8⇨ &e⚡ &748 J/s"});
        registerLegacyItem(registry, "sf:auto_anvil", "sf:electricity", Material.IRON_BLOCK, "&7Auto Anvil", null, null, new String[] {"", "&6Advanced Machine", "&8⇨ &7Repair Factor: 10%", "&8⇨ &e⚡ &724 J/s"});
        registerLegacyItem(registry, "sf:auto_anvil_2", "sf:electricity", Material.IRON_BLOCK, "&7Auto Anvil Mk.II", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &7Repair Factor: 25%", "&8⇨ &e⚡ &732 J/s"});
        registerLegacyItem(registry, "sf:auto_brewer", "sf:electricity", Material.SMOKER, "&6Auto Brewer", null, null, new String[] {"", "&aMedium Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &712 J/s"});
        registerLegacyItem(registry, "sf:book_binder", "sf:electricity", Material.BOOKSHELF, "&6Book Binder", null, null, new String[] {"", "&fBinds multiple enchanted books into one.", "", "&aMedium Machine", "&8⇨ &e⚡ &716 J/s"});
        registerLegacyItem(registry, "sf:bio_reactor", "sf:electricity", Material.LIME_TERRACOTTA, "&2Bio Reactor", null, null, new String[] {"", "&6Average Generator", "&8⇨ &e⚡ &7128 J Buffer", "&8⇨ &e⚡ &78 J/s"});
        registerLegacyItem(registry, "sf:multimeter", "sf:electricity", Material.CLOCK, "&eMultimeter", null, null, new String[] {"", "&fMeasures the Amount of stored", "&fEnergy in a Block"});
        registerLegacyItem(registry, "sf:small_capacitor", "sf:electricity", Material.PLAYER_HEAD, "&aSmall Energy Capacitor", "91361e576b493cbfdfae328661cedd1add55fab4e5eb418b92cebf6275f8bb4", null, new String[] {"&7Range: &c6 blocks", "", "&eBasic Capacitor", "&8⇨ &e⚡ &7128 J Capacity"});
        registerLegacyItem(registry, "sf:medium_capacitor", "sf:electricity", Material.PLAYER_HEAD, "&aMedium Energy Capacitor", "91361e576b493cbfdfae328661cedd1add55fab4e5eb418b92cebf6275f8bb4", null, new String[] {"&7Range: &c6 blocks", "", "&6Average Capacitor", "&8⇨ &e⚡ &7512 J Capacity"});
        registerLegacyItem(registry, "sf:big_capacitor", "sf:electricity", Material.PLAYER_HEAD, "&aBig Energy Capacitor", "91361e576b493cbfdfae328661cedd1add55fab4e5eb418b92cebf6275f8bb4", null, new String[] {"&7Range: &c6 blocks", "", "&aMedium Capacitor", "&8⇨ &e⚡ &71024 J Capacity"});
        registerLegacyItem(registry, "sf:large_capacitor", "sf:electricity", Material.PLAYER_HEAD, "&aLarge Energy Capacitor", "91361e576b493cbfdfae328661cedd1add55fab4e5eb418b92cebf6275f8bb4", null, new String[] {"&7Range: &c6 blocks", "", "&2Good Capacitor", "&8⇨ &e⚡ &78192 J Capacity"});
        registerLegacyItem(registry, "sf:carbonado_edged_capacitor", "sf:electricity", Material.PLAYER_HEAD, "&aCarbonado Edged Energy Capacitor", "91361e576b493cbfdfae328661cedd1add55fab4e5eb418b92cebf6275f8bb4", null, new String[] {"&7Range: &c6 blocks", "", "&4End-Game Capacitor", "&8⇨ &e⚡ &765536 J Capacity"});
        registerLegacyItem(registry, "sf:energized_capacitor", "sf:electricity", Material.PLAYER_HEAD, "&aEnergized Energy Capacitor", "91361e576b493cbfdfae328661cedd1add55fab4e5eb418b92cebf6275f8bb4", null, new String[] {"&7Range: &c6 blocks", "", "&4End-Game Capacitor", "&8⇨ &e⚡ &7524288 J Capacity"});
        registerLegacyItem(registry, "sf:programmable_android", "sf:robots", Material.PLAYER_HEAD, "&cProgrammable Android &7(Normal)", "3503cb7ed845e7a507f569afc647c47ac483771465c9a679a54594c76afba", null, new String[] {"", "&8⇨ &7Function: None", "&8⇨ &7Fuel Efficiency: 1.0x"});
        registerLegacyItem(registry, "sf:programmable_android_farmer", "sf:robots", Material.PLAYER_HEAD, "&cProgrammable Android &7(Farmer)", "f9d33357e8418823bf783de92de80291b4ebd392aec8706698e06896d498f6", null, new String[] {"", "&8⇨ &7Function: Farming", "&8⇨ &7Fuel Efficiency: 1.0x"});
        registerLegacyItem(registry, "sf:programmable_android_miner", "sf:robots", Material.PLAYER_HEAD, "&cProgrammable Android &7(Miner)", "e638a28541ab3ae0a723d5578738e08758388ec4c33247bd4ca13482aef334", null, new String[] {"", "&8⇨ &7Function: Mining", "&8⇨ &7Fuel Efficiency: 1.0x"});
        registerLegacyItem(registry, "sf:programmable_android_woodcutter", "sf:robots", Material.PLAYER_HEAD, "&cProgrammable Android &7(Woodcutter)", "d32a814510142205169a1ad32f0a745f18e9cb6c66ee64eca2e65babdef9ff", null, new String[] {"", "&8⇨ &7Function: Woodcutting", "&8⇨ &7Fuel Efficiency: 1.0x"});
        registerLegacyItem(registry, "sf:programmable_android_butcher", "sf:robots", Material.PLAYER_HEAD, "&cProgrammable Android &7(Butcher)", "3b472df0ad9a3be88f2e5d5d422d02b116d64d8df1475ed32e546afc84b31", null, new String[] {"", "&8⇨ &7Function: Slaughtering", "&8⇨ &7Damage: 4", "&8⇨ &7Fuel Efficiency: 1.0x"});
        registerLegacyItem(registry, "sf:programmable_android_fisherman", "sf:robots", Material.PLAYER_HEAD, "&cProgrammable Android &7(Fisherman)", "345e8733a73114333b98b3601751241722f4713e1a1a5d36fbb132493f1c7", null, new String[] {"", "&8⇨ &7Function: Fishing", "&8⇨ &7Success Rate: 10%", "&8⇨ &7Fuel Efficiency: 1.0x"});
        registerLegacyItem(registry, "sf:programmable_android_2", "sf:robots", Material.PLAYER_HEAD, "&cAdvanced Programmable Android &7(Normal)", "3503cb7ed845e7a507f569afc647c47ac483771465c9a679a54594c76afba", null, new String[] {"", "&8⇨ &7Function: None", "&8⇨ &7Fuel Efficiency: 1.5x"});
        registerLegacyItem(registry, "sf:programmable_android_2_fisherman", "sf:robots", Material.PLAYER_HEAD, "&cAdvanced Programmable Android &7(Fisherman)", "345e8733a73114333b98b3601751241722f4713e1a1a5d36fbb132493f1c7", null, new String[] {"", "&8⇨ &7Function: Fishing", "&8⇨ &7Success Rate: 20%", "&8⇨ &7Fuel Efficiency: 1.5x"});
        registerLegacyItem(registry, "sf:programmable_android_2_farmer", "sf:robots", Material.PLAYER_HEAD, "&cAdvanced Programmable Android &7(Farmer)", "f9d33357e8418823bf783de92de80291b4ebd392aec8706698e06896d498f6", null, new String[] {"", "&8⇨ &7Function: Farming", "&8⇨ &7Fuel Efficiency: 1.5x", "&8⇨ &7Can also harvest Plants from ExoticGarden"});
        registerLegacyItem(registry, "sf:programmable_android_2_butcher", "sf:robots", Material.PLAYER_HEAD, "&cAdvanced Programmable Android &7(Butcher)", "3b472df0ad9a3be88f2e5d5d422d02b116d64d8df1475ed32e546afc84b31", null, new String[] {"", "&8⇨ &7Function: Slaughtering", "&8⇨ &7Damage: 8", "&8⇨ &7Fuel Efficiency: 1.5x"});
        registerLegacyItem(registry, "sf:programmable_android_3", "sf:robots", Material.PLAYER_HEAD, "&eEmpowered Programmable Android &7(Normal)", "3503cb7ed845e7a507f569afc647c47ac483771465c9a679a54594c76afba", null, new String[] {"", "&8⇨ &7Function: None", "&8⇨ &7Fuel Efficiency: 3.0x"});
        registerLegacyItem(registry, "sf:programmable_android_3_fisherman", "sf:robots", Material.PLAYER_HEAD, "&eEmpowered Programmable Android &7(Fisherman)", "345e8733a73114333b98b3601751241722f4713e1a1a5d36fbb132493f1c7", null, new String[] {"", "&8⇨ &7Function: Fishing", "&8⇨ &7Success Rate: 30%", "&8⇨ &7Fuel Efficiency: 8.0x"});
        registerLegacyItem(registry, "sf:programmable_android_3_butcher", "sf:robots", Material.PLAYER_HEAD, "&eEmpowered Programmable Android &7(Butcher)", "3b472df0ad9a3be88f2e5d5d422d02b116d64d8df1475ed32e546afc84b31", null, new String[] {"", "&8⇨ &7Function: Slaughtering", "&8⇨ &7Damage: 20", "&8⇨ &7Fuel Efficiency: 8.0x"});
        registerLegacyItem(registry, "sf:gps_transmitter", "sf:gps", Material.PLAYER_HEAD, "&bGPS Transmitter", "b0c9c1a022f40b73f14b4cba37c718c6a533f3a2864b6536d5f456934cc1f", null, new String[] {"", "&8⇨ &e⚡ &716 J Buffer", "&8⇨ &e⚡ &72 J/s"});
        registerLegacyItem(registry, "sf:gps_transmitter_2", "sf:gps", Material.PLAYER_HEAD, "&cAdvanced GPS Transmitter", "b0c9c1a022f40b73f14b4cba37c718c6a533f3a2864b6536d5f456934cc1f", null, new String[] {"", "&8⇨ &e⚡ &764 J Buffer", "&8⇨ &e⚡ &76 J/s"});
        registerLegacyItem(registry, "sf:gps_transmitter_3", "sf:gps", Material.PLAYER_HEAD, "&4Carbonado GPS Transmitter", "b0c9c1a022f40b73f14b4cba37c718c6a533f3a2864b6536d5f456934cc1f", null, new String[] {"", "&8⇨ &e⚡ &7256 J Buffer", "&8⇨ &e⚡ &722 J/s"});
        registerLegacyItem(registry, "sf:gps_transmitter_4", "sf:gps", Material.PLAYER_HEAD, "&eEnergized GPS Transmitter", "b0c9c1a022f40b73f14b4cba37c718c6a533f3a2864b6536d5f456934cc1f", null, new String[] {"", "&8⇨ &e⚡ &71024 J Buffer", "&8⇨ &e⚡ &792 J/s"});
        registerLegacyItem(registry, "sf:gps_marker_tool", "sf:gps", Material.REDSTONE_TORCH, "&bGPS Marker Tool", null, null, new String[] {"", "&fAllows you to set a Waypoint at", "&fthe Location you place this"});
        registerLegacyItem(registry, "sf:gps_control_panel", "sf:gps", Material.PLAYER_HEAD, "&bGPS Control Panel", "ddcfba58faf1f64847884111822b64afa21d7fc62d4481f14f3f3bcb6330", null, new String[] {"", "&fAllows you to track your Satellites", "&fand manage your Waypoints"});
        registerLegacyItem(registry, "sf:gps_emergency_transmitter", "sf:gps", Material.PLAYER_HEAD, "&cGPS Emergency Transmitter", "b0c9c1a022f40b73f14b4cba37c718c6a533f3a2864b6536d5f456934cc1f", null, new String[] {"", "&fCarrying this in your Inventory", "&fautomatically sets a Waypoint", "&fat your Location when you die."});
        registerLegacyItem(registry, "sf:android_interface_fuel", "sf:robots", Material.DISPENSER, "&7Android Interface &c(Fuel)", null, null, new String[] {"", "&fItems stored in this Interface", "&fwill be inserted into an Android's Fuel Slot", "&fwhen its Script tells them to do so"});
        registerLegacyItem(registry, "sf:android_interface_items", "sf:robots", Material.DISPENSER, "&7Android Interface &9(Items)", null, null, new String[] {"", "&fItems stored in an Android's Inventory", "&fwill be inserted into this Interface", "&fwhen its Script tells them to do so"});
        registerLegacyItem(registry, "sf:gps_geo_scanner", "sf:gps", Material.PLAYER_HEAD, "&bGPS Geo-Scanner", "2ad8cfeb387a56e3e5bcf85345d6a417b242293887db3ce3ba91fa409b254b86", null, new String[] {"", "&fScans a Chunk for natural Resources", "&fsuch as &8Oil"});
        registerLegacyItem(registry, "sf:portable_geo_scanner", "sf:gps", Material.CLOCK, "&bPortable Geo-Scanner", null, null, new String[] {"", "&fScans a Chunk for natural Resources", "", "&eRight Click&7 to scan"});
        registerLegacyItem(registry, "sf:geo_miner", "sf:gps", Material.PLAYER_HEAD, "&6GEO Miner", "a37741f764dd3dd7adaeb43b63d3959eb70e5eb28f15d6b34cab34a1d1f60387", null, new String[] {"", "&eMines up resources from the chunk", "&eThese Resources cannot be mined with a pickaxe", "", "&6Advanced Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &748 J/s", "", "&c&l! &cMake sure to Geo-Scan the Chunk first"});
        registerLegacyItem(registry, "sf:oil_pump", "sf:gps", Material.PLAYER_HEAD, "&4Oil Pump", "afe1a040a425e31a46d4f9a9b9806fa2f0c47ee84711cc1932fd8ab32b2d038", null, new String[] {"", "&7Pumps up Oil and fills it into Buckets", "", "&c&l! &cMake sure to Geo-Scan the Chunk first"});
        registerLegacyItem(registry, "sf:bucket_of_oil", "sf:resources", Material.PLAYER_HEAD, "&fBucket of Oil", "6ce04b41d19ec7927f982a63a94a3d79f78ecec33363051fde0831bfabdbd", null, new String[] {});
        registerLegacyItem(registry, "sf:bucket_of_fuel", "sf:resources", Material.PLAYER_HEAD, "&fBucket of Fuel", "a84ddca766725b8b97413f259c3f7668070f6ae55483a90c8e5525394f9c099", null, new String[] {});
        registerLegacyItem(registry, "sf:refinery", "sf:electricity", Material.PISTON, "&cRefinery", null, null, new String[] {"", "&fRefines Oil to create Fuel"});
        registerLegacyItem(registry, "sf:combustion_reactor", "sf:electricity", Material.PLAYER_HEAD, "&cCombustion Reactor", "9343ce58da54c79924a2c9331cfc417fe8ccbbea9be45a7ac85860a6c730", null, new String[] {"", "&6Advanced Generator", "&8⇨ &e⚡ &7256 J Buffer", "&8⇨ &e⚡ &724 J/s"});
        registerLegacyItem(registry, "sf:android_memory_core", "sf:technical_components", Material.PLAYER_HEAD, "&bAndroid Memory Core", "d78f2b7e5e75639ea7fb796c35d364c4df28b4243e66b76277aadcd6261337", null, new String[] {});
        registerLegacyItem(registry, "sf:gps_teleporter_pylon", "sf:gps", Material.PURPLE_STAINED_GLASS, "&5GPS Teleporter Pylon", null, null, new String[] {"", "&7Teleporter Component"});
        registerLegacyItem(registry, "sf:gps_teleportation_matrix", "sf:gps", Material.IRON_BLOCK, "&bGPS Teleporter Matrix", null, null, new String[] {"", "&fThis is your Teleporter's Main Component", "&fThis Matrix allows Players to choose from all", "&fWaypoints made by the Player who has placed", "&fthis Device."});
        registerLegacyItem(registry, "sf:gps_activation_device_shared", "sf:gps", Material.STONE_PRESSURE_PLATE, "&fGPS Activation Device &3(Shared)", null, null, new String[] {"", "&fPlace this onto a Teleportation Matrix", "&fand step onto this Plate to activate", "&fthe Teleportation Process"});
        registerLegacyItem(registry, "sf:gps_activation_device_personal", "sf:gps", Material.STONE_PRESSURE_PLATE, "&fGPS Activation Device &a(Personal)", null, null, new String[] {"", "&fPlace this onto a Teleportation Matrix", "&fand step onto this Plate to activate", "&fthe Teleportation Process", "", "&fThis Version only allows the Person who", "&fplaced this Device to use it"});
        registerLegacyItem(registry, "sf:portable_teleporter", "sf:gps", Material.COMPASS, "&bPortable Teleporter", null, null, new String[] {"", "&fThis device allows you to teleport", "&fto your waypoints from anywhere", "", "&8⇨ &e⚡ &70 / 50 J", "", "&eRight Click&7 to use"});
        registerLegacyItem(registry, "sf:elevator_plate", "sf:gps", Material.STONE_PRESSURE_PLATE, "&bElevator Plate", null, null, new String[] {"", "&fPlace an Elevator Plate on every floor", "&fand you will be able to teleport between them.", "", "&eRight Click this Block &7to name it"});
        registerLegacyItem(registry, "sf:infused_hopper", "sf:gadgets", Material.HOPPER, "&5Infused Hopper", null, null, new String[] {"", "&fAutomatically picks up nearby Items in a 7x7x7", "&fRadius when placed."});
        registerLegacyItem(registry, "sf:heated_pressure_chamber", "sf:electricity", Material.LIGHT_GRAY_STAINED_GLASS, "&cHeated Pressure Chamber", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &710 J/s"});
        registerLegacyItem(registry, "sf:heated_pressure_chamber_2", "sf:electricity", Material.LIGHT_GRAY_STAINED_GLASS, "&cHeated Pressure Chamber &7- &eII", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b5x", "&8⇨ &e⚡ &744 J/s"});
        registerLegacyItem(registry, "sf:electric_smeltery", "sf:electricity", Material.FURNACE, "&cElectric Smeltery", null, null, new String[] {"", "&4Alloys-Only, doesn't smelt Dust into Ingots", "", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &720 J/s"});
        registerLegacyItem(registry, "sf:electric_smeltery_2", "sf:electricity", Material.FURNACE, "&cElectric Smeltery &7- &eII", null, null, new String[] {"", "&4Alloys-Only, doesn't smelt Dust into Ingots", "", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b3x", "&8⇨ &e⚡ &740 J/s"});
        registerLegacyItem(registry, "sf:electric_press", "sf:electricity", Material.PLAYER_HEAD, "&eElectric Press", "8d5cf92bc79ec19f4106441affff1406a1367010dcafb197dd94cfca1a6de0fc", null, new String[] {"", "&aMedium Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &716 J/s"});
        registerLegacyItem(registry, "sf:electric_press_2", "sf:electricity", Material.PLAYER_HEAD, "&eElectric Press &7- &eII", "8d5cf92bc79ec19f4106441affff1406a1367010dcafb197dd94cfca1a6de0fc", null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b3x", "&8⇨ &e⚡ &740 J/s"});
        registerLegacyItem(registry, "sf:electrified_crucible", "sf:electricity", Material.RED_TERRACOTTA, "&cElectrified Crucible", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &748 J/s"});
        registerLegacyItem(registry, "sf:electrified_crucible_2", "sf:electricity", Material.RED_TERRACOTTA, "&cElectrified Crucible &7- &eII", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b2x", "&8⇨ &e⚡ &780 J/s"});
        registerLegacyItem(registry, "sf:electrified_crucible_3", "sf:electricity", Material.RED_TERRACOTTA, "&cElectrified Crucible &7- &eIII", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b4x", "&8⇨ &e⚡ &7120 J/s"});
        registerLegacyItem(registry, "sf:carbon_press", "sf:electricity", Material.BLACK_STAINED_GLASS, "&cCarbon Press", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &720 J/s"});
        registerLegacyItem(registry, "sf:carbon_press_2", "sf:electricity", Material.BLACK_STAINED_GLASS, "&cCarbon Press &7- &eII", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b3x", "&8⇨ &e⚡ &750 J/s"});
        registerLegacyItem(registry, "sf:carbon_press_3", "sf:electricity", Material.BLACK_STAINED_GLASS, "&cCarbon Press &7- &eIII", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b15x", "&8⇨ &e⚡ &7180 J/s"});
        registerLegacyItem(registry, "sf:blistering_ingot", "sf:ingots", Material.GOLD_INGOT, "&6Blistering Ingot &7(33%)", null, null, new String[] {"", "&a☢&7 Radiation level: &6HIGH", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:blistering_ingot_2", "sf:ingots", Material.GOLD_INGOT, "&6Blistering Ingot &7(66%)", null, null, new String[] {"", "&a☢&7 Radiation level: &cVERY HIGH", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:blistering_ingot_3", "sf:ingots", Material.GOLD_INGOT, "&6Blistering Ingot", null, null, new String[] {"", "&a☢&7 Radiation level: &cVERY HIGH", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:energy_regulator", "sf:technical_components", Material.PLAYER_HEAD, "&6Energy Regulator", "d78f2b7e5e75639ea7fb796c35d364c4df28b4243e66b76277aadcd6261337", null, new String[] {"", "&fCore Component of an Energy Network"});
        registerLegacyItem(registry, "sf:energy_connector", "sf:electricity", Material.PLAYER_HEAD, "&eEnergy Connector", "1085e098756b995b00241644089c55a8f9acde35b9a37785d5e057a923613b", null, new String[] {"&7Range: &c6 blocks", "", "&fPlace this between machines", "&fand generators to connect them", "&fto your regulator."});
        registerLegacyItem(registry, "sf:debug_fish", "sf:gadgets", Material.SALMON, "&3How much is the Fish?", null, null, new String[] {"", "&eRight Click &fany Block to view it's BlockData", "&eLeft Click &fto break a Block", "&eShift + Left Click &fany Block to erase it's BlockData", "&eShift + Right Click &fto place a Placeholder Block"});
        registerLegacyItem(registry, "sf:nether_ice", "sf:resources", Material.PLAYER_HEAD, "&eNether Ice", "3ce2dad9baf7eaba7e80d4d0f9fac0aab01a76b12fb71c3d2af2a16fdd4c7383", null, new String[] {"", "&a☢&7 Radiation level: &eMODERATE", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:enriched_nether_ice", "sf:resources", Material.PLAYER_HEAD, "&eEnriched Nether Ice", "7c818aa13aabc7294838d21caac057e97bd8c89641a0c0f8a55442ff4e27", null, new String[] {"", "&a☢&7 Radiation level: &cVERY HIGH", "&8⇨ &4Hazmat Suit required!"});
        registerLegacyItem(registry, "sf:nether_ice_coolant_cell", "sf:resources", Material.PLAYER_HEAD, "&6Nether Ice Coolant Cell", "8d3cd412555f897016213e5d6c7431b448b9e5644e1b19ec51b5316f35840e0", null, new String[] {});
        registerLegacyItem(registry, "sf:cargo_manager", "sf:cargo", Material.PLAYER_HEAD, "&6Cargo Manager", "e510bc85362a130a6ff9d91ff11d6fa46d7d1912a3431f751558ef3c4d9c2", null, new String[] {"", "&fCore Component of an Item Transport Network"});
        registerLegacyItem(registry, "sf:cargo_node", "sf:cargo", Material.PLAYER_HEAD, "&7Cargo Node &c(Connector)", "07b7ef6fd7864865c31c1dc87bed24ab5973579f5c6638fecb8dedeb443ff0", null, new String[] {"", "&fCargo Connector Pipe"});
        registerLegacyItem(registry, "sf:cargo_node_input", "sf:cargo", Material.PLAYER_HEAD, "&7Cargo Node &c(Input)", "16d1c1a69a3de9fec962a77bf3b2e376dd25c873a3d8f14f1dd345dae4c4", null, new String[] {"", "&fCargo Input Pipe"});
        registerLegacyItem(registry, "sf:cargo_node_output", "sf:cargo", Material.PLAYER_HEAD, "&7Cargo Node &c(Output)", "55b21fd480c1c43bf3b9f842c869bdc3bc5acc2599bf2eb6b8a1c95dce978f", null, new String[] {"", "&fCargo Output Pipe"});
        registerLegacyItem(registry, "sf:cargo_node_output_advanced", "sf:cargo", Material.PLAYER_HEAD, "&6Advanced Cargo Node &c(Output)", "55b21fd480c1c43bf3b9f842c869bdc3bc5acc2599bf2eb6b8a1c95dce978f", null, new String[] {"", "&fCargo Output Pipe"});
        registerLegacyItem(registry, "sf:auto_breeder", "sf:electricity", Material.HAY_BLOCK, "&eAuto-Breeder", null, null, new String[] {"", "&fRuns on &aOrganic Food", "", "&4End-Game Machine", "&8⇨ &e⚡ &71024 J Buffer", "&8⇨ &e⚡ &760 J/Animal"});
        registerLegacyItem(registry, "sf:produce_collector", "sf:electricity", Material.HAY_BLOCK, "&bProduce Collector", null, null, new String[] {"", "&fThis machine allows you to", "&fcollect produce from nearby animals.", "", "&6Advanced Machine", "&8⇨ &e⚡ &7512 J Buffer", "&8⇨ &e⚡ &732 J/s"});
        registerLegacyItem(registry, "sf:organic_food", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9???"});
        registerLegacyItem(registry, "sf:organic_food_wheat", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Wheat"});
        registerLegacyItem(registry, "sf:organic_food_carrot", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Carrots"});
        registerLegacyItem(registry, "sf:organic_food_potato", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Potatoes"});
        registerLegacyItem(registry, "sf:organic_food_seeds", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Seeds"});
        registerLegacyItem(registry, "sf:organic_food_beetroot", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Beetroot"});
        registerLegacyItem(registry, "sf:organic_food_melon", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Melon"});
        registerLegacyItem(registry, "sf:organic_food_apple", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Apple"});
        registerLegacyItem(registry, "sf:organic_food_sweet_berries", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Sweet Berries"});
        registerLegacyItem(registry, "sf:organic_food_kelp", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Dried Kelp"});
        registerLegacyItem(registry, "sf:organic_food_cocoa", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Cocoa Beans"});
        registerLegacyItem(registry, "sf:organic_food_seagrass", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Food", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Seagrass"});
        registerLegacyItem(registry, "sf:fertilizer", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9???"});
        registerLegacyItem(registry, "sf:fertilizer_wheat", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Wheat"});
        registerLegacyItem(registry, "sf:fertilizer_carrot", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Carrots"});
        registerLegacyItem(registry, "sf:fertilizer_potato", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Potatoes"});
        registerLegacyItem(registry, "sf:fertilizer_seeds", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Seeds"});
        registerLegacyItem(registry, "sf:fertilizer_beetroot", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Beetroot"});
        registerLegacyItem(registry, "sf:fertilizer_melon", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Melon"});
        registerLegacyItem(registry, "sf:fertilizer_apple", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Apple"});
        registerLegacyItem(registry, "sf:fertilizer_sweet_berries", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Sweet Berries"});
        registerLegacyItem(registry, "sf:fertilizer_kelp", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Dried Kelp"});
        registerLegacyItem(registry, "sf:fertilizer_cocoa", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Cocoa beans"});
        registerLegacyItem(registry, "sf:fertilizer_seagrass", "sf:resources", Material.PLAYER_HEAD, "&aOrganic Fertilizer", "b439e3f5acbee9be4c4259289d6d9f35c635ffa661114687b3ea6dda8c79", null, new String[] {"&7Content: &9Seagrass"});
        registerLegacyItem(registry, "sf:animal_growth_accelerator", "sf:electricity", Material.HAY_BLOCK, "&bAnimal Growth Accelerator", null, null, new String[] {"", "&fRuns on &aOrganic Food", "", "&4End-Game Machine", "&8⇨ &e⚡ &71024 J Buffer", "&8⇨ &e⚡ &728 J/s"});
        registerLegacyItem(registry, "sf:crop_growth_accelerator", "sf:electricity", Material.LIME_TERRACOTTA, "&aCrop Growth Accelerator", null, null, new String[] {"", "&fRuns on &aOrganic Fertilizer", "", "&4End-Game Machine", "&8⇨ &7Radius: 7x7", "&8⇨ &7Speed: &a3/time", "&8⇨ &e⚡ &71024 J Buffer", "&8⇨ &e⚡ &750 J/s"});
        registerLegacyItem(registry, "sf:crop_growth_accelerator_2", "sf:electricity", Material.LIME_TERRACOTTA, "&aCrop Growth Accelerator &7(&eII&7)", null, null, new String[] {"", "&fRuns on &aOrganic Fertilizer", "", "&4End-Game Machine", "&8⇨ &7Radius: 9x9", "&8⇨ &7Speed: &a4/time", "&8⇨ &e⚡ &71024 J Buffer", "&8⇨ &e⚡ &760 J/s"});
        registerLegacyItem(registry, "sf:tree_growth_accelerator", "sf:electricity", Material.BROWN_TERRACOTTA, "&aTree Growth Accelerator", null, null, new String[] {"", "&fRuns on &aOrganic Fertilizer", "", "&4End-Game Machine", "&8⇨ &7Radius: 9x9", "&8⇨ &7Speed: &a4/time", "&8⇨ &e⚡ &71024 J Buffer", "&8⇨ &e⚡ &748 J/s"});
        registerLegacyItem(registry, "sf:food_fabricator", "sf:electricity", Material.GREEN_STAINED_GLASS, "&cFood Fabricator", null, null, new String[] {"", "&fProduces &aOrganic Food", "", "&6Advanced Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &7256 J Buffer", "&8⇨ &e⚡ &714 J/s"});
        registerLegacyItem(registry, "sf:food_fabricator_2", "sf:electricity", Material.GREEN_STAINED_GLASS, "&cFood Fabricator &7(&eII&7)", null, null, new String[] {"", "&fProduces &aOrganic Food", "", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b6x", "&8⇨ &e⚡ &7512 J Buffer", "&8⇨ &e⚡ &748 J/s"});
        registerLegacyItem(registry, "sf:food_composter", "sf:electricity", Material.GREEN_TERRACOTTA, "&cFood Composter", null, null, new String[] {"", "&fProduces &aOrganic Fertilizer", "", "&6Advanced Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &7256 J Buffer", "&8⇨ &e⚡ &716 J/s"});
        registerLegacyItem(registry, "sf:food_composter_2", "sf:electricity", Material.GREEN_TERRACOTTA, "&cFood Composter &7(&eII&7)", null, null, new String[] {"", "&fProduces &aOrganic Fertilizer", "", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b10x", "&8⇨ &e⚡ &7512 J Buffer", "&8⇨ &e⚡ &752 J/s"});
        registerLegacyItem(registry, "sf:xp_collector", "sf:electricity", Material.PLAYER_HEAD, "&aEXP Collector", "1762a15b04692a2e4b3fb3663bd4b78434dce1732b8eb1c7a9f7c0fbf6f", null, new String[] {"", "&fCollects nearby Exp and stores it", "", "&4End-Game Machine", "&8⇨ &e⚡ &71024 J Buffer", "&8⇨ &e⚡ &720 J/s"});
        registerLegacyItem(registry, "sf:reactor_collant_cell", "sf:resources", Material.PLAYER_HEAD, "&bReactor Coolant Cell", "de4073be40cb3deb310a0be959b4cac68e825372728fafb6c2973e4e7c33", null, new String[] {});
        registerLegacyItem(registry, "sf:nuclear_reactor", "sf:electricity", Material.PLAYER_HEAD, "&2Nuclear Reactor", "fa5de0bc2bfb5cc2d23eb72f96402ada479524dd0de404bc23b6dacee3ffd080", null, new String[] {"", "&fRequires Cooling!", "&8⇨ &bMust be surrounded by Water", "&8⇨ &bMust be supplied with Reactor Coolant Cells", "", "&4End-Game Generator", "&8⇨ &e⚡ &716384 J Buffer", "&8⇨ &e⚡ &7500 J/s"});
        registerLegacyItem(registry, "sf:netherstar_reactor", "sf:electricity", Material.PLAYER_HEAD, "&fNether Star Reactor", "a11ed1d1b25b624665ecdddc3d3a5dff0b9f35e3de77a12f516e60fe8501cc8d", null, new String[] {"", "&fRuns on Nether Stars", "&8⇨ &bMust be surrounded by Water", "&8⇨ &bMust be supplied with Nether Ice Coolant Cells", "", "&4End-Game Generator", "&8⇨ &e⚡ &732768 J Buffer", "&8⇨ &e⚡ &71024 J/s", "&8⇨ &4Causes nearby Entities to get Withered"});
        registerLegacyItem(registry, "sf:reactor_access_port", "sf:cargo", Material.CYAN_TERRACOTTA, "&2Reactor Access Port", null, null, new String[] {"", "&fAllows you to interact with a Reactor", "&fvia Cargo Nodes, can also be used", "&fas a Buffer", "", "&8⇨ &eMust be placed &a3 Blocks &eabove the Reactor"});
        registerLegacyItem(registry, "sf:freezer", "sf:electricity", Material.LIGHT_BLUE_STAINED_GLASS, "&bFreezer", null, null, new String[] {"", "&6Advanced Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &7256 J Buffer", "&8⇨ &e⚡ &718 J/s"});
        registerLegacyItem(registry, "sf:freezer_2", "sf:electricity", Material.LIGHT_BLUE_STAINED_GLASS, "&bFreezer &7(&eII&7)", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b2x", "&8⇨ &e⚡ &7256 J Buffer", "&8⇨ &e⚡ &730 J/s"});
        registerLegacyItem(registry, "sf:freezer_3", "sf:electricity", Material.LIGHT_BLUE_STAINED_GLASS, "&bFreezer &7(&eIII&7)", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b3x", "&8⇨ &e⚡ &7256 J Buffer", "&8⇨ &e⚡ &742 J/s"});
        registerLegacyItem(registry, "sf:electric_gold_pan", "sf:electricity", Material.BROWN_TERRACOTTA, "&6Electric Gold Pan", null, null, new String[] {"", "&eBasic Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &72 J/s"});
        registerLegacyItem(registry, "sf:electric_gold_pan_2", "sf:electricity", Material.BROWN_TERRACOTTA, "&6Electric Gold Pan &7(&eII&7)", null, null, new String[] {"", "&eBasic Machine", "&8⇨ &b⚡ &7Speed: &b3x", "&8⇨ &e⚡ &74 J/s"});
        registerLegacyItem(registry, "sf:electric_gold_pan_3", "sf:electricity", Material.BROWN_TERRACOTTA, "&6Electric Gold Pan &7(&eIII&7)", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b10x", "&8⇨ &e⚡ &714 J/s"});
        registerLegacyItem(registry, "sf:electric_dust_washer", "sf:electricity", Material.BLUE_STAINED_GLASS, "&3Electric Dust Washer", null, null, new String[] {"", "&eBasic Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &76 J/s"});
        registerLegacyItem(registry, "sf:electric_dust_washer_2", "sf:electricity", Material.BLUE_STAINED_GLASS, "&3Electric Dust Washer &7(&eII&7)", null, null, new String[] {"", "&eBasic Machine", "&8⇨ &b⚡ &7Speed: &b2x", "&8⇨ &e⚡ &710 J/s"});
        registerLegacyItem(registry, "sf:electric_dust_washer_3", "sf:electricity", Material.BLUE_STAINED_GLASS, "&3Electric Dust Washer &7(&eIII&7)", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b10x", "&8⇨ &e⚡ &730 J/s"});
        registerLegacyItem(registry, "sf:electric_ingot_factory", "sf:electricity", Material.RED_TERRACOTTA, "&cElectric Ingot Factory", null, null, new String[] {"", "&eBasic Machine", "&8⇨ &b⚡ &7Speed: &b1x", "&8⇨ &e⚡ &78 J/s"});
        registerLegacyItem(registry, "sf:electric_ingot_factory_2", "sf:electricity", Material.RED_TERRACOTTA, "&cElectric Ingot Factory &7(&eII&7)", null, null, new String[] {"", "&eBasic Machine", "&8⇨ &b⚡ &7Speed: &b2x", "&8⇨ &e⚡ &714 J/s"});
        registerLegacyItem(registry, "sf:electric_ingot_factory_3", "sf:electricity", Material.RED_TERRACOTTA, "&cElectric Ingot Factory &7(&eIII&7)", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &b⚡ &7Speed: &b8x", "&8⇨ &e⚡ &740 J/s"});
        registerLegacyItem(registry, "sf:fluid_pump", "sf:electricity", Material.BLUE_TERRACOTTA, "&9Fluid Pump", null, null, new String[] {"", "&6Advanced Machine", "&8⇨ &e⚡ &732 J/Block"});
        registerLegacyItem(registry, "sf:charging_bench", "sf:electricity", Material.CRAFTING_TABLE, "&6Charging Bench", null, null, new String[] {"", "&fCharges Items such as Jetpacks", "", "&eBasic Machine", "&8⇨ &e⚡ &7128 J Buffer", "&8⇨ &e⚡ &7Energy Loss: &c50%"});
        registerLegacyItem(registry, "sf:vanilla_auto_crafter", "sf:cargo", Material.PLAYER_HEAD, "&2Auto-Crafter &8(Vanilla)", "80a4334f6a61e40c0c63deb665fa7b581e6eb259f7a3207ced7a1ff8bdc8a9f9", null, new String[] {"", "&fPlace this machine on top of a", "&fchest or similar and make it craft", "&fanything that can be crafted using a", "&fnormal &eCrafting Table", "", "&6Advanced Machine", "&8⇨ &e⚡ &716 J/Item"});
        registerLegacyItem(registry, "sf:enhanced_auto_crafter", "sf:cargo", Material.PLAYER_HEAD, "&2Auto-Crafter &8(Enhanced)", "5038298306a5e28584df39e88896917c38d40a326226d8c83070723c95798b24", null, new String[] {"", "&fPlace this machine on top of a", "&fchest or similar and make it craft", "&fanything that can be crafted using an", "&eEnhanced Crafting Table", "", "&6Advanced Machine", "&8⇨ &e⚡ &716 J/Item"});
        registerLegacyItem(registry, "sf:armor_auto_crafter", "sf:electricity", Material.PLAYER_HEAD, "&2Auto-Crafter &8(Armor Forge)", "5cbd9f5ec1ed007259996491e69ff649a3106cf920227b1bb3a71ee7a89863f", null, new String[] {"", "&fPlace this machine on top of a", "&fchest or similar and make it craft", "&fanything that can be crafted using an", "&eArmor Forge", "", "&6Advanced Machine", "&8⇨ &e⚡ &732 J/Item"});
        registerLegacyItem(registry, "sf:iron_golem_assembler", "sf:electricity", Material.IRON_BLOCK, "&6Iron Golem Assembler", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &7Cooldown: &b30 Seconds", "&8⇨ &e⚡ &74096 J Buffer", "&8⇨ &e⚡ &72048 J/Golem"});
        registerLegacyItem(registry, "sf:wither_assembler", "sf:electricity", Material.OBSIDIAN, "&5Wither Assembler", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &7Cooldown: &b30 Seconds", "&8⇨ &e⚡ &74096 J Buffer", "&8⇨ &e⚡ &74096 J/Wither"});
        registerLegacyItem(registry, "sf:trash_can_block", "sf:cargo", Material.PLAYER_HEAD, "&3Trash Can", "32d41042ce99147cc38cac9e46741576e7ee791283e6fac8d3292cae2935f1f", null, new String[] {"", "&fWill destroy all Items put into it"});
        registerLegacyItem(registry, "sf:elytra_scale", "sf:armor", Material.FEATHER, "&bElytra Scale", null, null, new String[] {});
        registerLegacyItem(registry, "sf:infused_elytra", "sf:armor", Material.ELYTRA, "&5Infused Elytra", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_elytra", "sf:armor", Material.ELYTRA, "&cSoulbound Elytra", null, null, new String[] {});
        registerLegacyItem(registry, "sf:magnesium_salt", "sf:resources", Material.SUGAR, "&cMagnesium Salt", null, null, new String[] {"", "&7A special type of fuel that can be", "&7used in a Magnesium-powered Generator"});
        registerLegacyItem(registry, "sf:magnesium_generator", "sf:electricity", Material.PLAYER_HEAD, "&cMagnesium-powered Generator", "9343ce58da54c79924a2c9331cfc417fe8ccbbea9be45a7ac85860a6c730", null, new String[] {"", "&aMedium Generator", "&8⇨ &e⚡ &7128 J Buffer", "&8⇨ &e⚡ &736 J/s"});
        registerLegacyItem(registry, "sf:birthday_cake", "sf:seasonal", Material.CAKE, "&bBirthday Cake", null, null, new String[] {});
    }


    private static void registerEnderTalismanVariant(SfxItemRegistry registry, String baseId, String baseName) {
        String strippedName = baseName.replace("&a", "").replace("&6", "").replace("&5", "").replace("&7", "").replace("&f", "");
        registerLegacyItem(registry, "sf:ender_" + baseId, "sf:talisman", Material.EMERALD, "&5Ender " + strippedName, null, null, new String[] {
                "&7&oEnder Infused",
                "",
                "&7Works from your Ender Chest",
                "&7as the Tier II variant of",
                baseName
        });
    }

    private static void registerLegacyItem(SfxItemRegistry registry, String id, String categoryId, Material material, String legacyName, String textureHash, Integer colorRgb, String[] legacyLore) {
        SfxItemDefinition.Builder builder = SfxItemDefinition.builder(id, material, Text.legacy(legacyName))
                .category(categoryId)
                .flag("legacy-sf");
        if (textureHash != null) {
            builder.headTexture(textureHash);
        }
        if (colorRgb != null) {
            builder.colorRgb(colorRgb);
        }
        if (legacyLore != null) {
            for (String line : legacyLore) {
                builder.addLore(Text.legacy(line));
            }
        }
        applyLegacyVisualMetadata(id, builder);
        registry.registerItem(builder.build());
    }

    private static void applyLegacyVisualMetadata(String id, SfxItemDefinition.Builder builder) {
        applyLegacyFunctionFlags(id, builder);
        applyLegacyEnchantments(id, builder);
        if (!needsLegacyGlint(id) || LEGACY_ENCHANTMENTS.containsKey(id)) {
            return;
        }
        builder.enchantment("minecraft:unbreaking", 1);
        builder.itemFlag("HIDE_ENCHANTS");
    }

    private static void applyLegacyEnchantments(String id, SfxItemDefinition.Builder builder) {
        Map<String, Integer> enchantments = LEGACY_ENCHANTMENTS.get(id);
        if (enchantments == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            builder.enchantment(entry.getKey(), entry.getValue());
        }
    }

    private static void applyLegacyFunctionFlags(String id, SfxItemDefinition.Builder builder) {
        if (id == null) {
            return;
        }
        if (isTalismanId(id)) {
            builder.flag("talisman");
            builder.flag("talisman-" + talismanType(id));
            if (isEnderTalismanId(id)) {
                builder.flag("ender-talisman");
            }
        }
        if (id.equals("sf:glowstone_helmet")
                || id.equals("sf:glowstone_chestplate")
                || id.equals("sf:glowstone_leggings")
                || id.equals("sf:glowstone_boots")
                || id.equals("sf:night_vision_goggles")) {
            builder.flag("armor-night-vision");
        }
        if (id.equals("sf:slime_leggings") || id.equals("sf:slime_steel_leggings")) {
            builder.flag("armor-speed");
        }
        if (id.equals("sf:slime_boots") || id.equals("sf:slime_steel_boots") || id.equals("sf:bee_boots")) {
            builder.flag("armor-jump");
        }
        if (id.equals("sf:slime_boots") || id.equals("sf:slime_steel_boots") || id.equals("sf:boots_of_the_stomper") || id.equals("sf:bee_boots") || id.endsWith("_jetboots")) {
            builder.flag("armor-no-fall");
        }
        if (id.equals("sf:farmer_shoes")) {
            builder.flag("armor-farmland-safe");
        }
        if (id.equals("sf:scuba_helmet")) {
            builder.flag("armor-water-breathing");
        }
        if (id.equals("sf:hazmat_chestplate")) {
            builder.flag("armor-fire-resistance");
        }
        if (id.equals("sf:boots_of_the_stomper")) {
            builder.flag("armor-stomper");
        }
        if (id.equals("sf:bee_wings")) {
            builder.flag("armor-bee-wings");
        }
        if (id.equals("sf:elytra_cap")) {
            builder.flag("armor-elytra-impact");
        }
        if (id.equals("sf:ender_boots")) {
            builder.flag("armor-ender-pearl-safe");
        }
        if (id.startsWith("sf:hazmat_") || id.equals("sf:scuba_helmet")) {
            builder.flag("armor-hazmat");
        }
        if (id.startsWith("sf:ender_") && !isEnderTalismanId(id)) {
            builder.flag("armor-ender");
        }
    }

    private static boolean isTalismanId(String id) {
        return id.equals("sf:common_talisman") || id.equals("sf:ender_talisman") || id.endsWith("_talisman");
    }

    private static boolean isEnderTalismanId(String id) {
        return id.equals("sf:ender_talisman") || (id.startsWith("sf:ender_") && id.endsWith("_talisman"));
    }

    private static String talismanType(String id) {
        String normalized = id.substring("sf:".length());
        if (normalized.equals("common_talisman") || normalized.equals("ender_talisman")) {
            return "common";
        }
        if (normalized.startsWith("ender_")) {
            normalized = normalized.substring("ender_".length());
        }
        return normalized.substring(0, normalized.length() - "_talisman".length());
    }

    private static boolean needsLegacyGlint(String id) {
        if (id == null) {
            return false;
        }
        return isTalismanId(id)
                || id.startsWith("sf:staff_")
                || id.contains("soulbound")
                || id.equals("sf:magic_eye_of_ender")
                || id.equals("sf:infused_magnet")
                || id.equals("sf:infused_hopper")
                || id.equals("sf:necrotic_skull")
                || id.equals("sf:infused_elytra")
                || id.equals("sf:explosive_bow")
                || id.equals("sf:icy_bow")
                || id.equals("sf:smelters_pickaxe")
                || id.equals("sf:lumber_axe")
                || id.equals("sf:explosive_pickaxe")
                || id.equals("sf:explosive_shovel")
                || id.startsWith("sf:pickaxe_of_");
    }

    private static Map<String, Map<String, Integer>> createLegacyEnchantments() {
        Map<String, Map<String, Integer>> map = new LinkedHashMap<>();
        map.put("sf:grandmas_walking_stick", enchantments(entry("knockback", 2)));
        map.put("sf:grandpas_walking_stick", enchantments(entry("knockback", 5)));
        map.put("sf:blade_of_vampires", enchantments(
                entry("fire_aspect", 2),
                entry("unbreaking", 4),
                entry("sharpness", 2)
        ));
        map.put("sf:cobalt_pickaxe", enchantments(
                entry("unbreaking", 10),
                entry("efficiency", 6)
        ));
        map.put("sf:cactus_helmet", enchantments(entry("thorns", 3), entry("unbreaking", 6)));
        map.put("sf:cactus_chestplate", enchantments(entry("thorns", 3), entry("unbreaking", 6)));
        map.put("sf:cactus_leggings", enchantments(entry("thorns", 3), entry("unbreaking", 6)));
        map.put("sf:cactus_boots", enchantments(entry("thorns", 3), entry("unbreaking", 6)));
        map.put("sf:damascus_steel_helmet", enchantments(entry("unbreaking", 5), entry("protection", 5)));
        map.put("sf:damascus_steel_chestplate", enchantments(entry("unbreaking", 5), entry("protection", 5)));
        map.put("sf:damascus_steel_leggings", enchantments(entry("unbreaking", 5), entry("protection", 5)));
        map.put("sf:damascus_steel_boots", enchantments(entry("unbreaking", 5), entry("protection", 5)));
        map.put("sf:reinforced_alloy_helmet", enchantments(entry("unbreaking", 9), entry("protection", 9)));
        map.put("sf:reinforced_alloy_chestplate", enchantments(entry("unbreaking", 9), entry("protection", 9)));
        map.put("sf:reinforced_alloy_leggings", enchantments(entry("unbreaking", 9), entry("protection", 9)));
        map.put("sf:reinforced_alloy_boots", enchantments(entry("unbreaking", 9), entry("protection", 9)));
        map.put("sf:gilded_iron_helmet", enchantments(entry("unbreaking", 6), entry("protection", 8)));
        map.put("sf:gilded_iron_chestplate", enchantments(entry("unbreaking", 6), entry("protection", 8)));
        map.put("sf:gilded_iron_leggings", enchantments(entry("unbreaking", 6), entry("protection", 8)));
        map.put("sf:gilded_iron_boots", enchantments(entry("unbreaking", 6), entry("protection", 8)));
        map.put("sf:gold_12k_helmet", enchantments(entry("unbreaking", 10)));
        map.put("sf:gold_12k_chestplate", enchantments(entry("unbreaking", 10)));
        map.put("sf:gold_12k_leggings", enchantments(entry("unbreaking", 10)));
        map.put("sf:gold_12k_boots", enchantments(entry("unbreaking", 10)));
        map.put("sf:slime_steel_helmet", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:slime_steel_chestplate", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:slime_steel_leggings", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:slime_steel_boots", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:bee_helmet", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:bee_wings", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:bee_leggings", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:bee_boots", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:staff_elemental_wind", enchantments(entry("luck", 1)));
        map.put("sf:staff_elemental_fire", enchantments(entry("fire_aspect", 5)));
        map.put("sf:staff_elemental_water", enchantments(entry("aqua_affinity", 1)));
        map.put("sf:staff_elemental_storm", enchantments(entry("unbreaking", 1)));
        map.put("sf:infused_elytra", enchantments(entry("mending", 1)));
        return Map.copyOf(map);
    }

    private static Map<String, Integer> enchantments(Map.Entry<String, Integer>... entries) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            values.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(values);
    }

    private static Map.Entry<String, Integer> entry(String enchantment, int level) {
        return Map.entry(enchantment, level);
    }

    private static ItemStack icon(Material material, net.kyori.adventure.text.Component name, String textureHash, Integer colorRgb) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (material == Material.PLAYER_HEAD && textureHash != null) {
                HeadTextures.apply(meta, textureHash);
            }
            if (colorRgb != null) {
                applyColor(meta, colorRgb);
            }
            meta.displayName(Text.noItalic(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void applyColor(ItemMeta meta, int rgb) {
        Color color = Color.fromRGB(rgb);
        if (meta instanceof LeatherArmorMeta leatherArmorMeta) {
            leatherArmorMeta.setColor(color);
        } else if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setColor(color);
        } else if (meta instanceof FireworkEffectMeta fireworkEffectMeta) {
            fireworkEffectMeta.setEffect(FireworkEffect.builder().withColor(color).build());
        }
    }
}
