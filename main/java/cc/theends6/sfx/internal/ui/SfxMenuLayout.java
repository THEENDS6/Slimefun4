package cc.theends6.sfx.internal.ui;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SfxMenuLayout {
    private final int size;
    private final Map<Integer, SfxSlotPolicy> policies;

    private SfxMenuLayout(int size, Map<Integer, SfxSlotPolicy> policies) {
        this.size = size;
        this.policies = Map.copyOf(policies);
    }

    public int size() { return size; }

    public SfxSlotPolicy policyAt(int rawSlot) {
        return policies.getOrDefault(rawSlot, SfxSlotPolicy.locked());
    }

    public Map<Integer, SfxSlotPolicy> policies() { return Collections.unmodifiableMap(policies); }

    public static Builder builder(int size) { return new Builder(size); }

    public static final class Builder {
        private final int size;
        private final Map<Integer, SfxSlotPolicy> policies = new HashMap<>();

        private Builder(int size) { this.size = size; }

        public Builder slot(int slot, SfxSlotPolicy policy) {
            if (slot >= 0 && slot < size && policy != null) { policies.put(slot, policy); }
            return this;
        }

        public Builder slots(int[] slots, SfxSlotPolicy policy) {
            if (slots != null) {
                for (int slot : slots) { slot(slot, policy); }
            }
            return this;
        }

        public SfxMenuLayout build() { return new SfxMenuLayout(size, policies); }
    }
}
