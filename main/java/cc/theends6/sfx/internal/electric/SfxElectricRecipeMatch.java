package cc.theends6.sfx.internal.electric;

record SfxElectricRecipeMatch(int[] inputSlots, SfxElectricRecipe recipe) {
    int primaryInputSlot() {
        return inputSlots.length == 0 ? -1 : inputSlots[0];
    }
}
