package cc.theends6.sfx.internal.electric;

record SfxElectricRecipeStart(SfxElectricRecipe recipe, int[] inputSlots) {
    int primaryInputSlot() {
        return inputSlots.length == 0 ? -1 : inputSlots[0];
    }
}
