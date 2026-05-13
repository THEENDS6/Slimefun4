package cc.theends6.sfx.internal.electric;

record SfxElectricMachineTickResult(
        SfxElectricMachineRenderStatus status,
        int consumedEnergy,
        boolean changed,
        boolean keepActive
) {
    static SfxElectricMachineTickResult idle() {
        return new SfxElectricMachineTickResult(SfxElectricMachineRenderStatus.IDLE, 0, false, false);
    }

    static SfxElectricMachineTickResult status(SfxElectricMachineRenderStatus status, boolean keepActive) {
        return new SfxElectricMachineTickResult(status, 0, false, keepActive);
    }

    static SfxElectricMachineTickResult changed(SfxElectricMachineRenderStatus status, int consumedEnergy, boolean keepActive) {
        return new SfxElectricMachineTickResult(status, Math.max(0, consumedEnergy), true, keepActive);
    }
}
