package cc.theends6.sfx.internal.recipe;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxRecipe;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.machine.ManualMachineOperation;
import cc.theends6.sfx.internal.machine.ManualMachineOutput;
import cc.theends6.sfx.internal.machine.ManualMachineRecipe;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;

public final class DefaultSfxRecipeRegistry {
    private static final Set<String> ALLOW_MISSING_EXACT = Set.of(
            "sf:restored_backpack",
            "sf:organic_food",
            "sf:fertilizer",
            "sf:debug_fish"
    );

    private final Map<String, SfxRecipeDefinition> definitions = new LinkedHashMap<>();

    public void register(SfxRecipeDefinition definition) {
        if (definitions.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate SFX recipe: " + definition.id());
        }
        definitions.put(definition.id(), definition);
    }

    public Collection<SfxRecipeDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public AuditResult apply(DefaultSfxItemRegistry items, DefaultManualMachineRegistry machines) {
        Audit audit = new Audit();
        Map<String, List<SfxRecipe>> recipesByOutput = new LinkedHashMap<>();

        for (SfxRecipeDefinition definition : definitions.values()) {
            audit.attempted(definition.id());
            List<String> errors = validateDefinition(definition, items, machines);
            if (!errors.isEmpty()) {
                audit.skipped(definition.id(), String.join("; ", errors));
                continue;
            }

            List<String> runtimeMachineIds = runtimeMachineIds(definition, machines);
            if (definition.runtimeEnabled() && !runtimeMachineIds.isEmpty()) {
                registerRuntime(definition, machines, runtimeMachineIds, audit);
            }

            for (SfxRecipeOutputDefinition output : definition.allOutputs()) {
                if (!output.isSfxItem()) {
                    continue;
                }
                recipesByOutput.computeIfAbsent(output.sfxItemId(), ignored -> new ArrayList<>())
                        .add(toGuideRecipe(definition, output.amount()));
            }

            audit.registered(definition.id());
        }

        applyGuideRecipes(items, recipesByOutput);
        auditMissingExactRecipes(items, recipesByOutput.keySet(), machines, audit);
        return audit.result();
    }

    private List<String> validateDefinition(SfxRecipeDefinition definition, DefaultSfxItemRegistry items, DefaultManualMachineRegistry machines) {
        List<String> errors = new ArrayList<>();

        if (definition.runtimeEnabled()) {
            List<String> runtimeMachineIds = runtimeMachineIds(definition, machines);
            if (!definition.runtimeMachineTags().isEmpty() && runtimeMachineIds.isEmpty()) {
                errors.add("runtime machine tags matched no machines " + definition.runtimeMachineTags());
            }
            for (String machineId : runtimeMachineIds) {
                if (machines.machine(machineId).isEmpty()) {
                    errors.add("unknown runtime machine " + machineId);
                }
            }
        }

        for (SfxRecipeSlot slot : definition.inputs()) {
            if (slot != null && slot.isSfxItem() && items.item(slot.sfxItemId()).isEmpty()) {
                errors.add("missing input " + slot.sfxItemId());
            }
        }

        for (SfxRecipeOutputDefinition output : definition.allOutputs()) {
            if (output.isSfxItem() && items.item(output.sfxItemId()).isEmpty()) {
                errors.add("missing output item " + output.sfxItemId());
            }
        }

        return errors;
    }

    private void registerRuntime(SfxRecipeDefinition definition, DefaultManualMachineRegistry machines, List<String> runtimeMachineIds, Audit audit) {
        if (runtimeMachineIds.isEmpty()) {
            return;
        }

        Component note = toGuideNote(definition);
        List<ManualMachineOutput> fixedOutputs = toManualOutputs(definition.outputs());
        List<ManualMachineOutput> randomOutputs = toManualOutputs(definition.randomOutputs());

        for (String machineId : runtimeMachineIds) {
            ManualMachineRecipe recipe = switch (definition.operation()) {
                case SHAPED -> ManualMachineRecipe.shaped(machineId, definition.inputs(), fixedOutputs, note);
                case SHAPELESS -> definition.matchPriority() == null
                        ? ManualMachineRecipe.shapeless(machineId, definition.inputs(), fixedOutputs, note)
                        : ManualMachineRecipe.shapeless(machineId, definition.inputs(), fixedOutputs, note, definition.matchPriority());
                case HAND -> ManualMachineRecipe.hand(machineId, definition.inputs().getFirst(), fixedOutputs, note);
                case SINGLE -> randomOutputs.isEmpty()
                        ? ManualMachineRecipe.single(machineId, definition.inputs().getFirst(), fixedOutputs, note)
                        : ManualMachineRecipe.randomSingle(machineId, definition.inputs().getFirst(), randomOutputs, fixedOutputs, note);
            };

            try {
                machines.registerRecipe(recipe);
            } catch (IllegalArgumentException ex) {
                audit.skipped(definition.id(), ex.getMessage());
            }
        }
    }

    private List<String> runtimeMachineIds(SfxRecipeDefinition definition, DefaultManualMachineRegistry machines) {
        Set<String> result = new LinkedHashSet<>(definition.runtimeMachineIds());
        if (!definition.runtimeMachineTags().isEmpty()) {
            result.addAll(machines.machineIdsWithTags(definition.runtimeMachineTags()));
        }
        return List.copyOf(result);
    }

    private List<ManualMachineOutput> toManualOutputs(List<SfxRecipeOutputDefinition> outputs) {
        List<ManualMachineOutput> mapped = new ArrayList<>(outputs.size());
        for (SfxRecipeOutputDefinition output : outputs) {
            if (output.isSfxItem()) {
                mapped.add(ManualMachineOutput.sfx(output.sfxItemId(), output.amount()));
            } else {
                mapped.add(ManualMachineOutput.vanilla(output.material(), output.amount()));
            }
        }
        return mapped;
    }

    private void applyGuideRecipes(DefaultSfxItemRegistry items, Map<String, List<SfxRecipe>> recipesByOutput) {
        for (Map.Entry<String, List<SfxRecipe>> entry : recipesByOutput.entrySet()) {
            Optional<SfxItemDefinition> optional = items.item(entry.getKey());
            if (optional.isEmpty()) {
                continue;
            }
            List<SfxRecipe> merged = new ArrayList<>(optional.get().recipes());
            merged.addAll(entry.getValue());
            merged.sort(Comparator.comparingInt(this::guideOrderOf).thenComparing(SfxRecipe::id));
            items.replaceItem(withRecipes(optional.get(), merged));
        }
    }

    private int guideOrderOf(SfxRecipe recipe) {
        SfxRecipeDefinition definition = definitions.get(recipe.id());
        return definition == null ? 0 : definition.guideOrder();
    }

    private SfxRecipe toGuideRecipe(SfxRecipeDefinition definition, int outputAmount) {
        List<SfxRecipeSlot> matrix = switch (definition.operation()) {
            case SHAPED -> definition.inputs();
            case SHAPELESS -> paddedMatrix(definition.inputs());
            case SINGLE, HAND -> singleCenter(definition.inputs().getFirst());
        };
        return SfxRecipe.shaped(definition.id(), definition.recipeType(), matrix, toGuideNote(definition), outputAmount);
    }

    private Component toGuideNote(SfxRecipeDefinition definition) {
        List<String> segments = new ArrayList<>();
        if (definition.note() != null && !definition.note().isBlank()) {
            segments.add(definition.note());
        }
        if (definition.durationTicks() != null && definition.durationTicks() > 0) {
            segments.add("<gray>Time: " + definition.durationTicks() + "t</gray>");
        }
        if (segments.isEmpty()) {
            return null;
        }
        return Text.mm(String.join("<newline>", segments));
    }

    private List<SfxRecipeSlot> paddedMatrix(List<SfxRecipeSlot> inputs) {
        List<SfxRecipeSlot> matrix = new ArrayList<>(List.of(
                SfxRecipeSlot.empty(), SfxRecipeSlot.empty(), SfxRecipeSlot.empty(),
                SfxRecipeSlot.empty(), SfxRecipeSlot.empty(), SfxRecipeSlot.empty(),
                SfxRecipeSlot.empty(), SfxRecipeSlot.empty(), SfxRecipeSlot.empty()
        ));
        for (int i = 0; i < Math.min(inputs.size(), 9); i++) {
            matrix.set(i, inputs.get(i));
        }
        return matrix;
    }

    private List<SfxRecipeSlot> singleCenter(SfxRecipeSlot input) {
        return List.of(
                SfxRecipeSlot.empty(), SfxRecipeSlot.empty(), SfxRecipeSlot.empty(),
                SfxRecipeSlot.empty(), input, SfxRecipeSlot.empty(),
                SfxRecipeSlot.empty(), SfxRecipeSlot.empty(), SfxRecipeSlot.empty()
        );
    }

    private void auditMissingExactRecipes(DefaultSfxItemRegistry items, Set<String> outputsWithRecipe, DefaultManualMachineRegistry machines, Audit audit) {
        Set<String> recipeOutputs = new LinkedHashSet<>(outputsWithRecipe);
        for (var machine : machines.machines()) {
            recipeOutputs.add(machine.id());
            for (var recipe : machines.recipesFor(machine.id())) {
                for (ManualMachineOutput output : recipe.outputs()) {
                    if (output.isSfxItem()) {
                        recipeOutputs.add(output.sfxItemId());
                    }
                }
            }
        }

        for (SfxItemDefinition item : items.items()) {
            String id = item.id();
            if (item.hidden() || !item.giveable() || !id.startsWith("sf:")) {
                continue;
            }
            if (ALLOW_MISSING_EXACT.contains(id)) {
                continue;
            }
            if (!recipeOutputs.contains(id)) {
                audit.missingExact(id);
            }
        }
    }

    private SfxItemDefinition withRecipes(SfxItemDefinition existing, List<SfxRecipe> recipes) {
        SfxItemDefinition.Builder builder = SfxItemDefinition.builder(existing.id(), existing.material(), existing.name())
                .lore(existing.lore())
                .category(existing.categoryId())
                .order(existing.order())
                .version(existing.version())
                .hidden(existing.hidden())
                .giveable(existing.giveable())
                .kind(existing.kind())
                .variant(existing.variant());
        if (existing.headTextureHash() != null) {
            builder.headTexture(existing.headTextureHash());
        }
        if (existing.colorRgb() != null) {
            builder.colorRgb(existing.colorRgb());
        }
        for (String flag : existing.flags()) {
            builder.flag(flag);
        }
        for (String itemFlag : existing.itemFlags()) {
            builder.itemFlag(itemFlag);
        }
        for (var enchantment : existing.enchantments().entrySet()) {
            builder.enchantment(enchantment.getKey(), enchantment.getValue());
        }
        builder.unbreakable(existing.unbreakable());
        for (SfxRecipe recipe : recipes) {
            builder.addRecipe(recipe);
        }
        return builder.build();
    }

    public record AuditResult(int attempted, int registered, int skipped, int missingExact, List<String> warnings) {
        public String summary() {
            return "[SFX Recipe Import] exact-attempted=" + attempted
                    + " exact-registered=" + registered
                    + " skipped=" + skipped
                    + " missing-exact=" + missingExact;
        }
    }

    private static final class Audit {
        private int attempted;
        private int registered;
        private int skipped;
        private int missingExact;
        private final List<String> warnings = new ArrayList<>();

        private void attempted(String id) {
            attempted++;
        }

        private void registered(String id) {
            registered++;
        }

        private void skipped(String id, String reason) {
            skipped++;
            warnings.add("[SFX Recipe Import] skipped " + id + ": " + reason);
        }

        private void missingExact(String id) {
            missingExact++;
            if (warnings.size() < 200) {
                warnings.add("[SFX Recipe Import] missing exact recipe for " + id);
            }
        }

        private AuditResult result() {
            return new AuditResult(attempted, registered, skipped, missingExact, List.copyOf(warnings));
        }
    }
}
