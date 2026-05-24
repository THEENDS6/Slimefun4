package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.core.SfxResult;

/**
 * Default processor registered for every machine catalog entry that is still
 * implemented by an existing domain service. This keeps the shared framework
 * authoritative for discovery/lifecycle/status without forcing all legacy
 * machine rules to be reimplemented in one migration step.
 */
public final class SfxDefaultMachineProcessor implements SfxMachineProcessor {
    private final String machineId;

    public SfxDefaultMachineProcessor(String machineId) {
        this.machineId = machineId;
    }

    @Override
    public String machineId() {
        return machineId;
    }

    @Override
    public SfxResult<SfxMachineStatus> tick(SfxMachineRuntimeContext context, SfxMachineState state) {
        return SfxResult.ok(state == null ? SfxMachineStatus.IDLE : state.status());
    }
}
