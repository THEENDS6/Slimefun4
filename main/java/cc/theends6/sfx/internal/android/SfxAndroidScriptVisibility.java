package cc.theends6.sfx.internal.android;

public enum SfxAndroidScriptVisibility {
    PUBLIC,
    PRIVATE;

    public static SfxAndroidScriptVisibility parse(String input) {
        if (input == null || input.isBlank()) {
            return PUBLIC;
        }
        String normalized = input.trim().toUpperCase(java.util.Locale.ROOT);
        if ("UNLISTED".equals(normalized)) {
            return PRIVATE;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return PUBLIC;
        }
    }
}
