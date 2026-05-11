package cc.theends6.sfx.internal.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;

public final class SfxResearchFireworksListener implements Listener {

    @EventHandler
    public void onResearchFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework) {
            FireworkMeta meta = firework.getFireworkMeta();
            if (meta.hasDisplayName() && (ChatColor.GREEN + "Slimefun Research").equals(meta.getDisplayName())) {
                event.setCancelled(true);
            }
        }
    }
}
