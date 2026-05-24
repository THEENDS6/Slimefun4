package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.machine.SfxMachineRuntime;
import cc.theends6.sfx.api.machine.SfxMachineView;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/** Read-only API facade over the internal runtime engine. */
public final class DefaultSfxMachineRuntimeApi implements SfxMachineRuntime {
    private SfxMachineRuntimeEngine engine;

    public void bind(SfxMachineRuntimeEngine engine) {
        this.engine = engine;
    }

    @Override
    public int definitionCount() {
        return engine == null ? 0 : engine.definitionCount();
    }

    @Override
    public int effectHookCount() {
        return engine == null ? 0 : engine.effectHookCount();
    }

    @Override
    public Collection<String> machineIds() {
        if (engine == null) {
            return java.util.List.of();
        }
        return engine.definitions().stream()
                .map(SfxMachineDefinition::id)
                .sorted()
                .toList();
    }

    @Override
    public Optional<SfxMachineView> machine(String id) {
        if (engine == null) {
            return Optional.empty();
        }
        return engine.definition(id).map(DefaultSfxMachineRuntimeApi::view);
    }

    @Override
    public Set<String> unboundEffectNames() {
        return engine == null ? Set.of() : engine.unboundDeclaredEffectNames();
    }

    private static SfxMachineView view(SfxMachineDefinition definition) {
        return new SfxMachineView(
                definition.id(),
                definition.displayName(),
                definition.category().name(),
                definition.tickInterval(),
                definition.capabilities().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                definition.policyRefs().stream()
                        .map(ref -> ref.type() + ":" + ref.name())
                        .sorted()
                        .toList(),
                definition.effects().stream()
                        .sorted(Comparator.comparing((SfxMachineEffect effect) -> effect.phase().name()).thenComparing(SfxMachineEffect::name))
                        .map(effect -> effect.phase().name() + ":" + effect.name())
                        .toList()
        );
    }
}
