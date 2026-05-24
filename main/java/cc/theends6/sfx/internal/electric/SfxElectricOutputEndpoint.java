package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.inventory.SfxInventoryAccessState;
import cc.theends6.sfx.internal.inventory.SfxStorageEndpoint;
import cc.theends6.sfx.internal.inventory.SfxStorageKey;
import org.bukkit.inventory.ItemStack;

final class SfxElectricOutputEndpoint implements SfxStorageEndpoint {
    private final SfxItems items;
    private final SfxElectricMachineState state;
    private final int slot;

    SfxElectricOutputEndpoint(SfxItems items, SfxElectricMachineState state, int slot) {
        this.items = items;
        this.state = state;
        this.slot = slot;
    }

    @Override
    public SfxStorageKey storageKey() {
        return new SfxStorageKey("electric-output:" + slot);
    }

    @Override
    public SfxInventoryAccessState accessState() {
        return state == null || slot < 0 || slot >= state.outputCapacity()
                ? SfxInventoryAccessState.UNAVAILABLE
                : SfxInventoryAccessState.READY;
    }

    @Override
    public int simulateInsert(ItemStack stack, boolean smartFill) {
        if (!ready() || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        SfxElectricStack incoming = SfxElectricStack.fromItemStack(items, stack);
        if (incoming == null) {
            return 0;
        }
        SfxElectricStack current = state.output(slot);
        if (current == null) {
            return Math.min(stack.getAmount(), Math.max(1, incoming.toItemStack(items).getMaxStackSize()));
        }
        if (!incoming.sameKind(current)) {
            return 0;
        }
        int max = Math.min(incoming.toItemStack(items).getMaxStackSize(), current.toItemStack(items).getMaxStackSize());
        return Math.max(0, Math.min(stack.getAmount(), max - current.amount()));
    }

    @Override
    public int simulateInsertSingleSlot(ItemStack stack, boolean smartFill) {
        return simulateInsert(stack, smartFill);
    }

    @Override
    public ItemStack insertSingleSlot(ItemStack stack, boolean smartFill) {
        return insert(stack, smartFill);
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean smartFill) {
        if (!ready() || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return stack == null ? null : stack.clone();
        }
        int accepted = simulateInsert(stack, smartFill);
        if (accepted <= 0) {
            return stack.clone();
        }
        SfxElectricStack incoming = SfxElectricStack.fromItemStack(items, stack);
        if (incoming == null) {
            return stack.clone();
        }
        SfxElectricStack current = state.output(slot);
        state.output(slot, current == null ? incoming.copyWithAmount(accepted) : current.copyWithAmount(current.amount() + accepted));
        int remaining = stack.getAmount() - accepted;
        if (remaining <= 0) {
            return null;
        }
        ItemStack rest = stack.clone();
        rest.setAmount(remaining);
        return rest;
    }
}
