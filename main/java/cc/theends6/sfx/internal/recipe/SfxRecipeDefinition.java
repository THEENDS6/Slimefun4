package cc.theends6.sfx.internal.recipe;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SfxRecipeDefinition {
    private final String id;
    private final String recipeType;
    private final List<String> runtimeMachineIds;
    private final List<String> runtimeMachineTags;
    private final SfxRecipeOperation operation;
    private final List<SfxRecipeSlot> inputs;
    private final List<SfxRecipeOutputDefinition> outputs;
    private final List<SfxRecipeOutputDefinition> randomOutputs;
    private final int guideOrder;
    private final Integer matchPriority;
    private final Integer durationTicks;
    private final String source;
    private final String note;
    private final boolean runtimeEnabled;

    private SfxRecipeDefinition(Builder builder) {
        this.id = normalizeRecipeId(builder.id);
        this.recipeType = Objects.requireNonNull(builder.recipeType, "recipeType");
        this.runtimeMachineIds = Collections.unmodifiableList(builder.runtimeMachineIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(SfxItemDefinition::normalizeId)
                .distinct()
                .toList());
        this.runtimeMachineTags = Collections.unmodifiableList(builder.runtimeMachineTags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(SfxRecipeDefinition::normalizeTag)
                .distinct()
                .toList());
        this.operation = Objects.requireNonNull(builder.operation, "operation");
        this.inputs = Collections.unmodifiableList(new ArrayList<>(builder.inputs));
        this.outputs = Collections.unmodifiableList(new ArrayList<>(builder.outputs));
        this.randomOutputs = Collections.unmodifiableList(new ArrayList<>(builder.randomOutputs));
        this.guideOrder = builder.guideOrder;
        this.matchPriority = builder.matchPriority;
        this.durationTicks = builder.durationTicks;
        this.source = builder.source == null || builder.source.isBlank() ? "custom" : builder.source.trim().toLowerCase();
        this.note = builder.note;
        this.runtimeEnabled = builder.runtimeEnabled;

        validateShape();
    }

    public static Builder builder(String id, String recipeType, SfxRecipeOperation operation) {
        return new Builder(id, recipeType, operation);
    }

    private static String normalizeRecipeId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Recipe id cannot be blank.");
        }
        return id.trim().toLowerCase();
    }

    private void validateShape() {
        if (operation == SfxRecipeOperation.SHAPED && inputs.size() != 9) {
            throw new IllegalArgumentException("Shaped recipe must contain exactly 9 input slots.");
        }
        if ((operation == SfxRecipeOperation.SINGLE || operation == SfxRecipeOperation.HAND) && inputs.size() != 1) {
            throw new IllegalArgumentException("Single/hand recipe must contain exactly 1 input slot.");
        }
        if (operation == SfxRecipeOperation.SHAPELESS) {
            if (inputs.isEmpty() || inputs.size() > 9) {
                throw new IllegalArgumentException("Shapeless recipe must contain between 1 and 9 input slots.");
            }
            for (SfxRecipeSlot slot : inputs) {
                if (slot == null || slot.isEmpty()) {
                    throw new IllegalArgumentException("Shapeless recipe cannot contain empty slots.");
                }
            }
        }
        if (outputs.isEmpty() && randomOutputs.isEmpty()) {
            throw new IllegalArgumentException("Recipe must define at least one output.");
        }
    }

    public String id() {
        return id;
    }

    public String recipeType() {
        return recipeType;
    }

    public List<String> runtimeMachineIds() {
        return runtimeMachineIds;
    }

    public List<String> runtimeMachineTags() {
        return runtimeMachineTags;
    }

    public SfxRecipeOperation operation() {
        return operation;
    }

    public List<SfxRecipeSlot> inputs() {
        return inputs;
    }

    public List<SfxRecipeOutputDefinition> outputs() {
        return outputs;
    }

    public List<SfxRecipeOutputDefinition> randomOutputs() {
        return randomOutputs;
    }

    public List<SfxRecipeOutputDefinition> allOutputs() {
        List<SfxRecipeOutputDefinition> combined = new ArrayList<>(outputs.size() + randomOutputs.size());
        combined.addAll(outputs);
        combined.addAll(randomOutputs);
        return combined;
    }

    public int guideOrder() {
        return guideOrder;
    }

    public Integer matchPriority() {
        return matchPriority;
    }

    public Integer durationTicks() {
        return durationTicks;
    }

    public String source() {
        return source;
    }

    public String note() {
        return note;
    }

    public boolean runtimeEnabled() {
        return runtimeEnabled;
    }

    public static final class Builder {
        private final String id;
        private final String recipeType;
        private final SfxRecipeOperation operation;
        private final List<String> runtimeMachineIds = new ArrayList<>();
        private final List<String> runtimeMachineTags = new ArrayList<>();
        private final List<SfxRecipeSlot> inputs = new ArrayList<>();
        private final List<SfxRecipeOutputDefinition> outputs = new ArrayList<>();
        private final List<SfxRecipeOutputDefinition> randomOutputs = new ArrayList<>();
        private int guideOrder;
        private Integer matchPriority;
        private Integer durationTicks;
        private String source;
        private String note;
        private boolean runtimeEnabled;

        private Builder(String id, String recipeType, SfxRecipeOperation operation) {
            this.id = id;
            this.recipeType = recipeType;
            this.operation = operation;
        }

        public Builder runtimeMachineId(String machineId) {
            this.runtimeMachineIds.clear();
            if (machineId != null && !machineId.isBlank()) {
                this.runtimeMachineIds.add(machineId);
            }
            return this;
        }

        public Builder runtimeMachineIds(List<String> machineIds) {
            this.runtimeMachineIds.clear();
            if (machineIds != null) {
                this.runtimeMachineIds.addAll(machineIds);
            }
            return this;
        }

        public Builder runtimeMachineTags(List<String> tags) {
            this.runtimeMachineTags.clear();
            if (tags != null) {
                this.runtimeMachineTags.addAll(tags);
            }
            return this;
        }

        public Builder inputs(List<SfxRecipeSlot> inputs) {
            this.inputs.clear();
            this.inputs.addAll(inputs);
            return this;
        }

        public Builder outputs(List<SfxRecipeOutputDefinition> outputs) {
            this.outputs.clear();
            this.outputs.addAll(outputs);
            return this;
        }

        public Builder randomOutputs(List<SfxRecipeOutputDefinition> randomOutputs) {
            this.randomOutputs.clear();
            this.randomOutputs.addAll(randomOutputs);
            return this;
        }

        public Builder guideOrder(int guideOrder) {
            this.guideOrder = guideOrder;
            return this;
        }

        public Builder matchPriority(Integer matchPriority) {
            this.matchPriority = matchPriority;
            return this;
        }

        public Builder durationTicks(Integer durationTicks) {
            this.durationTicks = durationTicks;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder note(String note) {
            this.note = note;
            return this;
        }

        public Builder runtimeEnabled(boolean runtimeEnabled) {
            this.runtimeEnabled = runtimeEnabled;
            return this;
        }

        public SfxRecipeDefinition build() {
            return new SfxRecipeDefinition(this);
        }
    }

    private static String normalizeTag(String raw) {
        return raw.trim().replace('_', '-').toLowerCase(java.util.Locale.ROOT);
    }
}
