package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;


final class SfxConfigurableMachineDefinitions {
    private static final String RESOURCE_PATH = "content/machines/configurable-machines.yml";

    private SfxConfigurableMachineDefinitions() {
    }

    static Map<String, SfxConfigurableMachineDefinition> load(JavaPlugin plugin) {
        ensureBundledFile(plugin);
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!file.isFile()) {
            return Map.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if (root == null) {
            plugin.getLogger().warning("No machines section in " + RESOURCE_PATH + "; configurable machines will use Java defaults.");
            return Map.of();
        }
        Map<String, SfxConfigurableMachineDefinition> result = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                SfxConfigurableMachineDefinition definition = parse(id, section);
                result.put(definition.id(), definition);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Invalid configurable machine YAML entry " + id + "; skipping it.", ex);
            }
        }
        SfxValidationDiagnostics.log(plugin, "machine-yaml", "configurable machine yaml entries=" + result.size());
        return result;
    }

    private static SfxConfigurableMachineDefinition parse(String id, ConfigurationSection section) {
        SfxConfigurableMachineKind kind = SfxConfigurableMachineKind.valueOf(section.getString("kind", "ACCESS_PORT").trim().replace('-', '_').toUpperCase(Locale.ROOT));
        ConfigurationSection energy = section.getConfigurationSection("energy");
        ConfigurationSection assembler = section.getConfigurationSection("assembler");
        ConfigurationSection reactor = section.getConfigurationSection("reactor");
        return new SfxConfigurableMachineDefinition(
                id,
                kind,
                intValue(energy, section, "capacity", 0),
                intValue(energy, section, "energy-per-action", 0),
                intValue(energy, section, "energy-per-tick", 0),
                parseMaterial(assembler == null ? null : assembler.getString("head-material", null)),
                assembler == null ? 0 : assembler.getInt("head-amount", 0),
                parseMaterial(assembler == null ? null : assembler.getString("body-material", null)),
                assembler == null ? 0 : assembler.getInt("body-amount", 0),
                parseEntityType(assembler == null ? null : assembler.getString("spawn-type", null)),
                reactor == null ? null : reactor.getString("coolant-item", null),
                parseReactorFuels(reactor),
                reactor != null && reactor.getBoolean("wither-aura", false));
    }

    private static List<SfxConfigurableMachineDefinition.ReactorFuel> parseReactorFuels(ConfigurationSection reactor) {
        if (reactor == null) {
            return List.of();
        }
        List<SfxConfigurableMachineDefinition.ReactorFuel> result = new ArrayList<>();
        for (Map<?, ?> raw : reactor.getMapList("fuels")) {
            String key = string(raw.get("key"));
            Material material = parseMaterial(string(raw.get("material")));
            int amount = Math.max(1, integer(raw.containsKey("amount") ? raw.get("amount") : 1));
            int seconds = Math.max(1, integer(raw.containsKey("seconds") ? raw.get("seconds") : 1));
            SfxElectricStack output = parseOutput(raw.get("output"));
            result.add(new SfxConfigurableMachineDefinition.ReactorFuel(key, material, amount, seconds, output));
        }
        return result;
    }

    private static SfxElectricStack parseOutput(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String value) {
            return value.startsWith("sf:") ? SfxElectricStack.sfx(value, 1) : SfxElectricStack.vanilla(parseMaterial(value), 1);
        }
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        int amount = Math.max(1, integer(map.containsKey("amount") ? map.get("amount") : 1));
        Object item = map.get("item");
        if (item != null) {
            return SfxElectricStack.sfx(String.valueOf(item), amount);
        }
        Object material = map.get("material");
        if (material != null) {
            return SfxElectricStack.vanilla(parseMaterial(String.valueOf(material)), amount);
        }
        return null;
    }

    private static int intValue(ConfigurationSection preferred, ConfigurationSection fallback, String path, int defaultValue) {
        if (preferred != null && preferred.contains(path)) {
            return preferred.getInt(path);
        }
        return fallback.getInt(path, defaultValue);
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + raw);
        }
        return material;
    }

    private static EntityType parseEntityType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return EntityType.valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    private static String string(Object raw) {
        return raw == null ? null : String.valueOf(raw).trim();
    }

    private static int integer(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return raw == null || String.valueOf(raw).isBlank() ? 0 : Integer.parseInt(String.valueOf(raw).trim());
    }

    private static void ensureBundledFile(JavaPlugin plugin) {
        File target = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (target.isFile()) {
            return;
        }
        try {
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            plugin.saveResource(RESOURCE_PATH, false);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Bundled configurable machine config is missing: " + RESOURCE_PATH, ex);
        }
    }
}
