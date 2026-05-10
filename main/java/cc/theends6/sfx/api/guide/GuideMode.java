package cc.theends6.sfx.api.guide;

public enum GuideMode {
    SURVIVAL("survival"),
    CHEAT("cheat");

    private final String pdcValue;

    GuideMode(String pdcValue) {
        this.pdcValue = pdcValue;
    }

    public String pdcValue() {
        return pdcValue;
    }

    public static GuideMode fromPdc(String value) {
        if (value == null) {
            return SURVIVAL;
        }
        for (GuideMode mode : values()) {
            if (mode.pdcValue.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return SURVIVAL;
    }
}
