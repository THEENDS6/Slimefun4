package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class SfxItemUseDispatcher implements Listener {
    private final SfxItems items;
    private final SfxLegacyUtilityListener utilityListener;
    private final SfxLegacyCombatToolListener combatToolListener;
    private final SfxLegacyFoodListener foodListener;

    public SfxItemUseDispatcher(
            SfxItems items,
            SfxLegacyUtilityListener utilityListener,
            SfxLegacyCombatToolListener combatToolListener,
            SfxLegacyFoodListener foodListener
    ) {
        this.items = Objects.requireNonNull(items, "items");
        this.utilityListener = Objects.requireNonNull(utilityListener, "utilityListener");
        this.combatToolListener = Objects.requireNonNull(combatToolListener, "combatToolListener");
        this.foodListener = Objects.requireNonNull(foodListener, "foodListener");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            item = event.getPlayer().getInventory().getItemInMainHand();
        }
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (items.readGuideMode(item).isPresent()) {
            return;
        }

        String itemId = items.readMarker(item).map(SfxItemMarker::itemId).orElse(null);
        if (itemId == null) {
            return;
        }

        if (utilityListener.handleItemUse(event, itemId)) {
            return;
        }
        if (combatToolListener.handleItemUse(event, itemId)) {
            return;
        }
        foodListener.handleItemUse(event, itemId);
    }
}
