package cc.theends6.sfx.addons.basic.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import java.util.List;

abstract class SfxBasicExpansionWorldActionProvider implements SfxElectricRecipeProvider {
    @Override
    public List<SfxElectricRecipe> recipes() {
        return List.of();
    }

    @Override
    public boolean hasWorldAction() {
        return true;
    }
}
