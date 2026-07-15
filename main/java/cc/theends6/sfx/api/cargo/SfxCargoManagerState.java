package cc.theends6.sfx.api.cargo;


public record SfxCargoManagerState(boolean enabled, int speedMultiplier) {
    public SfxCargoManagerState {
        speedMultiplier = Math.max(1, Math.min(64, speedMultiplier));
    }
}
