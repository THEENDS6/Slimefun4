package cc.theends6.sfx.api.world;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface SfxProtectionService {
    boolean canBreak(Player player, Block block);
    boolean canPlace(Player player, Block block, ItemStack item);
    boolean canInteract(Player player, Block block);
    boolean canDamage(Player player, Entity entity);
    boolean canUseItem(Player player, org.bukkit.Location location, ItemStack item);
}
