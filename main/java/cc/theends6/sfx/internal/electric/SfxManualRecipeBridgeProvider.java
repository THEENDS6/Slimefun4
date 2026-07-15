package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.machine.manual.SfxManualMachineOutput;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineRecipe;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOperation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class SfxManualRecipeBridgeProvider implements SfxElectricRecipeProvider {
    private final List<SfxElectricRecipe> recipes;

    public SfxManualRecipeBridgeProvider(Collection<SfxManualMachineRecipe> manualRecipes, int baseTicks, String keyPrefix) {
        List<SfxElectricRecipe> bridged = new ArrayList<>();
        for (SfxManualMachineRecipe recipe : manualRecipes) {
            if (recipe.operation() != SfxManualMachineOperation.SINGLE_INPUT || recipe.hasRandomOutputs() || recipe.fixedOutputs().size() != 1) {
                continue;
            }
            SfxManualMachineOutput output = recipe.fixedOutputs().getFirst();
            SfxElectricStack stack = output.isSfxItem()
                    ? SfxElectricStack.sfx(output.sfxItemId(), output.amount())
                    : SfxElectricStack.vanilla(output.material(), output.amount());
            bridged.add(new SfxElectricRecipe(
                    keyPrefix + ":" + recipe.machineId() + ":" + bridged.size(),
                    recipe.input().getFirst(),
                    stack,
                    baseTicks));
        }
        this.recipes = List.copyOf(bridged);
    }

    @Override
    public List<SfxElectricRecipe> recipes() {
        return recipes;
    }
}
