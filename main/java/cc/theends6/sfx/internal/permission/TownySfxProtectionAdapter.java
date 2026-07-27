package cc.theends6.sfx.internal.permission;

import cc.theends6.sfx.api.world.SfxProtectionAdapter;
import cc.theends6.sfx.api.world.SfxProtectionDecision;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;




final class TownySfxProtectionAdapter implements SfxProtectionAdapter {
    private enum Action {
        BUILD,
        DESTROY,
        SWITCH,
        ITEM_USE
    }

    private final JavaPlugin plugin;
    private final Method getCachePermission;
    private final Map<Action, Object> actionTypes;
    private boolean failureLogged;

    private TownySfxProtectionAdapter(
            JavaPlugin plugin,
            Method getCachePermission,
            Map<Action, Object> actionTypes
    ) {
        this.plugin = plugin;
        this.getCachePermission = getCachePermission;
        this.actionTypes = actionTypes;
    }

    static SfxProtectionAdapter create(JavaPlugin plugin) {
        Plugin towny = plugin.getServer().getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            return null;
        }
        try {
            ClassLoader loader = towny.getClass().getClassLoader();
            Class<?> util = Class.forName(
                    "com.palmergames.bukkit.towny.utils.PlayerCacheUtil", true, loader);
            Class<?> actionType = Class.forName(
                    "com.palmergames.bukkit.towny.object.TownyPermission$ActionType", true, loader);
            Method permission = util.getMethod(
                    "getCachePermission", Player.class, Location.class, Material.class, actionType);
            Map<Action, Object> values = new EnumMap<>(Action.class);
            for (Action action : Action.values()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object value = Enum.valueOf((Class<? extends Enum>) actionType.asSubclass(Enum.class), action.name());
                values.put(action, value);
            }
            return new TownySfxProtectionAdapter(plugin, permission, Map.copyOf(values));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Towny is installed but its protection API could not be linked: "
                    + exception.getMessage());
            return null;
        }
    }

    @Override
    public SfxProtectionDecision canBreak(Player player, Block block) {
        return check(player, block.getLocation(), block.getType(), Action.DESTROY);
    }

    @Override
    public SfxProtectionDecision canPlace(Player player, Block block, ItemStack item) {
        Material material = item == null || item.getType().isAir() ? block.getType() : item.getType();
        return check(player, block.getLocation(), material, Action.BUILD);
    }

    @Override
    public SfxProtectionDecision canInteract(Player player, Block block) {
        return check(player, block.getLocation(), block.getType(), Action.SWITCH);
    }

    @Override
    public SfxProtectionDecision canUseItem(Player player, Location location, ItemStack item) {
        Material material = item == null ? Material.AIR : item.getType();
        return check(player, location, material, Action.ITEM_USE);
    }

    private SfxProtectionDecision check(Player player, Location location, Material material, Action action) {
        try {
            boolean allowed = (boolean) getCachePermission.invoke(
                    null, player, location, material, actionTypes.get(action));
            return allowed ? SfxProtectionDecision.ALLOW : SfxProtectionDecision.DENY;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!failureLogged) {
                failureLogged = true;
                plugin.getLogger().warning("Towny protection check failed; falling back to SFX policy: "
                        + exception.getMessage());
            }
            return SfxProtectionDecision.PASS;
        }
    }
}
