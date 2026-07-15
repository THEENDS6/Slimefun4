package cc.theends6.sfx.api.cargo;

import java.util.Locale;
import java.util.Objects;


public record SfxCargoNodeDefinition(String itemId, SfxCargoNodeKind kind, int rangeX, int rangeY, int rangeZ) {
    public SfxCargoNodeDefinition {
        itemId = Objects.requireNonNull(itemId, "itemId").trim().toLowerCase(Locale.ROOT);
        kind = Objects.requireNonNull(kind, "kind");
        if (!itemId.contains(":")) throw new IllegalArgumentException("Cargo node id must be namespaced.");
        rangeX = Math.max(0, Math.min(128, rangeX));
        rangeY = Math.max(0, Math.min(128, rangeY));
        rangeZ = Math.max(0, Math.min(128, rangeZ));
    }
}
