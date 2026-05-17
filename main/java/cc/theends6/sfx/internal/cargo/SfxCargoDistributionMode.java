package cc.theends6.sfx.internal.cargo;

public enum SfxCargoDistributionMode {
    SEQUENTIAL,
    ROUND_ROBIN,
    EVEN;

    public SfxCargoDistributionMode next() {
        return switch (this) {
            case SEQUENTIAL -> ROUND_ROBIN;
            case ROUND_ROBIN -> EVEN;
            case EVEN -> SEQUENTIAL;
        };
    }

    public SfxCargoDistributionMode previous() {
        return switch (this) {
            case SEQUENTIAL -> EVEN;
            case ROUND_ROBIN -> SEQUENTIAL;
            case EVEN -> ROUND_ROBIN;
        };
    }
}
