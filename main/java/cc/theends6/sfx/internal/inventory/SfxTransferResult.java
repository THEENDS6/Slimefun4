package cc.theends6.sfx.internal.inventory;

import cc.theends6.sfx.internal.core.SfxErrorCode;

public record SfxTransferResult(SfxErrorCode code, int requested, int inserted, int refunded, int lost) {
    public boolean success() {
        return code == SfxErrorCode.OK;
    }

    public static SfxTransferResult success(int requested, int inserted, int refunded) {
        return new SfxTransferResult(SfxErrorCode.OK, requested, inserted, refunded, 0);
    }

    public static SfxTransferResult failed(SfxErrorCode code, int requested, int inserted, int refunded, int lost) {
        return new SfxTransferResult(code, requested, inserted, refunded, lost);
    }
}
