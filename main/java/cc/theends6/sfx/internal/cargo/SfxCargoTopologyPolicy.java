package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.topology.SfxTopologyCapabilities;
import cc.theends6.sfx.internal.topology.SfxTopologyComponent;
import cc.theends6.sfx.internal.topology.SfxTopologyDomainKey;
import cc.theends6.sfx.internal.topology.SfxTopologyDomainPolicy;
import cc.theends6.sfx.internal.topology.SfxTopologyStatus;
import java.util.Map;
import java.util.Objects;

final class SfxCargoTopologyPolicy implements SfxTopologyDomainPolicy {
    static final SfxTopologyDomainKey DOMAIN = SfxTopologyDomainKey.of("sfx", "cargo");

    private final Map<String, SfxCargoComponentDefinition> definitions;

    SfxCargoTopologyPolicy(Map<String, SfxCargoComponentDefinition> definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    @Override
    public SfxTopologyDomainKey domain() {
        return DOMAIN;
    }

    @Override
    public SfxTopologyCapabilities capabilities(SfxBlockInstanceRecord instance) {
        SfxCargoComponentDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            return SfxTopologyCapabilities.NONE;
        }
        return new SfxTopologyCapabilities(definition.isTopologyBackbone(), definition.isController(), definition.isTerminal());
    }

    @Override
    public SfxTopologyStatus evaluateStatus(SfxTopologyComponent component) {
        int controllers = component.controllers().size();
        if (controllers <= 0) {
            return SfxTopologyStatus.INACTIVE;
        }
        return controllers == 1 ? SfxTopologyStatus.ONLINE : SfxTopologyStatus.MULTIPLE_CONTROLLERS;
    }
}
