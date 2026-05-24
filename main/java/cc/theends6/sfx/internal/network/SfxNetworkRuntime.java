package cc.theends6.sfx.internal.network;

public interface SfxNetworkRuntime {
    SfxNetworkDomain domain();

    SfxNetworkSnapshot snapshot();

    SfxNetworkReadiness readiness();

    void tick(SfxNetworkTickContext context);
}
