package cc.theends6.sfx.internal.configurable;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
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
        boolean strict = plugin.getConfig().getBoolean("content.runtime.compiled-only", true);
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if (root == null) {
            String message = "No machines section in " + RESOURCE_PATH + "; configurable machines were not loaded.";
            if (strict) {
                throw new IllegalStateException(message);
            }
            plugin.getLogger().warning(message);
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
                if (strict) {
                    throw new IllegalStateException("Invalid configurable machine YAML entry " + id, ex);
                }
                plugin.getLogger().log(Level.WARNING, "Invalid configurable machine YAML entry " + id + "; skipping it.", ex);
            }
        }
        SfxValidationDiagnostics.log(plugin, "machine-yaml", "configurable machine yaml entries=" + result.size());
        return result;
    }

    private static SfxConfigurableMachineDefinition parse(String id, ConfigurationSection section) {
        SfxConfigurableMachineKind kind = SfxConfigurableMachineKind.valueOf(requiredString(section, "kind").trim().replace('-', '_').toUpperCase(Locale.ROOT));
        ConfigurationSection energy = requiredSection(section, "energy");
        ConfigurationSection assembler = section.getConfigurationSection("assembler");
        ConfigurationSection reactor = section.getConfigurationSection("reactor");
        if (kind == SfxConfigurableMachineKind.REACTOR) {
            reactor = requiredSection(section, "reactor");
        } else if (kind == SfxConfigurableMachineKind.ASSEMBLER) {
            assembler = requiredSection(section, "assembler");
        }
        return new SfxConfigurableMachineDefinition(
                id,
                kind,
                requiredInt(energy, "capacity"),
                requiredInt(energy, "energy-per-action"),
                requiredInt(energy, "energy-per-tick"),
                parseMaterial(assembler == null ? null : assembler.getString("head-material", null)),
                assembler == null ? 0 : requiredInt(assembler, "head-amount"),
                parseMaterial(assembler == null ? null : assembler.getString("body-material", null)),
                assembler == null ? 0 : requiredInt(assembler, "body-amount"),
                parseEntityType(assembler == null ? null : assembler.getString("spawn-type", null)),
                reactor == null ? null : requiredString(reactor, "coolant-item"),
                parseReactorFuels(reactor),
                reactor != null && requiredBoolean(reactor, "wither-aura"));
    }

    private static List<SfxConfigurableMachineDefinition.ReactorFuel> parseReactorFuels(ConfigurationSection reactor) {
        if (reactor == null) {
            return List.of();
        }
        requiredList(reactor, "fuels");
        List<SfxConfigurableMachineDefinition.ReactorFuel> result = new ArrayList<>();
        for (Map<?, ?> raw : reactor.getMapList("fuels")) {
            String key = requiredString(raw, "key");
            Material material = parseMaterial(string(raw.get("material")));
            int amount = Math.max(1, integer(requiredValue(raw, "amount")));
            int seconds = Math.max(1, integer(requiredValue(raw, "seconds")));
            SfxElectricStack output = parseOutput(raw.get("output"));
            result.add(new SfxConfigurableMachineDefinition.ReactorFuel(key, material, amount, seconds, output));
        }
        return result;
    }

    private static SfxElectricStack parseOutput(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("configurable reactor output must be an explicit map: " + raw);
        }
        int amount = Math.max(1, integer(requiredValue(map, "amount")));
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

    private static ConfigurationSection requiredSection(ConfigurationSection section, String path) {
        ConfigurationSection value = section.getConfigurationSection(path);
        if (value == null) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return value;
    }

    private static String requiredString(ConfigurationSection section, String path) {
        String value = section.getString(path, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return value;
    }

    private static int requiredInt(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return section.getInt(path);
    }

    private static boolean requiredBoolean(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return section.getBoolean(path);
    }

    private static List<?> requiredList(ConfigurationSection section, String path) {
        List<?> value = section.getList(path);
        if (value == null) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return value;
    }

    private static Object requiredValue(Map<?, ?> map, String key) {
        if (!map.containsKey(key) || map.get(key) == null) {
            throw new IllegalArgumentException("map requires " + key);
        }
        return map.get(key);
    }

    private static String requiredString(Map<?, ?> map, String key) {
        String value = string(map.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("map requires " + key);
        }
        return value;
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
