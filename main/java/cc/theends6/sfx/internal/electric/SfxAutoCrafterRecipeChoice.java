package cc.theends6.sfx.internal.electric;

import org.bukkit.inventory.ItemStack;

record SfxAutoCrafterRecipeChoice(String key, ItemStack[] inputPreview, ItemStack outputPreview) {
    SfxAutoCrafterRecipeChoice {
        inputPreview = inputPreview == null ? new ItemStack[9] : clonePreview(inputPreview);
        outputPreview = outputPreview == null ? null : outputPreview.clone();
    }

    private static ItemStack[] clonePreview(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[9];
        for (int i = 0; i < Math.min(9, source.length); i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }
}
