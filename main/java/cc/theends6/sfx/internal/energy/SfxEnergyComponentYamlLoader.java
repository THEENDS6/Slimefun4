package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.electric.SfxElectricStack;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;


final class SfxEnergyComponentYamlLoader {
    private static final String RESOURCE_PATH = "content/machines/energy-components.yml";

    private final JavaPlugin plugin;

    SfxEnergyComponentYamlLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    Map<String, SfxEnergyComponentDefinition> load() {
        ensureBundledFile();
        boolean strict = plugin.getConfig().getBoolean("content.runtime.compiled-only", true);
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        ConfigurationSection root = yaml.getConfigurationSection("components");
        if (root == null) {
            String message = "No components section in " + RESOURCE_PATH + "; energy components were not loaded.";
            if (strict) {
                throw new IllegalStateException(message);
            }
            plugin.getLogger().warning(message);
            return Map.of();
        }
        Map<String, SfxEnergyComponentDefinition> result = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || !isEnabled(section)) {
                continue;
            }
            try {
                SfxEnergyComponentDefinition definition = parse(id, section);
                result.put(definition.id(), definition);
            } catch (RuntimeException ex) {
                if (strict) {
                    throw new IllegalStateException("Invalid energy component YAML entry " + id, ex);
                }
                plugin.getLogger().log(Level.WARNING, "Invalid energy component YAML entry " + id + "; skipping it.", ex);
            }
        }
        SfxValidationDiagnostics.log(plugin, "machine-yaml", "energy component yaml entries=" + result.size());
        return result;
    }

    private boolean isEnabled(ConfigurationSection section) {
        String requireTrue = section.getString("enabled-when-config-true", null);
        if (requireTrue != null && !requireTrue.isBlank() && !plugin.getConfig().getBoolean(requireTrue, false)) {
            return false;
        }
        String requireFalse = section.getString("enabled-when-config-false", null);
        return requireFalse == null || requireFalse.isBlank() || !plugin.getConfig().getBoolean(requireFalse, false);
    }

    private SfxEnergyComponentDefinition parse(String id, ConfigurationSection section) {
        SfxEnergyComponentType type = SfxEnergyComponentType.valueOf(requiredString(section, "type").trim().replace('-', '_').toUpperCase(Locale.ROOT));
        ConfigurationSection energy = requiredSection(section, "energy");
        int capacity = requiredInt(energy, "capacity");
        int generation = requiredInt(energy, "generation-per-tick");
        int consumption = requiredInt(energy, "consumption-per-tick");
        int energyPerTick = type == SfxEnergyComponentType.CHARGER ? consumption : generation;
        int burnRate = Math.max(1, requiredInt(energy, "burn-rate"));
        boolean vanillaFuel = requiredBoolean(section, "vanilla-fuel");
        Material progressMaterial = parseMaterial(requiredString(section, "progress-material"));
        requiredList(section, "fuels");
        List<SfxEnergyComponentDefinition.FuelRule> fuels = parseFuelRules(section);
        return new SfxEnergyComponentDefinition(id, type, capacity, energyPerTick, 0, burnRate, vanillaFuel, progressMaterial, fuels);
    }

    private ConfigurationSection requiredSection(ConfigurationSection section, String path) {
        ConfigurationSection value = section.getConfigurationSection(path);
        if (value == null) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return value;
    }

    private String requiredString(ConfigurationSection section, String path) {
        String value = section.getString(path, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return value;
    }

    private int requiredInt(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return section.getInt(path);
    }

    private boolean requiredBoolean(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return section.getBoolean(path);
    }

    private List<?> requiredList(ConfigurationSection section, String path) {
        List<?> value = section.getList(path);
        if (value == null) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return value;
    }

    private List<SfxEnergyComponentDefinition.FuelRule> parseFuelRules(ConfigurationSection section) {
        List<SfxEnergyComponentDefinition.FuelRule> fuels = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("fuels")) {
            Object keyRaw = raw.containsKey("key") ? raw.get("key") : raw.get("id");
            String key = requiredString(raw, "key", keyRaw);
            SfxElectricStack input = parseStack(raw.get("input"));
            SfxElectricStack output = parseStack(raw.get("output"));
            int seconds = integer(requiredValue(raw, "seconds"));
            if (input == null || seconds <= 0) {
                throw new IllegalArgumentException("fuel rule " + key + " requires input and positive seconds");
            }
            fuels.add(new SfxEnergyComponentDefinition.FuelRule(key, input, output, seconds));
        }
        for (Map<?, ?> raw : section.getMapList("tag-fuels")) {
            String prefix = string(raw.get("prefix"));
            String tagName = string(raw.get("tag"));
            int seconds = integer(raw.containsKey("seconds") ? raw.get("seconds") : 0);
            Tag<Material> tag = resolveMaterialTag(tagName);
            if (prefix == null || tag == null || seconds <= 0) {
                continue;
            }
            for (Material material : tag.getValues()) {
                fuels.add(new SfxEnergyComponentDefinition.FuelRule(prefix + ":" + material.key(), SfxElectricStack.vanilla(material, 1), null, seconds));
            }
        }
        return fuels;
    }

    private SfxElectricStack parseStack(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String value) {
            if (value.startsWith("sf:")) {
                return SfxElectricStack.sfx(value, 1);
            }
            return SfxElectricStack.vanilla(parseMaterial(value), 1);
        }
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
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

    private static Object requiredValue(Map<?, ?> map, String key) {
        if (!map.containsKey(key) || map.get(key) == null) {
            throw new IllegalArgumentException("map requires " + key);
        }
        return map.get(key);
    }

    private static String requiredString(Map<?, ?> map, String key, Object value) {
        String text = string(value);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("map requires " + key);
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private Tag<Material> resolveMaterialTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String fieldName = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            Field field = Tag.class.getField(fieldName);
            Object value = field.get(null);
            return value instanceof Tag<?> tag ? (Tag<Material>) tag : null;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Unknown material tag: " + raw, ex);
        }
    }

    private Material parseMaterial(String raw) {
        Material material = raw == null ? null : Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + raw);
        }
        return material;
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

    private void ensureBundledFile() {
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
            plugin.getLogger().log(Level.WARNING, "Bundled energy component config is missing: " + RESOURCE_PATH, ex);
        }
    }
}
