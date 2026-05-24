package cc.theends6.sfx.internal.block;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record SfxBlockPlacementContext(
        String typeId,
        Location location,
        Material material,
        UUID ownerId,
        Player player,
        ItemStack itemInHand
) {
}
