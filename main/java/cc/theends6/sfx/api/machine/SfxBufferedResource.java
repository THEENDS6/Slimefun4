package cc.theends6.sfx.api.machine;

public final class SfxBufferedResource {
    private final String key;
    private final int unitAmount;
    private final int maxAmount;
    private final boolean interruptRefund;
    private final boolean dropRefund;

    public SfxBufferedResource(String key, int unitAmount, int maxAmount, boolean interruptRefund, boolean dropRefund) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Buffered resource key must not be blank.");
        }
        this.key = key.trim();
        this.unitAmount = Math.max(1, unitAmount);
        this.maxAmount = Math.max(0, maxAmount);
        this.interruptRefund = interruptRefund;
        this.dropRefund = dropRefund;
    }

    public String key() {
        return key;
    }

    public int unitAmount() {
        return unitAmount;
    }

    public int maxAmount() {
        return maxAmount;
    }

    public boolean interruptRefund() {
        return interruptRefund;
    }

    public boolean dropRefund() {
        return dropRefund;
    }

    public int clamp(int amount) {
        return Math.max(0, Math.min(maxAmount, amount));
    }

    public int acceptedUnits(int stored, int itemCount) {
        if (itemCount <= 0 || stored >= maxAmount) {
            return 0;
        }
        int space = maxAmount - Math.max(0, stored);
        return Math.min(itemCount, space / unitAmount);
    }

    public int addUnits(int stored, int itemCount) {
        return clamp(Math.max(0, stored) + Math.max(0, itemCount) * unitAmount);
    }

    public int refundableItemsFloor(int stored) {
        if (!dropRefund || unitAmount <= 0) {
            return 0;
        }
        return Math.max(0, stored) / unitAmount;
    }
}
