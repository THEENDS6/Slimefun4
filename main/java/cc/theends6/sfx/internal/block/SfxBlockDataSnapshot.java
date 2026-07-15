package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

import java.util.List;
import java.util.Objects;

public record SfxBlockDataSnapshot(List<SfxAnchorRecord> anchors, List<SfxBlockInstanceRecord> instances) {
    public SfxBlockDataSnapshot {
        anchors = List.copyOf(Objects.requireNonNull(anchors, "anchors"));
        instances = List.copyOf(Objects.requireNonNull(instances, "instances"));
    }
}
