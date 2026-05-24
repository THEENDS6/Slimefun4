package cc.theends6.sfx.internal.inventory;

public enum SfxInventoryAccessState {
    READY,
    BUSY_WRONG_REGION,
    BUSY_EXTERNAL_FINALIZATION,
    UNAVAILABLE;

    public boolean ready() {
        return this == READY;
    }
}
