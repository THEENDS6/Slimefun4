package cc.theends6.sfx.internal.electric;

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
        SfxElectricMachineMenuStyle menuStyle,
        Set<String> functionTags,
        SfxElectricMachineUiDefinition ui,
        SfxElectricAssemblerSpec assemblerSpec
) {
    private static final int[] DEFAULT_INPUT_SLOTS = {19, 20};
    private static final int[] DEFAULT_OUTPUT_SLOTS = {24, 25};
    public static final int[] SIX_INPUT_SLOTS = {10, 11, 19, 20, 28, 29};
    public static final int[] SIMPLE_IO_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    public static final int[] GEO_MINER_OUTPUT_SLOTS = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    public static final int[] SEVEN_INPUT_SLOTS = SIMPLE_IO_SLOTS;
    public static final int[] THREE_OUTPUT_SLOTS = {12, 13, 14};
    public static final int[] ASSEMBLER_INPUT_SLOTS = {19, 28, 25, 34};
    public static final int[] AUTO_BREWER_INPUT_SLOTS = {10, 16, 37, 39, 41, 43};
    public static final int[] NO_INPUT_SLOTS = {};
    public static final int[] NO_OUTPUT_SLOTS = {};

    public SfxElectricMachineDefinition(
            String id,
            String nameKey,
            int speed,
            int energyCapacity,
            int energyConsumptionPerTick,
            Material progressMaterial,
            SfxElectricRecipeProvider recipeProvider
    ) {
        this(id, nameKey, speed, energyCapacity, energyConsumptionPerTick, progressMaterial, recipeProvider, DEFAULT_INPUT_SLOTS, DEFAULT_OUTPUT_SLOTS, SfxElectricMachineMenuStyle.STANDARD, functionTagsForLayout(SfxElectricMachineMenuStyle.STANDARD), SfxElectricMachineUiDefinition.UNDEFINED, null);
    }

    public SfxElectricMachineDefinition(
            String id,
            String nameKey,
            int speed,
            int energyCapacity,
            int energyConsumptionPerTick,
            Material progressMaterial,
            SfxElectricRecipeProvider recipeProvider,
            int[] inputSlots,
            int[] outputSlots
    ) {
        this(id, nameKey, speed, energyCapacity, energyConsumptionPerTick, progressMaterial, recipeProvider, inputSlots, outputSlots, SfxElectricMachineMenuStyle.STANDARD, functionTagsForLayout(SfxElectricMachineMenuStyle.STANDARD), SfxElectricMachineUiDefinition.UNDEFINED, null);
    }

    public SfxElectricMachineDefinition(
            String id,
            String nameKey,
            int speed,
            int energyCapacity,
            int energyConsumptionPerTick,
            Material progressMaterial,
            SfxElectricRecipeProvider recipeProvider,
            int[] inputSlots,
            int[] outputSlots,
            SfxElectricMachineMenuStyle menuStyle
    ) {
        this(id, nameKey, speed, energyCapacity, energyConsumptionPerTick, progressMaterial, recipeProvider, inputSlots, outputSlots, menuStyle, functionTagsForLayout(menuStyle), SfxElectricMachineUiDefinition.UNDEFINED, null);
    }

    public SfxElectricMachineDefinition(
            String id,
            String nameKey,
            int speed,
            int energyCapacity,
            int energyConsumptionPerTick,
            Material progressMaterial,
            SfxElectricRecipeProvider recipeProvider,
            int[] inputSlots,
            int[] outputSlots,
            SfxElectricMachineMenuStyle menuStyle,
            SfxElectricAssemblerSpec assemblerSpec
    ) {
        this(id, nameKey, speed, energyCapacity, energyConsumptionPerTick, progressMaterial, recipeProvider, inputSlots, outputSlots, menuStyle, functionTagsForLayout(menuStyle), SfxElectricMachineUiDefinition.UNDEFINED, assemblerSpec);
    }

    public SfxElectricMachineDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(progressMaterial, "progressMaterial");
        Objects.requireNonNull(recipeProvider, "recipeProvider");
        Objects.requireNonNull(inputSlots, "inputSlots");
        Objects.requireNonNull(outputSlots, "outputSlots");
        Objects.requireNonNull(menuStyle, "menuStyle");
        functionTags = normalizeFunctionTags(functionTags);
        Objects.requireNonNull(ui, "ui");
        if (inputSlots.length > SfxElectricMachineState.MAX_INPUTS) {
            throw new IllegalArgumentException("Electric machines support up to seven input slots.");
        }
        if (outputSlots.length > SfxElectricMachineState.MAX_OUTPUTS) {
            throw new IllegalArgumentException("Electric machines support up to ten output slots.");
        }
        speed = Math.max(1, speed);
        energyCapacity = Math.max(0, energyCapacity);
        energyConsumptionPerTick = Math.max(0, energyConsumptionPerTick);
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

    private static Set<String> functionTagsForLayout(SfxElectricMachineMenuStyle layout) {
        if (layout == null) {
            return Set.of();
        }
        return switch (layout) {
            case AUTO_CRAFTER -> Set.of("auto-crafter");
            case AUTO_BREWER -> Set.of("auto-brewer");
            case ASSEMBLER -> Set.of("assembler");
            case GEO_MINER -> Set.of("geo-miner");
            case SIMPLE_IO -> Set.of("simple-io");
            case NONE -> Set.of("no-menu");
            case STANDARD -> Set.of("standard-processing");
        };
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
}
