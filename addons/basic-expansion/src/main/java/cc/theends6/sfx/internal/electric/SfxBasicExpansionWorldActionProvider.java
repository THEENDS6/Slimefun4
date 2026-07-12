package cc.theends6.sfx.internal.electric;

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
