package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.internal.core.SfxErrorCode;
import cc.theends6.sfx.internal.core.SfxResult;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;

public final class SfxBlockPlacementTransaction {
    private final SfxBlockDataService blockData;
    private final SfxBlockBehavior behavior;
    private final Logger logger;

    public SfxBlockPlacementTransaction(SfxBlockDataService blockData, SfxBlockBehavior behavior, Logger logger) {
        this.blockData = blockData;
        this.behavior = behavior;
        this.logger = logger;
    }

    public SfxResult<UUID> commit(SfxBlockPlacementContext context) {
        if (context == null || context.location() == null || context.material() == null) {
            return SfxResult.fail(SfxErrorCode.INVALID_INPUT, "Invalid block placement context");
        }
        SfxResult<Void> validation = behavior == null ? SfxResult.ok() : behavior.canPlace(context);
        if (!validation.success()) {
            return SfxResult.fail(validation.code(), validation.message());
        }
        UUID instanceId = null;
        Material previous = context.location().getBlock().getType();
        try {
            instanceId = blockData.registerSingleBlock(context.typeId(), context.location(), context.material(), context.ownerId());
            if (behavior != null) {
                SfxResult<Void> placed = behavior.afterPlace(context, instanceId);
                if (!placed.success()) {
                    blockData.unregisterAt(context.location());
                    context.location().getBlock().setType(previous, false);
                    return SfxResult.fail(placed.code(), placed.message());
                }
            }
            return SfxResult.ok(instanceId);
        } catch (Throwable throwable) {
            if (instanceId != null) {
                try {
                    blockData.unregisterAt(context.location());
                } catch (Throwable rollback) {
                    if (logger != null) {
                        logger.warning("Failed to roll back SFX placement at " + context.location() + ": " + rollback.getMessage());
                    }
                }
            }
            return SfxResult.fail(SfxErrorCode.TRANSACTION_FAILED, "Failed to place SFX block", throwable);
        }
    }
}
