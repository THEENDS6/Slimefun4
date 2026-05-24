package cc.theends6.sfx.internal.network;

public record SfxNetworkTickContext(long tick, long nowMillis, SfxNetworkReadiness readiness) {
}
