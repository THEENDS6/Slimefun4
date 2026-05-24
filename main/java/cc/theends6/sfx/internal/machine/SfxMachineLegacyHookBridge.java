package cc.theends6.sfx.internal.machine;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

/** Convenience bridge for legacy services that are being split into framework phases. */
public final class SfxMachineLegacyHookBridge {
    private SfxMachineLegacyHookBridge() {}

    public static void phase(SfxMachineRuntimeEngine runtime, String machineId, SfxMachinePhase phase, UUID instanceId, Location location, String domain, String action, String source) {
        if (runtime == null || machineId == null || phase == null) return;
        Map<String, Object> attributes = SfxMachineFrameworkHookScopes.attributes(domain, action, source);
        runtime.runLegacyPhase(machineId, phase, instanceId, location, new SfxMachineTickContext(0L, 1L, false), SfxMachineStatus.IDLE, attributes);
    }

    public static void place(SfxMachineRuntimeEngine runtime, String machineId, UUID instanceId, Location location, String domain, String source) {
        phase(runtime, machineId, SfxMachinePhase.ON_PLACE, instanceId, location, domain, "place", source);
    }

    public static void interact(SfxMachineRuntimeEngine runtime, String machineId, UUID instanceId, Location location, String domain, String source) {
        phase(runtime, machineId, SfxMachinePhase.ON_INTERACT, instanceId, location, domain, "interact", source);
    }

    public static void menuOpen(SfxMachineRuntimeEngine runtime, String machineId, UUID instanceId, Location location, String domain, String source) {
        phase(runtime, machineId, SfxMachinePhase.ON_MENU_OPEN, instanceId, location, domain, "menu-open", source);
    }

    public static void menuClick(SfxMachineRuntimeEngine runtime, String machineId, UUID instanceId, Location location, String domain, String source) {
        phase(runtime, machineId, SfxMachinePhase.ON_MENU_CLICK, instanceId, location, domain, "menu-click", source);
    }

    public static void menuClose(SfxMachineRuntimeEngine runtime, String machineId, UUID instanceId, Location location, String domain, String source) {
        phase(runtime, machineId, SfxMachinePhase.ON_MENU_CLOSE, instanceId, location, domain, "menu-close", source);
    }

    public static void beforeTransfer(SfxMachineRuntimeEngine runtime, String machineId, UUID instanceId, Location location, String domain, String source) {
        phase(runtime, machineId, SfxMachinePhase.BEFORE_TRANSFER, instanceId, location, domain, "transfer-before", source);
    }

    public static void afterTransfer(SfxMachineRuntimeEngine runtime, String machineId, UUID instanceId, Location location, String domain, String source) {
        phase(runtime, machineId, SfxMachinePhase.AFTER_TRANSFER, instanceId, location, domain, "transfer-after", source);
    }

    public static void beforeNetworkTick(SfxMachineRuntimeEngine runtime, String machineId, UUID instanceId, Location location, String domain, String source) {
        phase(runtime, machineId, SfxMachinePhase.BEFORE_NETWORK_TICK, instanceId, location, domain, "network-before", source);
    }

    public static void afterNetworkTick(SfxMachineRuntimeEngine runtime, String machineId, UUID instanceId, Location location, String domain, String source) {
        phase(runtime, machineId, SfxMachinePhase.AFTER_NETWORK_TICK, instanceId, location, domain, "network-after", source);
    }
}
