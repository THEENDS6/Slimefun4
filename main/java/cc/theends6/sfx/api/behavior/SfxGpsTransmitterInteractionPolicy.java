package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxGpsTransmitterInteractionPolicy {
    SfxGpsTransmitterInteractionDecision decide(SfxGpsTransmitterInteractionContext context, SfxGpsTransmitterInteractionDecision currentDecision);
}
