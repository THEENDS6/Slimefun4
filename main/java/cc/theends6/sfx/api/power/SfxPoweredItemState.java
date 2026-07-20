package cc.theends6.sfx.api.power;

public record SfxPoweredItemState(double storedEnergy, boolean overclocked, long lastSettledActiveTick) {
    public SfxPoweredItemState {
        if (!Double.isFinite(storedEnergy) || storedEnergy < 0.0D) throw new IllegalArgumentException("storedEnergy must be finite and non-negative");
        lastSettledActiveTick = Math.max(0L, lastSettledActiveTick);
    }
}
