package cc.theends6.sfx.internal.machine;

import org.bukkit.inventory.ItemStack;

record ShapedMatchPlan(ManualMachineRecipe recipe, int[] consumed, ItemStack[] inputAfterConsume, OutputPlan outputPlan) {
    ShapedMatchPlan(ManualMachineRecipe recipe, int[] consumed, ItemStack[] inputAfterConsume) {
        this(recipe, consumed, inputAfterConsume, null);
    }

    ShapedMatchPlan withOutputPlan(OutputPlan outputPlan) {
        return new ShapedMatchPlan(recipe, consumed, inputAfterConsume, outputPlan);
    }
}

record OutputPlan(ItemStack[] contents) {
}

record ShapelessMatchPlan(ManualMachineRecipe recipe, int[] consumed, ItemStack[] inputAfterConsume) {
}

enum MatchResult {
    INPUT_MATCH_AND_FITS,
    INPUT_MATCH_BUT_FULL,
    NO_INPUT_MATCH
}
