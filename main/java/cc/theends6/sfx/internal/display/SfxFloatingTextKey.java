package cc.theends6.sfx.internal.display;

import java.util.Objects;
import java.util.UUID;

public record SfxFloatingTextKey(String namespace, UUID worldId, int x, int y, int z) {
    public SfxFloatingTextKey {
        namespace = Objects.requireNonNull(namespace, "namespace");
        worldId = Objects.requireNonNull(worldId, "worldId");
    }
}
