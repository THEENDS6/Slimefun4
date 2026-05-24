package cc.theends6.sfx.internal.inventory;

public record SfxStorageKey(String value) {
    public SfxStorageKey {
        if (value == null || value.isBlank()) {
            value = "unknown";
        }
    }
}
