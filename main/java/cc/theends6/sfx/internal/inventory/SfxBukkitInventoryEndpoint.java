package cc.theends6.sfx.internal.inventory;

import java.util.Arrays;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Bukkit inventory adapter for the common SFX transfer pipeline. */
public final class SfxBukkitInventoryEndpoint implements SfxStorageEndpoint {
    private final Inventory inventory;
    private final SfxStorageKey key;

    public SfxBukkitInventoryEndpoint(Inventory inventory, String key) {
        this.inventory = inventory;
        this.key = new SfxStorageKey(key == null || key.isBlank() ? "bukkit-inventory" : key);
    }

    @Override
    public SfxStorageKey storageKey() {
        return key;
    }

    @Override
    public SfxInventoryAccessState accessState() {
        return inventory == null ? SfxInventoryAccessState.UNAVAILABLE : SfxInventoryAccessState.READY;
    }

    @Override
    public int simulateInsert(ItemStack stack, boolean smartFill) {
        if (!ready() || isEmpty(stack)) {
            return 0;
        }
        ItemStack[] mirror = cloneContents(inventory.getStorageContents());
        ItemStack remaining = insertIntoMirror(mirror, stack, smartFill, false);
        return stack.getAmount() - amount(remaining);
    }

    @Override
    public int simulateInsertSingleSlot(ItemStack stack, boolean smartFill) {
        if (!ready() || isEmpty(stack)) {
            return 0;
        }
        ItemStack[] mirror = cloneContents(inventory.getStorageContents());
        ItemStack remaining = insertIntoMirror(mirror, stack, smartFill, true);
        return stack.getAmount() - amount(remaining);
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean smartFill) {
        return insertInternal(stack, smartFill, false);
    }

    @Override
    public ItemStack insertSingleSlot(ItemStack stack, boolean smartFill) {
        return insertInternal(stack, smartFill, true);
    }

    @Override
    public Object snapshot() {
        return ready() ? cloneContents(inventory.getStorageContents()) : null;
    }

    @Override
    public void restoreSnapshot(Object snapshot) {
        if (ready() && snapshot instanceof ItemStack[] contents) {
            inventory.setStorageContents(cloneContents(contents));
        }
    }

    private ItemStack insertInternal(ItemStack stack, boolean smartFill, boolean singleSlot) {
        if (!ready() || isEmpty(stack)) {
            return isEmpty(stack) ? null : stack.clone();
        }
        ItemStack[] mirror = cloneContents(inventory.getStorageContents());
        ItemStack remaining = insertIntoMirror(mirror, stack, smartFill, singleSlot);
        inventory.setStorageContents(mirror);
        return isEmpty(remaining) ? null : remaining;
    }

    private ItemStack insertIntoMirror(ItemStack[] contents, ItemStack input, boolean smartFill, boolean singleSlot) {
        ItemStack remaining = input.clone();
        if (smartFill) {
            boolean hasSimilar = hasSimilar(contents, remaining);
            if (singleSlot) {
                return hasSimilar ? fillOneExistingSlot(contents, remaining) : fillOneEmptySlot(contents, remaining);
            }
            return hasSimilar ? fillExisting(contents, remaining) : fillEmptyOnly(contents, remaining);
        }
        return singleSlot ? fillOneEmptyOrExistingSlot(contents, remaining) : fillEmptyOrExisting(contents, remaining);
    }

    private ItemStack fillExisting(ItemStack[] contents, ItemStack input) {
        ItemStack remaining = input.clone();
        for (ItemStack stack : contents) {
            if (isEmpty(stack) || !stack.isSimilar(remaining)) {
                continue;
            }
            int moved = Math.min(remaining.getAmount(), Math.max(0, slotLimit(stack, remaining) - stack.getAmount()));
            if (moved <= 0) {
                continue;
            }
            stack.setAmount(stack.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }
        return remaining;
    }

    private ItemStack fillEmptyOnly(ItemStack[] contents, ItemStack input) {
        ItemStack remaining = input.clone();
        for (int i = 0; i < contents.length; i++) {
            if (!isEmpty(contents[i])) {
                continue;
            }
            int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            contents[i] = placed;
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }
        return remaining;
    }

    private ItemStack fillEmptyOrExisting(ItemStack[] contents, ItemStack input) {
        ItemStack remaining = input.clone();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (isEmpty(stack)) {
                int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.clone();
                placed.setAmount(moved);
                contents[i] = placed;
                remaining.setAmount(remaining.getAmount() - moved);
            } else if (stack.isSimilar(remaining)) {
                int moved = Math.min(remaining.getAmount(), Math.max(0, slotLimit(stack, remaining) - stack.getAmount()));
                if (moved > 0) {
                    stack.setAmount(stack.getAmount() + moved);
                    remaining.setAmount(remaining.getAmount() - moved);
                }
            }
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }
        return remaining;
    }

    private ItemStack fillOneExistingSlot(ItemStack[] contents, ItemStack input) {
        ItemStack remaining = input.clone();
        for (ItemStack stack : contents) {
            if (!isEmpty(stack) && stack.isSimilar(remaining)) {
                int moved = Math.min(remaining.getAmount(), Math.max(0, slotLimit(stack, remaining) - stack.getAmount()));
                if (moved <= 0) {
                    continue;
                }
                stack.setAmount(stack.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
                return remaining.getAmount() <= 0 ? null : remaining;
            }
        }
        return remaining;
    }

    private ItemStack fillOneEmptySlot(ItemStack[] contents, ItemStack input) {
        ItemStack remaining = input.clone();
        for (int i = 0; i < contents.length; i++) {
            if (isEmpty(contents[i])) {
                int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.clone();
                placed.setAmount(moved);
                contents[i] = placed;
                remaining.setAmount(remaining.getAmount() - moved);
                return remaining.getAmount() <= 0 ? null : remaining;
            }
        }
        return remaining;
    }

    private ItemStack fillOneEmptyOrExistingSlot(ItemStack[] contents, ItemStack input) {
        ItemStack remaining = input.clone();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (isEmpty(stack)) {
                int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.clone();
                placed.setAmount(moved);
                contents[i] = placed;
                remaining.setAmount(remaining.getAmount() - moved);
                return remaining.getAmount() <= 0 ? null : remaining;
            }
            if (stack.isSimilar(remaining)) {
                int moved = Math.min(remaining.getAmount(), Math.max(0, slotLimit(stack, remaining) - stack.getAmount()));
                if (moved > 0) {
                    stack.setAmount(stack.getAmount() + moved);
                    remaining.setAmount(remaining.getAmount() - moved);
                    return remaining.getAmount() <= 0 ? null : remaining;
                }
            }
        }
        return remaining;
    }

    private boolean hasSimilar(ItemStack[] contents, ItemStack probe) {
        for (ItemStack stack : contents) {
            if (!isEmpty(stack) && stack.isSimilar(probe)) {
                return true;
            }
        }
        return false;
    }

    private int slotLimit(ItemStack current, ItemStack incoming) {
        return Math.min(current.getMaxStackSize(), incoming.getMaxStackSize());
    }

    private int amount(ItemStack stack) {
        return isEmpty(stack) ? 0 : stack.getAmount();
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = Arrays.copyOf(contents, contents.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copy[i] == null ? null : copy[i].clone();
        }
        return copy;
    }
}
