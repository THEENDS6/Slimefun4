package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import org.bukkit.inventory.ItemStack;







final class SfxElectricCargoInventoryOps {
    private SfxElectricCargoInventoryOps() {
    }

    static int capacityFor(ItemStack[] contents, ItemStack probe, boolean smartFill) {
        if (smartFill) {
            int existingCapacity = existingCapacity(contents, probe);
            if (hasSimilar(contents, probe)) {
                return existingCapacity;
            }
            return emptyCapacity(contents, probe);
        }
        int capacity = 0;
        for (ItemStack stack : contents) {
            if (isEmpty(stack)) {
                capacity += probe.getMaxStackSize();
                continue;
            }
            if (stack.isSimilar(probe)) {
                capacity += Math.max(0, Math.min(stack.getMaxStackSize(), probe.getMaxStackSize()) - stack.getAmount());
            }
        }
        return capacity;
    }

    static int capacityForSingleSlot(ItemStack[] contents, ItemStack probe, boolean smartFill) {
        if (smartFill) {
            boolean hasSimilar = hasSimilar(contents, probe);
            if (hasSimilar) {
                for (ItemStack stack : contents) {
                    if (!isEmpty(stack) && stack.isSimilar(probe)) {
                        int capacity = Math.max(0, Math.min(stack.getMaxStackSize(), probe.getMaxStackSize()) - stack.getAmount());
                        if (capacity > 0) {
                            return capacity;
                        }
                    }
                }
                return 0;
            }
            return firstEmptyCapacity(contents, probe);
        }
        for (ItemStack stack : contents) {
            if (isEmpty(stack)) {
                return probe.getMaxStackSize();
            }
            if (stack.isSimilar(probe)) {
                int capacity = Math.max(0, Math.min(stack.getMaxStackSize(), probe.getMaxStackSize()) - stack.getAmount());
                if (capacity > 0) {
                    return capacity;
                }
            }
        }
        return 0;
    }

    static ItemStack insert(ItemStack[] contents, ItemStack input, boolean smartFill) {
        ItemStack remaining = input.clone();
        if (smartFill) {
            remaining = hasSimilar(contents, remaining) ? fillExisting(contents, remaining) : fillEmptyOnly(contents, remaining);
        } else {
            remaining = fillEmptyOrExisting(contents, remaining, true);
        }
        return isEmpty(remaining) ? null : remaining;
    }

    static ItemStack insertSingleSlot(ItemStack[] contents, ItemStack input, boolean smartFill) {
        ItemStack remaining = input.clone();
        if (smartFill) {
            remaining = hasSimilar(contents, remaining) ? fillOneExistingSlot(contents, remaining) : fillOneEmptySlot(contents, remaining);
        } else {
            remaining = fillOneEmptyOrExistingSlot(contents, remaining);
        }
        return isEmpty(remaining) ? null : remaining;
    }

    static boolean hasSimilar(ItemStack[] contents, ItemStack probe) {
        for (ItemStack stack : contents) {
            if (!isEmpty(stack) && stack.isSimilar(probe)) {
                return true;
            }
        }
        return false;
    }

    static int existingCapacity(ItemStack[] contents, ItemStack probe) {
        int capacity = 0;
        for (ItemStack stack : contents) {
            if (!isEmpty(stack) && stack.isSimilar(probe)) {
                capacity += Math.max(0, Math.min(stack.getMaxStackSize(), probe.getMaxStackSize()) - stack.getAmount());
            }
        }
        return capacity;
    }

    static int emptyCapacity(ItemStack[] contents, ItemStack probe) {
        int capacity = 0;
        for (ItemStack stack : contents) {
            if (isEmpty(stack)) {
                capacity += probe.getMaxStackSize();
            }
        }
        return capacity;
    }

    static int firstEmptyCapacity(ItemStack[] contents, ItemStack probe) {
        for (ItemStack stack : contents) {
            if (isEmpty(stack)) {
                return probe.getMaxStackSize();
            }
        }
        return 0;
    }

    static ItemStack fillExisting(ItemStack[] contents, ItemStack input) {
        ItemStack remaining = input.clone();
        for (ItemStack stack : contents) {
            if (isEmpty(stack) || !stack.isSimilar(remaining)) {
                continue;
            }
            int limit = Math.min(stack.getMaxStackSize(), remaining.getMaxStackSize());
            int moved = Math.min(remaining.getAmount(), Math.max(0, limit - stack.getAmount()));
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

    static ItemStack fillEmptyOnly(ItemStack[] contents, ItemStack input) {
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

    static ItemStack fillOneExistingSlot(ItemStack[] contents, ItemStack input) {
        ItemStack remaining = input.clone();
        for (ItemStack stack : contents) {
            if (isEmpty(stack) || !stack.isSimilar(remaining)) {
                continue;
            }
            int limit = Math.min(stack.getMaxStackSize(), remaining.getMaxStackSize());
            int moved = Math.min(remaining.getAmount(), Math.max(0, limit - stack.getAmount()));
            if (moved <= 0) {
                continue;
            }
            stack.setAmount(stack.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            return remaining.getAmount() <= 0 ? null : remaining;
        }
        return remaining;
    }

    static ItemStack fillOneEmptySlot(ItemStack[] contents, ItemStack input) {
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
            return remaining.getAmount() <= 0 ? null : remaining;
        }
        return remaining;
    }

    static ItemStack fillOneEmptyOrExistingSlot(ItemStack[] contents, ItemStack input) {
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
                int limit = Math.min(stack.getMaxStackSize(), remaining.getMaxStackSize());
                int moved = Math.min(remaining.getAmount(), Math.max(0, limit - stack.getAmount()));
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

    static ItemStack fillEmptyOrExisting(ItemStack[] contents, ItemStack input, boolean existingAllowed) {
        ItemStack remaining = input.clone();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (isEmpty(stack)) {
                int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.clone();
                placed.setAmount(moved);
                contents[i] = placed;
                remaining.setAmount(remaining.getAmount() - moved);
            } else if (existingAllowed && stack.isSimilar(remaining)) {
                int limit = Math.min(stack.getMaxStackSize(), remaining.getMaxStackSize());
                int moved = Math.min(remaining.getAmount(), Math.max(0, limit - stack.getAmount()));
                stack.setAmount(stack.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
            }
            if (remaining.getAmount() <= 0) {
                return null;
            }
        }
        return remaining;
    }

    static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    static boolean sameContents(ItemStack[] left, ItemStack[] right) {
        if (left == null || right == null || left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!sameStack(left[i], right[i])) {
                return false;
            }
        }
        return true;
    }

    static boolean sameStack(ItemStack left, ItemStack right) {
        if (isEmpty(left) || isEmpty(right)) {
            return isEmpty(left) && isEmpty(right);
        }
        return left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents == null ? 0 : contents.length];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    static String itemKey(SfxItems items, ItemStack stack) {
        if (isEmpty(stack)) {
            return "air";
        }
        String marker = items.readMarker(stack).map(cc.theends6.sfx.api.item.SfxItemMarker::itemId).orElse(null);
        ItemStack probe = stack.clone();
        probe.setAmount(1);
        return (marker == null ? "vanilla:" + stack.getType().key() : "sfx:" + marker) + ":" + probe.hashCode();
    }
}
