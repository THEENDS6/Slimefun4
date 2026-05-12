package cc.theends6.sfx.internal.display;

import java.util.Objects;
import net.kyori.adventure.text.Component;

public record SfxFloatingTextProjection(
        SfxFloatingTextKey key,
        double x,
        double y,
        double z,
        Component text,
        int viewDistanceSquared
) {
    public SfxFloatingTextProjection {
        key = Objects.requireNonNull(key, "key");
        text = Objects.requireNonNull(text, "text");
        viewDistanceSquared = Math.max(1, viewDistanceSquared);
    }
}
