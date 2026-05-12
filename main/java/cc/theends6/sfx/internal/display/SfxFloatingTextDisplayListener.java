package cc.theends6.sfx.internal.display;

import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SfxFloatingTextDisplayListener implements Listener {
    private final SfxFloatingTextDisplayService displayService;

    public SfxFloatingTextDisplayListener(SfxFloatingTextDisplayService displayService) {
        this.displayService = Objects.requireNonNull(displayService, "displayService");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        displayService.clearViewer(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        displayService.refreshViewer(event.getPlayer());
    }
}
