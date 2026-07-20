package cc.theends6.sfx.internal.world;

import java.util.function.Supplier;


public final class SfxProtectionProbe {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private SfxProtectionProbe() {}

    public static boolean active() { return DEPTH.get() > 0; }

    static <T> T call(Supplier<T> action) {
        DEPTH.set(DEPTH.get() + 1);
        try { return action.get(); }
        finally {
            int remaining = DEPTH.get() - 1;
            if (remaining <= 0) DEPTH.remove(); else DEPTH.set(remaining);
        }
    }
}
