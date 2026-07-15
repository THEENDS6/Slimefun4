package cc.theends6.sfx.api.energy.runtime;

import cc.theends6.sfx.api.energy.runtime.*;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;
import java.util.List;

public interface SfxEnergyGeneratorAccess {
    boolean hasOutputSpace(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxElectricStack output);

    boolean hasOutputSpace(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, List<SfxElectricStack> outputs);

    boolean pushOutput(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxElectricStack output);

    boolean pushOutputs(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, List<SfxElectricStack> outputs);

    
    long fillGridEnergy();

    
    long clearGridEnergy();

    void markDirty();
}
