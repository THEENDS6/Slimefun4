package cc.theends6.sfx.internal.machine;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;


public final class SfxOutputPolicies {
    private SfxOutputPolicies() {}

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    public static boolean canMerge(ItemStack existing, ItemStack incoming) {
        return !isEmpty(existing) && !isEmpty(incoming)
                && existing.isSimilar(incoming)
                && existing.getAmount() + incoming.getAmount() <= existing.getMaxStackSize();
    }

    public static boolean canFitSingle(ItemStack existing, ItemStack incoming) {
        return isEmpty(incoming) || isEmpty(existing) || canMerge(existing, incoming);
    }

    public static boolean canFitIntoContents(ItemStack[] contents, ItemStack incoming) {
        if (isEmpty(incoming)) {
            return true;
        }
        int remaining = incoming.getAmount();
        if (contents != null) {
            for (ItemStack current : contents) {
                if (isEmpty(current)) {
                    remaining -= incoming.getMaxStackSize();
                } else if (current.isSimilar(incoming)) {
                    remaining -= Math.max(0, current.getMaxStackSize() - current.getAmount());
                }
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return remaining <= 0;
    }

    public static boolean canFitAll(Inventory inventory, List<ItemStack> outputs) {
        if (inventory == null || outputs == null || outputs.isEmpty()) {
            return true;
        }
        ItemStack[] simulated = inventory.getStorageContents();
        for (ItemStack output : outputs) {
            if (!insertSimulated(simulated, output)) {
                return false;
            }
        }
        return true;
    }

    private static boolean insertSimulated(ItemStack[] simulated, ItemStack output) {
        if (isEmpty(output)) {
            return true;
        }
        int remaining = output.getAmount();
        for (int i = 0; i < simulated.length && remaining > 0; i++) {
            ItemStack current = simulated[i];
            if (!isEmpty(current) && current.isSimilar(output)) {
                int moved = Math.min(remaining, Math.max(0, current.getMaxStackSize() - current.getAmount()));
                if (moved > 0) {
                    ItemStack copy = current.clone();
                    copy.setAmount(copy.getAmount() + moved);
                    simulated[i] = copy;
                    remaining -= moved;
                }
            }
        }
        for (int i = 0; i < simulated.length && remaining > 0; i++) {
            if (isEmpty(simulated[i])) {
                int moved = Math.min(remaining, output.getMaxStackSize());
                ItemStack copy = output.clone();
                copy.setAmount(moved);
                simulated[i] = copy;
                remaining -= moved;
            }
        }
        return remaining <= 0;
    }
}
