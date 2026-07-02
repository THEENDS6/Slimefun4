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
        SfxEnergyComponentUiDefinition ui = parseUi(id, requiredSection(section, "ui"));
        return new SfxEnergyComponentDefinition(id, type, capacity, energyPerTick, 0, burnRate, vanillaFuel, progressMaterial, fuels, ui);
    }

    private SfxEnergyComponentUiDefinition parseUi(String id, ConfigurationSection section) {
        int inventorySize = requiredInt(section, "inventory-size");
        int statusSlot = requiredInt(section, "status-slot");
        List<SfxEnergyComponentUiFrame> frames = new ArrayList<>();
        for (Object rawFrame : section.getList("frame", List.of())) {
            if (rawFrame instanceof Map<?, ?> map) {
                frames.add(parseUiFrame(map));
            }
        }
        Map<Integer, SfxEnergyComponentUiSlot> slots = parseUiSlots(requiredSection(section, "slots"));
        SfxEnergyComponentUiDefinition ui = new SfxEnergyComponentUiDefinition(inventorySize, statusSlot, frames, slots);
        validateUi(id, ui);
        return ui;
    }

    private void validateUi(String id, SfxEnergyComponentUiDefinition ui) {
        if (ui.inventorySize() <= 0) {
            throw new IllegalArgumentException(id + " ui.inventory-size must be positive");
        }
        if (ui.statusSlot() < 0 || ui.statusSlot() >= ui.inventorySize()) {
            throw new IllegalArgumentException(id + " ui.status-slot is outside inventory: " + ui.statusSlot());
        }
        if (ui.slots().size() != ui.inventorySize()) {
            throw new IllegalArgumentException(id + " ui.slots must explicitly define every inventory slot: expected " + ui.inventorySize() + ", got " + ui.slots().size());
        }
        for (int slot = 0; slot < ui.inventorySize(); slot++) {
            if (!ui.slots().containsKey(slot)) {
                throw new IllegalArgumentException(id + " ui.slots missing explicit slot " + slot);
            }
        }
        for (SfxEnergyComponentUiFrame frame : ui.frame()) {
            for (int slot : frame.slots()) {
                if (slot < 0 || slot >= ui.inventorySize()) {
                    throw new IllegalArgumentException(id + " ui.frame slot is outside inventory: " + slot);
                }
            }
        }
        if (!ui.isRole(ui.statusSlot(), "status")) {
            throw new IllegalArgumentException(id + " ui.status-slot must be a status slot: " + ui.statusSlot());
        }
        validateIndexedRoleSlots(id, ui, "input", 2);
        validateIndexedRoleSlots(id, ui, "output", 2);
    }

    private void validateIndexedRoleSlots(String id, SfxEnergyComponentUiDefinition ui, String role, int expectedCount) {
        List<Integer> indexes = new ArrayList<>();
        for (SfxEnergyComponentUiSlot slot : ui.slots().values()) {
            if (!slot.roleIs(role)) {
                continue;
            }
            if (slot.stateIndex() == null) {
                throw new IllegalArgumentException(id + " ui." + role + " slot " + slot.slot() + " missing state-index");
            }
            indexes.add(slot.stateIndex());
        }
        if (indexes.size() != expectedCount) {
            throw new IllegalArgumentException(id + " energy ui must declare exactly " + expectedCount + " " + role + " slots");
        }
        for (int index = 0; index < expectedCount; index++) {
            if (!indexes.contains(index)) {
                throw new IllegalArgumentException(id + " ui." + role + " slots missing state-index " + index);
            }
        }
    }

    private SfxEnergyComponentUiFrame parseUiFrame(Map<?, ?> map) {
        int[] slots = parseSlots(asList(map.get("slots")));
        Object itemRaw = map.get("item");
        if (!(itemRaw instanceof Map<?, ?> itemMap)) {
            throw new IllegalArgumentException("energy ui.frame item requires a map");
        }
        return new SfxEnergyComponentUiFrame(slots, parseUiItem(itemMap));
    }

    private Map<Integer, SfxEnergyComponentUiSlot> parseUiSlots(ConfigurationSection section) {
        Map<Integer, SfxEnergyComponentUiSlot> slots = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("energy ui.slots key must be numeric: " + key, ex);
            }
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                throw new IllegalArgumentException("energy ui.slots." + key + " requires a map");
            }
            ConfigurationSection item = child.getConfigurationSection("item");
            slots.put(slot, new SfxEnergyComponentUiSlot(
                    slot,
                    requiredString(child, "role"),
                    requiredString(child, "behavior"),
                    child.getString("item-source", null),
                    optionalInt(child, "state-index"),
                    item == null ? null : parseUiItem(item)));
        }
        return Map.copyOf(slots);
    }

    private SfxEnergyComponentUiItem parseUiItem(ConfigurationSection section) {
        String name = optionalPresentString(section, "name");
        String nameKey = section.getString("name-key", null);
        requireTextReference(section.getCurrentPath(), "name", name, nameKey);
        List<String> lore = section.contains("lore") ? section.getStringList("lore") : null;
        String loreKey = section.getString("lore-key", null);
        requireTextReference(section.getCurrentPath(), "lore", lore, loreKey);
        return new SfxEnergyComponentUiItem(
                parseMaterial(requiredString(section, "material")),
                name,
                lore,
                nameKey,
                loreKey,
                requiredBoolean(section, "glint"));
    }

    private SfxEnergyComponentUiItem parseUiItem(Map<?, ?> map) {
        Object name = map.get("name");
        Object nameKey = map.get("name-key");
        requireTextReference("energy ui item", "name", name, nameKey);
        Object lore = map.get("lore");
        Object loreKey = map.get("lore-key");
        requireTextReference("energy ui item", "lore", lore, loreKey);
        if (!map.containsKey("glint")) {
            throw new IllegalArgumentException("energy ui item requires glint");
        }
        return new SfxEnergyComponentUiItem(
                parseMaterial(requiredString(map, "material", map.get("material"))),
                string(name),
                strings(lore),
                string(nameKey),
                string(loreKey),
                Boolean.parseBoolean(String.valueOf(map.get("glint"))));
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
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("energy fuel stack must be an explicit map: " + raw);
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

    private static int[] parseSlots(List<?> raw) {
        List<Integer> values = new ArrayList<>();
        for (Object value : raw) {
            if (value instanceof Number number) {
                values.add(number.intValue());
            } else if (value != null && !String.valueOf(value).isBlank()) {
                values.add(Integer.parseInt(String.valueOf(value).trim()));
            }
        }
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry != null) {
                result.add(String.valueOf(entry));
            }
        }
        return result;
    }

    private static String optionalPresentString(ConfigurationSection section, String path) {
        return section.contains(path) ? section.getString(path, "") : null;
    }

    private static Integer optionalInt(ConfigurationSection section, String path) {
        if (section == null || !section.contains(path)) {
            return null;
        }
        return section.getInt(path);
    }

    private static void requireTextReference(String context, String field, Object literal, Object key) {
        if (literal == null && string(key) == null) {
            throw new IllegalArgumentException(context + " requires " + field + " or " + field + "-key");
        }
        if (containsNonBlankLiteral(literal)) {
            throw new IllegalArgumentException(context + " uses literal " + field + "; use " + field + "-key");
        }
    }

    private static boolean containsNonBlankLiteral(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                if (entry != null && !String.valueOf(entry).isBlank()) {
                    return true;
                }
            }
            return false;
        }
        return !String.valueOf(value).isBlank();
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
