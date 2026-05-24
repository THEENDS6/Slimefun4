package cc.theends6.sfx.internal.android;

import cc.theends6.sfx.internal.inventory.SfxBukkitInventoryEndpoint;
import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Output-buffer algorithms for Androids, kept outside the main service/tick controller. */
final class SfxAndroidOutputBuffer {
    private SfxAndroidOutputBuffer() {
    }

    record DepositResult(boolean moved, boolean hadOutput) {
    }

    static boolean pushAllOutputsAtomically(SfxAndroidState state, List<ItemStack> drops) {
        ItemStack[] backup = state.outputs();
        for (ItemStack drop : drops) {
            if (!state.pushOutput(drop)) {
                restoreOutputs(state, backup);
                return false;
            }
        }
        return true;
    }

    static boolean canFitAllOutputs(SfxAndroidState state, List<ItemStack> drops) {
        ItemStack[] scratch = state.outputs();
        for (ItemStack drop : drops) {
            if (!insertIntoScratch(scratch, drop)) {
                return false;
            }
        }
        return true;
    }

    static DepositResult depositOutputs(SfxAndroidState state, Inventory inventory) {
        if (inventory == null) {
            return new DepositResult(false, false);
        }
        boolean moved = false;
        boolean hadOutput = false;
        SfxBukkitInventoryEndpoint endpoint = new SfxBukkitInventoryEndpoint(inventory, "android:deposit-output");
        ItemStack[] outputs = state.outputs();
        for (int i = 0; i < outputs.length; i++) {
            ItemStack stack = outputs[i];
            if (isEmpty(stack)) {
                continue;
            }
            hadOutput = true;
            int before = stack.getAmount();
            ItemStack remaining = endpoint.insert(stack.clone(), false);
            int remainingAmount = isEmpty(remaining) ? 0 : remaining.getAmount();
            if (remainingAmount < before) {
                moved = true;
            }
            state.output(i, isEmpty(remaining) ? null : remaining);
        }
        return new DepositResult(moved, hadOutput);
    }

    private static boolean insertIntoScratch(ItemStack[] scratch, ItemStack stack) {
        if (isEmpty(stack)) {
            return true;
        }
        ItemStack remaining = stack.clone();
        for (int i = 0; i < scratch.length; i++) {
            ItemStack existing = scratch[i];
            if (isEmpty(existing)) {
                int amount = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                ItemStack inserted = remaining.clone();
                inserted.setAmount(amount);
                scratch[i] = inserted;
                remaining.setAmount(remaining.getAmount() - amount);
                if (remaining.getAmount() <= 0) {
                    return true;
                }
                continue;
            }
            if (!existing.isSimilar(remaining) || existing.getAmount() >= existing.getMaxStackSize()) {
                continue;
            }
            int insert = Math.min(remaining.getAmount(), existing.getMaxStackSize() - existing.getAmount());
            existing.setAmount(existing.getAmount() + insert);
            remaining.setAmount(remaining.getAmount() - insert);
            if (remaining.getAmount() <= 0) {
                return true;
            }
        }
        return false;
    }

    private static void restoreOutputs(SfxAndroidState state, ItemStack[] backup) {
        for (int i = 0; i < SfxAndroidState.OUTPUT_SIZE; i++) {
            state.output(i, backup != null && i < backup.length ? backup[i] : null);
        }
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }
}
