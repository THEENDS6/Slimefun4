package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SfxElectricMachineRegistry {
    private final Map<String, SfxElectricMachineDefinition> definitions = new LinkedHashMap<>();

    public void register(SfxElectricMachineDefinition definition) {
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate electric machine definition: " + definition.id());
        }
    }

    public Optional<SfxElectricMachineDefinition> definition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public boolean contains(String id) {
        return definitions.containsKey(id);
    }

    public Collection<SfxElectricMachineDefinition> definitions() {
        return definitions.values();
    }
}
