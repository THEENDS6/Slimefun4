package cc.theends6.sfx.internal.android;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxWorldMutationBridge;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;


final class SfxAndroidBlockAppearance {
    private SfxAndroidBlockAppearance() {
    }

    static void apply(SfxMachineRuntimeEngine machineRuntime, SfxItemRegistry itemRegistry, Block block, String typeId, BlockFace rotation) {
        if (block == null || typeId == null) {
            return;
        }
        SfxWorldMutationBridge.setType(machineRuntime, typeId, block, Material.PLAYER_HEAD, false, "android", "applyHeadRotation");
        applyRotation(machineRuntime, typeId, block, rotation);
        SfxItemDefinition definition = itemRegistry.item(typeId).orElse(null);
        if (definition != null && block.getState() instanceof Skull skull) {
            HeadTextures.apply(skull, definition.headTextureHash());
        }
    }

    static void applyRotation(SfxMachineRuntimeEngine machineRuntime, String typeId, Block block, BlockFace rotation) {
        if (block == null || !(block.getBlockData() instanceof Rotatable rotatable)) {
            return;
        }
        BlockFace visualRotation = rotation == null ? BlockFace.NORTH : rotation.getOppositeFace();
        rotatable.setRotation(visualRotation);
        SfxWorldMutationBridge.setBlockData(machineRuntime, typeId, block, rotatable, false, "android", "applyRotation");
    }

    static BlockFace facingFromPlayer(Player player) {
        return player == null ? BlockFace.NORTH : player.getFacing();
    }

    static BlockFace turnLeft(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.NORTH;
        };
    }

    static BlockFace turnRight(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.NORTH;
        };
    }

    static void dropInventory(Block block) {
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return;
        }
        Inventory inventory = holder.getInventory();
        for (ItemStack stack : inventory.getContents()) {
            SfxBlockDrops.dropItem(block, stack);
        }
        inventory.clear();
    }
}
