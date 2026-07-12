package cc.theends6.sfx.api.behavior;

import cc.theends6.sfx.api.item.SfxItems;
import org.bukkit.plugin.java.JavaPlugin;

public record SfxEnergyGeneratorProviderContext(
        JavaPlugin plugin,
        SfxItems items
) {
    public SfxEnergyGeneratorProviderContext {
        if (plugin == null || items == null) {
            throw new IllegalArgumentException("Energy generator provider context values must not be null.");
        }
    }
}
