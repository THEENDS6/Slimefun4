package cc.theends6.sfx.internal.ui;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class SfxInventoryPolicy {
    private SfxInventoryPolicy() {
    }

    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    public static boolean cancelDangerousClick(InventoryClickEvent event) {
        if (event == null) { return true; }
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        boolean dangerous = click == ClickType.NUMBER_KEY || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.DOUBLE_CLICK || click == ClickType.SWAP_OFFHAND || action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.HOTBAR_MOVE_AND_READD || action == InventoryAction.HOTBAR_SWAP;
        if (dangerous) { event.setCancelled(true); return true; }
        return false;
    }

    public static boolean protectClick(InventoryClickEvent event, SfxMenuLayout layout) {
        if (event == null || layout == null) {
            return true;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return false;
        }
        SfxSlotPolicy policy = layout.policyAt(raw);
        if (cancelDangerousClick(event)) {
            return true;
        }
        if (policy.role() == SfxSlotRole.INPUT) {
            if (!isEmpty(event.getCursor()) && !policy.accepts(event.getCursor())) {
                event.setCancelled(true);
                return true;
            }
            return false;
        }
        if (policy.role() == SfxSlotRole.OUTPUT) {
            if (!isEmpty(event.getCursor()) && (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR)) {
                event.setCancelled(true);
                return true;
            }
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    public static boolean protectDrag(InventoryDragEvent event, SfxMenuLayout layout) {
        if (event == null || layout == null) {
            return true;
        }
        int topSize = event.getView().getTopInventory().getSize();
        Set<Integer> rawSlots = event.getRawSlots();
        for (Integer raw : rawSlots) {
            if (raw != null && raw >= 0 && raw < topSize) {
                SfxSlotPolicy policy = layout.policyAt(raw);
                if (policy.role() != SfxSlotRole.INPUT || !policy.accepts(event.getOldCursor())) {
                    event.setCancelled(true);
                    return true;
                }
            }
        }
        return false;
    }
}
