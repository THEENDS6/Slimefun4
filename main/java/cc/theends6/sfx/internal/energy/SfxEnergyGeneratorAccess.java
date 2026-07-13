package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.util.List;

public interface SfxEnergyGeneratorAccess {
    boolean hasOutputSpace(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxElectricStack output);

    boolean hasOutputSpace(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, List<SfxElectricStack> outputs);

    boolean pushOutput(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, SfxElectricStack output);

    boolean pushOutputs(SfxEnergyComponentDefinition definition, SfxEnergyNodeState state, List<SfxElectricStack> outputs);

    void markDirty();
}
