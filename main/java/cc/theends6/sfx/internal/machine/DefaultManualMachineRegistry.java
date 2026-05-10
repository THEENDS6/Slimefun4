package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DefaultManualMachineRegistry implements SfxManualMachineRegistry {
    private final Map<String, ManualMachineDefinition> machines = new LinkedHashMap<>();
    private final Map<String, List<ManualMachineRecipe>> recipes = new LinkedHashMap<>();

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
    }

    public void clear() {
        machines.clear();
        recipes.clear();
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

    @Override
    public Collection<ManualMachineRecipe> recipesFor(String machineId) {
        return List.copyOf(recipes.getOrDefault(SfxItemDefinition.normalizeId(machineId), List.of()));
    }
}
