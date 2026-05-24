package cc.theends6.sfx.internal.network;

public enum SfxNetworkReadiness {
    READY,
    BUSY_WRONG_REGION,
    WAITING_REMOTE_SNAPSHOT,
    WORLD_UNLOADED,
    INVALID_TOPOLOGY,
    DISABLED;

    public boolean ready() {
        return this == READY;
    }
}
