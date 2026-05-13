package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.machine.ManualMachineOutput;
import cc.theends6.sfx.internal.machine.ManualMachineRecipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class SfxElectricSmelteryRecipeProvider implements SfxElectricRecipeProvider {
    private final List<SfxElectricRecipe> recipes;

    SfxElectricSmelteryRecipeProvider(Collection<ManualMachineRecipe> smelteryRecipes, int baseSeconds) {
        List<SfxElectricRecipe> bridged = new ArrayList<>();
        for (ManualMachineRecipe recipe : smelteryRecipes) {
            if (recipe.input().size() < 2 || recipe.input().size() > SfxElectricMachineState.MAX_INPUTS || recipe.hasRandomOutputs() || recipe.fixedOutputs().isEmpty() || recipe.fixedOutputs().size() > SfxElectricMachineState.MAX_OUTPUTS) {
                continue;
            }
            List<SfxElectricStack> outputs = new ArrayList<>(recipe.fixedOutputs().size());
            for (ManualMachineOutput output : recipe.fixedOutputs()) {
                outputs.add(output.isSfxItem()
                        ? SfxElectricStack.sfx(output.sfxItemId(), output.amount())
                        : SfxElectricStack.vanilla(output.material(), output.amount()));
            }
            bridged.add(SfxElectricRecipe.fixedOutputs(
                    "sf:electric_smeltery:" + recipe.machineId() + ":" + bridged.size(),
                    recipe.input(),
                    outputs,
                    baseSeconds));
        }
        this.recipes = List.copyOf(bridged);
    }

    @Override
    public List<SfxElectricRecipe> recipes() {
        return recipes;
    }
}
