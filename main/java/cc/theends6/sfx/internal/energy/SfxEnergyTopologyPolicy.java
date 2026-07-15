package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.topology.SfxTopologyCapabilities;
import cc.theends6.sfx.internal.topology.SfxTopologyDomainKey;
import cc.theends6.sfx.internal.topology.SfxTopologyDomainPolicy;
import java.util.Map;
import java.util.Objects;

final class SfxEnergyTopologyPolicy implements SfxTopologyDomainPolicy {
    static final SfxTopologyDomainKey DOMAIN = SfxTopologyDomainKey.of("sfx", "energy");

    private final Map<String, SfxEnergyComponentDefinition> definitions;
    private final SfxElectricMachineService electricMachines;
    private final SfxConfigurableMachineService configurableMachines;

    SfxEnergyTopologyPolicy(
            Map<String, SfxEnergyComponentDefinition> definitions,
            SfxElectricMachineService electricMachines,
            SfxConfigurableMachineService configurableMachines
    ) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.configurableMachines = Objects.requireNonNull(configurableMachines, "configurableMachines");
    }

    @Override
    public SfxTopologyDomainKey domain() {
        return DOMAIN;
    }

    @Override
    public SfxTopologyCapabilities capabilities(SfxBlockInstanceRecord instance) {
        if (instance == null) {
            return SfxTopologyCapabilities.NONE;
        }
        SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
        if (definition != null) {
            return switch (definition.componentType()) {
                case REGULATOR -> new SfxTopologyCapabilities(true, true, false);
                case CONNECTOR, CAPACITOR -> new SfxTopologyCapabilities(true, false, false);
                case GENERATOR, CHARGER -> new SfxTopologyCapabilities(false, false, true);
            };
        }
        if (electricMachines.supportsType(instance.typeId()) || configurableMachines.isEnergyNode(instance.typeId())) {
            return new SfxTopologyCapabilities(false, false, true);
        }
        return SfxTopologyCapabilities.NONE;
    }
}
