package cc.theends6.sfx.internal.core;

public interface SfxModule {
    String name();

    default void load() throws Exception {
    }

    default void enable() throws Exception {
    }

    default void reload() throws Exception {
    }

    default void disable() throws Exception {
    }
}
