package cc.theends6.sfx.internal.electric;

import java.util.Arrays;
import java.util.Objects;

record SfxElectricMachineUiFrame(int[] slots, SfxElectricMachineUiItem item) {
    SfxElectricMachineUiFrame {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(item, "item");
        slots = Arrays.copyOf(slots, slots.length);
    }

    @Override
    public int[] slots() {
        return Arrays.copyOf(slots, slots.length);
    }
}
