package cc.theends6.sfx.internal.machine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record SfxMachineDefinition(
        String id,
        String displayName,
        SfxMachineCategory category,
        List<Integer> inputSlots,
        List<Integer> outputSlots,
        int statusSlot,
        int tickInterval,
        Set<SfxMachineCapability> capabilities,
        SfxMachineInputProvider inputProvider,
        SfxMachineOutputProvider outputProvider,
        List<SfxMachinePolicyRef> policyRefs,
        List<SfxMachineEffect> effects
) {
    public SfxMachineDefinition(String id, String displayName, List<Integer> inputSlots, List<Integer> outputSlots, int statusSlot, int tickInterval) {
        this(id, displayName, SfxMachineCategory.SPECIAL, inputSlots, outputSlots, statusSlot, tickInterval);
    }

    public SfxMachineDefinition(String id, String displayName, SfxMachineCategory category, List<Integer> inputSlots, List<Integer> outputSlots, int statusSlot, int tickInterval) {
        this(id, displayName, category, inputSlots, outputSlots, statusSlot, tickInterval,
                defaultCapabilities(category, inputSlots, outputSlots),
                inputSlots == null || inputSlots.isEmpty() ? SfxMachineInputProvider.none() : SfxMachineInputProvider.guiSlots(inputSlots),
                outputSlots == null || outputSlots.isEmpty() ? SfxMachineOutputProvider.none() : SfxMachineOutputProvider.guiSlots(outputSlots),
                List.of(), List.of());
    }

    public SfxMachineDefinition {
        inputSlots = inputSlots == null ? List.of() : List.copyOf(inputSlots);
        outputSlots = outputSlots == null ? List.of() : List.copyOf(outputSlots);
        category = category == null ? SfxMachineCategory.SPECIAL : category;
        tickInterval = Math.max(1, tickInterval);
        capabilities = capabilities == null ? defaultCapabilities(category, inputSlots, outputSlots) : Set.copyOf(capabilities);
        inputProvider = inputProvider == null ? (inputSlots.isEmpty() ? SfxMachineInputProvider.none() : SfxMachineInputProvider.guiSlots(inputSlots)) : inputProvider;
        outputProvider = outputProvider == null ? (outputSlots.isEmpty() ? SfxMachineOutputProvider.none() : SfxMachineOutputProvider.guiSlots(outputSlots)) : outputProvider;
        policyRefs = policyRefs == null ? List.of() : List.copyOf(policyRefs);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public Builder toBuilder() {
        return builder(id)
                .displayName(displayName)
                .category(category)
                .inputSlots(inputSlots)
                .outputSlots(outputSlots)
                .statusSlot(statusSlot)
                .tickInterval(tickInterval)
                .capabilities(capabilities)
                .inputProvider(inputProvider)
                .outputProvider(outputProvider)
                .policyRefs(policyRefs)
                .effects(effects);
    }

    public SfxMachineDefinition withCapabilities(Set<SfxMachineCapability> capabilities) {
        return toBuilder().capabilities(capabilities).build();
    }

    public SfxMachineDefinition withEffect(SfxMachineEffect effect) {
        Builder builder = toBuilder();
        if (effect != null) builder.effect(effect);
        return builder.build();
    }

    public boolean hasCapability(SfxMachineCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    private static Set<SfxMachineCapability> defaultCapabilities(SfxMachineCategory category, List<Integer> inputSlots, List<Integer> outputSlots) {
        EnumSet<SfxMachineCapability> set = EnumSet.noneOf(SfxMachineCapability.class);
        if (category == SfxMachineCategory.ELECTRIC || category == SfxMachineCategory.CONFIGURABLE) {
            set.add(SfxMachineCapability.HAS_GUI);
            set.add(SfxMachineCapability.USES_ENERGY);
        }
        if (category == SfxMachineCategory.ENERGY) {
            set.add(SfxMachineCapability.TOPOLOGY_NODE);
        }
        if (category == SfxMachineCategory.CARGO) {
            set.add(SfxMachineCapability.TOPOLOGY_NODE);
            set.add(SfxMachineCapability.STORAGE_ENDPOINT);
        }
        if (inputSlots != null && !inputSlots.isEmpty()) set.add(SfxMachineCapability.HAS_INPUT);
        if (outputSlots != null && !outputSlots.isEmpty()) set.add(SfxMachineCapability.HAS_OUTPUT);
        return Set.copyOf(set);
    }

    public static final class Builder {
        private final String id;
        private String displayName;
        private SfxMachineCategory category = SfxMachineCategory.SPECIAL;
        private List<Integer> inputSlots = List.of();
        private List<Integer> outputSlots = List.of();
        private int statusSlot = -1;
        private int tickInterval = 1;
        private final EnumSet<SfxMachineCapability> capabilities = EnumSet.noneOf(SfxMachineCapability.class);
        private SfxMachineInputProvider inputProvider;
        private SfxMachineOutputProvider outputProvider;
        private final List<SfxMachinePolicyRef> policyRefs = new ArrayList<>();
        private final List<SfxMachineEffect> effects = new ArrayList<>();

        private Builder(String id) { this.id = id; this.displayName = id; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder category(SfxMachineCategory category) { this.category = category == null ? SfxMachineCategory.SPECIAL : category; return this; }
        public Builder inputSlots(List<Integer> inputSlots) { this.inputSlots = inputSlots == null ? List.of() : List.copyOf(inputSlots); return this; }
        public Builder outputSlots(List<Integer> outputSlots) { this.outputSlots = outputSlots == null ? List.of() : List.copyOf(outputSlots); return this; }
        public Builder statusSlot(int statusSlot) { this.statusSlot = statusSlot; return this; }
        public Builder tickInterval(int tickInterval) { this.tickInterval = Math.max(1, tickInterval); return this; }
        public Builder capability(SfxMachineCapability capability) { if (capability != null) this.capabilities.add(capability); return this; }
        public Builder capabilities(Set<SfxMachineCapability> capabilities) { if (capabilities != null) this.capabilities.addAll(capabilities); return this; }
        public Builder inputProvider(SfxMachineInputProvider inputProvider) { this.inputProvider = inputProvider; return this; }
        public Builder outputProvider(SfxMachineOutputProvider outputProvider) { this.outputProvider = outputProvider; return this; }
        public Builder policyRef(SfxMachinePolicyRef policyRef) {
            if (policyRef != null && this.policyRefs.stream().noneMatch(existing -> existing.type().equals(policyRef.type()) && existing.name().equals(policyRef.name()))) {
                this.policyRefs.add(policyRef);
            }
            return this;
        }
        public Builder policyRefs(List<SfxMachinePolicyRef> policyRefs) { if (policyRefs != null) policyRefs.forEach(this::policyRef); return this; }
        public Builder effect(SfxMachineEffect effect) {
            if (effect != null && this.effects.stream().noneMatch(existing -> existing.phase() == effect.phase() && existing.name().equals(effect.name()))) {
                this.effects.add(effect);
            }
            return this;
        }
        public Builder effects(List<SfxMachineEffect> effects) { if (effects != null) effects.forEach(this::effect); return this; }

        public SfxMachineDefinition build() {
            Set<SfxMachineCapability> finalCapabilities = capabilities.isEmpty() ? defaultCapabilities(category, inputSlots, outputSlots) : Set.copyOf(capabilities);
            SfxMachineInputProvider finalInput = inputProvider == null ? (inputSlots.isEmpty() ? SfxMachineInputProvider.none() : SfxMachineInputProvider.guiSlots(inputSlots)) : inputProvider;
            SfxMachineOutputProvider finalOutput = outputProvider == null ? (outputSlots.isEmpty() ? SfxMachineOutputProvider.none() : SfxMachineOutputProvider.guiSlots(outputSlots)) : outputProvider;
            return new SfxMachineDefinition(id, displayName, category, inputSlots, outputSlots, statusSlot, tickInterval, finalCapabilities, finalInput, finalOutput, policyRefs, effects);
        }
    }
}
