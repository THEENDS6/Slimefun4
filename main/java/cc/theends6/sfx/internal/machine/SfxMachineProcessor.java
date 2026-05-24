package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.core.SfxResult;

public interface SfxMachineProcessor {
    String machineId();

    SfxResult<SfxMachineStatus> tick(SfxMachineRuntimeContext context, SfxMachineState state);
}
