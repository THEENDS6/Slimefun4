package cc.theends6.sfx.internal.display;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;

final class SfxFloatingTextDisplayState {
    private final int entityId;
    private final Map<UUID, Boolean> viewers = new ConcurrentHashMap<>();
    private final Map<UUID, Component> viewerText = new ConcurrentHashMap<>();
    private volatile SfxFloatingTextProjection projection;

    SfxFloatingTextDisplayState(int entityId) {
        this.entityId = entityId;
    }

    int entityId() {
        return entityId;
    }

    Map<UUID, Boolean> viewers() {
        return viewers;
    }

    Map<UUID, Component> viewerText() {
        return viewerText;
    }

    SfxFloatingTextProjection projection() {
        return projection;
    }

    void projection(SfxFloatingTextProjection projection) {
        this.projection = projection;
    }
}
