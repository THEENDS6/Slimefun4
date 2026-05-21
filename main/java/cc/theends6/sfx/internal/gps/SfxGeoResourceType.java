package cc.theends6.sfx.internal.gps;

public enum SfxGeoResourceType {
    OIL("Oil", "sf:bucket_of_oil", 0),
    SALT("Salt", "sf:salt", 1),
    URANIUM("Small Chunks of Uranium", "sf:small_uranium", 2),
    NETHER_ICE("Nether Ice", "sf:nether_ice", 3);

    private final String displayName;
    private final String itemId;
    private final int salt;

    SfxGeoResourceType(String displayName, String itemId, int salt) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.salt = salt;
    }

    public String displayName() {
        return displayName;
    }

    public String itemId() {
        return itemId;
    }

    public int salt() {
        return salt;
    }
}
