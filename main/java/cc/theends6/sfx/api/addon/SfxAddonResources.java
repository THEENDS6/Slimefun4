package cc.theends6.sfx.api.addon;

import org.bukkit.event.Listener;


public interface SfxAddonResources extends SfxScheduler {
    <T extends Listener> T registerListener(T listener);

    <T extends AutoCloseable> T own(T resource);
}
