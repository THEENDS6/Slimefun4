package cc.theends6.sfx.api.world;

import cc.theends6.sfx.api.permission.SfxActionActor;
import cc.theends6.sfx.api.permission.SfxWorldActionType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;


public interface SfxProtectionAdapter {
    





    default SfxProtectionDecision canPerform(
            SfxActionActor actor,
            SfxWorldActionType action,
            Location location,
            Block block,
            Entity entity,
            EntityType spawnType,
            ItemStack item
    ) {
        return SfxProtectionDecision.PASS;
    }

    default SfxProtectionDecision canBreak(Player player, Block block) { return SfxProtectionDecision.PASS; }
    default SfxProtectionDecision canPlace(Player player, Block block, ItemStack item) { return SfxProtectionDecision.PASS; }
    default SfxProtectionDecision canInteract(Player player, Block block) { return SfxProtectionDecision.PASS; }
    default SfxProtectionDecision canDamage(Player player, Entity entity) { return SfxProtectionDecision.PASS; }
    default SfxProtectionDecision canUseItem(Player player, Location location, ItemStack item) { return SfxProtectionDecision.PASS; }
}
