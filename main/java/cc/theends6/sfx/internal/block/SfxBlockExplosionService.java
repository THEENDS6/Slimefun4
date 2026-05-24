package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxWorldMutationBridge;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Handles explosion and wither-block-change semantics for anchored SFX blocks.
 *
 * <p>This keeps destructive world-interaction policy outside the Bukkit event listener and routes all
 * anchored destruction through {@link SfxBlockLifecycleRouter}. The listener should only translate
 * Bukkit events into lifecycle requests.</p>
 */
public final class SfxBlockExplosionService {
    private final SfxBlockDataService blockData;
    private final SfxBlockLifecycleRouter lifecycleRouter;
    private final SfxMachineRuntimeEngine machineRuntime;

    public SfxBlockExplosionService(
            SfxBlockDataService blockData,
            SfxBlockLifecycleRouter lifecycleRouter,
            SfxMachineRuntimeEngine machineRuntime
    ) {
        this.blockData = blockData;
        this.lifecycleRouter = lifecycleRouter;
        this.machineRuntime = machineRuntime;
    }

    public void handleEntityExplode(EntityExplodeEvent event) {
        destroyExplodedBlocks(event.blockList(), event.getLocation(), isWitherExplosion(event.getEntity()));
    }

    public void handleBlockExplode(BlockExplodeEvent event) {
        destroyExplodedBlocks(event.blockList(), event.getBlock().getLocation().add(0.5, 0.5, 0.5), false);
    }

    public void handleEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        SfxAnchorRecord anchor = blockData.findAnchor(block.getLocation()).orElse(null);
        if (anchor == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        event.setCancelled(true);
        if (instance == null) {
            blockData.unregisterAt(block.getLocation());
            SfxWorldMutationBridge.setType(machineRuntime, "sf:unknown", block, Material.AIR, false, "block-lifecycle", "entity-change:orphan");
            return;
        }
        if (!isWitherExplosion(event.getEntity())) {
            return;
        }
        if (isWitherProof(instance.typeId())) {
            return;
        }
        lifecycleRouter.destroyAnchoredBlock(block, instance.instanceId(), instance.typeId());
        SfxWorldMutationBridge.setType(machineRuntime, instance.typeId(), block, Material.AIR, false, "block-lifecycle", "entity-change:wither");
    }

    private void destroyExplodedBlocks(java.util.List<Block> blocks, Location explosionCenter, boolean witherExplosion) {
        java.util.List<Block> targets = new java.util.ArrayList<>(blocks);
        for (Block block : targets) {
            SfxAnchorRecord anchor = blockData.findAnchor(block.getLocation()).orElse(null);
            SfxBlockInstanceRecord instance = anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance != null && isExplosionProtected(instance.typeId(), witherExplosion)) {
                blocks.remove(block);
                continue;
            }
            if (isExplosionRayBlocked(explosionCenter, block, witherExplosion)) {
                blocks.remove(block);
                continue;
            }
            if (anchor == null) {
                continue;
            }
            blocks.remove(block);
            if (instance == null) {
                blockData.unregisterAt(block.getLocation());
                SfxWorldMutationBridge.setType(machineRuntime, "sf:unknown", block, Material.AIR, false, "block-lifecycle", "explode:orphan");
                continue;
            }
            lifecycleRouter.destroyAnchoredBlock(block, instance.instanceId(), instance.typeId());
            SfxWorldMutationBridge.setType(machineRuntime, instance.typeId(), block, Material.AIR, false, "block-lifecycle", "explode:destroy");
        }
    }

    private boolean isExplosionRayBlocked(Location explosionCenter, Block target, boolean witherExplosion) {
        if (explosionCenter == null || explosionCenter.getWorld() == null || target.getWorld() == null
                || !explosionCenter.getWorld().equals(target.getWorld())) {
            return false;
        }
        Location targetCenter = target.getLocation().add(0.5, 0.5, 0.5);
        double dx = targetCenter.getX() - explosionCenter.getX();
        double dy = targetCenter.getY() - explosionCenter.getY();
        double dz = targetCenter.getZ() - explosionCenter.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 0.0D) {
            return false;
        }
        double step = 0.2D;
        int steps = Math.max(1, (int) Math.ceil(distance / step));
        int targetX = target.getX();
        int targetY = target.getY();
        int targetZ = target.getZ();
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int x = floorBlockCoordinate(explosionCenter.getX() + dx * t);
            int y = floorBlockCoordinate(explosionCenter.getY() + dy * t);
            int z = floorBlockCoordinate(explosionCenter.getZ() + dz * t);
            if (x == targetX && y == targetY && z == targetZ) {
                break;
            }
            if (x == lastX && y == lastY && z == lastZ) {
                continue;
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            Block rayBlock = explosionCenter.getWorld().getBlockAt(x, y, z);
            SfxAnchorRecord anchor = blockData.findAnchor(rayBlock.getLocation()).orElse(null);
            if (anchor == null) {
                continue;
            }
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance != null && blocksExplosionRay(instance.typeId(), witherExplosion)) {
                return true;
            }
        }
        return false;
    }

    private int floorBlockCoordinate(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private boolean isExplosionProtected(String typeId, boolean witherExplosion) {
        if (typeId == null) {
            return false;
        }
        if (isWitherProof(typeId)) {
            return true;
        }
        return !witherExplosion && typeId.equals("sf:hardened_glass");
    }

    private boolean blocksExplosionRay(String typeId, boolean witherExplosion) {
        if (typeId == null) {
            return false;
        }
        if (isWitherProof(typeId)) {
            return true;
        }
        return !witherExplosion && typeId.equals("sf:hardened_glass");
    }

    private boolean isWitherProof(String typeId) {
        return typeId.equals("sf:wither_proof_obsidian")
                || typeId.equals("sf:wither_proof_glass")
                || typeId.equals("sf:wither_assembler");
    }

    private boolean isWitherExplosion(Entity entity) {
        return entity instanceof Wither || entity instanceof WitherSkull;
    }
}
