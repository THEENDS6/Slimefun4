package cc.theends6.sfx.internal.core;

import java.util.List;

public interface SfxModule {
    String name();

    default List<String> dependsOn() {
        return List.of();
    }

    default void load() throws Exception {
    }

    default void enable() throws Exception {
    }

    default void reload() throws Exception {
    }

    default void disable() throws Exception {
    }
}
