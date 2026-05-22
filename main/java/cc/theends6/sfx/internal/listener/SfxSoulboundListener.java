package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.research.SfxResearchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxSoulboundListener implements Listener {
    private final SfxItems items;
    private final SfxResearchService researches;
    private final NamespacedKey soulboundKey;
    private final Map<UUID, Map<Integer, ItemStack>> pending = new HashMap<>();

    public SfxSoulboundListener(JavaPlugin plugin, SfxItems items, SfxResearchService researches) {
        this.items = Objects.requireNonNull(items, "items");
        this.researches = Objects.requireNonNull(researches, "researches");
        this.soulboundKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "soulbound");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Map<Integer, ItemStack> restored = new HashMap<>();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isSoulbound(player, item)) {
                continue;
            }
            restored.put(slot, item.clone());
        }
        if (restored.isEmpty()) {
            return;
        }
        pending.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).putAll(restored);
        event.getDrops().removeIf(item -> isSoulbound(player, item));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Map<Integer, ItemStack> restored = pending.remove(event.getPlayer().getUniqueId());
        if (restored == null || restored.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, ItemStack> entry : restored.entrySet()) {
            event.getPlayer().getInventory().setItem(entry.getKey(), entry.getValue());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private boolean isSoulbound(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(soulboundKey, PersistentDataType.BYTE)) {
            return true;
        }
        String itemId = items.readMarker(item).map(SfxItemMarker::itemId).orElse(null);
        if (itemId == null) {
            return false;
        }
        if (!itemId.contains("soulbound") && !"sf:bound_backpack".equals(itemId)) {
            return false;
        }
        return researches.canUse(player, itemId);
    }
}
