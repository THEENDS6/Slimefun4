package cc.theends6.sfx.internal.ui;

import org.bukkit.GameMode;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class SfxMachineMenuTransactions {
    private SfxMachineMenuTransactions() {
    }

    public static boolean isUnsupportedManagedClick(InventoryClickEvent event) {
        if (event == null) {
            return true;
        }
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        return action == InventoryAction.UNKNOWN;
    }

    public static boolean cancelUnsupportedManagedClick(InventoryClickEvent event) {
        if (isUnsupportedManagedClick(event)) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    public static boolean isCreativeCloneClick(Player player, InventoryClickEvent event) {
        if (player == null || event == null || player.getGameMode() != GameMode.CREATIVE) {
            return false;
        }
        return event.getClick() == ClickType.MIDDLE || event.getAction() == InventoryAction.CLONE_STACK;
    }

    public static boolean isTakingFromOutput(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        return !SfxInventoryPolicy.isEmpty(current) && (SfxInventoryPolicy.isEmpty(cursor)
                || event.isShiftClick()
                || event.getClick() == ClickType.DROP
                || event.getClick() == ClickType.CONTROL_DROP
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.DOUBLE_CLICK);
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

    public static boolean moveCurrentItemToTopSlots(InventoryClickEvent event, Inventory topInventory, IntPredicate allowedTopSlot, Predicate<ItemStack> validator) {
        ItemStack current = event.getCurrentItem();
        if (SfxInventoryPolicy.isEmpty(current)) {
            return false;
        }
        Predicate<ItemStack> safeValidator = validator == null ? ignored -> true : validator;
        if (!safeValidator.test(current)) {
            return false;
        }
        ItemStack remaining = current.clone();
        for (int slot = 0; slot < topInventory.getSize() && !SfxInventoryPolicy.isEmpty(remaining); slot++) {
            if (allowedTopSlot != null && !allowedTopSlot.test(slot)) {
                continue;
            }
            ItemStack target = topInventory.getItem(slot);
            if (!SfxInventoryPolicy.isEmpty(target) && !target.isSimilar(remaining)) {
                continue;
            }
            int currentAmount = SfxInventoryPolicy.isEmpty(target) ? 0 : target.getAmount();
            int max = SfxInventoryPolicy.isEmpty(target) ? Math.min(remaining.getMaxStackSize(), topInventory.getMaxStackSize()) : target.getMaxStackSize();
            int room = max - currentAmount;
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining.getAmount());
            ItemStack updated = SfxInventoryPolicy.isEmpty(target) ? remaining.clone() : target.clone();
            updated.setAmount(currentAmount + moved);
            if (!safeValidator.test(updated)) {
                continue;
            }
            topInventory.setItem(slot, updated);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        int remainingAmount = SfxInventoryPolicy.isEmpty(remaining) ? 0 : remaining.getAmount();
        if (remainingAmount >= current.getAmount()) {
            return false;
        }
        event.setCurrentItem(remainingAmount <= 0 ? null : remaining);
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
        Vector direction = player.getLocation().getDirection().normalize();
        org.bukkit.Location dropLocation = player.getEyeLocation().add(direction.clone().multiply(0.35D));
        org.bukkit.entity.Item entity = player.getWorld().dropItem(dropLocation, dropped);
        entity.setVelocity(direction.multiply(0.35D).add(new Vector(0.0D, 0.1D, 0.0D)));
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

    public static boolean handleManagedHotbarOrOffhand(InventoryClickEvent event, Inventory topInventory, int rawSlot, Player player, boolean inputSlot, boolean outputSlot, Predicate<ItemStack> inputValidator) {
        ClickType click = event.getClick();
        if (click != ClickType.NUMBER_KEY && click != ClickType.SWAP_OFFHAND) {
            return false;
        }
        event.setCancelled(true);
        if (click == ClickType.NUMBER_KEY && event.getHotbarButton() < 0) {
            return true;
        }
        ItemStack current = topInventory.getItem(rawSlot);
        ItemStack carrier = click == ClickType.SWAP_OFFHAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItem(event.getHotbarButton());
        Predicate<ItemStack> safeValidator = inputValidator == null ? ignored -> true : inputValidator;
        if (outputSlot) {
            if (SfxInventoryPolicy.isEmpty(current) || !SfxInventoryPolicy.isEmpty(carrier)) {
                return true;
            }
            writeCarrier(player, event, current.clone());
            topInventory.setItem(rawSlot, null);
            return true;
        }
        if (!inputSlot) {
            return true;
        }
        if (!SfxInventoryPolicy.isEmpty(carrier) && !safeValidator.test(carrier)) {
            return true;
        }
        if (!SfxInventoryPolicy.isEmpty(current) && !safeValidator.test(current)) {
            return true;
        }
        topInventory.setItem(rawSlot, SfxInventoryPolicy.isEmpty(carrier) ? null : carrier.clone());
        writeCarrier(player, event, SfxInventoryPolicy.isEmpty(current) ? null : current.clone());
        return true;
    }

    public static boolean handleManagedDoubleClick(InventoryClickEvent event, Inventory topInventory, Player player, IntPredicate allowedTopSlot) {
        if (event.getClick() != ClickType.DOUBLE_CLICK && event.getAction() != InventoryAction.COLLECT_TO_CURSOR) {
            return false;
        }
        event.setCancelled(true);
        ItemStack cursor = event.getCursor();
        ItemStack target = SfxInventoryPolicy.isEmpty(cursor) ? event.getCurrentItem() : cursor;
        if (SfxInventoryPolicy.isEmpty(target)) {
            return true;
        }
        ItemStack collected = target.clone();
        int amount = SfxInventoryPolicy.isEmpty(cursor) ? 0 : cursor.getAmount();
        collected.setAmount(amount);
        int max = collected.getMaxStackSize();
        for (int slot = 0; slot < topInventory.getSize() && amount < max; slot++) {
            if (allowedTopSlot == null || !allowedTopSlot.test(slot)) {
                continue;
            }
            amount = collectFromSlot(topInventory, slot, collected, amount, max);
        }
        Inventory playerInventory = player.getInventory();
        for (int slot = 0; slot < playerInventory.getSize() && amount < max; slot++) {
            amount = collectFromSlot(playerInventory, slot, collected, amount, max);
        }
        if (amount <= 0) {
            event.setCursor(null);
        } else {
            collected.setAmount(amount);
            event.setCursor(collected);
        }
        return true;
    }

    private static int collectFromSlot(Inventory inventory, int slot, ItemStack target, int amount, int max) {
        ItemStack stack = inventory.getItem(slot);
        if (SfxInventoryPolicy.isEmpty(stack) || !stack.isSimilar(target) || amount >= max) {
            return amount;
        }
        int moved = Math.min(stack.getAmount(), max - amount);
        int remaining = stack.getAmount() - moved;
        if (remaining <= 0) {
            inventory.setItem(slot, null);
        } else {
            ItemStack rest = stack.clone();
            rest.setAmount(remaining);
            inventory.setItem(slot, rest);
        }
        return amount + moved;
    }

    private static void writeCarrier(Player player, InventoryClickEvent event, ItemStack value) {
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            player.getInventory().setItemInOffHand(value);
        } else if (event.getHotbarButton() >= 0) {
            player.getInventory().setItem(event.getHotbarButton(), value);
        }
    }
}
