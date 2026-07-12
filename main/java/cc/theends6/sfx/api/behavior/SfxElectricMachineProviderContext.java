package cc.theends6.sfx.api.behavior;

import cc.theends6.sfx.api.item.SfxItems;
import org.bukkit.plugin.java.JavaPlugin;

public record SfxElectricMachineProviderContext(
        JavaPlugin plugin,
        SfxItems items,
        SfxAreaMachineRuntime areaMachines
) {
    public SfxElectricMachineProviderContext {
        if (plugin == null || items == null || areaMachines == null) {
            throw new IllegalArgumentException("Electric provider context values must not be null.");
        }
    }
}
