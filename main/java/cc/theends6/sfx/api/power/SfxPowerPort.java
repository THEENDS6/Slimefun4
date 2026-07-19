package cc.theends6.sfx.api.power;

import cc.theends6.sfx.api.container.SfxTransactionMode;

public interface SfxPowerPort {
    String id();
    int priority();
    double available();
    double demand();
    double extract(double amount, SfxTransactionMode mode);
    double insert(double amount, SfxTransactionMode mode);
}
