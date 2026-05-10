package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxManualMachineDeployListener implements Listener {
    private final JavaPlugin plugin;
    private final DefaultManualMachineRegistry registry;
    private final SfxLocalization localization;

    public SfxManualMachineDeployListener(JavaPlugin plugin, DefaultManualMachineRegistry registry, SfxLocalization localization) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Optional<String> machineId = ManualMachineDeployPacks.readMachineId(plugin, item);
        if (machineId.isEmpty()) {
            return;
        }
        ManualMachineDefinition definition = registry.machine(machineId.get()).orElse(null);
        if (definition == null) {
            return;
        }
        event.setCancelled(true);
        Block center = event.getBlockPlaced();
        BlockFace sideDirection = sideDirection(event.getPlayer());
        Map<Block, Material> deployment = deployment(center, definition, sideDirection);
        if (!canDeploy(deployment)) {
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text("machines.deploy-no-space", "<red>空间不足，无法自动部署这台基础机器。</red>")));
            return;
        }
        consumeOne(event.getPlayer(), event.getHand());
        for (Map.Entry<Block, Material> entry : deployment.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isAir()) {
                continue;
            }
            entry.getKey().setType(entry.getValue(), false);
        }
        Player player = event.getPlayer();
        player.sendMessage(Text.prefixed(plugin, localization.text("machines.deploy-success", "<green>基础机器已自动部署完成。</green>")));
    }

    private boolean canDeploy(Map<Block, Material> deployment) {
        for (Map.Entry<Block, Material> entry : deployment.entrySet()) {
            Material expected = entry.getValue();
            if (expected == null || expected.isAir()) {
                continue;
            }
            Material actual = entry.getKey().getType();
            if (actual == expected) {
                continue;
            }
            if (!actual.isAir()) {
                return false;
            }
        }
        return true;
    }

    private Map<Block, Material> deployment(Block center, ManualMachineDefinition definition, BlockFace sideDirection) {
        Material[] pattern = definition.pattern();
        Map<Block, Material> blocks = new LinkedHashMap<>();
        addColumn(blocks, center.getRelative(sideDirection), pattern[0], pattern[3], pattern[6]);
        addColumn(blocks, center, pattern[1], pattern[4], pattern[7]);
        addColumn(blocks, center.getRelative(sideDirection.getOppositeFace()), pattern[2], pattern[5], pattern[8]);
        return blocks;
    }

    private void addColumn(Map<Block, Material> blocks, Block base, Material top, Material middle, Material bottom) {
        blocks.put(base.getRelative(BlockFace.UP), top);
        blocks.put(base, middle);
        blocks.put(base.getRelative(BlockFace.DOWN), bottom);
    }

    private void consumeOne(Player player, EquipmentSlot hand) {
        if (player == null || hand == null) {
            return;
        }
        ItemStack current = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (current == null || current.getType().isAir()) {
            return;
        }
        int next = current.getAmount() - 1;
        if (next <= 0) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            current.setAmount(next);
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(current);
            } else {
                player.getInventory().setItemInMainHand(current);
            }
        }
    }

    private BlockFace sideDirection(Player player) {
        BlockFace facing = player == null ? BlockFace.NORTH : player.getFacing();
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.NORTH;
        };
    }
}
