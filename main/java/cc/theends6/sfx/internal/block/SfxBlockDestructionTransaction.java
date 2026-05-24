package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.internal.core.SfxErrorCode;
import cc.theends6.sfx.internal.core.SfxResult;
import java.util.Optional;
import java.util.logging.Logger;

public final class SfxBlockDestructionTransaction {
    private final SfxBlockDataService blockData;
    private final SfxBlockBehaviorRegistry behaviors;
    private final Logger logger;

    public SfxBlockDestructionTransaction(SfxBlockDataService blockData, SfxBlockBehaviorRegistry behaviors, Logger logger) {
        this.blockData = blockData;
        this.behaviors = behaviors;
        this.logger = logger;
    }

    public SfxResult<Void> commit(SfxBlockBreakContext context) {
        if (context == null || context.location() == null) {
            return SfxResult.fail(SfxErrorCode.INVALID_INPUT, "Invalid block break context");
        }
        Optional<SfxAnchorRecord> anchor = blockData.findAnchorFast(context.location());
        if (anchor.isEmpty()) {
            return SfxResult.ok();
        }
        Optional<SfxBlockInstanceRecord> instance = blockData.findInstance(anchor.get().instanceId());
        try {
            if (instance.isPresent() && behaviors != null) {
                Optional<SfxBlockBehavior> behavior = behaviors.find(instance.get().typeId());
                if (behavior.isPresent()) {
                    SfxResult<Void> before = behavior.get().beforeBreak(context, anchor.get(), instance.get());
                    if (!before.success()) {
                        return before;
                    }
                }
            }
            blockData.unregisterAt(context.location());
            instance.ifPresent(record -> behaviors.find(record.typeId()).ifPresent(behavior -> behavior.afterBreak(context, anchor.get(), record)));
            return SfxResult.ok();
        } catch (Throwable throwable) {
            if (logger != null) {
                logger.warning("Failed to destroy SFX block at " + context.location() + ": " + throwable.getMessage());
            }
            return SfxResult.fail(SfxErrorCode.TRANSACTION_FAILED, "Failed to destroy SFX block", throwable);
        }
    }
}
