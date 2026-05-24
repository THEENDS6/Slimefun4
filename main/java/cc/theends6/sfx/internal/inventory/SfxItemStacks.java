package cc.theends6.sfx.internal.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class SfxItemStacks {
    private SfxItemStacks() {
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    public static ItemStack cloneWithAmount(ItemStack stack, int amount) {
        if (stack == null) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(Math.max(0, amount));
        return copy;
    }
}
