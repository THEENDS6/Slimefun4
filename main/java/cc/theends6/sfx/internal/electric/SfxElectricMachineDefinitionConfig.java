package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
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
            return fallback;
        }
        String title = entry.title == null || entry.title.isBlank() ? fallback.title() : entry.title;
        int speed = entry.speed == null ? fallback.speed() : entry.speed;
        int energyCapacity = entry.energyCapacity == null ? fallback.energyCapacity() : entry.energyCapacity;
        int energyConsumption = entry.energyConsumptionPerTick == null ? fallback.energyConsumptionPerTick() : entry.energyConsumptionPerTick;
        Material progressMaterial = entry.progressMaterial == null ? fallback.progressMaterial() : entry.progressMaterial;
        int[] inputSlots = entry.inputSlots == null ? fallback.inputSlots() : entry.inputSlots;
        int[] outputSlots = entry.outputSlots == null ? fallback.outputSlots() : entry.outputSlots;
            SfxElectricMachineMenuStyle menuStyle = entry.menuStyle == null ? fallback.menuStyle() : entry.menuStyle;
        SfxElectricMachineUiDefinition ui = entry.ui == null ? fallback.ui() : entry.ui;
        SfxElectricAssemblerSpec assemblerSpec = entry.assemblerSpec == null ? fallback.assemblerSpec() : entry.assemblerSpec;
        return new SfxElectricMachineDefinition(
                fallback.id(),
                title,
                speed,
                energyCapacity,
                energyConsumption,
                progressMaterial,
                fallback.recipeProvider(),
                inputSlots,
                outputSlots,
                menuStyle,
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
            String title,
            Integer speed,
            Integer energyCapacity,
            Integer energyConsumptionPerTick,
            Material progressMaterial,
            int[] inputSlots,
            int[] outputSlots,
            SfxElectricMachineMenuStyle menuStyle,
            SfxElectricMachineUiDefinition ui,
            SfxElectricAssemblerSpec assemblerSpec
    ) {
        static Entry parse(ConfigurationSection section) {
            ConfigurationSection slots = section.getConfigurationSection("slots");
            ConfigurationSection energy = section.getConfigurationSection("energy");
            return new Entry(
                    section.getString("title"),
                    optionalInt(section, "speed"),
                    optionalInt(energy, "capacity", optionalInt(section, "energy-capacity")),
                    optionalInt(energy, "consumption-per-tick", optionalInt(section, "energy-consumption-per-tick")),
                    parseMaterial(section.getString("progress-material", null)),
                    parseSlots(slots == null ? section.getList("input-slots") : slots.getList("input")),
                    parseSlots(slots == null ? section.getList("output-slots") : slots.getList("output")),
                    parseMenuStyle(section.getString("menu-style", null)),
                    parseUi(section.getConfigurationSection("ui"), parseMenuStyle(section.getString("menu-style", null))),
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

    private static SfxElectricMachineMenuStyle parseMenuStyle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return SfxElectricMachineMenuStyle.valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    private static SfxElectricMachineUiDefinition parseUi(ConfigurationSection section, SfxElectricMachineMenuStyle menuStyle) {
        if (section == null) {
            return null;
        }
        SfxElectricMachineUiDefinition base = SfxElectricMachineUiDefinition.forStyle(menuStyle == null ? SfxElectricMachineMenuStyle.STANDARD : menuStyle);
        int inventorySize = optionalInt(section, "inventory-size", base.inventorySize());
        int statusSlot = optionalInt(section, "status-slot", base.statusSlot());
        List<SfxElectricMachineUiFrame> frames = new ArrayList<>();
        for (Object rawFrame : section.getList("frame", List.of())) {
            if (rawFrame instanceof Map<?, ?> map) {
                frames.add(parseUiFrame(map));
            }
        }
        if (frames.isEmpty() && section.isConfigurationSection("border")) {
            frames.add(new SfxElectricMachineUiFrame(parseSlots(section.getList("border.slots", List.of())), parseUiItem(section.getConfigurationSection("border"))));
        }
        return new SfxElectricMachineUiDefinition(inventorySize, statusSlot, frames.isEmpty() ? base.frame() : frames);
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
                section.getStringList("lore"));
    }

    private static SfxElectricMachineUiItem parseUiItem(Map<?, ?> map) {
        return new SfxElectricMachineUiItem(
                parseRequiredMaterial(string(map.get("material"))),
                stringOrDefault(map.get("name"), " "),
                strings(map.get("lore")));
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
