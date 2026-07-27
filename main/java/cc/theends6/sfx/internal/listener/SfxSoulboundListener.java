package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxSoulboundListener implements Listener {
    private static final Set<String> SOULBOUND_IDS = Set.of(
            "sf:soulbound_sword",
            "sf:soulbound_bow",
            "sf:soulbound_pickaxe",
            "sf:soulbound_axe",
            "sf:soulbound_shovel",
            "sf:soulbound_hoe",
            "sf:soulbound_trident",
            "sf:soulbound_helmet",
            "sf:soulbound_chestplate",
            "sf:soulbound_leggings",
            "sf:soulbound_boots",
            "sf:soulbound_elytra",
            "sf:ancient_rune_soulbound",
            "sf:bound_backpack"
    );

    private final SfxItems items;
    private final NamespacedKey soulboundKey;

    public SfxSoulboundListener(JavaPlugin plugin, SfxItems items) {
        this.items = Objects.requireNonNull(items, "items");
        this.soulboundKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "soulbound");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        
        
        
        
        for (Iterator<ItemStack> iterator = event.getDrops().iterator(); iterator.hasNext(); ) {
            ItemStack drop = iterator.next();
            if (!isSoulbound(drop)) {
                continue;
            }
            
            iterator.remove();
            event.getItemsToKeep().add(drop);
        }
    }

    private boolean isSoulbound(ItemStack item) {
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
        return itemId != null && SOULBOUND_IDS.contains(itemId);
    }
}
