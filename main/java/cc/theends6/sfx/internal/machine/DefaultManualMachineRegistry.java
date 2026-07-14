package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DefaultManualMachineRegistry implements SfxManualMachineRegistry {
    private final Map<String, ManualMachineDefinition> machines = new LinkedHashMap<>();
    private final Map<String, List<ManualMachineRecipe>> recipes = new LinkedHashMap<>();
    private final Map<String, Map<ManualRecipeHash, List<ManualMachineRecipe>>> orderedRecipeIndex = new LinkedHashMap<>();
    private final Map<String, Map<ManualRecipeHash, List<ManualMachineRecipe>>> unorderedRecipeIndex = new LinkedHashMap<>();
    private volatile long revision;

    @Override
    public void registerMachine(ManualMachineDefinition definition) {
        if (machines.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate SFX manual machine: " + definition.id());
        }
        machines.put(definition.id(), definition);
    }

    @Override
    public void registerRecipe(ManualMachineRecipe recipe) {
        if (!machines.containsKey(recipe.machineId())) {
            throw new IllegalArgumentException("Unknown manual machine for recipe: " + recipe.machineId());
        }
        ManualMachineOperation machineOperation = machines.get(recipe.machineId()).operation();
        if (!isOperationAllowed(machineOperation, recipe.operation())) {
            throw new IllegalArgumentException("Recipe operation does not match manual machine: " + recipe.machineId());
        }
        recipes.computeIfAbsent(recipe.machineId(), ignored -> new ArrayList<>()).add(recipe);
        if (recipe.operation() == ManualMachineOperation.SHAPED_3X3) {
            index(orderedRecipeIndex, recipe.machineId(), ManualRecipeHash.orderedRecipe(recipe.input()), recipe);
        } else if (recipe.operation() == ManualMachineOperation.SHAPELESS_INPUT) {
            index(unorderedRecipeIndex, recipe.machineId(), ManualRecipeHash.unorderedRecipe(recipe.input()), recipe);
        }
        revision++;
    }

    public void clear() {
        machines.clear();
        recipes.clear();
        orderedRecipeIndex.clear();
        unorderedRecipeIndex.clear();
        revision++;
    }

    private void index(Map<String, Map<ManualRecipeHash, List<ManualMachineRecipe>>> index, String machineId,
                       ManualRecipeHash hash, ManualMachineRecipe recipe) {
        index.computeIfAbsent(machineId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(hash, ignored -> new ArrayList<>())
                .add(recipe);
    }

    List<ManualMachineRecipe> orderedCandidates(String machineId, ManualRecipeHash hash) {
        return candidates(orderedRecipeIndex, machineId, hash);
    }

    List<ManualMachineRecipe> unorderedCandidates(String machineId, ManualRecipeHash hash) {
        return candidates(unorderedRecipeIndex, machineId, hash);
    }

    private List<ManualMachineRecipe> candidates(Map<String, Map<ManualRecipeHash, List<ManualMachineRecipe>>> index,
                                                 String machineId, ManualRecipeHash hash) {
        Map<ManualRecipeHash, List<ManualMachineRecipe>> byHash = index.get(SfxItemDefinition.normalizeId(machineId));
        return byHash == null ? List.of() : List.copyOf(byHash.getOrDefault(hash, List.of()));
    }

    long revision() {
        return revision;
    }


    private boolean isOperationAllowed(ManualMachineOperation machineOperation, ManualMachineOperation recipeOperation) {
        if (machineOperation == ManualMachineOperation.HAND_INPUT || recipeOperation == ManualMachineOperation.HAND_INPUT) {
            return machineOperation == recipeOperation;
        }
        return recipeOperation == ManualMachineOperation.SINGLE_INPUT || recipeOperation == ManualMachineOperation.SHAPED_3X3 || recipeOperation == ManualMachineOperation.SHAPELESS_INPUT;
    }

    @Override
    public Optional<ManualMachineDefinition> machine(String id) {
        return Optional.ofNullable(machines.get(SfxItemDefinition.normalizeId(id)));
    }

    @Override
    public Collection<ManualMachineDefinition> machines() {
        return List.copyOf(machines.values());
    }

    public List<String> machineIdsWithTags(Collection<String> requiredTags) {
        Set<String> tags = new LinkedHashSet<>();
        if (requiredTags != null) {
            for (String tag : requiredTags) {
                if (tag != null && !tag.isBlank()) {
                    tags.add(tag);
                }
            }
        }
        if (tags.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (ManualMachineDefinition definition : machines.values()) {
            if (definition.hasTags(tags)) {
                result.add(definition.id());
            }
        }
        return List.copyOf(result);
    }

    public boolean acceptsRecipeType(String machineId, String recipeType) {
        ManualMachineDefinition definition = machines.get(SfxItemDefinition.normalizeId(machineId));
        return definition != null && definition.acceptsRecipeType(recipeType);
    }

    @Override
    public Collection<ManualMachineRecipe> recipesFor(String machineId) {
        return List.copyOf(recipes.getOrDefault(SfxItemDefinition.normalizeId(machineId), List.of()));
    }
}
