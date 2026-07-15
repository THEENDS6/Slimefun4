package cc.theends6.sfx.addons.basic.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import java.util.List;

abstract class SfxBasicExpansionTickProvider implements SfxElectricRecipeProvider {
    @Override
    public List<SfxElectricRecipe> recipes() {
        return List.of();
    }

    @Override
    public boolean hasSpecialTick() {
        return true;
    }

    @Override
    public int specialTickIntervalTicks() {
        return 1;
    }
}
