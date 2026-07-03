package cc.theends6.sfx.internal.configurable;

import java.util.Arrays;
import java.util.Objects;

record SfxConfigurableMachineUiFrame(int[] slots, SfxConfigurableMachineUiItem item) {
    SfxConfigurableMachineUiFrame {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(item, "item");
        slots = Arrays.copyOf(slots, slots.length);
    }

    @Override
    public int[] slots() {
        return Arrays.copyOf(slots, slots.length);
    }
}
