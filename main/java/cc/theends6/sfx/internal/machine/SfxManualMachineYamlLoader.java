package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.api.machine.manual.SfxManualMachineDefinition;
import cc.theends6.sfx.api.machine.manual.SfxManualMachineOperation;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.api.text.Text;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;


public final class SfxManualMachineYamlLoader {
    private static final String RESOURCE_PATH = "content/machines/manual-machines.yml";
    private static final String BASIC_MACHINE_RESOURCE_PATH = "content/machines/basic-machines.yml";
    private static final String MANUAL_MACHINE_EXECUTOR = "sf:manual_machine";

    private final JavaPlugin plugin;
    private final SfxLocalization localization;

    public SfxManualMachineYamlLoader(JavaPlugin plugin, SfxLocalization localization) {
        this.plugin = plugin;
        this.localization = localization;
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
        boolean strict = plugin.getConfig().getBoolean("content.runtime.compiled-only", true);
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if (root == null) {
            String message = "No machines section in " + RESOURCE_PATH + "; no manual machines loaded.";
            if (strict) {
                throw new IllegalStateException(message);
            }
            plugin.getLogger().warning(message);
            return 0;
        }
        Map<String, Set<String>> acceptedRecipeTypes = loadAcceptedRecipeTypes(strict);
        int loaded = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                String normalizedId = cc.theends6.sfx.api.item.SfxItemDefinition.normalizeId(id);
                if (!acceptedRecipeTypes.containsKey(normalizedId)) {
                    throw new IllegalArgumentException("compiled manual machine runtime contract is missing in " + BASIC_MACHINE_RESOURCE_PATH);
                }
                registry.registerMachine(parse(id, section, acceptedRecipeTypes.get(normalizedId)));
                loaded++;
            } catch (RuntimeException ex) {
                if (strict) {
                    throw new IllegalStateException("Invalid manual machine YAML entry " + id, ex);
                }
                plugin.getLogger().log(Level.WARNING, "Invalid manual machine YAML entry " + id + "; skipping it.", ex);
            }
        }
        SfxValidationDiagnostics.log(plugin, "machine-yaml", "manual machine yaml entries=" + loaded);
        return loaded;
    }

    private Map<String, Set<String>> loadAcceptedRecipeTypes(boolean strict) {
        YamlConfiguration yaml = SfxCompiledYamlResolver.loadMerged(plugin, BASIC_MACHINE_RESOURCE_PATH);
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if (root == null) {
            String message = "No machines section in " + BASIC_MACHINE_RESOURCE_PATH + "; no manual machine runtime contracts loaded.";
            if (strict) {
                throw new IllegalStateException(message);
            }
            plugin.getLogger().warning(message);
            return Map.of();
        }

        Map<String, Set<String>> result = new java.util.LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String executor = section.getString("runtime.executor", "");
            if (!MANUAL_MACHINE_EXECUTOR.equals(cc.theends6.sfx.api.item.SfxItemDefinition.normalizeId(executor))) {
                continue;
            }
            result.put(cc.theends6.sfx.api.item.SfxItemDefinition.normalizeId(id), stringSet(section.getList("runtime.accepts.recipe-types")));
        }
        return Map.copyOf(result);
    }

    private SfxManualMachineDefinition parse(String id, ConfigurationSection section, Set<String> acceptedRecipeTypes) {
        if (section.contains("name")) {
            throw new IllegalArgumentException("literal field is not allowed in compiled manual machine content: name (use name-key)");
        }
        Component name = Text.renderFlexible(requiredLanguageString(section, "name-key"));
        Material icon = parseMaterial(requiredString(section, "icon"));
        Material[] pattern = parsePattern(section.getList("pattern"));
        Material[] displayPattern = parsePattern(section.getList("display-pattern"));
        BlockFace triggerFace = parseFace(requiredString(section, "trigger-face"));
        BlockFace inventoryFace = parseFace(requiredString(section, "inventory-face"));
        SfxManualMachineOperation operation = SfxManualMachineOperation.valueOf(requiredString(section, "operation").trim().replace('-', '_').toUpperCase(Locale.ROOT));
        boolean deployable = requiredBoolean(section, "deployable");
        return new SfxManualMachineDefinition(id, name, icon, pattern, displayPattern, triggerFace, inventoryFace, operation, deployable, stringSet(section.getList("tags")), acceptedRecipeTypes);
    }

    private String requiredLanguageString(ConfigurationSection section, String path) {
        String key = requiredString(section, path);
        if (!localization.has(key)) {
            throw new IllegalArgumentException("language key missing: " + key);
        }
        return localization.requiredText(key);
    }

    private String requiredString(ConfigurationSection section, String path) {
        String value = section.getString(path, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("manual machine field is required: " + path);
        }
        return value;
    }

    private boolean requiredBoolean(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            throw new IllegalArgumentException("manual machine field is required: " + path);
        }
        return section.getBoolean(path);
    }

    private Set<String> stringSet(List<?> raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw != null) {
            for (Object value : raw) {
                if (value != null && !String.valueOf(value).isBlank()) {
                    result.add(String.valueOf(value).trim());
                }
            }
        }
        return result;
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
            String type = requiredString(map, "type");
            if (type.equalsIgnoreCase("empty")) {
                return null;
            }
            if (!type.equalsIgnoreCase("material")) {
                throw new IllegalArgumentException("unsupported manual machine pattern slot type: " + type);
            }
            return parseMaterial(requiredString(map, "material"));
        }
        throw new IllegalArgumentException("manual machine pattern slot must be an explicit map: " + raw);
    }

    private String requiredString(Map<?, ?> map, String path) {
        Object value = map.get(path);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("manual machine field is required: " + path);
        }
        return String.valueOf(value);
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
