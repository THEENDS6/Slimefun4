package cc.theends6.sfx.internal.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;

public record SfxClickContext(
        Player player,
        int rawSlot,
        int slot,
        boolean topInventory,
        ClickType clickType,
        InventoryAction action,
        ItemStack currentItem,
        ItemStack cursorItem
) {
}
