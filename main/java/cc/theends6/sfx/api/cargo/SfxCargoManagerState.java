package cc.theends6.sfx.api.cargo;


public record SfxCargoManagerState(boolean enabled, double workIntervalTicks) {
    public SfxCargoManagerState {
        workIntervalTicks = Double.isFinite(workIntervalTicks)
                ? Math.max(0.0D, workIntervalTicks)
                : 10.0D;
    }
}
