package cc.theends6.sfx.internal.permission;

import cc.theends6.sfx.api.permission.SfxActionActor;
import cc.theends6.sfx.api.permission.SfxWorldActionPermissionEvent;
import cc.theends6.sfx.api.permission.SfxWorldActionType;
import cc.theends6.sfx.api.permission.SfxWorldPermissionService;
import cc.theends6.sfx.api.world.SfxProtectionAdapter;
import cc.theends6.sfx.api.world.SfxProtectionDecision;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
















public final class DefaultSfxWorldPermissionService implements SfxWorldPermissionService {
    private final JavaPlugin plugin;
    private final List<SfxProtectionAdapter> builtInAdapters;

    public DefaultSfxWorldPermissionService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.builtInAdapters = discoverBuiltInAdapters(plugin);
    }

    @Override
    public boolean allowed(SfxActionActor actor, SfxWorldActionType action, Location location,
                           Block block, Entity entity, EntityType spawnType, ItemStack item) {
        if (actor == null || action == null) {
            return false;
        }
        if (!validTarget(location, block, entity)) {
            return false;
        }

        
        
        Boolean adapterVerdict = adapterDecision(action, actor, block, entity, location, spawnType, item);
        if (adapterVerdict != null) {
            if (!adapterVerdict) {
                return false;
            }
            return fireEvent(action, actor, location, block, entity, spawnType, item, true);
        }

        
        return fireEvent(action, actor, location, block, entity, spawnType, item, fallbackBaseline(actor));
    }

    

    
    private Boolean adapterDecision(SfxWorldActionType action, SfxActionActor actor, Block block, Entity entity,
                                    Location location, EntityType spawnType, ItemStack item) {
        boolean sawAllow = false;
        Player player = actor.hasOnlinePlayer() ? actor.onlinePlayer() : null;
        for (SfxProtectionAdapter adapter : allAdapters()) {
            try {
                SfxProtectionDecision decision = adapter.canPerform(
                        actor, action, location, block, entity, spawnType, item);
                if (decision == SfxProtectionDecision.DENY) {
                    return Boolean.FALSE;
                }
                if (decision == SfxProtectionDecision.ALLOW) {
                    sawAllow = true;
                }
                if (player == null) {
                    continue;
                }
                decision = legacyDecision(adapter, action, player, block, entity, location, item);
                if (decision == SfxProtectionDecision.DENY) {
                    return Boolean.FALSE;
                }
                if (decision == SfxProtectionDecision.ALLOW) {
                    sawAllow = true;
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Protection adapter " + adapter.getClass().getName()
                        + " threw during a permission check: " + exception.getMessage());
            }
        }
        return sawAllow ? Boolean.TRUE : null;
    }

    private SfxProtectionDecision legacyDecision(SfxProtectionAdapter adapter, SfxWorldActionType action,
                                                  Player player, Block block, Entity entity,
                                                  Location location, ItemStack item) {
        return switch (action) {
            case BREAK_BLOCK, RANGE_BREAK -> adapter.canBreak(player, block);
            case PLACE_BLOCK -> adapter.canPlace(player, block, item);
            case INTERACT_BLOCK, ACCESS_CONTAINER, MOVE_INTO -> adapter.canInteract(player, block);
            case DAMAGE_ENTITY -> adapter.canDamage(player, entity);
            case SPAWN_ENTITY, USE_ITEM -> adapter.canUseItem(player, location, item);
        };
    }

    private List<SfxProtectionAdapter> allAdapters() {
        List<SfxProtectionAdapter> adapters = new ArrayList<>(builtInAdapters);
        for (RegisteredServiceProvider<SfxProtectionAdapter> registration
                : plugin.getServer().getServicesManager().getRegistrations(SfxProtectionAdapter.class)) {
            if (!adapters.contains(registration.getProvider())) {
                adapters.add(registration.getProvider());
            }
        }
        return adapters;
    }

    private static List<SfxProtectionAdapter> discoverBuiltInAdapters(JavaPlugin plugin) {
        List<SfxProtectionAdapter> adapters = new ArrayList<>();
        if (plugin.getServer().getPluginManager().isPluginEnabled("Towny")) {
            SfxProtectionAdapter towny = TownySfxProtectionAdapter.create(plugin);
            if (towny != null) {
                adapters.add(towny);
                plugin.getLogger().info("Enabled built-in Towny world-protection bridge.");
            }
        }
        return List.copyOf(adapters);
    }

    

    private boolean fireEvent(SfxWorldActionType action, SfxActionActor actor, Location location, Block block,
                              Entity entity, EntityType spawnType, ItemStack item, boolean baseline) {
        SfxWorldActionPermissionEvent event = new SfxWorldActionPermissionEvent(
                action, actor, location, block, entity, spawnType, item);
        plugin.getServer().getPluginManager().callEvent(event);
        return switch (event.result()) {
            case DENY -> false;
            case ALLOW -> true;
            case PASS -> baseline;
        };
    }

    

    private boolean fallbackBaseline(SfxActionActor actor) {
        return switch (actor.kind()) {
            case PLAYER -> configFallbackAllow();
            case SYSTEM -> configSystemAllow();
            case OWNER, MACHINE -> {
                if (actor.hasOnlinePlayer()) {
                    yield configFallbackAllow();
                }
                if (actor.ownerId() == null) {
                    yield configUnownedMachineAllow();
                }
                yield configOfflineOwnerAllow();
            }
        };
    }

    private boolean configFallbackAllow() {
        return plugin.getConfig().getBoolean("permissions.fallback-allow", true);
    }

    private boolean configOfflineOwnerAllow() {
        return plugin.getConfig().getBoolean("permissions.allow-when-owner-offline", false);
    }

    private boolean configUnownedMachineAllow() {
        return plugin.getConfig().getBoolean("permissions.allow-unowned-machines", false);
    }

    private boolean configSystemAllow() {
        return plugin.getConfig().getBoolean("permissions.allow-system-actions", false);
    }

    

    private boolean validTarget(Location location, Block block, Entity entity) {
        World locationWorld = location == null ? null : location.getWorld();
        if (entity != null) {
            World world = entity.getWorld();
            return world != null && (locationWorld == null || world.equals(locationWorld));
        }
        if (block != null) {
            World world = block.getWorld();
            return world != null && (locationWorld == null || world.equals(locationWorld));
        }
        return locationWorld != null;
    }
}
