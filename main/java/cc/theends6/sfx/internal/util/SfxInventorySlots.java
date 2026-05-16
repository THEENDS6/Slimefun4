package cc.theends6.sfx.internal.util;

import java.util.function.BiPredicate;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class SfxInventorySlots {
    private SfxInventorySlots() {
    }

    public static boolean contains(int[] slots, int value) {
        for (int slot : slots) {
            if (slot == value) {
                return true;
            }
        }
        return false;
    }

    public static boolean moveStackToSlots(Inventory inventory, int[] targetSlots, ItemStack current) {
        return moveStackToSlots(inventory, targetSlots, current, (slot, stack) -> true);
    }

    public static boolean moveStackToSlots(Inventory inventory, int[] targetSlots, ItemStack current, BiPredicate<Integer, ItemStack> validator) {
        if (inventory == null || targetSlots == null || current == null || current.getType().isAir()) {
            return false;
        }
        BiPredicate<Integer, ItemStack> safeValidator = validator == null ? (slot, stack) -> true : validator;
        int original = current.getAmount();
        int remaining = current.getAmount();
        for (int slot : targetSlots) {
            if (!safeValidator.test(slot, current)) {
                continue;
            }
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(current)) {
                continue;
            }
            int room = existing.getMaxStackSize() - existing.getAmount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining);
            existing.setAmount(existing.getAmount() + moved);
            remaining -= moved;
            if (remaining <= 0) {
                current.setAmount(0);
                return true;
            }
        }
        for (int slot : targetSlots) {
            if (!safeValidator.test(slot, current)) {
                continue;
            }
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            int moved = Math.min(current.getMaxStackSize(), remaining);
            ItemStack inserted = current.clone();
            inserted.setAmount(moved);
            inventory.setItem(slot, inserted);
            remaining -= moved;
            if (remaining <= 0) {
                current.setAmount(0);
                return true;
            }
        }
        current.setAmount(remaining);
        return remaining < original;
    }
}
