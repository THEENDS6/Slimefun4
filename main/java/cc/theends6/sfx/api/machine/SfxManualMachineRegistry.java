package cc.theends6.sfx.api.machine;

import cc.theends6.sfx.api.machine.manual.SfxManualMachineDefinition;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineRecipe;
import java.util.Collection;
import java.util.Optional;

public interface SfxManualMachineRegistry {
    void registerMachine(SfxManualMachineDefinition definition);

    void registerRecipe(SfxManualMachineRecipe recipe);

    Optional<SfxManualMachineDefinition> machine(String id);

    Collection<SfxManualMachineDefinition> machines();

    Collection<SfxManualMachineRecipe> recipesFor(String machineId);
}
