package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;




public final class SfxAnchoredBlockEnvironmentalGuard {
    private static final BlockFace[] HORIZONTAL_FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final SfxBlockDataService blockData;
    private final SfxRuntime runtime;

    public SfxAnchoredBlockEnvironmentalGuard(SfxBlockDataService blockData, SfxRuntime runtime) {
        this.blockData = blockData;
        this.runtime = runtime;
    }

    public void handlePistonExtend(BlockPistonExtendEvent event) {
        if (touchesAnchoredBlock(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    public void handlePistonRetract(BlockPistonRetractEvent event) {
        if (touchesAnchoredBlock(event.getBlocks(), event.getDirection().getOppositeFace())) {
            event.setCancelled(true);
        }
    }

    public void handleFluidFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (blockData.findAnchor(to.getLocation()).isPresent()) {
            event.setCancelled(true);
            scheduleWaterSourceCheckAbove(to);
            return;
        }
        if (blockData.findAnchor(to.getRelative(BlockFace.DOWN).getLocation()).isPresent()) {
            scheduleWaterSourceCheck(to);
        }
    }

    public void handleBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (blockData.findAnchor(block.getRelative(BlockFace.DOWN).getLocation()).isPresent()) {
            scheduleWaterSourceCheck(block);
        } else if (blockData.findAnchor(block.getLocation()).isPresent()) {
            if (isSkullBlock(block.getType())) {
                event.setCancelled(true);
            }
            scheduleWaterSourceCheckAbove(block);
        }
    }

    public void handleBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (blockData.findAnchor(target.getLocation()).isPresent()) {
            event.setCancelled(true);
            scheduleWaterSourceCheckAbove(target);
            return;
        }
        if (blockData.findAnchor(target.getRelative(BlockFace.DOWN).getLocation()).isPresent()) {
            scheduleWaterSourceCheck(target);
        }
    }

    public void handleSpongeAbsorb(SpongeAbsorbEvent event) {
        event.getBlocks().removeIf(state -> blockData.findAnchor(state.getLocation()).isPresent());
    }

    public void cancelIfAnchored(Cancellable event, Block block) {
        if (block != null && blockData.findAnchor(block.getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    private boolean isSkullBlock(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD;
    }

    private void scheduleWaterSourceCheckAbove(Block anchoredBlock) {
        scheduleWaterSourceCheck(anchoredBlock.getRelative(BlockFace.UP));
    }

    private void scheduleWaterSourceCheck(Block block) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        Location location = block.getLocation();
        runtime.executeAtLater(location, 1L, () -> normalizeWaterSourceAboveAnchoredBlock(block));
    }

    private void normalizeWaterSourceAboveAnchoredBlock(Block block) {
        if (block == null || block.getType() != Material.WATER) {
            return;
        }
        if (blockData.findAnchor(block.getRelative(BlockFace.DOWN).getLocation()).isEmpty()) {
            return;
        }
        if (horizontalWaterSourceCount(block) < 2) {
            return;
        }
        if (block.getBlockData() instanceof Levelled levelled && levelled.getLevel() != 0) {
            levelled.setLevel(0);
            cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.setBlockData(null, "sf:environment", block, levelled, false, "block-environment", "normalize-water-source");
        }
    }

    private int horizontalWaterSourceCount(Block block) {
        int count = 0;
        for (BlockFace face : HORIZONTAL_FACES) {
            Block relative = block.getRelative(face);
            if (relative.getType() == Material.WATER
                    && relative.getBlockData() instanceof Levelled levelled
                    && levelled.getLevel() == 0) {
                count++;
            }
        }
        return count;
    }

    private boolean touchesAnchoredBlock(java.util.List<Block> movedBlocks, BlockFace moveDirection) {
        for (Block block : movedBlocks) {
            if (blockData.findAnchor(block.getLocation()).isPresent()) {
                return true;
            }
            if (blockData.findAnchor(block.getRelative(moveDirection).getLocation()).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
