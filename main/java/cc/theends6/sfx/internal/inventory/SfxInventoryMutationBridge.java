package cc.theends6.sfx.internal.inventory;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Convenience helpers that route Bukkit inventory writes through SfxTransferTransaction. */
public final class SfxInventoryMutationBridge {
    private SfxInventoryMutationBridge() {
    }

    public static SfxTransferResult insertAll(Inventory inventory, ItemStack stack, boolean smartFill, String source) {
        if (inventory == null || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return SfxTransferResult.failed(cc.theends6.sfx.internal.core.SfxErrorCode.INVALID_INPUT, stack == null ? 0 : stack.getAmount(), 0, stack == null ? 0 : stack.getAmount(), 0);
        }
        SfxBukkitInventoryEndpoint endpoint = new SfxBukkitInventoryEndpoint(inventory, source == null ? "bukkit-inventory" : source);
        return new SfxTransferTransaction().commit(stack, stack.getAmount(), List.of(new SfxTransferTransaction.Target(endpoint, stack.getAmount())), smartFill);
    }

    public static SfxTransferResult insertAllOrDrop(Inventory inventory, ItemStack stack, boolean smartFill, Location dropLocation, String source) {
        int requested = stack == null ? 0 : stack.getAmount();
        SfxTransferResult result = insertAll(inventory, stack, smartFill, source);
        if (!result.success() && stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
            drop(dropLocation, stack.clone());
            return SfxTransferResult.failed(result.code(), requested, result.inserted(), requested, result.lost());
        }
        return result;
    }

    public static void drop(Location dropLocation, ItemStack stack) {
        if (dropLocation == null || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        World world = dropLocation.getWorld();
        if (world != null) {
            world.dropItemNaturally(dropLocation, stack);
        }
    }
}
