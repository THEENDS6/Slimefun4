package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import cc.theends6.sfx.internal.ui.SfxDurabilityBarMode;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;






final class SfxElectricMachineDefinitionConfig {
    private static final String RESOURCE_PATH = "content/machines/electric-machines.yml";

    private final JavaPlugin plugin;
    private final Map<String, Entry> entries;

    private SfxElectricMachineDefinitionConfig(JavaPlugin plugin, Map<String, Entry> entries) {
        this.plugin = plugin;
        this.entries = Map.copyOf(entries);
    }

    static SfxElectricMachineDefinitionConfig load(JavaPlugin plugin) {
        ensureBundledFile(plugin);
        boolean strict = plugin.getConfig().getBoolean("content.runtime.compiled-only", true);
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if (root == null) {
            String message = "No machines section in " + RESOURCE_PATH + "; electric machines will use code defaults.";
            if (strict) {
                throw new IllegalStateException(message);
            }
            plugin.getLogger().warning(message);
            return new SfxElectricMachineDefinitionConfig(plugin, Map.of());
        }
        Map<String, Entry> parsed = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                parsed.put(id, Entry.parse(section));
            } catch (RuntimeException ex) {
                if (strict) {
                    throw new IllegalStateException("Invalid electric machine YAML entry " + id, ex);
                }
                plugin.getLogger().log(Level.WARNING, "Invalid electric machine YAML entry " + id + "; keeping Java defaults for this machine.", ex);
            }
        }
        SfxValidationDiagnostics.log(plugin, "machine-yaml", "electric machine yaml entries=" + parsed.size());
        return new SfxElectricMachineDefinitionConfig(plugin, parsed);
    }

    SfxElectricMachineDefinition apply(SfxElectricMachineDefinition fallback) {
        Entry entry = selectEntry(fallback);
        if (entry == null) {
            throw new IllegalStateException("Missing compiled electric machine definition for " + fallback.id());
        }
        String nameKey = requireString(fallback.id(), "name-key", entry.nameKey);
        int speed = requireInt(fallback.id(), "speed", entry.speed);
        int energyCapacity = requireInt(fallback.id(), "energy.capacity", entry.energyCapacity);
        int energyConsumption = requireInt(fallback.id(), "energy.consumption-per-tick", entry.energyConsumptionPerTick);
        Material progressMaterial = requireValue(fallback.id(), "progress-material", entry.progressMaterial);
        int[] inputSlots = requireSlotsFromUi(entry, "input");
        int[] outputSlots = requireSlotsFromUi(entry, "output");
        assertSameSlots(fallback.id(), "input", entry.inputSlots, inputSlots);
        assertSameSlots(fallback.id(), "output", entry.outputSlots, outputSlots);
        Set<String> functionTags = requireFunctionTags(fallback.id(), entry);
        if (entry.ui == null) {
            throw new IllegalStateException("Missing compiled UI definition for electric machine " + fallback.id());
        }
        SfxElectricMachineUiDefinition ui = entry.ui;
        assertStatusSlot(fallback.id(), ui);
        assertStatusTemplates(fallback.id(), ui);
        assertRequiredUiItems(fallback.id(), functionTags, ui);
        SfxElectricAssemblerSpec assemblerSpec = entry.assemblerSpec;
        if (functionTags.contains("assembler") && assemblerSpec == null) {
            throw new IllegalStateException("Missing compiled assembler spec for electric machine " + fallback.id());
        }
        return new SfxElectricMachineDefinition(
                fallback.id(),
                nameKey,
                speed,
                energyCapacity,
                energyConsumption,
                progressMaterial,
                fallback.recipeProvider(),
                inputSlots,
                outputSlots,
                fallback.compiledEntryId(),
                functionTags,
                ui,
                assemblerSpec);
    }

    private Entry selectEntry(SfxElectricMachineDefinition fallback) {
        Entry variant = entries.get(fallback.compiledEntryId());
        if (variant != null) {
            return variant;
        }
        return entries.get(fallback.id());
    }

    private static Set<String> requireFunctionTags(String machineId, Entry entry) {
        if (entry.functionTags == null) {
            throw new IllegalStateException("Missing compiled function tags for electric machine " + machineId);
        }
        return entry.functionTags;
    }

    private static String requireString(String machineId, String path, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing compiled " + path + " for electric machine " + machineId);
        }
        return value;
    }

    private static int requireInt(String machineId, String path, Integer value) {
        if (value == null) {
            throw new IllegalStateException("Missing compiled " + path + " for electric machine " + machineId);
        }
        return value;
    }

    private static <T> T requireValue(String machineId, String path, T value) {
        if (value == null) {
            throw new IllegalStateException("Missing compiled " + path + " for electric machine " + machineId);
        }
        return value;
    }

    private static int[] requireSlotsFromUi(Entry entry, String role) {
        if (entry.ui == null) {
            throw new IllegalStateException("Missing compiled UI definition.");
        }
        if (entry.ui.slots().isEmpty() && entry.ui.inventorySize() > 0) {
            throw new IllegalStateException("Compiled UI has no explicit slots.");
        }
        return entry.ui.stateSlots(role);
    }

    private static void assertSameSlots(String machineId, String role, int[] declared, int[] compiled) {
        int[] safeDeclared = declared == null ? new int[0] : declared;
        if (!Arrays.equals(safeDeclared, compiled)) {
            throw new IllegalStateException("Compiled electric machine " + machineId + " has mismatched " + role
                    + " slots: declared=" + Arrays.toString(safeDeclared) + ", ui.slots=" + Arrays.toString(compiled));
        }
    }

    private static void assertStatusSlot(String machineId, SfxElectricMachineUiDefinition ui) {
        if (ui.inventorySize() <= 0) {
            return;
        }
        int statusSlot = ui.statusSlot();
        if (statusSlot < 0 || statusSlot >= ui.inventorySize()) {
            throw new IllegalStateException("Compiled electric machine " + machineId + " has invalid status slot " + statusSlot);
        }
        if (!ui.isRole(statusSlot, "status") && !ui.isRole(statusSlot, "button")) {
            throw new IllegalStateException("Compiled electric machine " + machineId + " status slot " + statusSlot + " is not a status/button slot.");
        }
    }

    private static void assertStatusTemplates(String machineId, SfxElectricMachineUiDefinition ui) {
        if (ui.inventorySize() <= 0) {
            return;
        }
        for (SfxElectricMachineRenderStatus status : SfxElectricMachineRenderStatus.values()) {
            String key = status.name().toLowerCase(Locale.ROOT).replace('_', '-');
            SfxElectricMachineStatusUiTemplate template = ui.statusTemplate(key);
            if (template == null) {
                throw new IllegalStateException("Compiled electric machine " + machineId + " missing status template " + key);
            }
            if (template.material() == null) {
                throw new IllegalStateException("Compiled electric machine " + machineId + " status template " + key + " missing material");
            }
        }
    }

    private static void assertRequiredUiItems(String machineId, Set<String> functionTags, SfxElectricMachineUiDefinition ui) {
        if (functionTags.contains("auto-crafter")) {
            requireUiItems(machineId, ui,
                    "auto-crafter.no-recipe",
                    "auto-crafter.enabled",
                    "auto-crafter.disabled",
                    "auto-crafter.select",
                    "auto-crafter.previous",
                    "auto-crafter.next",
                    "auto-crafter.container.missing",
                    "auto-crafter.container.ok");
        }
        if (functionTags.contains("auto-brewer")) {
            requireUiItems(machineId, ui, "auto-brewer.fuel.empty", "auto-brewer.fuel.stored");
        }
        if (functionTags.contains("assembler")) {
            requireUiItems(machineId, ui,
                    "assembler.enabled",
                    "assembler.disabled",
                    "assembler.offset",
                    "assembler.head.display",
                    "assembler.body.display");
        }
    }

    private static void requireUiItems(String machineId, SfxElectricMachineUiDefinition ui, String... keys) {
        for (String key : keys) {
            if (ui.item(key, null) == null) {
                throw new IllegalStateException("Compiled electric machine " + machineId + " missing UI item " + key);
            }
        }
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
            plugin.getLogger().log(Level.WARNING, "Bundled electric machine config is missing: " + RESOURCE_PATH, ex);
        }
    }

    private record Entry(
            String nameKey,
            Integer speed,
            Integer energyCapacity,
            Integer energyConsumptionPerTick,
            Material progressMaterial,
            int[] inputSlots,
            int[] outputSlots,
            Set<String> functionTags,
            SfxElectricMachineUiDefinition ui,
            SfxElectricAssemblerSpec assemblerSpec
    ) {
        static Entry parse(ConfigurationSection section) {
            ConfigurationSection slots = section.getConfigurationSection("slots");
            ConfigurationSection energy = section.getConfigurationSection("energy");
            return new Entry(
                    section.getString("name-key", null),
                    optionalInt(section, "speed"),
                    optionalInt(energy, "capacity", optionalInt(section, "energy-capacity")),
                    optionalInt(energy, "consumption-per-tick", optionalInt(section, "energy-consumption-per-tick")),
                    parseMaterial(section.getString("progress-material", null)),
                    parseSlots(slots == null ? section.getList("input-slots") : slots.getList("input")),
                    parseSlots(slots == null ? section.getList("output-slots") : slots.getList("output")),
                    parseFunctionTags(section),
                    parseUi(section.getConfigurationSection("ui")),
                    parseAssembler(section.getConfigurationSection("assembler")));
        }
    }

    private static Integer optionalInt(ConfigurationSection section, String path) {
        return optionalInt(section, path, null);
    }

    private static Integer optionalInt(ConfigurationSection section, String path, Integer fallback) {
        if (section == null || path == null || !section.contains(path)) {
            return fallback;
        }
        return section.getInt(path);
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

    private static Set<String> parseFunctionTags(ConfigurationSection section) {
        if (section == null || !section.contains("functions")) {
            return null;
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object raw : section.getList("functions", List.of())) {
            if (raw != null && !String.valueOf(raw).isBlank()) {
                result.add(String.valueOf(raw).trim().replace('_', '-').toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }

    private static SfxElectricMachineUiDefinition parseUi(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        int inventorySize = optionalInt(section, "inventory-size", 0);
        int statusSlot = optionalInt(section, "status-slot", -1);
        List<SfxElectricMachineUiFrame> frames = new ArrayList<>();
        for (Object rawFrame : section.getList("frame", List.of())) {
            if (rawFrame instanceof Map<?, ?> map) {
                frames.add(parseUiFrame(map));
            }
        }
        if (frames.isEmpty() && section.isConfigurationSection("border")) {
            frames.add(new SfxElectricMachineUiFrame(parseSlots(section.getList("border.slots", List.of())), parseUiItem(section.getConfigurationSection("border"))));
        }
        Map<String, SfxElectricMachineUiItem> items = new LinkedHashMap<>();
        ConfigurationSection itemSection = section.getConfigurationSection("items");
        if (itemSection != null) {
            collectUiItems(items, "", itemSection);
        }
        Map<String, SfxElectricMachineStatusUiTemplate> status = new LinkedHashMap<>();
        ConfigurationSection statusSection = section.getConfigurationSection("status");
        if (statusSection != null) {
            for (String key : statusSection.getKeys(false)) {
                ConfigurationSection statusEntry = statusSection.getConfigurationSection(key);
                if (statusEntry != null) {
                    status.put(key, parseStatusTemplate(statusEntry));
                }
            }
        }
        Map<Integer, SfxElectricMachineUiSlot> slots = parseUiSlots(section.getConfigurationSection("slots"));
        return new SfxElectricMachineUiDefinition(inventorySize, statusSlot, frames, items, status, slots);
    }

    private static Map<Integer, SfxElectricMachineUiSlot> parseUiSlots(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Integer, SfxElectricMachineUiSlot> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("ui.slots key must be numeric: " + key, ex);
            }
            ConfigurationSection slotSection = section.getConfigurationSection(key);
            if (slotSection == null) {
                throw new IllegalArgumentException("ui.slots." + key + " requires a map");
            }
            result.put(slot, parseUiSlot(slot, slotSection));
        }
        return result;
    }

    private static SfxElectricMachineUiSlot parseUiSlot(int slot, ConfigurationSection section) {
        ConfigurationSection item = section.getConfigurationSection("item");
        return new SfxElectricMachineUiSlot(
                slot,
                requiredString(section, "role"),
                requiredString(section, "behavior"),
                section.getString("accepts", null),
                section.getString("action", null),
                section.getString("item-source", null),
                optionalInt(section, "state-index"),
                item == null ? null : parseUiItem(item));
    }

    private static String requiredString(ConfigurationSection section, String path) {
        String value = section.getString(path, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return value;
    }

    private static SfxElectricMachineUiFrame parseUiFrame(Map<?, ?> map) {
        int[] slots = parseSlots(asList(map.get("slots")));
        Object itemRaw = map.get("item");
        if (!(itemRaw instanceof Map<?, ?> itemMap)) {
            throw new IllegalArgumentException("ui.frame item requires a map");
        }
        return new SfxElectricMachineUiFrame(slots, parseUiItem(itemMap));
    }

    private static SfxElectricMachineUiItem parseUiItem(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("ui item section is missing");
        }
        String name = optionalPresentString(section, "name");
        String nameKey = section.getString("name-key", null);
        requireTextReference(section, "name", name, nameKey);
        List<String> lore = section.contains("lore") ? section.getStringList("lore") : null;
        String loreKey = section.getString("lore-key", null);
        requireTextReference(section, "lore", lore, loreKey);
        return new SfxElectricMachineUiItem(
                parseRequiredMaterial(section.getString("material", null)),
                name,
                lore,
                nameKey,
                loreKey,
                requiredBoolean(section, "glint"));
    }

    private static void collectUiItems(Map<String, SfxElectricMachineUiItem> target, String prefix, ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (child.contains("material")) {
                target.put(path, parseUiItem(child));
            } else {
                collectUiItems(target, path, child);
            }
        }
    }

    private static SfxElectricMachineUiItem parseUiItem(Map<?, ?> map) {
        Object name = map.get("name");
        Object nameKey = map.get("name-key");
        requireTextReference(map, "name", name, nameKey);
        Object lore = map.get("lore");
        Object loreKey = map.get("lore-key");
        requireTextReference(map, "lore", lore, loreKey);
        if (!map.containsKey("glint")) {
            throw new IllegalArgumentException("ui item requires glint");
        }
        return new SfxElectricMachineUiItem(
                parseRequiredMaterial(string(map.get("material"))),
                string(name),
                strings(lore),
                string(nameKey),
                string(loreKey),
                Boolean.parseBoolean(string(map.get("glint"))));
    }

    private static SfxElectricMachineStatusUiTemplate parseStatusTemplate(ConfigurationSection section) {
        String name = optionalPresentString(section, "name");
        String nameKey = section.getString("name-key", null);
        requireTextReference(section, "name", name, nameKey);
        List<String> lore = section.contains("lore") ? section.getStringList("lore") : null;
        String loreKey = section.getString("lore-key", null);
        requireTextReference(section, "lore", lore, loreKey);
        return new SfxElectricMachineStatusUiTemplate(
                parseRequiredMaterial(section.getString("material", null)),
                name,
                lore,
                nameKey,
                loreKey,
                requiredBoolean(section, "include-default-lore"),
                parseDurabilityMode(requiredString(section, "durability-mode")));
    }

    private static String optionalPresentString(ConfigurationSection section, String path) {
        return section.contains(path) ? section.getString(path, "") : null;
    }

    private static boolean requiredBoolean(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + path);
        }
        return section.getBoolean(path);
    }

    private static void requireTextReference(ConfigurationSection section, String field, Object literal, Object key) {
        if (literal == null && stringOrNull(key) == null) {
            throw new IllegalArgumentException(section.getCurrentPath() + " requires " + field + " or " + field + "-key");
        }
    }

    private static void requireTextReference(Map<?, ?> map, String field, Object literal, Object key) {
        if (literal == null && stringOrNull(key) == null) {
            throw new IllegalArgumentException("ui item requires " + field + " or " + field + "-key");
        }
    }

    private static SfxDurabilityBarMode parseDurabilityMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return SfxDurabilityBarMode.NONE;
        }
        return SfxDurabilityBarMode.valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    private static Material parseRequiredMaterial(String raw) {
        Material material = parseMaterial(raw);
        if (material == null) {
            throw new IllegalArgumentException("ui item requires material");
        }
        return material;
    }

    private static int[] parseSlots(List<?> raw) {
        if (raw == null) {
            return null;
        }
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

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String stringOrNull(Object value) {
        String text = string(value);
        return text == null || text.isBlank() ? null : text;
    }

    private static SfxElectricAssemblerSpec parseAssembler(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Material head = parseMaterial(section.getString("head-material", null));
        int headAmount = Math.max(0, section.getInt("head-amount", 0));
        Set<Material> bodies = new LinkedHashSet<>();
        for (Object raw : section.getList("body-materials", List.of())) {
            Material material = parseMaterial(String.valueOf(raw));
            if (material != null) {
                bodies.add(material);
            }
        }
        int bodyAmount = Math.max(0, section.getInt("body-amount", 0));
        if (head == null || bodies.isEmpty() || headAmount <= 0 || bodyAmount <= 0) {
            throw new IllegalArgumentException("assembler requires head-material/head-amount/body-materials/body-amount");
        }
        return new SfxElectricAssemblerSpec(head, headAmount, bodies, bodyAmount);
    }
}
