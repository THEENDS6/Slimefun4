package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.ui.SfxMachineStatusKey;

enum SfxElectricMachineRenderStatus {
    IDLE(SfxMachineStatusKey.IDLE),
    NO_INPUT(SfxMachineStatusKey.IDLE),
    NO_TARGET(SfxMachineStatusKey.NO_TARGET),
    NO_RECIPE(SfxMachineStatusKey.NO_RECIPE),
    NO_BLAZE_FUEL(SfxMachineStatusKey.MISSING_RESOURCE),
    NO_BREWING_INGREDIENT(SfxMachineStatusKey.MISSING_RESOURCE),
    NO_POTION(SfxMachineStatusKey.MISSING_RESOURCE),
    WORKING(SfxMachineStatusKey.WORKING),
    BLOCKED_OUTPUT(SfxMachineStatusKey.BLOCKED_OUTPUT),
    OUTPUT_FULL(SfxMachineStatusKey.OUTPUT_FULL),
    NO_POWER(SfxMachineStatusKey.NO_POWER),
    PAUSED(SfxMachineStatusKey.PAUSED),
    OVERLAPPING_AREA(SfxMachineStatusKey.AREA_CONFLICT);

    private final SfxMachineStatusKey statusKey;

    SfxElectricMachineRenderStatus(SfxMachineStatusKey statusKey) {
        this.statusKey = statusKey;
    }

    SfxMachineStatusKey statusKey() {
        return statusKey;
    }
}
