package cc.theends6.sfx.internal.gps;

import org.bukkit.Location;

public final class SfxGpsElectricBridge {
    private static volatile SfxGpsService service;

    private SfxGpsElectricBridge() {
    }

    static void bind(SfxGpsService gpsService) {
        service = gpsService;
    }

    static void unbind(SfxGpsService gpsService) {
        if (service == gpsService) {
            service = null;
        }
    }

    public static SfxGpsExtractionResult peekExtraction(Location location, boolean oilOnly) {
        SfxGpsService gps = service;
        return gps == null ? SfxGpsExtractionResult.notScanned() : gps.peekExtraction(location, oilOnly);
    }

    public static SfxGpsExtractionResult consumeExtraction(Location location, boolean oilOnly) {
        SfxGpsService gps = service;
        return gps == null ? SfxGpsExtractionResult.notScanned() : gps.consumeExtraction(location, oilOnly);
    }
}
