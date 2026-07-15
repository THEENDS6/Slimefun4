package cc.theends6.sfx.internal.gps;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.machine.runtime.SfxElectricStack;

public record SfxGpsExtractionResult(
        boolean scanned,
        boolean hasResource,
        SfxElectricStack output
) {
    public static SfxGpsExtractionResult notScanned() {
        return new SfxGpsExtractionResult(false, false, null);
    }

    public static SfxGpsExtractionResult empty() {
        return new SfxGpsExtractionResult(true, false, null);
    }

    public static SfxGpsExtractionResult output(SfxElectricStack output) {
        return new SfxGpsExtractionResult(true, output != null, output);
    }
}
