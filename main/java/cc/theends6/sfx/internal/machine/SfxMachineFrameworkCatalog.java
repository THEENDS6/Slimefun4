package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Registers every concrete SFX machine-like item into the shared machine runtime catalog. */
public final class SfxMachineFrameworkCatalog {
    private SfxMachineFrameworkCatalog() {}

    public record Candidate(SfxMachineCategory category, Predicate<String> supports) {
        public Candidate {
            category = category == null ? SfxMachineCategory.SPECIAL : category;
            supports = Objects.requireNonNull(supports, "supports");
        }
        public static Candidate of(SfxMachineCategory category, Predicate<String> supports) {
            return new Candidate(category, supports);
        }
    }

    public static int registerDefinitions(SfxMachineRuntimeEngine engine, Collection<SfxItemDefinition> items, Candidate... candidates) {
        if (engine == null || items == null || candidates == null || candidates.length == 0) {
            return 0;
        }
        int registered = 0;
        for (SfxItemDefinition item : items) {
            if (item == null || item.id() == null) {
                continue;
            }
            for (Candidate candidate : candidates) {
                if (candidate == null || !candidate.supports().test(item.id())) {
                    continue;
                }
                SfxMachineDefinition computed = SfxMachineSpecialProfiles.apply(new SfxMachineDefinition(item.id(), item.id(), candidate.category(), List.of(), List.of(), -1, 1));
                if (engine.definition(item.id()).isEmpty()) {
                    engine.registerDefinitionIfAbsent(computed);
                    registered++;
                } else {
                    engine.enrichDefinition(item.id(), existing -> mergeFrameworkMetadata(existing, computed));
                }
                break;
            }
        }
        return registered;
    }

    private static SfxMachineDefinition mergeFrameworkMetadata(SfxMachineDefinition existing, SfxMachineDefinition computed) {
        if (existing == null) {
            return computed;
        }
        if (computed == null) {
            return existing;
        }
        SfxMachineDefinition.Builder builder = existing.toBuilder();
        if (existing.category() == SfxMachineCategory.SPECIAL && computed.category() != SfxMachineCategory.SPECIAL) {
            builder.category(computed.category());
        }
        builder.capabilities(computed.capabilities());
        if (existing.inputSlots().isEmpty() && !computed.inputSlots().isEmpty()) {
            builder.inputSlots(computed.inputSlots()).inputProvider(computed.inputProvider());
        }
        if (existing.outputSlots().isEmpty() && !computed.outputSlots().isEmpty()) {
            builder.outputSlots(computed.outputSlots()).outputProvider(computed.outputProvider());
        }
        if (existing.statusSlot() < 0 && computed.statusSlot() >= 0) {
            builder.statusSlot(computed.statusSlot());
        }
        builder.policyRefs(computed.policyRefs());
        builder.effects(computed.effects());
        return builder.build();
    }

    public static Collection<SfxMachineDefinition> definitionsFor(Collection<String> ids, SfxMachineCategory category) {
        List<SfxMachineDefinition> result = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    result.add(SfxMachineSpecialProfiles.apply(new SfxMachineDefinition(id, id, category, List.of(), List.of(), -1, 1)));
                }
            }
        }
        return result;
    }
}
