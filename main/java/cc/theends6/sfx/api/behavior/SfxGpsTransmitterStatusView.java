package cc.theends6.sfx.api.behavior;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record SfxGpsTransmitterStatusView(
        String titleKey,
        String backgroundKey,
        Material backgroundMaterial,
        int statusSlot,
        Material onlineMaterial,
        Material offlineMaterial,
        String statusNameKey,
        String onlineLoreKey,
        String offlineLoreKey,
        List<Line> statusLore,
        int infoSlot,
        Material infoFallbackMaterial,
        String infoNameKey,
        List<Line> infoLore
) {
    public record Line(String key, Map<String, Object> placeholders) {
        public Line {
            placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        }
    }
}
