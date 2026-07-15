package cc.theends6.sfx.internal.util;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

public final class SfxBlockDrops {
    private SfxBlockDrops() {
    }

    public static void dropPluginBlock(Block block, SfxItems items, String typeId) {
        if (block == null || items == null || typeId == null || typeId.isBlank()) {
            return;
        }
        dropItem(block, items.create(typeId));
    }

    public static void dropStack(Block block, SfxItems items, SfxElectricStack stack) {
        if (block == null || items == null || stack == null) {
            return;
        }
        dropItem(block, stack.toItemStack(items));
    }

    public static void dropItem(Block block, ItemStack stack) {
        if (block == null || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        Location location = block.getLocation().add(0.5D, 0.5D, 0.5D);
        Item dropped = block.getWorld().dropItem(location, stack);
        dropped.setPickupDelay(0);
    }
}
