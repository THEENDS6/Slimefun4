package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.core.SfxResult;







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
