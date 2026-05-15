package cc.theends6.sfx.internal.radiation;

public enum SfxRadiationStage {
    NONE(0, 0),
    I(1, 200),
    II(2, 500),
    III(3, 1000),
    IV(4, 1500),
    V(5, 2000);

    private final int level;
    private final int threshold;

    SfxRadiationStage(int level, int threshold) {
        this.level = level;
        this.threshold = threshold;
    }

    public int level() {
        return level;
    }

    public int threshold() {
        return threshold;
    }

    public static SfxRadiationStage fromExposure(int exposure) {
        if (exposure >= V.threshold) {
            return V;
        }
        if (exposure >= IV.threshold) {
            return IV;
        }
        if (exposure >= III.threshold) {
            return III;
        }
        if (exposure >= II.threshold) {
            return II;
        }
        if (exposure >= I.threshold) {
            return I;
        }
        return NONE;
    }
}
