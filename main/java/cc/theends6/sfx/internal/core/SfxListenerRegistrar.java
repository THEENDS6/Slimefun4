package cc.theends6.sfx.internal.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxListenerRegistrar {
    private final JavaPlugin plugin;
    private final List<Listener> registered = new ArrayList<>();

    public SfxListenerRegistrar(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public <T extends Listener> T register(T listener) {
        Objects.requireNonNull(listener, "listener");
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        registered.add(listener);
        return listener;
    }

    public List<Listener> registered() {
        return Collections.unmodifiableList(registered);
    }
}
