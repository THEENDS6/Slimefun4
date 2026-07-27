package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.topology.SfxTopologyConnectivityPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SfxCargoConnectivityPolicy implements SfxTopologyConnectivityPolicy {
    private final int range;
    private final SfxBlockDataService blockData;
    private final Map<String, SfxCargoComponentDefinition> definitions;
    private final int maxAreaRangeX;
    private final int maxAreaRangeZ;

    SfxCargoConnectivityPolicy(int range, SfxBlockDataService blockData, Map<String, SfxCargoComponentDefinition> definitions) {
        this.range = Math.max(1, range);
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.maxAreaRangeX = definitions.values().stream()
                .filter(SfxCargoComponentDefinition::hasAreaRange)
                .mapToInt(SfxCargoComponentDefinition::rangeX)
                .max()
                .orElse(this.range);
        this.maxAreaRangeZ = definitions.values().stream()
                .filter(SfxCargoComponentDefinition::hasAreaRange)
                .mapToInt(SfxCargoComponentDefinition::rangeZ)
                .max()
                .orElse(this.range);
    }

    @Override
    public Collection<SfxBlockAnchorKey> findBackboneNeighbours(SfxBlockAnchorKey origin) {
        SfxCargoComponentDefinition definition = definitionAt(origin);
        return definition != null && definition.hasAreaRange() ? areaCargoKeys(origin, definition) : axialKeys(origin);
    }

    @Override
    public Collection<SfxBlockAnchorKey> findAttachableBackbones(SfxBlockAnchorKey terminal) {
        List<SfxBlockAnchorKey> keys = new ArrayList<>(axialKeys(terminal));
        for (SfxAnchorRecord anchor : blockData.anchorsNear(
                terminal, maxAreaRangeX, maxAreaRangeZ)) {
            SfxCargoComponentDefinition definition = definitionAt(anchor.key());
            if (definition != null && definition.hasAreaRange() && inside(anchor.key(), terminal, definition)) {
                keys.add(anchor.key());
            }
        }
        return keys;
    }

    private List<SfxBlockAnchorKey> areaCargoKeys(SfxBlockAnchorKey origin, SfxCargoComponentDefinition manager) {
        List<SfxBlockAnchorKey> keys = new ArrayList<>();
        for (SfxAnchorRecord anchor : blockData.anchorsNear(
                origin, manager.rangeX(), manager.rangeZ())) {
            if (!anchor.key().equals(origin) && inside(origin, anchor.key(), manager) && definitionAt(anchor.key()) != null) {
                keys.add(anchor.key());
            }
        }
        return keys;
    }

    private boolean inside(SfxBlockAnchorKey origin, SfxBlockAnchorKey target, SfxCargoComponentDefinition manager) {
        return origin.worldId().equals(target.worldId())
                && Math.abs(origin.x() - target.x()) <= manager.rangeX()
                && Math.abs(origin.y() - target.y()) <= manager.rangeY()
                && Math.abs(origin.z() - target.z()) <= manager.rangeZ();
    }

    private SfxCargoComponentDefinition definitionAt(SfxBlockAnchorKey key) {
        SfxAnchorRecord anchor = blockData.findAnchorFast(key).orElse(null);
        SfxBlockInstanceRecord instance = anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
        return instance == null ? null : definitions.get(instance.typeId());
    }

    private List<SfxBlockAnchorKey> axialKeys(SfxBlockAnchorKey origin) {
        List<SfxBlockAnchorKey> keys = new ArrayList<>(range * 6);
        for (int distance = 1; distance <= range; distance++) {
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x() + distance, origin.y(), origin.z()));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x() - distance, origin.y(), origin.z()));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x(), origin.y() + distance, origin.z()));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x(), origin.y() - distance, origin.z()));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x(), origin.y(), origin.z() + distance));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x(), origin.y(), origin.z() - distance));
        }
        return keys;
    }
}
