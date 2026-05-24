package cc.theends6.sfx.internal.persistence;

public interface SfxRepository {
    String name();

    default void awaitPendingWrites() throws Exception {
    }

    default void close() throws Exception {
    }
}
