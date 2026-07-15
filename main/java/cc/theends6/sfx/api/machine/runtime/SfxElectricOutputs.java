package cc.theends6.sfx.api.machine.runtime;

import cc.theends6.sfx.api.item.SfxItems;


public final class SfxElectricOutputs {
    private SfxElectricOutputs() {
    }

    public static boolean insert(SfxItems items, SfxElectricMachineState state, int slot, SfxElectricStack incoming) {
        if (items == null || state == null || incoming == null || incoming.amount() <= 0
                || slot < 0 || slot >= state.outputCapacity()) {
            return false;
        }
        SfxElectricStack current = state.output(slot);
        int maximum = Math.max(1, incoming.toItemStack(items).getMaxStackSize());
        if (current == null) {
            if (incoming.amount() > maximum) {
                return false;
            }
            state.output(slot, incoming.copyWithAmount(incoming.amount()));
            return true;
        }
        if (!incoming.canMerge(current, items)) {
            return false;
        }
        maximum = Math.min(maximum, Math.max(1, current.toItemStack(items).getMaxStackSize()));
        if (current.amount() + incoming.amount() > maximum) {
            return false;
        }
        state.output(slot, current.copyWithAmount(current.amount() + incoming.amount()));
        return true;
    }
}
