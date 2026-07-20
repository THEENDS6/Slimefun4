package cc.theends6.sfx.api.power;

import cc.theends6.sfx.api.container.SfxTransactionMode;
import cc.theends6.sfx.api.container.SfxTransactionReservation;

public interface SfxPowerPort {
    String id();
    int priority();
    double available();
    double demand();
    double extract(double amount, SfxTransactionMode mode);
    double insert(double amount, SfxTransactionMode mode);
    default java.util.Optional<SfxTransactionReservation> prepareExtract(double amount) {
        return java.util.Optional.empty();
    }
    default java.util.Optional<SfxTransactionReservation> prepareInsert(double amount) {
        return java.util.Optional.empty();
    }
}
