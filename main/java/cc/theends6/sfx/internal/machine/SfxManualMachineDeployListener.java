package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxManualMachineDeployListener implements Listener {
    private final JavaPlugin plugin;
    private final DefaultManualMachineRegistry registry;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;

    public SfxManualMachineDeployListener(JavaPlugin plugin, DefaultManualMachineRegistry registry, SfxLocalization localization, SfxBlockDataService blockData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        ManualMachineDefinition definition = resolveDefinition(item);
        if (definition == null) {
            return;
        }
        Block anchor = event.getBlockPlaced();
        BlockFace playerFacing = playerFacing(event.getPlayer());
        BlockFace sideDirection = sideDirection(playerFacing);
        DeploymentPlan plan = deploymentPlan(anchor, definition, sideDirection);
        if (!canDeploy(plan.placements(), anchor, true)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text("machines.deploy-no-space", "<red>空间不足，无法自动部署这台基础机器。</red>")));
            return;
        }
        applyDeployment(plan, definition, sideDirection, playerFacing);
        event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text("machines.deploy-success", "<green>基础机器已自动部署完成。</green>")));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        ManualMachineDefinition definition = resolveDefinition(item);
        if (definition == null) {
            return;
        }
        Material icon = definition.icon();
        if (icon.isBlock()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        if (clicked == null || face == null) {
            return;
        }
        Block anchor = clicked.getRelative(face);
        if (!anchor.isEmpty()) {
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text("machines.deploy-no-space", "<red>空间不足，无法自动部署这台基础机器。</red>")));
            event.setCancelled(true);
            return;
        }
        BlockFace playerFacing = playerFacing(event.getPlayer());
        BlockFace sideDirection = sideDirection(playerFacing);
        DeploymentPlan plan = deploymentPlan(anchor, definition, sideDirection);
        if (!canDeploy(plan.placements(), anchor, false)) {
            event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text("machines.deploy-no-space", "<red>空间不足，无法自动部署这台基础机器。</red>")));
            event.setCancelled(true);
            return;
        }
        applyDeployment(plan, definition, sideDirection, playerFacing);
        consumeOne(event.getPlayer(), event.getHand());
        event.setCancelled(true);
        event.getPlayer().sendMessage(Text.prefixed(plugin, localization.text("machines.deploy-success", "<green>基础机器已自动部署完成。</green>")));
    }

    private ManualMachineDefinition resolveDefinition(ItemStack item) {
        Optional<String> machineId = ManualMachineDeployPacks.readMachineId(plugin, item);
        if (machineId.isEmpty()) {
            return null;
        }
        return registry.machine(machineId.get())
                .or(() -> ExtraDeployStructures.machine(machineId.get()))
                .orElse(null);
    }

    private boolean canDeploy(Map<Block, Material> deployment, Block anchor, boolean allowAnchorOccupied) {
        for (Map.Entry<Block, Material> entry : deployment.entrySet()) {
            Material expected = entry.getValue();
            if (expected == null || expected.isAir()) {
                continue;
            }
            Block block = entry.getKey();
            if (isSfxAnchored(block)) {
                return false;
            }
            if (allowAnchorOccupied && block.equals(anchor)) {
                continue;
            }
            Material actual = block.getType();
            if (actual == expected || actual.isAir()) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean isSfxAnchored(Block block) {
        return block != null && blockData.findAnchor(block.getLocation()).isPresent();
    }

    private DeploymentPlan deploymentPlan(Block anchor, ManualMachineDefinition definition, BlockFace sideDirection) {
        Material[] pattern = definition.pattern();
        int anchorRow = anchorRow(definition, pattern);
        Block center = switch (anchorRow) {
            case 3 -> anchor.getRelative(BlockFace.UP, 2);
            case 2 -> anchor.getRelative(BlockFace.UP);
            case 1 -> anchor;
            case 0 -> anchor.getRelative(BlockFace.DOWN);
            default -> throw new IllegalStateException("Unexpected anchor row: " + anchorRow);
        };
        Map<Block, Material> placements = new LinkedHashMap<>();
        addColumn(placements, center.getRelative(sideDirection), pattern[0], pattern[3], pattern[6]);
        addColumn(placements, center, pattern[1], pattern[4], pattern[7]);
        addColumn(placements, center.getRelative(sideDirection.getOppositeFace()), pattern[2], pattern[5], pattern[8]);
        return new DeploymentPlan(anchor, center, placements);
    }

    private int anchorRow(ManualMachineDefinition definition, Material[] pattern) {
        if (usesVirtualFireBase(definition)) {
            return 2;
        }
        if (hasMaterial(pattern[6], pattern[7], pattern[8])) {
            return 2;
        }
        if (hasMaterial(pattern[3], pattern[4], pattern[5])) {
            return 1;
        }
        return 0;
    }

    private boolean hasMaterial(Material... materials) {
        for (Material material : materials) {
            if (material != null && !material.isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean usesVirtualFireBase(ManualMachineDefinition definition) {
        String id = definition.id();
        return "sf:smeltery".equals(id) || "sf:makeshift_smeltery".equals(id);
    }

    private void addColumn(Map<Block, Material> blocks, Block middle, Material top, Material center, Material bottom) {
        blocks.put(middle.getRelative(BlockFace.UP), top);
        blocks.put(middle, center);
        blocks.put(middle.getRelative(BlockFace.DOWN), bottom);
    }

    private void applyDeployment(DeploymentPlan plan, ManualMachineDefinition definition, BlockFace sideDirection, BlockFace playerFacing) {
        List<Block> placedBlocks = new ArrayList<>();
        for (Map.Entry<Block, Material> entry : plan.placements().entrySet()) {
            Material material = entry.getValue();
            if (material == null || material.isAir()) {
                continue;
            }
            Block block = entry.getKey();
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, itemId, block, material, false, "manual-deploy", "place-structure");
            placedBlocks.add(block);
        }
        for (Block block : placedBlocks) {
            BlockData updated = orientedData(block, plan.center(), definition, sideDirection, playerFacing);
            if (updated != null) {
                cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(null, itemId, block, updated, false, "manual-deploy", "orient-structure");
            }
            clearPlacedBlockName(block);
        }
        Material anchorExpected = plan.placements().get(plan.anchor());
        if (anchorExpected == null || anchorExpected.isAir()) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setType(null, itemId, plan.anchor(), Material.AIR, false, "manual-deploy", "rollback-anchor");
        }
        for (Block block : placedBlocks) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(null, itemId, block, block.getBlockData(), true, "manual-deploy", "rollback-physics");
        }
        if (anchorExpected == null || anchorExpected.isAir()) {
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(null, itemId, plan.anchor(), plan.anchor().getBlockData(), true, "manual-deploy", "rollback-anchor-physics");
        }
    }

    private BlockData orientedData(Block block, Block center, ManualMachineDefinition definition, BlockFace sideDirection, BlockFace playerFacing) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Directional directional)) {
            return null;
        }
        if (!shouldOrient(block.getType())) {
            return null;
        }
        int dx = block.getX() - center.getX();
        int dy = block.getY() - center.getY();
        int dz = block.getZ() - center.getZ();
        BlockFace target = facingFor(block.getType(), dx, dy, dz, definition, sideDirection, playerFacing);
        if (target == null || !directional.getFaces().contains(target)) {
            return null;
        }
        directional.setFacing(target);
        return directional;
    }

    private boolean shouldOrient(Material material) {
        return switch (material) {
            case DISPENSER, DROPPER, PISTON, STICKY_PISTON, FURNACE, BLAST_FURNACE, SMOKER, CHEST -> true;
            default -> false;
        };
    }

    private BlockFace facingFor(Material material, int dx, int dy, int dz, ManualMachineDefinition definition, BlockFace sideDirection, BlockFace playerFacing) {
        if (isIndustrialMiner(definition)) {
            if (material == Material.PISTON || material == Material.STICKY_PISTON) {
                return BlockFace.UP;
            }
            if ((material == Material.BLAST_FURNACE || material == Material.FURNACE) && dx == 0 && dz == 0 && dy < 0) {
                return playerFacing.getOppositeFace();
            }
            if (material == Material.CHEST && dx == 0 && dz == 0 && dy == 0) {
                return playerFacing.getOppositeFace();
            }
        }
        if (dy < 0) {
            return BlockFace.UP;
        }
        if (dy > 0) {
            return BlockFace.DOWN;
        }
        if (dx == 0 && dz == 0) {
            if (definition.inventoryFace() == BlockFace.DOWN || definition.triggerFace() == BlockFace.DOWN) {
                return BlockFace.UP;
            }
            if (definition.inventoryFace() == BlockFace.UP || definition.triggerFace() == BlockFace.UP) {
                return BlockFace.DOWN;
            }
            return null;
        }
        if (dx == sideDirection.getModX() && dz == sideDirection.getModZ()) {
            return sideDirection.getOppositeFace();
        }
        if (dx == -sideDirection.getModX() && dz == -sideDirection.getModZ()) {
            return sideDirection;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return dz > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    private boolean isIndustrialMiner(ManualMachineDefinition definition) {
        return "sf:industrial_miner".equals(definition.id()) || "sf:advanced_industrial_miner".equals(definition.id());
    }

    private void consumeOne(Player player, EquipmentSlot hand) {
        if (player == null || hand == null || player.getGameMode() == GameMode.CREATIVE) {
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
            return;
        }
        current.setAmount(next);
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(current);
        } else {
            player.getInventory().setItemInMainHand(current);
        }
    }

    private BlockFace sideDirection(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            case WEST -> BlockFace.SOUTH;
            default -> BlockFace.NORTH;
        };
    }

    private BlockFace playerFacing(Player player) {
        return player == null ? BlockFace.NORTH : player.getFacing();
    }

    private void clearPlacedBlockName(Block block) {
        BlockState state = block.getState();
        if (state instanceof Nameable nameable) {
            nameable.customName((Component) null);
            state.update(true, false);
        }
    }

    private record DeploymentPlan(Block anchor, Block center, Map<Block, Material> placements) {
    }
}
