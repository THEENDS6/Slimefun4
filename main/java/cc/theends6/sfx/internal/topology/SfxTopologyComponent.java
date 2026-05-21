package cc.theends6.sfx.internal.topology;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class SfxTopologyComponent {
    private final UUID componentId;
    private final SfxTopologyDomainKey domain;
    private final long topologyRevision;
    private final Set<UUID> backboneNodes = new LinkedHashSet<>();
    private final Set<UUID> controllers = new LinkedHashSet<>();
    private final Set<UUID> terminals = new LinkedHashSet<>();
    private SfxTopologyStatus status = SfxTopologyStatus.INACTIVE;

    SfxTopologyComponent(UUID componentId, SfxTopologyDomainKey domain, long topologyRevision) {
        this.componentId = componentId;
        this.domain = domain;
        this.topologyRevision = topologyRevision;
    }

    public UUID componentId() {
        return componentId;
    }

    public SfxTopologyDomainKey domain() {
        return domain;
    }

    public long topologyRevision() {
        return topologyRevision;
    }

    public Set<UUID> backboneNodes() {
        return Collections.unmodifiableSet(backboneNodes);
    }

    public Set<UUID> controllers() {
        return Collections.unmodifiableSet(controllers);
    }

    public Set<UUID> terminals() {
        return Collections.unmodifiableSet(terminals);
    }

    public Set<UUID> members() {
        Set<UUID> members = new LinkedHashSet<>(backboneNodes);
        members.addAll(terminals);
        return Collections.unmodifiableSet(members);
    }

    public SfxTopologyStatus status() {
        return status;
    }

    void addBackbone(UUID instanceId) {
        backboneNodes.add(instanceId);
    }

    void addController(UUID instanceId) {
        controllers.add(instanceId);
    }

    void addTerminal(UUID instanceId) {
        terminals.add(instanceId);
    }

    void status(SfxTopologyStatus status) {
        this.status = status;
    }
}
