package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

record SfxElectricRecipeStart(SfxElectricRecipe recipe, int[] inputSlots) {
    int primaryInputSlot() {
        return inputSlots.length == 0 ? -1 : inputSlots[0];
    }
}
