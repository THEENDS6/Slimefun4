package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.electric.SfxElectricStack;

public interface SfxEnergyGeneratorAccess {
    boolean hasOutputSpace(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxElectricStack output);

    boolean pushOutput(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxElectricStack output);

    void markDirty();
}
