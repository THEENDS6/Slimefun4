package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.guide.SfxGuide;
import cc.theends6.sfx.api.item.SfxItems;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxGuideListener implements Listener {
    private final JavaPlugin plugin;
    private final SfxItems items;
    private final SfxGuide guide;

    public SfxGuideListener(JavaPlugin plugin, SfxItems items, SfxGuide guide) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.guide = Objects.requireNonNull(guide, "guide");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!rightClick && !leftClick) {
            return;
        }
        if (rightClick && !plugin.getConfig().getBoolean("guide.allow-right-click-open", true)) {
            return;
        }
        if (leftClick && !plugin.getConfig().getBoolean("guide.allow-left-click-open", false)) {
            return;
        }
        if (rightClick && action == Action.RIGHT_CLICK_BLOCK && shouldPreserveVanillaBlockInteraction(event)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && items.readGuideMode(event.getPlayer().getInventory().getItemInMainHand()).isPresent()) {
            return;
        }
        items.readGuideMode(item).ifPresent(mode -> {
            event.setCancelled(true);
            if (rightClick && event.getPlayer().isSneaking() && plugin.getConfig().getBoolean("guide.shift-right-click-settings", true)) {
                guide.openSettings(event.getPlayer(), mode);
            } else {
                guide.open(event.getPlayer(), mode);
            }
        });
    }

    private boolean shouldPreserveVanillaBlockInteraction(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return false;
        }
        BlockState state = clicked.getState();
        return state instanceof InventoryHolder;
    }
}
