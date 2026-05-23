package cc.theends6.sfx.internal.android;

public enum SfxAndroidScriptVisibility {
    PUBLIC,
    PRIVATE,
    UNLISTED;

    public static SfxAndroidScriptVisibility parse(String input) {
        if (input == null || input.isBlank()) {
            return PUBLIC;
        }
        try {
            return valueOf(input.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PUBLIC;
        }
    }
}
