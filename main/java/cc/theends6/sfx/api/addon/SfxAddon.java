package cc.theends6.sfx.api.addon;

public interface SfxAddon {
    String id();

    default String name() {
        return id();
    }

    
    default void onRegister(SfxAddonContext context) {
        onLoad(context);
    }

    
    @Deprecated(forRemoval = false)
    default void onLoad(SfxAddonContext context) {
    }

    
    default void onEnable(SfxAddonContext context) {
    }

    




    default void onDisable() {
    }
}
