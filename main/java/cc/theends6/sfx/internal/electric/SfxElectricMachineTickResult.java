package cc.theends6.sfx.internal.electric;

record SfxElectricMachineTickResult(
        SfxElectricMachineRenderStatus status,
        int consumedEnergy,
        int supplementalEnergy,
        boolean changed,
        boolean keepActive
) {
    SfxElectricMachineTickResult(SfxElectricMachineRenderStatus status, int consumedEnergy, boolean changed, boolean keepActive) {
        this(status, consumedEnergy, 0, changed, keepActive);
    }

    static SfxElectricMachineTickResult idle() {
        return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.IDLE, 0, false, false);
    }

    static SfxElectricMachineTickResult status(SfxElectricMachineRenderStatus status, boolean keepActive) {
        return new SfxElectricMachineTickResult(status, 0, false, keepActive);
    }

    static SfxElectricMachineTickResult changed(SfxElectricMachineRenderStatus status, int consumedEnergy, boolean keepActive) {
        return new SfxElectricMachineTickResult(status, Math.max(0, consumedEnergy), true, keepActive);
    }

    static SfxElectricMachineTickResult changed(SfxElectricMachineRenderStatus status, int consumedEnergy, int supplementalEnergy, boolean keepActive) {
        return new SfxElectricMachineTickResult(status, Math.max(0, consumedEnergy), Math.max(0, supplementalEnergy), true, keepActive);
    }
}
