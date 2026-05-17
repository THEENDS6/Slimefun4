package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemRegistry;
import org.bukkit.Material;

import static cc.theends6.sfx.internal.bootstrap.LegacySfBootstrapSupport.registerEnderTalismanVariant;
import static cc.theends6.sfx.internal.bootstrap.LegacySfBootstrapSupport.registerLegacyItem;

final class LegacySfItemBootstrapPart4 {
    private LegacySfItemBootstrapPart4() {
    }

    static void register(SfxItemRegistry registry) {
        registerLegacyItem(registry, "sf:enhanced_auto_crafter", "sf:cargo", Material.PLAYER_HEAD, "&2Auto-Crafter &8(Enhanced)", "5038298306a5e28584df39e88896917c38d40a326226d8c83070723c95798b24", null, new String[] {"", "&fPlace this machine on top of a", "&fchest or similar and make it craft", "&fanything that can be crafted using an", "&eEnhanced Crafting Table", "", "&6Advanced Machine", "&8⇨ &e⚡&75120 J Buffer", "&8⇨ &e⚡&732 J/t", "&8⇨ &e⚡&7Work Time: &b10 ticks"});
        registerLegacyItem(registry, "sf:armor_auto_crafter", "sf:cargo", Material.PLAYER_HEAD, "&2Auto-Crafter &8(Armor Forge)", "5cbd9f5ec1ed007259996491e69ff649a3106cf920227b1bb3a71ee7a89863f", null, new String[] {"", "&fPlace this machine on top of a", "&fchest or similar and make it craft", "&fanything that can be crafted using an", "&eArmor Forge", "", "&6Advanced Machine", "&8⇨ &e⚡&75120 J Buffer", "&8⇨ &e⚡&764 J/t", "&8⇨ &e⚡&7Work Time: &b10 ticks"});
        registerLegacyItem(registry, "sf:iron_golem_assembler", "sf:electricity", Material.IRON_BLOCK, "&6Iron Golem Assembler", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &7Work Time: &b1.5 Seconds", "&8⇨ &e⚡&781920 J Buffer", "&8⇨ &e⚡&775 J/t"});
        registerLegacyItem(registry, "sf:wither_assembler", "sf:electricity", Material.OBSIDIAN, "&5Wither Assembler", null, null, new String[] {"", "&4End-Game Machine", "&8⇨ &7Work Time: &b1.5 Seconds", "&8⇨ &e⚡&781920 J Buffer", "&8⇨ &e⚡&7150 J/t"});
        registerLegacyItem(registry, "sf:trash_can_block", "sf:cargo", Material.PLAYER_HEAD, "&3Trash Can", "32d41042ce99147cc38cac9e46741576e7ee791283e6fac8d3292cae2935f1f", null, new String[] {"", "&fWill destroy all Items put into it"});
        registerLegacyItem(registry, "sf:elytra_scale", "sf:armor", Material.FEATHER, "&bElytra Scale", null, null, new String[] {});
        registerLegacyItem(registry, "sf:infused_elytra", "sf:armor", Material.ELYTRA, "&5Infused Elytra", null, null, new String[] {});
        registerLegacyItem(registry, "sf:soulbound_elytra", "sf:armor", Material.ELYTRA, "&cSoulbound Elytra", null, null, new String[] {});
        registerLegacyItem(registry, "sf:magnesium_salt", "sf:resources", Material.SUGAR, "&cMagnesium Salt", null, null, new String[] {"", "&7A special type of fuel that can be", "&7used in a Magnesium-powered Generator"});
        registerLegacyItem(registry, "sf:magnesium_generator", "sf:electricity", Material.PLAYER_HEAD, "&cMagnesium-powered Generator", "9343ce58da54c79924a2c9331cfc417fe8ccbbea9be45a7ac85860a6c730", null, new String[] {"", "&aMedium Generator", "&8⇨ &e⚡&72560 J Buffer", "&8⇨ &e⚡&736 J/t"});
        registerLegacyItem(registry, "sf:birthday_cake", "sf:seasonal", Material.CAKE, "&bBirthday Cake", null, null, new String[] {});
    }
}
