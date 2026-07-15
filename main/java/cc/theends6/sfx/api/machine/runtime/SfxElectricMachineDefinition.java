package cc.theends6.sfx.api.machine.runtime;

import cc.theends6.sfx.api.machine.runtime.*;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;

public record SfxElectricMachineDefinition(
        String id,
        String nameKey,
        int speed,
        int energyCapacity,
        int energyConsumptionPerTick,
        Material progressMaterial,
        SfxElectricRecipeProvider recipeProvider,
        int[] inputSlots,
        int[] outputSlots,
        String compiledEntryId,
        Set<String> functionTags,
        Set<String> guideRecipeTypes,
        SfxElectricMachineUiDefinition ui,
        SfxElectricAssemblerSpec assemblerSpec
) {
    public SfxElectricMachineDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(progressMaterial, "progressMaterial");
        Objects.requireNonNull(recipeProvider, "recipeProvider");
        Objects.requireNonNull(inputSlots, "inputSlots");
        Objects.requireNonNull(outputSlots, "outputSlots");
        compiledEntryId = compiledEntryId == null || compiledEntryId.isBlank() ? id : compiledEntryId.trim();
        functionTags = normalizeFunctionTags(functionTags);
        guideRecipeTypes = normalizeRecipeTypes(guideRecipeTypes);
        Objects.requireNonNull(ui, "ui");
        if (inputSlots.length > SfxElectricMachineState.MAX_INPUTS) {
            throw new IllegalArgumentException("Electric machines support up to seven input slots.");
        }
        if (outputSlots.length > SfxElectricMachineState.MAX_OUTPUTS) {
            throw new IllegalArgumentException("Electric machines support up to ten output slots.");
        }
        if (speed < 1) {
            throw new IllegalArgumentException("Electric machine speed must be at least 1.");
        }
        if (energyCapacity < 0) {
            throw new IllegalArgumentException("Electric machine energy capacity must be zero or greater.");
        }
        if (energyConsumptionPerTick < 0) {
            throw new IllegalArgumentException("Electric machine energy consumption must be zero or greater.");
        }
        inputSlots = Arrays.copyOf(inputSlots, inputSlots.length);
        outputSlots = Arrays.copyOf(outputSlots, outputSlots.length);
    }

    @Override
    public int[] inputSlots() {
        return Arrays.copyOf(inputSlots, inputSlots.length);
    }

    @Override
    public int[] outputSlots() {
        return Arrays.copyOf(outputSlots, outputSlots.length);
    }

    public boolean hasFunction(String function) {
        return function != null && functionTags.contains(normalizeFunctionTag(function));
    }

    public boolean executesGuideRecipeType(String recipeType) {
        return recipeType != null && guideRecipeTypes.contains(normalizeRecipeType(recipeType));
    }

    private static Set<String> normalizeFunctionTags(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                result.add(normalizeFunctionTag(value));
            }
        }
        return Set.copyOf(result);
    }

    private static String normalizeFunctionTag(String raw) {
        return raw.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeRecipeTypes(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                result.add(normalizeRecipeType(value));
            }
        }
        return Set.copyOf(result);
    }

    private static String normalizeRecipeType(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
