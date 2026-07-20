package cc.theends6.sfx.api.world;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public interface SfxWorldActionService {
    CompletableFuture<SfxWorldActionResult> breakBlock(Player actor, Location location, ItemStack tool, boolean drops);
    CompletableFuture<SfxWorldActionResult> replaceBlock(Player actor, Location location, Material material,
                                                         ItemStack placementItem, boolean physics);
    default CompletableFuture<SfxWorldActionResult> placeBlock(Player actor, Location location, Material material,
                                                                ItemStack placementItem, boolean physics) {
        return replaceBlock(actor, location, material, placementItem, physics);
    }
    CompletableFuture<SfxWorldActionResult> damageEntity(Player actor, LivingEntity target, double damage);
    CompletableFuture<SfxWorldActionResult> spawnEntity(Player actor, Location location, EntityType type);
    CompletableFuture<SfxWorldActionResult> applyEffect(Player actor, LivingEntity target, PotionEffect effect);

    



    CompletableFuture<SfxRangeWorldActionResult> breakBlocks(SfxRangeBlockBreakRequest request);
}
