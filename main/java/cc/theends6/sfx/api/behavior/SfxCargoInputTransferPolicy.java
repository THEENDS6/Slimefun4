package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxCargoInputTransferPolicy {
    SfxCargoInputTransferDecision decide(SfxCargoInputTransferContext context, SfxCargoInputTransferDecision currentDecision);
}
