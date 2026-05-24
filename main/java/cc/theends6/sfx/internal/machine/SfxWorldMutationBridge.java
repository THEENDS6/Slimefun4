package cc.theends6.sfx.internal.machine;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;








public final class SfxWorldMutationBridge {
    private static final AtomicReference<SfxMachineRuntimeEngine> DEFAULT_RUNTIME = new AtomicReference<>();

    private SfxWorldMutationBridge() {}

    public static void bindDefaultRuntime(SfxMachineRuntimeEngine runtime) {
        if (runtime != null) {
            DEFAULT_RUNTIME.set(runtime);
        }
    }

    public static void clearDefaultRuntime(SfxMachineRuntimeEngine runtime) {
        if (runtime == null || DEFAULT_RUNTIME.get() == runtime) {
            DEFAULT_RUNTIME.compareAndSet(DEFAULT_RUNTIME.get(), null);
        }
    }

    public static boolean setType(SfxMachineRuntimeEngine runtime, String machineId, Block block, Material material, String domain, String source) {
        return setType(runtime, machineId, block, material, true, domain, source);
    }

    public static boolean setType(SfxMachineRuntimeEngine runtime, String machineId, Block block, Material material, boolean applyPhysics, String domain, String source) {
        if (block == null || material == null) {
            return false;
        }
        BlockData previous = block.getBlockData().clone();
        SfxMachinePhaseResult before = run(runtime, machineId, SfxMachinePhase.BEFORE_WORLD_MUTATION, block, domain, "set-type", source);
        if (vetoesMutation(before)) {
            return false;
        }
        try {
            block.setType(material, applyPhysics);
        } catch (RuntimeException exception) {
            restore(block, previous);
            runError(runtime, machineId, block, domain, "set-type", source, exception);
            return false;
        }
        SfxMachinePhaseResult after = run(runtime, machineId, SfxMachinePhase.AFTER_WORLD_MUTATION, block, domain, "set-type", source);
        if (requiresRollback(after)) {
            restore(block, previous);
            return false;
        }
        return true;
    }

    public static boolean setBlockData(SfxMachineRuntimeEngine runtime, String machineId, Block block, BlockData blockData, boolean applyPhysics, String domain, String source) {
        if (block == null || blockData == null) {
            return false;
        }
        BlockData previous = block.getBlockData().clone();
        SfxMachinePhaseResult before = run(runtime, machineId, SfxMachinePhase.BEFORE_WORLD_MUTATION, block, domain, "set-block-data", source);
        if (vetoesMutation(before)) {
            return false;
        }
        try {
            block.setBlockData(blockData, applyPhysics);
        } catch (RuntimeException exception) {
            restore(block, previous);
            runError(runtime, machineId, block, domain, "set-block-data", source, exception);
            return false;
        }
        SfxMachinePhaseResult after = run(runtime, machineId, SfxMachinePhase.AFTER_WORLD_MUTATION, block, domain, "set-block-data", source);
        if (requiresRollback(after)) {
            restore(block, previous);
            return false;
        }
        return true;
    }

    public static boolean breakNaturally(SfxMachineRuntimeEngine runtime, String machineId, Block block, ItemStack tool, String domain, String source) {
        if (block == null) {
            return false;
        }
        BlockData previous = block.getBlockData().clone();
        SfxMachinePhaseResult before = run(runtime, machineId, SfxMachinePhase.BEFORE_WORLD_MUTATION, block, domain, "break-naturally", source);
        if (vetoesMutation(before)) {
            return false;
        }
        boolean broken;
        try {
            broken = tool == null ? block.breakNaturally() : block.breakNaturally(tool);
        } catch (RuntimeException exception) {
            restore(block, previous);
            runError(runtime, machineId, block, domain, "break-naturally", source, exception);
            return false;
        }
        SfxMachinePhaseResult after = run(runtime, machineId, SfxMachinePhase.AFTER_WORLD_MUTATION, block, domain, "break-naturally", source);
        if (requiresRollback(after)) {
            restore(block, previous);
            return false;
        }
        return broken;
    }

    private static SfxMachineRuntimeEngine effective(SfxMachineRuntimeEngine runtime) {
        return runtime == null ? DEFAULT_RUNTIME.get() : runtime;
    }

    private static SfxMachinePhaseResult run(SfxMachineRuntimeEngine runtime, String machineId, SfxMachinePhase phase, Block block, String domain, String action, String source) {
        SfxMachineRuntimeEngine effectiveRuntime = effective(runtime);
        if (effectiveRuntime == null || machineId == null || phase == null) {
            return SfxMachinePhaseResult.cont();
        }
        Map<String, Object> attributes = SfxMachineFrameworkHookScopes.attributes(domain, action, source);
        if (block != null) {
            attributes.put("framework.world.location", block.getLocation());
            attributes.put("framework.world.material", block.getType().name());
        }
        return effectiveRuntime.runLegacyPhase(machineId, phase, null, block == null ? null : block.getLocation(), new SfxMachineTickContext(0L, 1L, false), SfxMachineStatus.IDLE, attributes);
    }

    private static void runError(SfxMachineRuntimeEngine runtime, String machineId, Block block, String domain, String action, String source, RuntimeException exception) {
        SfxMachineRuntimeEngine effectiveRuntime = effective(runtime);
        if (effectiveRuntime == null || machineId == null) {
            return;
        }
        Map<String, Object> attributes = SfxMachineFrameworkHookScopes.attributes(domain, action, source);
        attributes.put("framework.world.exception", exception);
        effectiveRuntime.runLegacyPhase(machineId, SfxMachinePhase.ON_ERROR, null, block == null ? null : block.getLocation(), new SfxMachineTickContext(0L, 1L, false), SfxMachineStatus.ERROR, attributes);
    }

    private static boolean vetoesMutation(SfxMachinePhaseResult result) {
        return result != null && result.stopsPipeline();
    }

    private static boolean requiresRollback(SfxMachinePhaseResult result) {
        if (result == null || result.action() == null) {
            return false;
        }
        return result.action() == SfxMachinePhaseResult.Action.ROLLBACK || result.action() == SfxMachinePhaseResult.Action.FAILED;
    }

    private static void restore(Block block, BlockData previous) {
        if (block != null && previous != null) {
            block.setBlockData(previous, false);
        }
    }
}
