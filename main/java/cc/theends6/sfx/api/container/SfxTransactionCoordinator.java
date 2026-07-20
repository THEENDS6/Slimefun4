package cc.theends6.sfx.api.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;


public final class SfxTransactionCoordinator {
    private SfxTransactionCoordinator() {
    }

    public static void commit(Collection<? extends Supplier<Optional<SfxTransactionReservation>>> participants) {
        List<SfxTransactionReservation> prepared = new ArrayList<>();
        try {
            for (Supplier<Optional<SfxTransactionReservation>> participant : participants) {
                SfxTransactionReservation reservation = participant.get().orElseThrow(
                        () -> new IllegalStateException("Transaction participant does not support prepared rollback"));
                prepared.add(reservation);
            }
            for (SfxTransactionReservation reservation : prepared) reservation.commit();
        } catch (RuntimeException failure) {
            rollback(prepared, failure);
            throw failure;
        }
    }

    private static void rollback(List<SfxTransactionReservation> prepared, RuntimeException failure) {
        for (int index = prepared.size() - 1; index >= 0; index--) {
            try { prepared.get(index).rollback(); }
            catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
        }
    }
}
