package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.item.SfxItems;
import java.util.Objects;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

final class SfxPickBlockSupport {
    private static final int HOTBAR_FIRST = 0;
    private static final int HOTBAR_LAST = 8;
    private static final int STORAGE_FIRST = 9;
    private static final int STORAGE_LAST = 35;

    private SfxPickBlockSupport() {
    }

    static void selectOrCreate(Player player, SfxItems items, String typeId, int requestedHotbarSlot) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(items, "items");
        if (typeId == null || typeId.isBlank()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int targetSlot = normalizeHotbarSlot(requestedHotbarSlot, inventory.getHeldItemSlot());

        int hotbarSlot = findMatchingSlot(inventory, items, typeId, HOTBAR_FIRST, HOTBAR_LAST);
        if (hotbarSlot >= 0) {
            inventory.setHeldItemSlot(hotbarSlot);
            player.updateInventory();
            return;
        }

        int storageSlot = findMatchingSlot(inventory, items, typeId, STORAGE_FIRST, STORAGE_LAST);
        if (storageSlot >= 0) {
            ItemStack targetItem = inventory.getItem(storageSlot);
            ItemStack previousHotbarItem = inventory.getItem(targetSlot);
            inventory.setItem(targetSlot, targetItem);
            inventory.setItem(storageSlot, previousHotbarItem);
            inventory.setHeldItemSlot(targetSlot);
            player.updateInventory();
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            ItemStack created = items.create(typeId);
            if (created != null) {
                inventory.setItem(targetSlot, created);
                inventory.setHeldItemSlot(targetSlot);
                player.updateInventory();
            }
        }
    }

    private static int normalizeHotbarSlot(int requestedSlot, int fallbackSlot) {
        if (requestedSlot >= HOTBAR_FIRST && requestedSlot <= HOTBAR_LAST) {
            return requestedSlot;
        }
        if (fallbackSlot >= HOTBAR_FIRST && fallbackSlot <= HOTBAR_LAST) {
            return fallbackSlot;
        }
        return HOTBAR_FIRST;
    }

    private static int findMatchingSlot(PlayerInventory inventory, SfxItems items, String typeId, int first, int last) {
        for (int slot = first; slot <= last; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (matchesSfxId(items, item, typeId)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean matchesSfxId(SfxItems items, ItemStack item, String typeId) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return items.readMarker(item)
                .map(marker -> typeId.equals(marker.itemId()))
                .orElse(false);
    }
}
