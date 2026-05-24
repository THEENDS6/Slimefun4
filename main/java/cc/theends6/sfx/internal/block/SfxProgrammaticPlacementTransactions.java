package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.internal.core.SfxResult;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** Shared transaction entry for SFX blocks placed by machines instead of player BlockPlaceEvent. */
public final class SfxProgrammaticPlacementTransactions {
    private SfxProgrammaticPlacementTransactions() {
    }

    public static Optional<UUID> place(
            SfxBlockDataService blockData,
            String typeId,
            Block target,
            Material material,
            UUID ownerId,
            ItemStack sourceItem,
            SfxDelegatingBlockBehavior.PlacementInitializer initializer,
            Logger logger
    ) {
        if (blockData == null || typeId == null || target == null || material == null) {
            return Optional.empty();
        }
        SfxBlockPlacementContext context = new SfxBlockPlacementContext(
                typeId,
                target.getLocation(),
                material,
                ownerId,
                null,
                sourceItem);
        SfxBlockPlacementTransaction transaction = new SfxBlockPlacementTransaction(
                blockData,
                new SfxDelegatingBlockBehavior(typeId, initializer),
                logger);
        SfxResult<UUID> result = transaction.commit(context);
        if (!result.success()) {
            if (logger != null) {
                result.cause().ifPresentOrElse(
                        cause -> logger.log(Level.WARNING, "Failed to programmatically place SFX block " + typeId + " at " + target.getLocation(), cause),
                        () -> logger.warning("Failed to programmatically place SFX block " + typeId + " at " + target.getLocation() + ": " + result.message()));
            }
            return Optional.empty();
        }
        return result.value();
    }
}
