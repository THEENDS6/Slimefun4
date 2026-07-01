package cc.theends6.sfx.internal.ui;

import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class SfxMachineMenuTransactions {
    private SfxMachineMenuTransactions() {
    }

    public static boolean isUnsupportedManagedClick(InventoryClickEvent event) {
        if (event == null) {
            return true;
        }
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        return click == ClickType.NUMBER_KEY
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.MIDDLE
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.CLONE_STACK;
    }

    public static boolean cancelUnsupportedManagedClick(InventoryClickEvent event) {
        if (isUnsupportedManagedClick(event)) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    public static boolean isTakingFromOutput(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        return !SfxInventoryPolicy.isEmpty(current) && (SfxInventoryPolicy.isEmpty(cursor)
                || event.isShiftClick()
                || event.getClick() == ClickType.DROP
                || event.getClick() == ClickType.CONTROL_DROP);
    }

    public static boolean moveTopSlotToPlayer(Inventory topInventory, int rawSlot, Player player) {
        ItemStack current = topInventory.getItem(rawSlot);
        if (SfxInventoryPolicy.isEmpty(current)) {
            return false;
        }
        ItemStack moving = current.clone();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(moving);
        int leftoverAmount = leftovers.values().stream()
                .filter(stack -> !SfxInventoryPolicy.isEmpty(stack))
                .mapToInt(ItemStack::getAmount)
                .sum();
        int moved = current.getAmount() - leftoverAmount;
        if (moved <= 0) {
            return false;
        }
        if (leftoverAmount <= 0) {
            topInventory.setItem(rawSlot, null);
        } else {
            ItemStack remaining = current.clone();
            remaining.setAmount(leftoverAmount);
            topInventory.setItem(rawSlot, remaining);
        }
        return true;
    }

    public static boolean dropFromTopSlot(InventoryClickEvent event, Inventory topInventory, int rawSlot, Player player) {
        ClickType click = event.getClick();
        if (click != ClickType.DROP && click != ClickType.CONTROL_DROP) {
            return false;
        }
        ItemStack current = topInventory.getItem(rawSlot);
        if (SfxInventoryPolicy.isEmpty(current)) {
            return false;
        }
        int amount = click == ClickType.DROP ? 1 : current.getAmount();
        ItemStack dropped = current.clone();
        dropped.setAmount(amount);
        int remainingAmount = current.getAmount() - amount;
        if (remainingAmount <= 0) {
            topInventory.setItem(rawSlot, null);
        } else {
            ItemStack remaining = current.clone();
            remaining.setAmount(remainingAmount);
            topInventory.setItem(rawSlot, remaining);
        }
        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        return true;
    }

    public static boolean takeFromSlotToCursor(InventoryClickEvent event, Inventory topInventory, int rawSlot) {
        ItemStack current = topInventory.getItem(rawSlot);
        if (SfxInventoryPolicy.isEmpty(current) || !SfxInventoryPolicy.isEmpty(event.getCursor())) {
            return false;
        }
        int amount;
        if (event.getAction() == InventoryAction.PICKUP_HALF) {
            amount = (current.getAmount() + 1) / 2;
        } else if (event.getAction() == InventoryAction.PICKUP_ONE) {
            amount = 1;
        } else if (event.getAction() == InventoryAction.PICKUP_ALL || event.getAction() == InventoryAction.PICKUP_SOME) {
            amount = current.getAmount();
        } else {
            return false;
        }
        ItemStack cursor = current.clone();
        cursor.setAmount(amount);
        int remainingAmount = current.getAmount() - amount;
        if (remainingAmount <= 0) {
            topInventory.setItem(rawSlot, null);
        } else {
            ItemStack remaining = current.clone();
            remaining.setAmount(remainingAmount);
            topInventory.setItem(rawSlot, remaining);
        }
        event.setCursor(cursor);
        return true;
    }

    public static boolean handleInputSlotCursorTransaction(InventoryClickEvent event, Inventory topInventory, int rawSlot, Predicate<ItemStack> validator) {
        InventoryAction action = event.getAction();
        ItemStack current = topInventory.getItem(rawSlot);
        ItemStack cursor = event.getCursor();
        Predicate<ItemStack> safeValidator = validator == null ? ignored -> true : validator;
        if (SfxInventoryPolicy.isEmpty(cursor)) {
            return takeFromSlotToCursor(event, topInventory, rawSlot);
        }
        if (!safeValidator.test(cursor)) {
            return false;
        }
        if (action == InventoryAction.SWAP_WITH_CURSOR) {
            if (!SfxInventoryPolicy.isEmpty(current) && !safeValidator.test(current)) {
                return false;
            }
            topInventory.setItem(rawSlot, cursor.clone());
            event.setCursor(SfxInventoryPolicy.isEmpty(current) ? null : current.clone());
            return true;
        }
        if (action != InventoryAction.PLACE_ALL && action != InventoryAction.PLACE_ONE && action != InventoryAction.PLACE_SOME) {
            return false;
        }
        if (!SfxInventoryPolicy.isEmpty(current) && !current.isSimilar(cursor)) {
            return false;
        }
        int room = SfxInventoryPolicy.isEmpty(current) ? cursor.getMaxStackSize() : current.getMaxStackSize() - current.getAmount();
        if (room <= 0) {
            return false;
        }
        int requested = action == InventoryAction.PLACE_ONE ? 1 : cursor.getAmount();
        int moved = Math.min(room, requested);
        ItemStack updated = SfxInventoryPolicy.isEmpty(current) ? cursor.clone() : current.clone();
        updated.setAmount((SfxInventoryPolicy.isEmpty(current) ? 0 : current.getAmount()) + moved);
        if (!safeValidator.test(updated)) {
            return false;
        }
        topInventory.setItem(rawSlot, updated);
        int cursorRemaining = cursor.getAmount() - moved;
        if (cursorRemaining <= 0) {
            event.setCursor(null);
        } else {
            ItemStack remainingCursor = cursor.clone();
            remainingCursor.setAmount(cursorRemaining);
            event.setCursor(remainingCursor);
        }
        return true;
    }
}
