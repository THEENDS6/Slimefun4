package cc.theends6.sfx.internal.display;

import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

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
        displayService.refreshViewerLater(event.getPlayer(), 20L);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        displayService.refreshViewerLater(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        displayService.refreshViewerLater(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        displayService.refreshViewerLater(event.getPlayer(), 20L);
    }
}
