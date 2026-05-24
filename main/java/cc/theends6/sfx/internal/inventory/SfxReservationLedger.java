package cc.theends6.sfx.internal.inventory;

import java.util.HashMap;
import java.util.Map;

public final class SfxReservationLedger {
    private final Map<String, Integer> reservations = new HashMap<>();

    public int reserved(String key) {
        return key == null ? 0 : reservations.getOrDefault(key, 0);
    }

    public int available(String key, int capacity) {
        return Math.max(0, capacity - reserved(key));
    }

    public void reserve(String key, int amount) {
        if (key != null && amount > 0) {
            reservations.merge(key, amount, Integer::sum);
        }
    }

    public Map<String, Integer> snapshot() {
        return Map.copyOf(reservations);
    }
}
