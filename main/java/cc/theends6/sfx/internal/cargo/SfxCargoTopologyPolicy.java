package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
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
    private final SfxBlockDataService blockData;

    SfxCargoTopologyPolicy(Map<String, SfxCargoComponentDefinition> definitions, SfxBlockDataService blockData) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
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
        int exclusiveControllers = 0;
        int compatibleControllers = 0;
        for (var controllerId : component.controllers()) {
            var instance = blockData.findInstance(controllerId).orElse(null);
            SfxCargoComponentDefinition definition = instance == null ? null : definitions.get(instance.typeId());
            if (definition == null || !definition.coexistsWithManagers()) {
                exclusiveControllers++;
            } else {
                compatibleControllers++;
            }
        }
        if (exclusiveControllers + compatibleControllers <= 0) {
            return SfxTopologyStatus.INACTIVE;
        }
        return exclusiveControllers <= 1 ? SfxTopologyStatus.ONLINE : SfxTopologyStatus.MULTIPLE_CONTROLLERS;
    }
}
