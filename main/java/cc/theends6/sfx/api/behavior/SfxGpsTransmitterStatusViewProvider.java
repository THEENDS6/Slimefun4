package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxGpsTransmitterStatusViewProvider {
    SfxGpsTransmitterStatusView view(SfxGpsTransmitterInteractionContext context, SfxGpsTransmitterStatusView currentView);
}
