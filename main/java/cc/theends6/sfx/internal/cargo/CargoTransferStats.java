package cc.theends6.sfx.internal.cargo;

final class CargoTransferStats {
    private final long[] buckets = new long[60];
    private long total;
    private long currentSecond = Long.MIN_VALUE;
    private int currentBucket;

    synchronized void record(int amount) {
        if (amount <= 0) {
            return;
        }
        rotateToNow();
        buckets[currentBucket] += amount;
        total += amount;
    }

    synchronized long total() {
        return total;
    }

    synchronized long lastMinute() {
        rotateToNow();
        long sum = 0L;
        for (long bucket : buckets) {
            sum += bucket;
        }
        return sum;
    }

    private void rotateToNow() {
        long now = System.currentTimeMillis() / 1000L;
        if (currentSecond == Long.MIN_VALUE) {
            currentSecond = now;
            currentBucket = (int) Math.floorMod(now, buckets.length);
            buckets[currentBucket] = 0L;
            return;
        }
        long delta = now - currentSecond;
        if (delta <= 0L) {
            return;
        }
        long steps = Math.min(delta, buckets.length);
        for (int i = 1; i <= steps; i++) {
            int index = (int) Math.floorMod(currentSecond + i, buckets.length);
            buckets[index] = 0L;
        }
        currentSecond = now;
        currentBucket = (int) Math.floorMod(now, buckets.length);
    }
}
