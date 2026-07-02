package cc.theends6.sfx.internal.recipe;

import java.util.Locale;

public enum SfxRecipeOperation {
    SHAPED,
    SHAPELESS,
    SINGLE,
    HAND;

    public static SfxRecipeOperation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("recipe operation is required");
        }
        return switch (raw.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "SHAPELESS", "SHAPELESS_INPUT" -> SHAPELESS;
            case "SINGLE", "SINGLE_INPUT" -> SINGLE;
            case "HAND", "HAND_INPUT" -> HAND;
            case "SHAPED", "SHAPED_3X3" -> SHAPED;
            default -> throw new IllegalArgumentException("Unsupported recipe operation: " + raw);
        };
    }
}
