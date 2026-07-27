package cc.theends6.sfx.api.permission;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;









public interface SfxWorldPermissionService {

    
    boolean allowed(SfxActionActor actor, SfxWorldActionType action, Location location,
                    Block block, Entity entity, EntityType spawnType, ItemStack item);

    default boolean canBreak(SfxActionActor actor, Block block) {
        return allowed(actor, SfxWorldActionType.BREAK_BLOCK, block.getLocation(), block, null, null, null);
    }

    default boolean canPlace(SfxActionActor actor, Block block, ItemStack item) {
        return allowed(actor, SfxWorldActionType.PLACE_BLOCK, block.getLocation(), block, null, null, item);
    }

    default boolean canInteract(SfxActionActor actor, Block block) {
        return allowed(actor, SfxWorldActionType.INTERACT_BLOCK, block.getLocation(), block, null, null, null);
    }

    default boolean canDamage(SfxActionActor actor, Entity entity) {
        return allowed(actor, SfxWorldActionType.DAMAGE_ENTITY, entity.getLocation(), null, entity, null, null);
    }

    default boolean canSpawn(SfxActionActor actor, Location location, EntityType type) {
        return allowed(actor, SfxWorldActionType.SPAWN_ENTITY, location, null, null, type, null);
    }

    default boolean canUseItem(SfxActionActor actor, Location location, ItemStack item) {
        return allowed(actor, SfxWorldActionType.USE_ITEM, location, null, null, null, item);
    }

    default boolean canAccessContainer(SfxActionActor actor, Block block) {
        return allowed(actor, SfxWorldActionType.ACCESS_CONTAINER, block.getLocation(), block, null, null, null);
    }
}
