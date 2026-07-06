package cc.theends6.sfx.api.behavior;

public record SfxCargoInputTransferContext(
        String componentId,
        boolean advancedInputNode,
        boolean roundRobin,
        SfxCargoDistribution distribution,
        boolean allowMultipleSlots,
        int batchLimit,
        int maxDistinctTypes
) {
}
