package cc.theends6.sfx.internal.energy;

import java.util.Arrays;
import java.util.Objects;

record SfxEnergyComponentUiFrame(int[] slots, SfxEnergyComponentUiItem item) {
    SfxEnergyComponentUiFrame {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(item, "item");
        slots = Arrays.copyOf(slots, slots.length);
    }

    @Override
    public int[] slots() {
        return Arrays.copyOf(slots, slots.length);
    }
}
