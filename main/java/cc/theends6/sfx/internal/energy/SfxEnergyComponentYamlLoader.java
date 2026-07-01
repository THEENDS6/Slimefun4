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

/** Loads energy-only components from YAML; machine UI/runtime logic stays outside the energy system. */
final class SfxEnergyComponentYamlLoader {
    private static final String RESOURCE_PATH = "content/machines/energy-components.yml";

    private final JavaPlugin plugin;

    SfxEnergyComponentYamlLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    Map<String, SfxEnergyComponentDefinition> load() {
        ensureBundledFile();
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!file.isFile()) {
            return Map.of();
        }
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        ConfigurationSection root = yaml.getConfigurationSection("components");
        if (root == null) {
            plugin.getLogger().warning("No components section in " + RESOURCE_PATH + "; energy components will use Java defaults.");
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
        SfxEnergyComponentType type = SfxEnergyComponentType.valueOf(section.getString("type", "CONNECTOR").trim().replace('-', '_').toUpperCase(Locale.ROOT));
        ConfigurationSection energy = section.getConfigurationSection("energy");
        int capacity = intValue(energy, section, "capacity", 0);
        int generation = intValue(energy, section, "generation-per-tick", 0);
        int consumption = intValue(energy, section, "consumption-per-tick", 0);
        int energyPerTick = type == SfxEnergyComponentType.CHARGER ? consumption : generation;
        int burnRate = Math.max(1, intValue(energy, section, "burn-rate", 10));
        boolean vanillaFuel = section.getBoolean("vanilla-fuel", false);
        Material progressMaterial = parseMaterial(section.getString("progress-material", "REDSTONE"));
        List<SfxEnergyComponentDefinition.FuelRule> fuels = parseFuelRules(section);
        return new SfxEnergyComponentDefinition(id, type, capacity, energyPerTick, 0, burnRate, vanillaFuel, progressMaterial, fuels);
    }

    private int intValue(ConfigurationSection preferred, ConfigurationSection fallback, String path, int defaultValue) {
        if (preferred != null && preferred.contains(path)) {
            return preferred.getInt(path);
        }
        return fallback.getInt(path, defaultValue);
    }

    private List<SfxEnergyComponentDefinition.FuelRule> parseFuelRules(ConfigurationSection section) {
        List<SfxEnergyComponentDefinition.FuelRule> fuels = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("fuels")) {
            Object keyRaw = raw.containsKey("key") ? raw.get("key") : raw.get("id");
            String key = string(keyRaw);
            SfxElectricStack input = parseStack(raw.get("input"));
            SfxElectricStack output = parseStack(raw.get("output"));
            int seconds = integer(raw.containsKey("seconds") ? raw.get("seconds") : 0);
            if (key != null && input != null && seconds > 0) {
                fuels.add(new SfxEnergyComponentDefinition.FuelRule(key, input, output, seconds));
            }
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
