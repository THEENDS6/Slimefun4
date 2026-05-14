package cc.theends6.sfx.internal.electric;

enum SfxElectricMachineRenderStatus {
    IDLE,
    NO_INPUT,
    NO_TARGET,
    NO_RECIPE,
    WORKING,
    BLOCKED_OUTPUT,
    OUTPUT_FULL,
    NO_POWER,
    PAUSED,
    OVERLAPPING_AREA
}
