package cc.theends6.sfx.internal.electric;

public record SfxElectricRecipeMatch(int[] inputSlots, SfxElectricRecipe recipe) {
    public int primaryInputSlot() {
        return inputSlots.length == 0 ? -1 : inputSlots[0];
    }
}
