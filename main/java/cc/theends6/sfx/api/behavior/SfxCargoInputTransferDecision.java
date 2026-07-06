package cc.theends6.sfx.api.behavior;

public record SfxCargoInputTransferDecision(
        int maxItems,
        int maxDistinctTypes,
        boolean allowMultipleSlots,
        SfxCargoDistribution distribution
) {
    public static SfxCargoInputTransferDecision singleStack(SfxCargoDistribution distribution) {
        return new SfxCargoInputTransferDecision(64, 1, false, distribution == null ? SfxCargoDistribution.SEQUENTIAL : distribution);
    }
}
