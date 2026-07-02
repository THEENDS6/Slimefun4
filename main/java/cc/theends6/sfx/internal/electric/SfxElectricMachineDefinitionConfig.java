package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import cc.theends6.sfx.internal.ui.SfxDurabilityBarMode;
import java.io.File;
import java.util.ArrayList;
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

/**
 * Data layer for electric machine metadata. Recipe providers remain Java strategy
 * objects because they encapsulate Bukkit/Paper world mutations and legacy
 * recipe bridges, but tunable machine/UI/energy metadata is resolved from YAML.
 */
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
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!file.isFile()) {
            return new SfxElectricMachineDefinitionConfig(plugin, Map.of());
        }
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if (root == null) {
            plugin.getLogger().warning("No machines section in " + RESOURCE_PATH + "; electric machines will use code defaults.");
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
        String nameKey = entry.nameKey == null || entry.nameKey.isBlank() ? itemNameKey(fallback.id()) : entry.nameKey;
        int speed = entry.speed == null ? fallback.speed() : entry.speed;
        int energyCapacity = entry.energyCapacity == null ? fallback.energyCapacity() : entry.energyCapacity;
        int energyConsumption = entry.energyConsumptionPerTick == null ? fallback.energyConsumptionPerTick() : entry.energyConsumptionPerTick;
        Material progressMaterial = entry.progressMaterial == null ? fallback.progressMaterial() : entry.progressMaterial;
        int[] inputSlots = entry.inputSlots == null ? fallback.inputSlots() : entry.inputSlots;
        int[] outputSlots = entry.outputSlots == null ? fallback.outputSlots() : entry.outputSlots;
        SfxElectricMachineMenuStyle menuStyle = fallback.menuStyle();
        Set<String> functionTags = entry.functionTags == null ? fallback.functionTags() : entry.functionTags;
        if (entry.ui == null) {
            throw new IllegalStateException("Missing compiled UI definition for electric machine " + fallback.id());
        }
        SfxElectricMachineUiDefinition ui = entry.ui;
        SfxElectricAssemblerSpec assemblerSpec = entry.assemblerSpec == null ? fallback.assemblerSpec() : entry.assemblerSpec;
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
                menuStyle,
                functionTags,
                ui,
                assemblerSpec);
    }

    private Entry selectEntry(SfxElectricMachineDefinition fallback) {
        if ("sf:auto_brewer".equals(fallback.id()) && fallback.menuStyle() == SfxElectricMachineMenuStyle.STANDARD) {
            Entry legacy = entries.get("sf:auto_brewer#legacy");
            if (legacy != null) {
                return legacy;
            }
        }
        return entries.get(fallback.id());
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
                    section.getString("name-key", itemNameKey(section.getName())),
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

    private static String itemNameKey(String id) {
        String baseId = id == null ? "" : id.split("#", 2)[0];
        return "items." + baseId.replace(':', '.').toLowerCase(Locale.ROOT) + ".name";
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
        return new SfxElectricMachineUiDefinition(inventorySize, statusSlot, frames, items, status);
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
        return new SfxElectricMachineUiItem(
                parseRequiredMaterial(section.getString("material", null)),
                section.getString("name", " "),
                section.getStringList("lore"),
                section.getString("name-key", null),
                section.getString("lore-key", null),
                section.getBoolean("glint", false));
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
        return new SfxElectricMachineUiItem(
                parseRequiredMaterial(string(map.get("material"))),
                stringOrDefault(map.get("name"), " "),
                strings(map.get("lore")),
                string(map.get("name-key")),
                string(map.get("lore-key")),
                Boolean.parseBoolean(stringOrDefault(map.get("glint"), "false")));
    }

    private static SfxElectricMachineStatusUiTemplate parseStatusTemplate(ConfigurationSection section) {
        return new SfxElectricMachineStatusUiTemplate(
                parseMaterial(section.getString("material", null)),
                section.getString("name", null),
                section.getStringList("lore"),
                section.getString("name-key", null),
                section.getString("lore-key", null),
                section.getBoolean("include-default-lore", true),
                parseDurabilityMode(section.getString("durability-mode", "NONE")));
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

    private static String stringOrDefault(Object value, String fallback) {
        String result = string(value);
        return result == null ? fallback : result;
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
