package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.ui.SfxMachineStatusKey;

public enum SfxElectricMachineRenderStatus {
    IDLE(SfxMachineStatusKey.IDLE),
    NO_INPUT(SfxMachineStatusKey.NO_INPUT),
    NO_TARGET(SfxMachineStatusKey.NO_TARGET),
    CHUNK_NOT_SCANNED(SfxMachineStatusKey.CHUNK_NOT_SCANNED),
    NO_GEO_RESOURCE(SfxMachineStatusKey.NO_GEO_RESOURCE),
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

    public SfxMachineStatusKey statusKey() {
        return statusKey;
    }
}
