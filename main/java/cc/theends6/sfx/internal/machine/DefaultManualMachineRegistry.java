package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.machine.manual.SfxManualMachineDefinition;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineRecipe;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOperation;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import cc.theends6.sfx.internal.core.SfxOwnedEntries;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.IdentityHashMap;

public final class DefaultManualMachineRegistry implements SfxManualMachineRegistry {
    private final Map<String, SfxManualMachineDefinition> machines = new LinkedHashMap<>();
    private final Map<String, String> machineOwners = new LinkedHashMap<>();
    private final Map<String, List<SfxManualMachineRecipe>> recipes = new LinkedHashMap<>();
    private final Map<SfxManualMachineRecipe, String> recipeOwners = new IdentityHashMap<>();
    private final Map<String, Map<ManualRecipeHash, List<SfxManualMachineRecipe>>> orderedRecipeIndex = new LinkedHashMap<>();
    private final Map<String, Map<ManualRecipeHash, List<SfxManualMachineRecipe>>> unorderedRecipeIndex = new LinkedHashMap<>();
    private volatile long revision;
    private final ThreadLocal<String> registrationOwner = ThreadLocal.withInitial(() -> SfxOwnedEntries.CORE_OWNER);

    @Override
    public synchronized void registerMachine(SfxManualMachineDefinition definition) {
        if (machines.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate SFX manual machine: " + definition.id());
        }
        machines.put(definition.id(), definition);
        machineOwners.put(definition.id(), registrationOwner.get());
    }

    @Override
    public synchronized void registerRecipe(SfxManualMachineRecipe recipe) {
        if (!machines.containsKey(recipe.machineId())) {
            throw new IllegalArgumentException("Unknown manual machine for recipe: " + recipe.machineId());
        }
        SfxManualMachineOperation machineOperation = machines.get(recipe.machineId()).operation();
        if (!isOperationAllowed(machineOperation, recipe.operation())) {
            throw new IllegalArgumentException("Recipe operation does not match manual machine: " + recipe.machineId());
        }
        recipes.computeIfAbsent(recipe.machineId(), ignored -> new ArrayList<>()).add(recipe);
        recipeOwners.put(recipe, registrationOwner.get());
        if (recipe.operation() == SfxManualMachineOperation.SHAPED_3X3) {
            index(orderedRecipeIndex, recipe.machineId(), ManualRecipeHash.orderedRecipe(recipe.input()), recipe);
        } else if (recipe.operation() == SfxManualMachineOperation.SHAPELESS_INPUT) {
            index(unorderedRecipeIndex, recipe.machineId(), ManualRecipeHash.unorderedRecipe(recipe.input()), recipe);
        }
        revision++;
    }

    public void clear() {
        machines.clear();
        machineOwners.clear();
        recipes.clear();
        recipeOwners.clear();
        orderedRecipeIndex.clear();
        unorderedRecipeIndex.clear();
        revision++;
    }

    public synchronized void removeOwner(String owner) {
        recipes.values().forEach(list -> list.removeIf(recipe -> owner.equals(recipeOwners.get(recipe))));
        recipes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        recipeOwners.entrySet().removeIf(entry -> owner.equals(entry.getValue()));
        machineOwners.entrySet().removeIf(entry -> {
            if (!owner.equals(entry.getValue())) {
                return false;
            }
            machines.remove(entry.getKey());
            return true;
        });
        rebuildIndexes();
        revision++;
    }

    public SfxManualMachineRegistry registrarFor(String owner) {
        return (SfxManualMachineRegistry) Proxy.newProxyInstance(
                SfxManualMachineRegistry.class.getClassLoader(),
                new Class<?>[] {SfxManualMachineRegistry.class},
                (proxy, method, args) -> {
                    String previous = registrationOwner.get();
                    registrationOwner.set(owner);
                    try {
                        return method.invoke(this, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    } finally {
                        registrationOwner.set(previous);
                    }
                });
    }

    private void rebuildIndexes() {
        orderedRecipeIndex.clear();
        unorderedRecipeIndex.clear();
        for (List<SfxManualMachineRecipe> values : recipes.values()) {
            for (SfxManualMachineRecipe recipe : values) {
                if (recipe.operation() == SfxManualMachineOperation.SHAPED_3X3) {
                    index(orderedRecipeIndex, recipe.machineId(), ManualRecipeHash.orderedRecipe(recipe.input()), recipe);
                } else if (recipe.operation() == SfxManualMachineOperation.SHAPELESS_INPUT) {
                    index(unorderedRecipeIndex, recipe.machineId(), ManualRecipeHash.unorderedRecipe(recipe.input()), recipe);
                }
            }
        }
    }

    private void index(Map<String, Map<ManualRecipeHash, List<SfxManualMachineRecipe>>> index, String machineId,
                       ManualRecipeHash hash, SfxManualMachineRecipe recipe) {
        index.computeIfAbsent(machineId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(hash, ignored -> new ArrayList<>())
                .add(recipe);
    }

    List<SfxManualMachineRecipe> orderedCandidates(String machineId, ManualRecipeHash hash) {
        return candidates(orderedRecipeIndex, machineId, hash);
    }

    List<SfxManualMachineRecipe> unorderedCandidates(String machineId, ManualRecipeHash hash) {
        return candidates(unorderedRecipeIndex, machineId, hash);
    }

    private List<SfxManualMachineRecipe> candidates(Map<String, Map<ManualRecipeHash, List<SfxManualMachineRecipe>>> index,
                                                 String machineId, ManualRecipeHash hash) {
        Map<ManualRecipeHash, List<SfxManualMachineRecipe>> byHash = index.get(SfxItemDefinition.normalizeId(machineId));
        return byHash == null ? List.of() : List.copyOf(byHash.getOrDefault(hash, List.of()));
    }

    long revision() {
        return revision;
    }


    private boolean isOperationAllowed(SfxManualMachineOperation machineOperation, SfxManualMachineOperation recipeOperation) {
        if (machineOperation == SfxManualMachineOperation.HAND_INPUT || recipeOperation == SfxManualMachineOperation.HAND_INPUT) {
            return machineOperation == recipeOperation;
        }
        return recipeOperation == SfxManualMachineOperation.SINGLE_INPUT || recipeOperation == SfxManualMachineOperation.SHAPED_3X3 || recipeOperation == SfxManualMachineOperation.SHAPELESS_INPUT;
    }

    @Override
    public Optional<SfxManualMachineDefinition> machine(String id) {
        return Optional.ofNullable(machines.get(SfxItemDefinition.normalizeId(id)));
    }

    @Override
    public Collection<SfxManualMachineDefinition> machines() {
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
        for (SfxManualMachineDefinition definition : machines.values()) {
            if (definition.hasTags(tags)) {
                result.add(definition.id());
            }
        }
        return List.copyOf(result);
    }

    public boolean acceptsRecipeType(String machineId, String recipeType) {
        SfxManualMachineDefinition definition = machines.get(SfxItemDefinition.normalizeId(machineId));
        return definition != null && definition.acceptsRecipeType(recipeType);
    }

    @Override
    public Collection<SfxManualMachineRecipe> recipesFor(String machineId) {
        return List.copyOf(recipes.getOrDefault(SfxItemDefinition.normalizeId(machineId), List.of()));
    }
}
