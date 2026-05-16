package cc.theends6.sfx.internal.cargo;

public enum SfxCargoDistributionMode {
    CLASSIC,
    EVEN;

    public SfxCargoDistributionMode toggle() {
        return this == CLASSIC ? EVEN : CLASSIC;
    }
}
