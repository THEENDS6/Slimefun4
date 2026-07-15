package cc.theends6.sfx.api.machine.runtime;

import cc.theends6.sfx.api.machine.runtime.*;

public record SfxElectricRecipeMatch(int[] inputSlots, SfxElectricRecipe recipe) {
    public int primaryInputSlot() {
        return inputSlots.length == 0 ? -1 : inputSlots[0];
    }
}
