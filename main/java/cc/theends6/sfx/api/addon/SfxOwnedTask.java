package cc.theends6.sfx.api.addon;


public interface SfxOwnedTask extends AutoCloseable {
    void cancel();

    boolean cancelled();

    @Override
    default void close() {
        cancel();
    }
}
