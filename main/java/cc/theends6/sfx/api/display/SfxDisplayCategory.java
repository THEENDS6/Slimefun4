package cc.theends6.sfx.api.display;

public record SfxDisplayCategory(String id, String nameKey, boolean defaultEnabled) {
    public SfxDisplayCategory {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Display category id must not be blank");
        if (nameKey == null || nameKey.isBlank()) throw new IllegalArgumentException("Display category name key must not be blank");
    }
}
