package cc.theends6.sfx.api.item;

import java.util.List;
import java.util.Objects;

public record SfxItemMarker(
        String itemId,
        int itemVersion,
        int schemaVersion,
        String variant,
        SfxItemKind kind,
        List<String> flags
) {
    public SfxItemMarker {
        itemId = SfxItemDefinition.normalizeId(itemId);
        itemVersion = Math.max(1, itemVersion);
        schemaVersion = Math.max(1, schemaVersion);
        variant = Objects.requireNonNullElse(variant, "default");
        kind = kind == null ? SfxItemKind.ITEM : kind;
        flags = flags == null ? List.of() : List.copyOf(flags);
    }

    public String flagsAsString() {
        return String.join(",", flags);
    }
}
