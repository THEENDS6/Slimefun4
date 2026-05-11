package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SfxPlayerProfileListener implements Listener {
    private final SfxPlayerDataService profiles;

    public SfxPlayerProfileListener(SfxPlayerDataService profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profiles.request(event.getPlayer(), profile -> {
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profiles.saveAndUnload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        profiles.saveAndUnload(event.getPlayer().getUniqueId());
    }
}
