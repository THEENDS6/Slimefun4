package cc.theends6.sfx.api.power;

public record SfxPoweredItem(String id, SfxPoweredItemKind kind, int tier, double capacity,
                             double maxInputPerTick, double maxOutputPerTick,
                             double baseUseCost, SfxOverclockProfile overclock) {
    public SfxPoweredItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Powered item id must not be blank");
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        if (tier < 0 || capacity < 0.0D || maxInputPerTick < 0.0D || maxOutputPerTick < 0.0D || baseUseCost < 0.0D) {
            throw new IllegalArgumentException("Powered item numeric values must not be negative");
        }
        if (overclock != null && tier < 3) throw new IllegalArgumentException("Only tier III powered items may overclock");
    }
}
