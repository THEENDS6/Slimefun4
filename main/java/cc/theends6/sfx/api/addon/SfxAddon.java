package cc.theends6.sfx.api.addon;

public interface SfxAddon {
    String id();

    default String name() {
        return id();
    }

    default void onLoad(SfxAddonContext context) {
    }
}
