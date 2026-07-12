package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class SfxGuideIconLibrary {
    // Public CC0/community head textures from mc-heads.com.
    private static final String LEFT_ARROW = "a185c97dbb8353de652698d24b64327b793a3f32a98be67b719fbedab35e";
    private static final String RIGHT_ARROW = "31c0ededd7115fc1b23d51ce966358b27195daf26ebb6e45a66c34c69c34091";
    private static final String CHAPTER_BACK = "67f046f1c5f8540735d436e6643b37a916506f1b9e33799351e3c39f0829c2ab";
    private static final String[] PAGE_NUMBERS = {
            "987444ed6d845690115b38588cf1a2644da9c71e6f31ae24651038cb1d476ea",
            "9e44b707b205b7d23e40a3c0df19b09b62d027807278ef301b3c2ab98b4f0d6",
            "13fd6c225998c2db4a4afddd24a93c6f1cf349914aa3c5a1525cc55873b30d72",
            "7f3187a5a0aa7ab48937c7cf919881d6178995a5615d2ea244e2dacec66e8d56",
            "186c128baff9add35dcc71835decc553b58c9b2116f54ef423960cd26c9469c9",
            "23181fa8cf6bf089877ad921e3f8401430445a3e6b1850ea4d67501dab6b2024",
            "d4ba6ac07d422377a855793f36dea2ed240223f52fd1648181612ecd1a0cfd5",
            "7ac84bafc43e4d3de661d7f85de48d5e9a62f4e488278834c79e309516315496",
            "4a50e9a112feb795a645be2440ead5a712c889eb1902805f1c07f2f56ff49067",
            "d9b399760177b2775e174d49512f7b6fa9aa080713ef434d4407b11e3d997a84",
            "3fadebf4f09e07f53310add12b7f47d48dae6c77aa3e627001b53092e69f8719",
            "fda9cbbf33f8b79e706138d4fae843dcd49d441900133d1588ed486c45e88211",
            "afb854e178b567816896e23ece5e856c88b92e3bd897027640af34257b9cc6e9",
            "7e3c59d7c445b2e99e0ae3496e2427a32b88d760e948ccf726e12d03c1c21bf9",
            "e6a267ceab17fbb891160a58cf4a16a1f03d6e5e682b81c780f0209ca591d64a",
            "8967842f769d590a5b4d70ebb479296ce6ad03b89a0ed52c8e3faefc51f517e9",
            "90f71edbca528f214faab8627f2580de60b0e8c16f4479c03122b8360725fe0c",
            "84ac98f89201ac5b22732df485fc44dcabd1cac6f836e636e54d6eff1cd03004",
            "6997bfdc98916aa88ec5d7a7b89f809804e3e745abf79777ed55fdabc54fb87",
            "577ca8a24d886d21ac507aff1531d6a67209435e6ebe7ec2574d9c9420c50ccf",
            "8c9fcbde17139e106f3b7b3c49b7a790116528ea74931b844c617c542ebff2cb",
            "70de5a87673ce8ce01598ad98232b6804cc841e979252f992fa9899b704e4e37",
            "b7873cd9be0e55759d9ac9f011d91c35312fda1e24b0669c53127ab355986cc",
            "db49c8ca1c41276b63a07d71b4d442a13ff877175a4e484063d57d862afe2e11",
            "e0c34fab0d475c2ec32b774831e98cbc459f7bdce2d08c6291b483123ced8f7f"
    };

    private SfxGuideIconLibrary() {
    }

    static ItemStack previous(String name, String... lore) {
        return textured(LEFT_ARROW, name, lore, 1);
    }

    static ItemStack next(String name, String... lore) {
        return textured(RIGHT_ARROW, name, lore, 1);
    }

    static ItemStack page(String name, String lore, int currentPage) {
        int safePage = Math.max(1, currentPage);
        return textured(PAGE_NUMBERS[Math.min(PAGE_NUMBERS.length, safePage) - 1], name, new String[]{lore}, 1);
    }

    static ItemStack back(String name) {
        return textured(CHAPTER_BACK, name, new String[0], 1);
    }

    private static ItemStack textured(String hash, String name, String[] lore, int amount) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(name)
                .lore(lore)
                .amount(amount)
                .editMeta(meta -> HeadTextures.apply(meta, hash))
                .build();
    }
}
