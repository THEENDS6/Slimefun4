package cc.theends6.sfx.api.energy.runtime;

import java.util.Arrays;
import java.util.Objects;

public record SfxEnergyComponentUiFrame(int[] slots, SfxEnergyComponentUiItem item) {
    public SfxEnergyComponentUiFrame {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(item, "item");
        slots = Arrays.copyOf(slots, slots.length);
    }

    @Override
    public int[] slots() {
        return Arrays.copyOf(slots, slots.length);
    }
}
