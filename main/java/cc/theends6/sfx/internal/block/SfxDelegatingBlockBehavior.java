package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.internal.core.SfxErrorCode;
import cc.theends6.sfx.internal.core.SfxResult;
import java.util.Objects;
import java.util.UUID;


public final class SfxDelegatingBlockBehavior implements SfxBlockBehavior {
    @FunctionalInterface
    public interface PlacementInitializer {
        void afterPlace(SfxBlockPlacementContext context, UUID instanceId);
    }

    private final String typeId;
    private final PlacementInitializer initializer;

    public SfxDelegatingBlockBehavior(String typeId, PlacementInitializer initializer) {
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        this.initializer = initializer;
    }

    @Override
    public String typeId() {
        return typeId;
    }

    @Override
    public SfxResult<Void> afterPlace(SfxBlockPlacementContext context, UUID instanceId) {
        if (initializer == null) {
            return SfxResult.ok();
        }
        try {
            initializer.afterPlace(context, instanceId);
            return SfxResult.ok();
        } catch (RuntimeException exception) {
            return SfxResult.fail(SfxErrorCode.TRANSACTION_FAILED, "Domain placement initializer failed for " + typeId, exception);
        }
    }
}
