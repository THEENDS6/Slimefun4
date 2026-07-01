package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.util.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads manual/multiblock machine definitions from YAML. */
public final class SfxManualMachineYamlLoader {
    private static final String RESOURCE_PATH = "content/machines/manual-machines.yml";

    private final JavaPlugin plugin;

    public SfxManualMachineYamlLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureDefaultFile(boolean overwriteExisting) {
        File target = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (target.isFile() && !overwriteExisting) {
            return;
        }
        try {
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            plugin.saveResource(RESOURCE_PATH, true);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Bundled manual machine YAML is missing: " + RESOURCE_PATH, ex);
        }
    }

    public int loadInto(DefaultManualMachineRegistry registry) {
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!file.isFile()) {
            plugin.getLogger().warning("Manual machine YAML missing: " + RESOURCE_PATH + "; no manual machines loaded.");
            return 0;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if (root == null) {
            plugin.getLogger().warning("No machines section in " + RESOURCE_PATH + "; no manual machines loaded.");
            return 0;
        }
        int loaded = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                registry.registerMachine(parse(id, section));
                loaded++;
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Invalid manual machine YAML entry " + id + "; skipping it.", ex);
            }
        }
        SfxValidationDiagnostics.log(plugin, "machine-yaml", "manual machine yaml entries=" + loaded);
        return loaded;
    }

    private ManualMachineDefinition parse(String id, ConfigurationSection section) {
        Component name = Text.renderFlexible(section.getString("name", id));
        Material icon = parseMaterial(section.getString("icon", "CRAFTING_TABLE"));
        Material[] pattern = parsePattern(section.getList("pattern"));
        Material[] displayPattern = section.contains("display-pattern") ? parsePattern(section.getList("display-pattern")) : pattern;
        BlockFace triggerFace = parseFace(section.getString("trigger-face", "SELF"));
        BlockFace inventoryFace = parseFace(section.getString("inventory-face", "SELF"));
        ManualMachineOperation operation = ManualMachineOperation.valueOf(section.getString("operation", "SINGLE_INPUT").trim().replace('-', '_').toUpperCase(Locale.ROOT));
        boolean deployable = section.getBoolean("deployable", true);
        return new ManualMachineDefinition(id, name, icon, pattern, displayPattern, triggerFace, inventoryFace, operation, deployable);
    }

    private Material[] parsePattern(List<?> raw) {
        if (raw == null || raw.size() != 9) {
            throw new IllegalArgumentException("manual machine pattern must contain exactly 9 entries");
        }
        List<Material> parsed = new ArrayList<>(9);
        for (Object entry : raw) {
            parsed.add(parseOptionalMaterial(entry));
        }
        return parsed.toArray(new Material[0]);
    }

    private Material parseOptionalMaterial(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            raw = map.get("material");
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty() || value.equalsIgnoreCase("air") || value.equalsIgnoreCase("empty") || value.equals("-")) {
            return null;
        }
        return parseMaterial(value);
    }

    private Material parseMaterial(String raw) {
        Material material = Material.matchMaterial(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + raw);
        }
        return material;
    }

    private BlockFace parseFace(String raw) {
        return BlockFace.valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }
}
