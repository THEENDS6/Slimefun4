package cc.theends6.sfx.internal.radiation;

public enum SfxRadiationLevel {
    LOW(1),
    MODERATE(2),
    HIGH(3),
    VERY_HIGH(5),
    VERY_DEADLY(10);

    private final int exposureModifier;

    SfxRadiationLevel(int exposureModifier) {
        this.exposureModifier = exposureModifier;
    }

    public int exposureModifier() {
        return exposureModifier;
    }
}
