package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.util.SfxEventGuards;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.api.text.Text;
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
    private final SfxBackpackListener backpackListener;
    private final SfxLegacyUtilityListener utilityListener;
    private final SfxLegacyCombatToolListener combatToolListener;
    private final SfxLegacyFoodListener foodListener;
    private final SfxResearchService researches;
    private final SfxLocalization localization;

    public SfxItemUseDispatcher(
            SfxItems items,
            SfxBackpackListener backpackListener,
            SfxLegacyUtilityListener utilityListener,
            SfxLegacyCombatToolListener combatToolListener,
            SfxLegacyFoodListener foodListener,
            SfxResearchService researches,
            SfxLocalization localization
    ) {
        this.items = Objects.requireNonNull(items, "items");
        this.backpackListener = Objects.requireNonNull(backpackListener, "backpackListener");
        this.utilityListener = Objects.requireNonNull(utilityListener, "utilityListener");
        this.combatToolListener = Objects.requireNonNull(combatToolListener, "combatToolListener");
        this.foodListener = Objects.requireNonNull(foodListener, "foodListener");
        this.researches = Objects.requireNonNull(researches, "researches");
        this.localization = Objects.requireNonNull(localization, "localization");
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

        if (!items.canUse(event.getPlayer(), itemId)) {
            SfxEventGuards.denyBlockAndItemUse(event);
            event.getPlayer().sendMessage(Text.prefixed(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                    localization.text("messages.no-item-permission")
            ));
            return;
        }

        if (researches.researchForItem(itemId).isPresent()
                && researches.findProfile(event.getPlayer()).isEmpty()) {
            SfxEventGuards.denyBlockAndItemUse(event);
            event.getPlayer().sendMessage(Text.prefixed(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                    localization.text("messages.profile.loading")
            ));
            return;
        }

        if (!researches.canUse(event.getPlayer(), itemId)) {
            SfxEventGuards.denyBlockAndItemUse(event);
            event.getPlayer().sendMessage(Text.prefixed(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                    localization.text("messages.not-researched-item")
            ));
            return;
        }

        if (backpackListener.handleItemUse(event, itemId)) {
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
