package cc.theends6.sfx.internal.world;

import cc.theends6.sfx.api.permission.SfxActionActor;
import cc.theends6.sfx.api.permission.SfxWorldPermissionService;
import cc.theends6.sfx.api.world.SfxProtectionService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;










public final class DefaultSfxProtectionService implements SfxProtectionService {
    private final SfxWorldPermissionService permissions;

    public DefaultSfxProtectionService(SfxWorldPermissionService permissions) {
        this.permissions = permissions;
    }

    @Override public boolean canBreak(org.bukkit.entity.Player player, Block block) {
        return player != null && block != null && permissions.canBreak(SfxActionActor.player(player), block);
    }

    @Override public boolean canPlace(org.bukkit.entity.Player player, Block block, ItemStack item) {
        return player != null && block != null && permissions.canPlace(SfxActionActor.player(player), block, item);
    }

    @Override public boolean canInteract(org.bukkit.entity.Player player, Block block) {
        return player != null && block != null && permissions.canInteract(SfxActionActor.player(player), block);
    }

    @Override public boolean canDamage(org.bukkit.entity.Player player, Entity entity) {
        return player != null && entity != null && permissions.canDamage(SfxActionActor.player(player), entity);
    }

    @Override public boolean canUseItem(org.bukkit.entity.Player player, Location location, ItemStack item) {
        return player != null && location != null && permissions.canUseItem(SfxActionActor.player(player), location, item);
    }
}
