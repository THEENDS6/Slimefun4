package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxCargoDefinitions {
    private static final String RESOURCE_PATH = "content/machines/cargo-components.yml";

    private SfxCargoDefinitions() {
    }

    public static Map<String, SfxCargoComponentDefinition> load(JavaPlugin plugin) {
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        ConfigurationSection root = yaml.getConfigurationSection("cargo-components");
        if (root == null) {
            throw new IllegalStateException("No cargo-components section in " + RESOURCE_PATH + ".");
        }
        Map<String, SfxCargoComponentDefinition> definitions = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                throw new IllegalStateException("Invalid cargo component entry " + id + " in " + RESOURCE_PATH + ".");
            }
            String rawType = section.getString("type", "").trim();
            if (rawType.isBlank()) {
                throw new IllegalStateException("Cargo component " + id + " is missing type in " + RESOURCE_PATH + ".");
            }
            SfxCargoComponentType type = SfxCargoComponentType.valueOf(rawType.replace('-', '_').toUpperCase(Locale.ROOT));
            define(definitions, id, type);
        }
        if (definitions.isEmpty()) {
            throw new IllegalStateException("No cargo component definitions loaded from " + RESOURCE_PATH + ".");
        }
        return definitions;
    }

    private static void define(Map<String, SfxCargoComponentDefinition> definitions, String id, SfxCargoComponentType type) {
        definitions.put(id, new SfxCargoComponentDefinition(id, type));
    }
}
