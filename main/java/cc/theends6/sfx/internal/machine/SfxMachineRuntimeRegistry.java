package cc.theends6.sfx.internal.machine;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SfxMachineRuntimeRegistry {
    private final Map<String, SfxMachineProcessor> processors = new ConcurrentHashMap<>();

    public void register(SfxMachineProcessor processor) {
        if (processor != null && processor.machineId() != null) {
            processors.put(processor.machineId(), processor);
        }
    }

    public Optional<SfxMachineProcessor> find(String machineId) {
        return Optional.ofNullable(processors.get(machineId));
    }

    public Collection<SfxMachineProcessor> processors() {
        return java.util.Collections.unmodifiableCollection(processors.values());
    }
}
