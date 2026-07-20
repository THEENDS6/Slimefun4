package cc.theends6.sfx.api.container;


public interface SfxTransactionReservation {
    void commit();
    void rollback();

    static SfxTransactionReservation noop() {
        return new SfxTransactionReservation() {
            @Override public void commit() { }
            @Override public void rollback() { }
        };
    }
}
