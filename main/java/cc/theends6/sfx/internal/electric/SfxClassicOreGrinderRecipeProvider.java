package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOutput;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOperation;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineRecipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;

public final class SfxClassicOreGrinderRecipeProvider implements SfxElectricRecipeProvider {
    private final List<SfxElectricRecipe> recipes;

    public SfxClassicOreGrinderRecipeProvider(
            Collection<SfxManualMachineRecipe> grindStoneRecipes,
            Collection<SfxManualMachineRecipe> oreCrusherRecipes,
            int baseSeconds
    ) {
        List<SfxElectricRecipe> collected = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>();
        registerImportedRecipes(collected, signatures, grindStoneRecipes, "sf:electric_ore_grinder:grind");
        registerImportedRecipes(collected, signatures, oreCrusherRecipes, "sf:electric_ore_grinder:crusher");
        registerExplicitRecipes(collected, signatures, baseSeconds);
        this.recipes = List.copyOf(collected);
    }

    @Override
    public List<SfxElectricRecipe> recipes() {
        return recipes;
    }

    private void registerImportedRecipes(List<SfxElectricRecipe> target, Set<String> signatures, Collection<SfxManualMachineRecipe> manualRecipes, String keyPrefix) {
        int index = 0;
        for (SfxManualMachineRecipe recipe : manualRecipes) {
            if (recipe.operation() != SfxManualMachineOperation.SINGLE_INPUT || recipe.hasRandomOutputs() || recipe.fixedOutputs().size() != 1) {
                continue;
            }
            SfxManualMachineOutput output = recipe.fixedOutputs().getFirst();
            SfxElectricRecipe electricRecipe = new SfxElectricRecipe(
                    keyPrefix + ":" + index++,
                    recipe.input().getFirst(),
                    output.isSfxItem()
                            ? SfxElectricStack.sfx(output.sfxItemId(), output.amount())
                            : SfxElectricStack.vanilla(output.material(), output.amount()),
                    4);
            if (isDisabledRecipe(electricRecipe) || !signatures.add(signatureOf(electricRecipe))) {
                continue;
            }
            target.add(electricRecipe);
        }
    }

    private void registerExplicitRecipes(List<SfxElectricRecipe> target, Set<String> signatures, int baseSeconds) {
        addVanilla(target, signatures, "grind", Material.BLAZE_ROD, 1, Material.BLAZE_POWDER, 4, baseSeconds);
        addVanilla(target, signatures, "grind", Material.BONE, 1, Material.BONE_MEAL, 4, baseSeconds);
        addVanilla(target, signatures, "grind", Material.BONE_BLOCK, 1, Material.BONE_MEAL, 9, baseSeconds);
        addVanilla(target, signatures, "grind", Material.COBBLESTONE, 1, Material.GRAVEL, 1, baseSeconds);
        addVanilla(target, signatures, "grind", Material.ANDESITE, 1, Material.GRAVEL, 1, baseSeconds);
        addVanilla(target, signatures, "grind", Material.DIORITE, 1, Material.GRAVEL, 1, baseSeconds);
        addVanilla(target, signatures, "grind", Material.GRANITE, 1, Material.GRAVEL, 1, baseSeconds);
        addVanilla(target, signatures, "grind", Material.BLACKSTONE, 1, Material.GRAVEL, 1, baseSeconds);
        addVanilla(target, signatures, "grind", Material.BASALT, 1, Material.GRAVEL, 1, baseSeconds);
        addVanilla(target, signatures, "grind", Material.COBBLED_DEEPSLATE, 1, Material.GRAVEL, 1, baseSeconds);
        addVanilla(target, signatures, "grind", Material.SANDSTONE, 1, Material.SAND, 4, baseSeconds);
        addVanilla(target, signatures, "grind", Material.RED_SANDSTONE, 1, Material.RED_SAND, 4, baseSeconds);
        addSfx(target, signatures, "grind", Material.DIRT, 1, "sf:stone_chunk", 1, baseSeconds);

        addVanilla(target, signatures, "crusher", Material.GRAVEL, 1, Material.SAND, 1, baseSeconds);
        addVanilla(target, signatures, "crusher", Material.BLACKSTONE, 8, Material.RED_SAND, 1, baseSeconds);
        addSfx(target, signatures, "crusher", Material.NETHERRACK, 1, "sf:sulfate", 1, baseSeconds);
        addSfx(target, signatures, "crusher", Material.MAGMA_BLOCK, 1, "sf:sulfate", 1, baseSeconds);
        addVanillaFromSfx(target, signatures, "crusher", "sf:carbon", 1, Material.COAL, 8, baseSeconds);
        addSfxFromSfx(target, signatures, "crusher", "sf:compressed_carbon", 1, "sf:carbon", 4, baseSeconds);
    }

    private void addVanilla(List<SfxElectricRecipe> target, Set<String> signatures, String family, Material input, int inputAmount, Material output, int outputAmount, int baseSeconds) {
        register(target, signatures, new SfxElectricRecipe(
                "sf:electric_ore_grinder:" + family + ":" + input.key(),
                SfxRecipeSlot.vanilla(input, inputAmount),
                SfxElectricStack.vanilla(output, outputAmount),
                baseSeconds));
    }

    private void addSfx(List<SfxElectricRecipe> target, Set<String> signatures, String family, Material input, int inputAmount, String outputId, int outputAmount, int baseSeconds) {
        register(target, signatures, new SfxElectricRecipe(
                "sf:electric_ore_grinder:" + family + ":" + input.key(),
                SfxRecipeSlot.vanilla(input, inputAmount),
                SfxElectricStack.sfx(outputId, outputAmount),
                baseSeconds));
    }

    private void addVanillaFromSfx(List<SfxElectricRecipe> target, Set<String> signatures, String family, String inputId, int inputAmount, Material output, int outputAmount, int baseSeconds) {
        register(target, signatures, new SfxElectricRecipe(
                "sf:electric_ore_grinder:" + family + ":" + inputId.replace(':', '.'),
                SfxRecipeSlot.sfx(inputId, inputAmount),
                SfxElectricStack.vanilla(output, outputAmount),
                baseSeconds));
    }

    private void addSfxFromSfx(List<SfxElectricRecipe> target, Set<String> signatures, String family, String inputId, int inputAmount, String outputId, int outputAmount, int baseSeconds) {
        register(target, signatures, new SfxElectricRecipe(
                "sf:electric_ore_grinder:" + family + ":" + inputId.replace(':', '.'),
                SfxRecipeSlot.sfx(inputId, inputAmount),
                SfxElectricStack.sfx(outputId, outputAmount),
                baseSeconds));
    }

    private void register(List<SfxElectricRecipe> target, Set<String> signatures, SfxElectricRecipe recipe) {
        if (isDisabledRecipe(recipe) || !signatures.add(signatureOf(recipe))) {
            return;
        }
        target.add(recipe);
    }

    private boolean isDisabledRecipe(SfxElectricRecipe recipe) {
        return recipe.input().material() == Material.COBBLESTONE
                && recipe.input().amount() == 8
                && !recipe.output().isSfxItem()
                && recipe.output().material() == Material.SAND
                && recipe.output().amount() == 1;
    }

    private String signatureOf(SfxElectricRecipe recipe) {
        String input = recipe.input().isSfxItem()
                ? "sfx:" + recipe.input().sfxItemId() + ":" + recipe.input().amount()
                : "vanilla:" + recipe.input().material() + ":" + recipe.input().amount();
        String output = recipe.output().isSfxItem()
                ? "sfx:" + recipe.output().itemId() + ":" + recipe.output().amount()
                : "vanilla:" + recipe.output().material() + ":" + recipe.output().amount();
        return input + "->" + output;
    }
}
