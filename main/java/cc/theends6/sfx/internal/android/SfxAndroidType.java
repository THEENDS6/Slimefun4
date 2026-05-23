package cc.theends6.sfx.internal.android;

import java.util.Locale;
import java.util.Map;

public enum SfxAndroidType {
    NORMAL(0, "normal", 1, AndroidFunction.NONE),
    FARMER(1, "farmer", 1, AndroidFunction.FARMING),
    MINER(2, "miner", 1, AndroidFunction.MINING),
    WOODCUTTER(3, "woodcutter", 1, AndroidFunction.WOODCUTTING),
    BUTCHER(4, "butcher", 1, AndroidFunction.SLAUGHTERING),
    FISHERMAN(5, "fisherman", 1, AndroidFunction.FISHING),
    ADVANCED_NORMAL(6, "advanced_normal", 2, AndroidFunction.NONE),
    ADVANCED_FISHERMAN(7, "advanced_fisherman", 2, AndroidFunction.FISHING),
    ADVANCED_FARMER(8, "advanced_farmer", 2, AndroidFunction.FARMING),
    ADVANCED_BUTCHER(9, "advanced_butcher", 2, AndroidFunction.SLAUGHTERING),
    EMPOWERED_NORMAL(10, "empowered_normal", 3, AndroidFunction.NONE),
    EMPOWERED_FISHERMAN(11, "empowered_fisherman", 3, AndroidFunction.FISHING),
    EMPOWERED_BUTCHER(12, "empowered_butcher", 3, AndroidFunction.SLAUGHTERING);

    private static final Map<String, SfxAndroidType> BY_ITEM_ID = Map.ofEntries(
            Map.entry("sf:programmable_android", NORMAL),
            Map.entry("sf:programmable_android_farmer", FARMER),
            Map.entry("sf:programmable_android_miner", MINER),
            Map.entry("sf:programmable_android_woodcutter", WOODCUTTER),
            Map.entry("sf:programmable_android_butcher", BUTCHER),
            Map.entry("sf:programmable_android_fisherman", FISHERMAN),
            Map.entry("sf:programmable_android_2", ADVANCED_NORMAL),
            Map.entry("sf:programmable_android_2_fisherman", ADVANCED_FISHERMAN),
            Map.entry("sf:programmable_android_2_farmer", ADVANCED_FARMER),
            Map.entry("sf:programmable_android_2_butcher", ADVANCED_BUTCHER),
            Map.entry("sf:programmable_android_3", EMPOWERED_NORMAL),
            Map.entry("sf:programmable_android_3_fisherman", EMPOWERED_FISHERMAN),
            Map.entry("sf:programmable_android_3_butcher", EMPOWERED_BUTCHER)
    );

    private final int codecId;
    private final String key;
    private final int tier;
    private final AndroidFunction function;

    SfxAndroidType(int codecId, String key, int tier, AndroidFunction function) {
        this.codecId = codecId;
        this.key = key;
        this.tier = tier;
        this.function = function;
    }

    public int codecId() {
        return codecId;
    }

    public String key() {
        return key;
    }

    public int tier() {
        return tier;
    }

    public AndroidFunction function() {
        return function;
    }

    public boolean isAdvancedFarmer() {
        return this == ADVANCED_FARMER;
    }

    public boolean canMove() {
        return function != AndroidFunction.SLAUGHTERING;
    }

    public double fuelEfficiency() {
        return switch (tier) {
            case 2 -> 1.5D;
            case 3 -> function == AndroidFunction.FISHING || function == AndroidFunction.SLAUGHTERING ? 8.0D : 3.0D;
            default -> 1.0D;
        };
    }

    public static boolean isAndroidItem(String itemId) {
        return fromItemId(itemId) != null;
    }

    public static SfxAndroidType fromItemId(String itemId) {
        if (itemId == null) {
            return null;
        }
        return BY_ITEM_ID.get(itemId.toLowerCase(Locale.ROOT));
    }

    public static SfxAndroidType fromCodecId(int codecId) {
        for (SfxAndroidType type : values()) {
            if (type.codecId == codecId) {
                return type;
            }
        }
        return null;
    }

    public enum AndroidFunction {
        NONE,
        MINING,
        FARMING,
        WOODCUTTING,
        SLAUGHTERING,
        FISHING
    }
}
