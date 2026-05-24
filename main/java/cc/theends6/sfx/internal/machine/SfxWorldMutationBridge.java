package cc.theends6.sfx.internal.machine;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Central world-mutation bridge used while legacy services are being split.
 *
 * <p>Business code should request mutations through this bridge instead of calling Block#setType
 * directly, so framework observers can audit and later replace mutation semantics with prepared
 * transactions.</p>
 */
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

    private static void run(SfxMachineRuntimeEngine runtime, String machineId, SfxMachinePhase phase, Block block, String domain, String action, String source) {
        if (runtime == null || machineId == null || phase == null) {
            return;
        }
        Map<String, Object> attributes = SfxMachineFrameworkHookScopes.attributes(domain, action, source);
        runtime.runLegacyPhase(machineId, phase, null, block == null ? null : block.getLocation(), new SfxMachineTickContext(0L, 1L, false), SfxMachineStatus.IDLE, attributes);
    }
}
