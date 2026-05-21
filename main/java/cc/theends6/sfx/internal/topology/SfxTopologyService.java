package cc.theends6.sfx.internal.topology;

import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SfxTopologyService {
    private final SfxBlockDataService blockData;
    private final SfxTopologyDomainPolicy domainPolicy;
    private final SfxTopologyConnectivityPolicy connectivityPolicy;
    private final Map<UUID, SfxTopologyComponent> components = new LinkedHashMap<>();
    private final Map<UUID, UUID> memberToComponent = new LinkedHashMap<>();
    private final Set<UUID> detachedTerminals = new LinkedHashSet<>();
    private final Set<UUID> conflictedTerminals = new LinkedHashSet<>();
    private long rebuiltAtRevision = Long.MIN_VALUE;

    public SfxTopologyService(
            SfxBlockDataService blockData,
            SfxTopologyDomainPolicy domainPolicy,
            SfxTopologyConnectivityPolicy connectivityPolicy
    ) {
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.domainPolicy = Objects.requireNonNull(domainPolicy, "domainPolicy");
        this.connectivityPolicy = Objects.requireNonNull(connectivityPolicy, "connectivityPolicy");
    }

    public synchronized void rebuildIfStale() {
        long revision = blockData.revision();
        if (revision != rebuiltAtRevision) {
            rebuild();
        }
    }

    public synchronized void rebuild() {
        long revision = blockData.revision();
        components.clear();
        memberToComponent.clear();
        detachedTerminals.clear();
        conflictedTerminals.clear();

        Map<UUID, SfxBlockInstanceRecord> participants = new LinkedHashMap<>();
        Map<SfxBlockAnchorKey, UUID> instanceByKey = new LinkedHashMap<>();
        Map<UUID, SfxTopologyCapabilities> capabilitiesById = new LinkedHashMap<>();
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxTopologyCapabilities capabilities = domainPolicy.capabilities(instance);
            if (capabilities == null || !capabilities.participates()) {
                continue;
            }
            participants.put(instance.instanceId(), instance);
            instanceByKey.put(instance.anchorKey(), instance.instanceId());
            capabilitiesById.put(instance.instanceId(), capabilities);
        }

        Set<UUID> visitedBackbone = new LinkedHashSet<>();
        for (SfxBlockInstanceRecord instance : participants.values()) {
            SfxTopologyCapabilities capabilities = capabilitiesById.get(instance.instanceId());
            if (capabilities == null || !capabilities.backbone() || visitedBackbone.contains(instance.instanceId())) {
                continue;
            }
            UUID componentId = instance.instanceId();
            SfxTopologyComponent component = new SfxTopologyComponent(componentId, domainPolicy.domain(), revision);
            ArrayDeque<UUID> queue = new ArrayDeque<>();
            queue.add(instance.instanceId());
            visitedBackbone.add(instance.instanceId());
            while (!queue.isEmpty()) {
                UUID currentId = queue.removeFirst();
                SfxBlockInstanceRecord current = participants.get(currentId);
                if (current == null) {
                    continue;
                }
                SfxTopologyCapabilities currentCapabilities = capabilitiesById.get(currentId);
                if (currentCapabilities == null || !currentCapabilities.backbone()) {
                    continue;
                }
                component.addBackbone(currentId);
                memberToComponent.put(currentId, componentId);
                if (currentCapabilities.controller()) {
                    component.addController(currentId);
                }
                for (SfxBlockAnchorKey neighbourKey : connectivityPolicy.findBackboneNeighbours(current.anchorKey())) {
                    UUID neighbourId = instanceByKey.get(neighbourKey);
                    if (neighbourId == null) {
                        continue;
                    }
                    SfxTopologyCapabilities neighbourCapabilities = capabilitiesById.get(neighbourId);
                    if (neighbourCapabilities == null || !neighbourCapabilities.backbone()) {
                        continue;
                    }
                    if (visitedBackbone.add(neighbourId)) {
                        queue.addLast(neighbourId);
                    }
                }
            }
            component.status(domainPolicy.evaluateStatus(component));
            components.put(componentId, component);
        }

        for (SfxBlockInstanceRecord instance : participants.values()) {
            SfxTopologyCapabilities capabilities = capabilitiesById.get(instance.instanceId());
            if (capabilities == null || !capabilities.terminal() || capabilities.backbone()) {
                continue;
            }
            Set<UUID> attachedComponents = new LinkedHashSet<>();
            for (SfxBlockAnchorKey neighbourKey : connectivityPolicy.findAttachableBackbones(instance.anchorKey())) {
                UUID neighbourId = instanceByKey.get(neighbourKey);
                if (neighbourId == null) {
                    continue;
                }
                UUID componentId = memberToComponent.get(neighbourId);
                if (componentId != null) {
                    attachedComponents.add(componentId);
                }
            }
            if (attachedComponents.isEmpty()) {
                detachedTerminals.add(instance.instanceId());
            } else if (attachedComponents.size() > 1) {
                conflictedTerminals.add(instance.instanceId());
            } else {
                UUID componentId = attachedComponents.iterator().next();
                SfxTopologyComponent component = components.get(componentId);
                if (component != null) {
                    component.addTerminal(instance.instanceId());
                    memberToComponent.put(instance.instanceId(), componentId);
                }
            }
        }
        rebuiltAtRevision = revision;
    }

    public synchronized long revision() {
        return rebuiltAtRevision;
    }

    public synchronized Collection<SfxTopologyComponent> components() {
        return List.copyOf(components.values());
    }

    public synchronized Optional<SfxTopologyComponent> component(UUID componentId) {
        return Optional.ofNullable(components.get(componentId));
    }

    public synchronized Optional<SfxTopologyComponent> componentForMember(UUID instanceId) {
        UUID componentId = memberToComponent.get(instanceId);
        return componentId == null ? Optional.empty() : Optional.ofNullable(components.get(componentId));
    }

    public synchronized boolean isDetachedTerminal(UUID instanceId) {
        return detachedTerminals.contains(instanceId);
    }

    public synchronized boolean isConflictedTerminal(UUID instanceId) {
        return conflictedTerminals.contains(instanceId);
    }

    public synchronized Set<UUID> conflictedTerminals() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(conflictedTerminals));
    }

    public synchronized Set<UUID> detachedTerminals() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(detachedTerminals));
    }
}
