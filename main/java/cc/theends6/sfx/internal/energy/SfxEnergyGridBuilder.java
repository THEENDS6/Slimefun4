package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class SfxEnergyGridBuilder {
    private final SfxBlockDataService blockData;
    private final Map<String, SfxEnergyComponentDefinition> definitions;
    private final SfxElectricMachineService electricMachines;
    private final int range;

    SfxEnergyGridBuilder(
            SfxBlockDataService blockData,
            Map<String, SfxEnergyComponentDefinition> definitions,
            SfxElectricMachineService electricMachines,
            int range
    ) {
        this.blockData = blockData;
        this.definitions = definitions;
        this.electricMachines = electricMachines;
        this.range = range;
    }

    SfxEnergyGridResult buildGrid(UUID regulatorId, SfxBlockAnchorKey regulatorKey) {
        Set<UUID> members = new LinkedHashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(regulatorId);
        members.add(regulatorId);
        boolean multipleRegulators = false;

        while (!queue.isEmpty()) {
            UUID currentId = queue.removeFirst();
            SfxBlockInstanceRecord current = blockData.findInstance(currentId).orElse(null);
            if (current == null) {
                continue;
            }
            if (!currentId.equals(regulatorId) && !canExpandNetwork(current)) {
                continue;
            }

            for (SfxBlockInstanceRecord neighbour : registeredEnergyNeighbours(current.anchorKey())) {
                if (!members.add(neighbour.instanceId())) {
                    continue;
                }
                SfxEnergyComponentDefinition neighbourDefinition = definitions.get(neighbour.typeId());
                if (neighbourDefinition != null && neighbourDefinition.componentType() == SfxEnergyComponentType.REGULATOR && !neighbour.instanceId().equals(regulatorId)) {
                    multipleRegulators = true;
                }
                queue.addLast(neighbour.instanceId());
            }
        }

        if (members.size() <= 1) {
            return new SfxEnergyGridResult(regulatorId, regulatorKey, members, SfxEnergyGridStatus.NO_NETWORK);
        }
        return new SfxEnergyGridResult(regulatorId, regulatorKey, members, multipleRegulators ? SfxEnergyGridStatus.MULTIPLE_REGULATORS : SfxEnergyGridStatus.ONLINE);
    }


    private boolean canExpandNetwork(SfxBlockInstanceRecord instance) {
        SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
        if (definition != null) {
            return definition.expandsNetwork();
        }
        if (electricMachines.supportsType(instance.typeId())) {
            return false;
        }
        return false;
    }

    private List<SfxBlockInstanceRecord> registeredEnergyNeighbours(SfxBlockAnchorKey origin) {
        List<SfxBlockInstanceRecord> neighbours = new ArrayList<>();
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            if (!anchor.key().worldId().equals(origin.worldId()) || anchor.key().equals(origin)) {
                continue;
            }
            if (!isReachable(origin, anchor.key())) {
                continue;
            }
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            if (definitions.containsKey(instance.typeId()) || electricMachines.supportsType(instance.typeId())) {
                neighbours.add(instance);
            }
        }
        return neighbours;
    }

    private boolean isReachable(SfxBlockAnchorKey first, SfxBlockAnchorKey second) {
        int dx = Math.abs(first.x() - second.x());
        int dy = Math.abs(first.y() - second.y());
        int dz = Math.abs(first.z() - second.z());
        int changedAxes = (dx > 0 ? 1 : 0) + (dy > 0 ? 1 : 0) + (dz > 0 ? 1 : 0);
        if (changedAxes != 1) {
            return false;
        }
        return dx + dy + dz <= range;
    }
}
