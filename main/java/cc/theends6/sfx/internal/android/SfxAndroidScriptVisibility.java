package cc.theends6.sfx.internal.android;

import java.util.Locale;

public enum SfxAndroidScriptVisibility {
    PUBLIC,
    PRIVATE;

    public static SfxAndroidScriptVisibility parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Missing Android script visibility");
        }
        return valueOf(input.trim().toUpperCase(Locale.ROOT));
    }
}
