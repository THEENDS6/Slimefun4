package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

import cc.theends6.sfx.internal.core.SfxResult;

public interface SfxBlockBehavior {
    String typeId();

    default SfxResult<Void> canPlace(SfxBlockPlacementContext context) {
        return SfxResult.ok();
    }

    default SfxResult<Void> afterPlace(SfxBlockPlacementContext context, java.util.UUID instanceId) {
        return SfxResult.ok();
    }

    default SfxResult<Void> beforeBreak(SfxBlockBreakContext context, SfxAnchorRecord anchor, SfxBlockInstanceRecord instance) {
        return SfxResult.ok();
    }

    default void afterBreak(SfxBlockBreakContext context, SfxAnchorRecord anchor, SfxBlockInstanceRecord instance) {
    }
}
