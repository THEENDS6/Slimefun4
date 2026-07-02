package cc.theends6.sfx.internal.machine;

import cc.theends6.sfx.internal.diagnostics.SfxValidationDiagnostics;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;


public final class SfxMachineCatalogYamlLoader {
    private static final String RESOURCE_PATH = "content/machines/machine-catalog.yml";

    private final JavaPlugin plugin;

    public SfxMachineCatalogYamlLoader(JavaPlugin plugin) {
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
            plugin.getLogger().log(Level.WARNING, "Bundled machine catalog YAML is missing: " + RESOURCE_PATH, ex);
        }
    }

    public int loadInto(SfxMachineRuntimeEngine engine) {
        boolean strict = plugin.getConfig().getBoolean("content.runtime.compiled-only", true);
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!file.isFile()) {
            String message = "Machine catalog YAML missing: " + RESOURCE_PATH + "; shared runtime catalog will only contain domain-registered definitions.";
            if (strict) {
                throw new IllegalStateException(message);
            }
            plugin.getLogger().warning(message);
            return 0;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if (root == null) {
            String message = "No machines section in " + RESOURCE_PATH + "; shared runtime catalog will only contain domain-registered definitions.";
            if (strict) {
                throw new IllegalStateException(message);
            }
            plugin.getLogger().warning(message);
            return 0;
        }
        int loaded = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                SfxMachineDefinition definition = parse(id, section);
                if (engine.definition(definition.id()).isEmpty()) {
                    engine.registerDefinitionIfAbsent(definition);
                } else {
                    engine.enrichDefinition(definition.id(), existing -> merge(existing, definition));
                }
                loaded++;
            } catch (RuntimeException ex) {
                if (strict) {
                    throw new IllegalStateException("Invalid machine catalog YAML entry " + id, ex);
                }
                plugin.getLogger().log(Level.WARNING, "Invalid machine catalog YAML entry " + id + "; skipping it.", ex);
            }
        }
        SfxValidationDiagnostics.log(plugin, "machine-yaml", "machine catalog yaml entries=" + loaded);
        return loaded;
    }

    private SfxMachineDefinition parse(String id, ConfigurationSection section) {
        SfxMachineCategory category = parseCategory(requiredString(section, "category"));
        Set<String> tags = stringSet(requiredList(section, "tags"));
        if (tags.isEmpty()) {
            throw new IllegalArgumentException("machine catalog field must not be empty: tags");
        }
        SfxMachineDefinition.Builder builder = SfxMachineDefinition.builder(id)
                .displayName(requiredString(section, "display-name"))
                .category(category)
                .inputSlots(integerList(section.getList("input-slots")))
                .outputSlots(integerList(section.getList("output-slots")))
                .statusSlot(section.getInt("status-slot", -1))
                .tickInterval(Math.max(1, section.getInt("tick-interval", 1)))
                .tags(tags);

        Set<SfxMachineCapability> capabilities = parseCapabilities(section.getList("capabilities"));
        if (!capabilities.isEmpty()) {
            builder.capabilities(capabilities);
        }
        for (Map<?, ?> raw : section.getMapList("policies")) {
            String type = string(raw.get("type"));
            String name = string(raw.get("name"));
            builder.policyRef(SfxMachinePolicyRef.of(type, name));
        }
        for (Map<?, ?> raw : section.getMapList("effects")) {
            String name = string(raw.get("name"));
            SfxMachinePhase phase = SfxMachinePhase.valueOf(string(raw.get("phase")).trim().replace('-', '_').toUpperCase(Locale.ROOT));
            builder.effect(SfxMachineEffect.marker(name, phase));
        }
        return SfxMachineSpecialProfiles.apply(builder.build(), section.getString("profile", null));
    }

    private SfxMachineDefinition merge(SfxMachineDefinition existing, SfxMachineDefinition incoming) {
        if (existing == null) {
            return incoming;
        }
        SfxMachineDefinition.Builder builder = existing.toBuilder();
        builder.displayName(incoming.displayName()).category(incoming.category()).tags(incoming.tags()).capabilities(incoming.capabilities());
        if (!incoming.inputSlots().isEmpty()) {
            builder.inputSlots(incoming.inputSlots()).inputProvider(incoming.inputProvider());
        }
        if (!incoming.outputSlots().isEmpty()) {
            builder.outputSlots(incoming.outputSlots()).outputProvider(incoming.outputProvider());
        }
        if (incoming.statusSlot() >= 0) {
            builder.statusSlot(incoming.statusSlot());
        }
        builder.tickInterval(incoming.tickInterval()).policyRefs(incoming.policyRefs()).effects(incoming.effects());
        return builder.build();
    }

    private SfxMachineCategory parseCategory(String raw) {
        return SfxMachineCategory.valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    private String requiredString(ConfigurationSection section, String path) {
        String value = section.getString(path, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("machine catalog field is required: " + path);
        }
        return value;
    }

    private List<?> requiredList(ConfigurationSection section, String path) {
        List<?> value = section.getList(path);
        if (value == null) {
            throw new IllegalArgumentException("machine catalog field is required: " + path);
        }
        return value;
    }

    private Set<SfxMachineCapability> parseCapabilities(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        EnumSet<SfxMachineCapability> result = EnumSet.noneOf(SfxMachineCapability.class);
        for (Object entry : raw) {
            if (entry != null && !String.valueOf(entry).isBlank()) {
                result.add(SfxMachineCapability.valueOf(String.valueOf(entry).trim().replace('-', '_').toUpperCase(Locale.ROOT)));
            }
        }
        return Set.copyOf(result);
    }

    private List<Integer> integerList(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object value : raw) {
            if (value instanceof Number number) {
                result.add(number.intValue());
            } else if (value != null && !String.valueOf(value).isBlank()) {
                result.add(Integer.parseInt(String.valueOf(value).trim()));
            }
        }
        return result;
    }

    private Set<String> stringSet(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new java.util.LinkedHashSet<>();
        for (Object value : raw) {
            if (value != null && !String.valueOf(value).isBlank()) {
                result.add(String.valueOf(value).trim());
            }
        }
        return Set.copyOf(result);
    }

    private static String string(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new IllegalArgumentException("Missing string value");
        }
        return String.valueOf(raw).trim();
    }
}
