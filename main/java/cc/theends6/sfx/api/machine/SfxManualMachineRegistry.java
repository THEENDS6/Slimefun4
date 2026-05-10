package cc.theends6.sfx.api.machine;

import cc.theends6.sfx.internal.machine.ManualMachineDefinition;
import cc.theends6.sfx.internal.machine.ManualMachineRecipe;
import java.util.Collection;
import java.util.Optional;

public interface SfxManualMachineRegistry {
    void registerMachine(ManualMachineDefinition definition);

    void registerRecipe(ManualMachineRecipe recipe);

    Optional<ManualMachineDefinition> machine(String id);

    Collection<ManualMachineDefinition> machines();

    Collection<ManualMachineRecipe> recipesFor(String machineId);
}
