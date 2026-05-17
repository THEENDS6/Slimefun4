package cc.theends6.sfx.internal.display;

import java.util.Objects;
import net.kyori.adventure.text.Component;

public record SfxFloatingTextProjection(
        SfxFloatingTextKey key,
        double x,
        double y,
        double z,
        Component text,
        int viewDistanceSquared,
        boolean seeThrough,
        SfxFloatingTextDisplayMode displayMode
) {
    public SfxFloatingTextProjection(
            SfxFloatingTextKey key,
            double x,
            double y,
            double z,
            Component text,
            int viewDistanceSquared
    ) {
        this(key, x, y, z, text, viewDistanceSquared, false, null);
    }

    public SfxFloatingTextProjection(
            SfxFloatingTextKey key,
            double x,
            double y,
            double z,
            Component text,
            int viewDistanceSquared,
            boolean seeThrough
    ) {
        this(key, x, y, z, text, viewDistanceSquared, seeThrough, null);
    }

    public SfxFloatingTextProjection {
        key = Objects.requireNonNull(key, "key");
        text = Objects.requireNonNull(text, "text");
        viewDistanceSquared = Math.max(1, viewDistanceSquared);
    }
}
