package cc.theends6.sfx.internal.world;

import cc.theends6.sfx.api.world.SfxProtectionAdapter;
import cc.theends6.sfx.api.world.SfxProtectionDecision;
import cc.theends6.sfx.api.world.SfxProtectionService;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;


public final class DefaultSfxProtectionService implements SfxProtectionService {
    private final JavaPlugin plugin;

    public DefaultSfxProtectionService(JavaPlugin plugin) { this.plugin = plugin; }

    @Override public boolean canBreak(Player player, Block block) {
        if (!sameWorld(player, block)) return false;
        Boolean decision = breakAdapterDecision(player, block);
        if (decision != null) return decision;
        return SfxProtectionProbe.call(() -> {
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            plugin.getServer().getPluginManager().callEvent(event);
            return !event.isCancelled();
        });
    }

    @Override public boolean canPlace(Player player, Block block, ItemStack item) {
        if (!sameWorld(player, block)) return false;
        Boolean decision = placeAdapterDecision(player, block, item);
        if (decision != null) return decision;
        return SfxProtectionProbe.call(() -> {
            BlockState replaced = block.getState();
            Block against = block.getRelative(org.bukkit.block.BlockFace.DOWN);
            BlockPlaceEvent event = new BlockPlaceEvent(block, replaced, against,
                    item == null ? new ItemStack(block.getType()) : item, player, true, EquipmentSlot.HAND);
            plugin.getServer().getPluginManager().callEvent(event);
            return !event.isCancelled() && event.canBuild();
        });
    }

    @Override public boolean canInteract(Player player, Block block) {
        if (!sameWorld(player, block)) return false;
        Boolean decision = adapters(adapter -> adapter.canInteract(player, block));
        return decision == null || decision;
    }

    @Override public boolean canDamage(Player player, Entity entity) {
        if (player == null || entity == null || !player.getWorld().equals(entity.getWorld())) return false;
        Boolean decision = adapters(adapter -> adapter.canDamage(player, entity));
        return decision == null || decision;
    }

    @Override public boolean canUseItem(Player player, Location location, ItemStack item) {
        if (player == null || location == null || location.getWorld() == null || !player.getWorld().equals(location.getWorld())) return false;
        Boolean decision = adapters(adapter -> adapter.canUseItem(player, location, item));
        return decision == null || decision;
    }

    private Boolean adapters(Function<SfxProtectionAdapter, SfxProtectionDecision> check) {
        boolean allowed = false;
        for (RegisteredServiceProvider<SfxProtectionAdapter> registration
                : plugin.getServer().getServicesManager().getRegistrations(SfxProtectionAdapter.class)) {
            SfxProtectionDecision decision = check.apply(registration.getProvider());
            if (decision == SfxProtectionDecision.DENY) return false;
            if (decision == SfxProtectionDecision.ALLOW) allowed = true;
        }
        return allowed ? Boolean.TRUE : null;
    }

    
    Boolean breakAdapterDecision(Player player, Block block) {
        return sameWorld(player, block) ? adapters(adapter -> adapter.canBreak(player, block)) : Boolean.FALSE;
    }

    
    Boolean placeAdapterDecision(Player player, Block block, ItemStack item) {
        return sameWorld(player, block) ? adapters(adapter -> adapter.canPlace(player, block, item)) : Boolean.FALSE;
    }

    private static boolean sameWorld(Player player, Block block) {
        return player != null && block != null && player.getWorld().equals(block.getWorld());
    }
}
