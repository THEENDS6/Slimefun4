package cc.theends6.sfx.api.machine.runtime;

import java.util.Arrays;
import java.util.Objects;

public record SfxElectricMachineUiFrame(int[] slots, SfxElectricMachineUiItem item) {
    public SfxElectricMachineUiFrame {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(item, "item");
        slots = Arrays.copyOf(slots, slots.length);
    }

    @Override
    public int[] slots() {
        return Arrays.copyOf(slots, slots.length);
    }
}
