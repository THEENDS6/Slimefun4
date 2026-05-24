package cc.theends6.sfx.internal.machine;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;








public final class SfxWorldMutationBridge {
    private SfxWorldMutationBridge() {}

    public static void setType(SfxMachineRuntimeEngine runtime, String machineId, Block block, Material material, String domain, String source) {
        setType(runtime, machineId, block, material, true, domain, source);
    }

    public static void setType(SfxMachineRuntimeEngine runtime, String machineId, Block block, Material material, boolean applyPhysics, String domain, String source) {
        if (block == null || material == null) {
            return;
        }
        run(runtime, machineId, SfxMachinePhase.BEFORE_WORLD_MUTATION, block, domain, "set-type", source);
        block.setType(material, applyPhysics);
        run(runtime, machineId, SfxMachinePhase.AFTER_WORLD_MUTATION, block, domain, "set-type", source);
    }


    public static void setBlockData(SfxMachineRuntimeEngine runtime, String machineId, Block block, BlockData blockData, boolean applyPhysics, String domain, String source) {
        if (block == null || blockData == null) {
            return;
        }
        run(runtime, machineId, SfxMachinePhase.BEFORE_WORLD_MUTATION, block, domain, "set-block-data", source);
        block.setBlockData(blockData, applyPhysics);
        run(runtime, machineId, SfxMachinePhase.AFTER_WORLD_MUTATION, block, domain, "set-block-data", source);
    }


    public static boolean breakNaturally(SfxMachineRuntimeEngine runtime, String machineId, Block block, ItemStack tool, String domain, String source) {
        if (block == null) {
            return false;
        }
        run(runtime, machineId, SfxMachinePhase.BEFORE_WORLD_MUTATION, block, domain, "break-naturally", source);
        boolean broken = tool == null ? block.breakNaturally() : block.breakNaturally(tool);
        run(runtime, machineId, SfxMachinePhase.AFTER_WORLD_MUTATION, block, domain, "break-naturally", source);
        return broken;
    }

    private static void run(SfxMachineRuntimeEngine runtime, String machineId, SfxMachinePhase phase, Block block, String domain, String action, String source) {
        if (runtime == null || machineId == null || phase == null) {
            return;
        }
        Map<String, Object> attributes = SfxMachineFrameworkHookScopes.attributes(domain, action, source);
        runtime.runLegacyPhase(machineId, phase, null, block == null ? null : block.getLocation(), new SfxMachineTickContext(0L, 1L, false), SfxMachineStatus.IDLE, attributes);
    }
}
