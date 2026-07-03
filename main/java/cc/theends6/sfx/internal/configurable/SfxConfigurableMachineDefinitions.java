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
                reactor != null && requiredBoolean(reactor, "wither-aura"),
                parseUi(id, requiredSection(section, "ui")));
    }

    private static SfxConfigurableMachineUiDefinition parseUi(String id, ConfigurationSection section) {
        ConfigurationSection panels = requiredSection(section, "panels");
        Map<String, SfxConfigurableMachineUiPanel> result = new LinkedHashMap<>();
        for (String key : panels.getKeys(false)) {
            ConfigurationSection panel = panels.getConfigurationSection(key);
            if (panel == null) {
                throw new IllegalArgumentException(id + " ui.panels." + key + " requires a map");
            }
            result.put(SfxConfigurableMachineUiDefinition.normalize(key), parsePanel(id, key, panel));
        }
        SfxConfigurableMachineUiDefinition ui = new SfxConfigurableMachineUiDefinition(result);
        if (ui.panel("reactor") == null && ui.panel("access-port") == null) {
            throw new IllegalArgumentException(id + " configurable ui requires reactor or access-port panel");
        }
        return ui;
    }

    private static SfxConfigurableMachineUiPanel parsePanel(String id, String key, ConfigurationSection section) {
        int inventorySize = requiredInt(section, "inventory-size");
        List<SfxConfigurableMachineUiFrame> frames = new ArrayList<>();
        for (Object rawFrame : section.getList("frame", List.of())) {
            if (rawFrame instanceof Map<?, ?> map) {
                frames.add(parseUiFrame(map));
            }
        }
        Map<Integer, SfxConfigurableMachineUiSlot> slots = parseUiSlots(requiredSection(section, "slots"));
        SfxConfigurableMachineUiPanel panel = new SfxConfigurableMachineUiPanel(inventorySize, frames, slots);
        validatePanel(id, key, panel);
        return panel;
    }

    private static void validatePanel(String id, String key, SfxConfigurableMachineUiPanel panel) {
        if (panel.inventorySize() <= 0) {
            throw new IllegalArgumentException(id + " ui.panels." + key + ".inventory-size must be positive");
        }
        if (panel.slots().size() != panel.inventorySize()) {
            throw new IllegalArgumentException(id + " ui.panels." + key + ".slots must explicitly define every slot");
        }
        for (int slot = 0; slot < panel.inventorySize(); slot++) {
            if (!panel.slots().containsKey(slot)) {
                throw new IllegalArgumentException(id + " ui.panels." + key + ".slots missing slot " + slot);
            }
        }
        validateRoleStateIndexes(id, key, panel, "input");
        validateRoleStateIndexes(id, key, panel, "output");
    }

    private static void validateRoleStateIndexes(String id, String panelKey, SfxConfigurableMachineUiPanel panel, String role) {
        int count = 0;
        List<Integer> indexes = new ArrayList<>();
        for (SfxConfigurableMachineUiSlot slot : panel.slots().values()) {
            if (!slot.isRole(role)) {
                continue;
            }
            count++;
            if (slot.stateIndex() == null) {
                throw new IllegalArgumentException(id + " ui.panels." + panelKey + "." + role + " slot " + slot.slot() + " missing state-index");
            }
            indexes.add(slot.stateIndex());
        }
        for (int index = 0; index < count; index++) {
            if (!indexes.contains(index)) {
                throw new IllegalArgumentException(id + " ui.panels." + panelKey + "." + role + " slots missing state-index " + index);
            }
        }
    }

    private static SfxConfigurableMachineUiFrame parseUiFrame(Map<?, ?> map) {
        int[] slots = parseSlots(asList(map.get("slots")));
        Object itemRaw = map.get("item");
        if (!(itemRaw instanceof Map<?, ?> itemMap)) {
            throw new IllegalArgumentException("configurable ui.frame item requires a map");
        }
        return new SfxConfigurableMachineUiFrame(slots, parseUiItem(itemMap));
    }

    private static Map<Integer, SfxConfigurableMachineUiSlot> parseUiSlots(ConfigurationSection section) {
        Map<Integer, SfxConfigurableMachineUiSlot> slots = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("configurable ui.slots key must be numeric: " + key, ex);
            }
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                throw new IllegalArgumentException("configurable ui.slots." + key + " requires a map");
            }
            ConfigurationSection item = child.getConfigurationSection("item");
            slots.put(slot, new SfxConfigurableMachineUiSlot(
                    slot,
                    requiredString(child, "role"),
                    requiredString(child, "behavior"),
                    child.getString("accepts", null),
                    child.getString("action", null),
                    child.getString("item-source", null),
                    optionalInt(child, "state-index"),
                    item == null ? null : parseUiItem(item)));
        }
        return Map.copyOf(slots);
    }

    private static SfxConfigurableMachineUiItem parseUiItem(ConfigurationSection section) {
        String name = optionalPresentString(section, "name");
        String nameKey = section.getString("name-key", null);
        requireTextReference(section.getCurrentPath(), "name", name, nameKey);
        List<String> lore = section.contains("lore") ? section.getStringList("lore") : null;
        String loreKey = section.getString("lore-key", null);
        requireTextReference(section.getCurrentPath(), "lore", lore, loreKey);
        return new SfxConfigurableMachineUiItem(
                parseMaterial(requiredString(section, "material")),
                name,
                lore,
                nameKey,
                loreKey,
                requiredBoolean(section, "glint"));
    }

    private static SfxConfigurableMachineUiItem parseUiItem(Map<?, ?> map) {
        Object name = map.get("name");
        Object nameKey = map.get("name-key");
        requireTextReference("configurable ui item", "name", name, nameKey);
        Object lore = map.get("lore");
        Object loreKey = map.get("lore-key");
        requireTextReference("configurable ui item", "lore", lore, loreKey);
        if (!map.containsKey("glint")) {
            throw new IllegalArgumentException("configurable ui item requires glint");
        }
        return new SfxConfigurableMachineUiItem(
                parseMaterial(requiredString(map, "material")),
                string(name),
                strings(lore),
                string(nameKey),
                string(loreKey),
                Boolean.parseBoolean(String.valueOf(map.get("glint"))));
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

    private static Integer optionalInt(ConfigurationSection section, String path) {
        if (section == null || !section.contains(path)) {
            return null;
        }
        return section.getInt(path);
    }

    private static String optionalPresentString(ConfigurationSection section, String path) {
        return section.contains(path) ? section.getString(path, "") : null;
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
