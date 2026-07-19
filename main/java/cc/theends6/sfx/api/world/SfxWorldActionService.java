package cc.theends6.sfx.api.world;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface SfxWorldActionService {
    CompletableFuture<SfxWorldActionResult> breakBlock(Player actor, Location location, ItemStack tool, boolean drops);
    CompletableFuture<SfxWorldActionResult> replaceBlock(Player actor, Location location, Material material,
                                                         ItemStack placementItem, boolean physics);
}
