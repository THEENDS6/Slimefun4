package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.energy.runtime.*;

import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxEnergyDefinitions {
    private SfxEnergyDefinitions() {
    }

    public static Map<String, SfxEnergyComponentDefinition> create(JavaPlugin plugin) {
        Map<String, SfxEnergyComponentDefinition> definitions = new SfxEnergyComponentYamlLoader(plugin).load();
        if (definitions.isEmpty()) {
            throw new IllegalStateException("No compiled energy component definitions were loaded; Java fallback definitions are disabled.");
        }
        return definitions;
    }
}
