package cc.theends6.sfx.internal.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;

final class SfxFloatingTextDisplayState {
    private final List<Integer> entityIds = new ArrayList<>();
    private final Map<UUID, Boolean> viewers = new ConcurrentHashMap<>();
    private final Map<UUID, Component> viewerText = new ConcurrentHashMap<>();
    private volatile SfxFloatingTextProjection projection;
    private volatile SfxFloatingTextDisplayMode displayMode;

    SfxFloatingTextDisplayState(int entityId) {
        this.entityIds.add(entityId);
    }

    int entityId() {
        return entityIds.get(0);
    }

    List<Integer> entityIds() {
        return entityIds;
    }

    void ensureEntityCount(int count, AtomicInteger ids) {
        int safeCount = Math.max(1, count);
        while (entityIds.size() < safeCount) {
            entityIds.add(ids.incrementAndGet());
        }
        while (entityIds.size() > safeCount) {
            entityIds.remove(entityIds.size() - 1);
        }
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

    SfxFloatingTextDisplayMode displayMode() {
        return displayMode;
    }

    void displayMode(SfxFloatingTextDisplayMode displayMode) {
        this.displayMode = displayMode;
    }
}
