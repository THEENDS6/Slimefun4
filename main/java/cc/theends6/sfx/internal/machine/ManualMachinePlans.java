package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.machine.manual.SfxManualMachineRecipe;

import org.bukkit.inventory.ItemStack;

record ShapedMatchPlan(SfxManualMachineRecipe recipe, int[] consumed, ItemStack[] inputAfterConsume, OutputPlan outputPlan) {
    ShapedMatchPlan(SfxManualMachineRecipe recipe, int[] consumed, ItemStack[] inputAfterConsume) {
        this(recipe, consumed, inputAfterConsume, null);
    }

    ShapedMatchPlan withOutputPlan(OutputPlan outputPlan) {
        return new ShapedMatchPlan(recipe, consumed, inputAfterConsume, outputPlan);
    }
}

record OutputPlan(ItemStack[] contents) {
}

record ShapelessMatchPlan(SfxManualMachineRecipe recipe, int[] consumed, ItemStack[] inputAfterConsume) {
}

enum MatchResult {
    INPUT_MATCH_AND_FITS,
    INPUT_MATCH_BUT_FULL,
    NO_INPUT_MATCH
}
