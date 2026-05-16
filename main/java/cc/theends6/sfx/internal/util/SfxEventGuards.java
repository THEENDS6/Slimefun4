package cc.theends6.sfx.internal.util;

import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;

public final class SfxEventGuards {
    private SfxEventGuards() {
    }

    public static void denyItemUse(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    public static void denyBlockAndItemUse(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }
}
