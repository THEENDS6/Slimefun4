package cc.theends6.sfx.api.item;

public enum SfxItemKind {
    ITEM("sfx:item"),
    GUIDE("sfx:guide"),
    SYSTEM("sfx:system");

    private final String pdcValue;

    SfxItemKind(String pdcValue) {
        this.pdcValue = pdcValue;
    }

    public String pdcValue() {
        return pdcValue;
    }

    public static SfxItemKind fromPdc(String value) {
        if (value == null) {
            return ITEM;
        }
        for (SfxItemKind kind : values()) {
            if (kind.pdcValue.equalsIgnoreCase(value)) {
                return kind;
            }
        }
        return ITEM;
    }
}
