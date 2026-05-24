package cc.theends6.sfx.api.machine;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;


public interface SfxMachineRuntime {
    int definitionCount();

    int effectHookCount();

    Collection<String> machineIds();

    Optional<SfxMachineView> machine(String id);

    Set<String> unboundEffectNames();
}
