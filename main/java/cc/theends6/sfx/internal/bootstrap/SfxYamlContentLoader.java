package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxRecipe;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.Text;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Additive / override entrypoint for item/category YAML.
 * Recipe YAML is handled by SfxRecipeYamlLoader.
 */
public final class SfxYamlContentLoader {
    private static final Set<String> LEGACY_NON_PLACEABLE_BLOCK_IDS = Set.of(
            "sf:broken_spawner"
    );
    private static final Set<String> LEGACY_PLACEABLE_HEAD_CATEGORIES = Set.of(
            "sf:electricity",
            "sf:cargo",
            "sf:machines",
            "sf:technical_components",
            "sf:gps"
    );
    private static final String ITEMS_PATH = "content/items.yml";
    private static final String ITEMS_DIRECTORY = "content/items";
    private static final List<String> BUNDLED_ITEM_RESOURCES = List.of(
            "content/items/10-legacy-categories.yml",
            "content/items/20-legacy-items.yml",
            ITEMS_PATH
    );
    private final JavaPlugin plugin;
    private final DefaultSfxItemRegistry registry;
    private final Logger logger;

    public SfxYamlContentLoader(JavaPlugin plugin, SfxItemRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = (DefaultSfxItemRegistry) Objects.requireNonNull(registry, "registry");
        this.logger = plugin.getLogger();
    }

    public void ensureDefaultFiles(boolean overwrite) {
        for (String resource : BUNDLED_ITEM_RESOURCES) {
            saveBundled(resource, overwrite);
        }
    }

    public void registerAll() {
        loadItems();
    }

    private void loadItems() {
        if (plugin.getConfig().getBoolean("content.runtime.compiled-only", true)) {
            int index = 0;
            for (YamlConfiguration yaml : SfxCompiledYamlResolver.loadCompiledUnder(plugin, "content/items")) {
                loadYaml(yaml, "compiled item content " + (++index));
            }
            return;
        }
        List<File> files = new ArrayList<>();
        File directory = new File(plugin.getDataFolder(), ITEMS_DIRECTORY);
        if (directory.isDirectory()) {
            File[] discovered = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (discovered != null) {
                files.addAll(List.of(discovered));
            }
        }
        File singleFile = new File(plugin.getDataFolder(), ITEMS_PATH);
        if (singleFile.isFile()) {
            files.add(singleFile);
        }
        files.sort(Comparator.comparing(File::getName));
        for (File file : files) {
            loadYaml(YamlConfiguration.loadConfiguration(file), file.getName());
        }
    }

    private void loadYaml(YamlConfiguration yaml, String sourceName) {
        for (Map<?, ?> entry : yaml.getMapList("categories")) {
            try {
                SfxItemCategory category = parseCategory(entry);
                if (Boolean.TRUE.equals(entry.get("replace"))) {
                    registry.replaceCategory(category);
                } else {
                    registry.registerCategory(category);
                }
            } catch (Exception ex) {
                logger.warning("Failed to load category from YAML " + sourceName + ": " + ex.getMessage());
            }
        }
        for (Map<?, ?> entry : yaml.getMapList("items")) {
            try {
                if (!isFeatureEnabled(entry)) {
                    continue;
                }
                SfxItemDefinition item = parseItem(entry);
                if (Boolean.TRUE.equals(entry.get("replace"))) {
                    registry.replaceItem(item);
                } else {
                    registry.registerItem(item);
                }
            } catch (Exception ex) {
                logger.warning("Failed to load item from YAML " + sourceName + ": " + ex.getMessage());
            }
        }
    }


    private boolean isFeatureEnabled(Map<?, ?> entry) {
        boolean sfxGeneratorBalance = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true);
        if (Boolean.TRUE.equals(entry.get("requires-sfx-generator-balance")) && !sfxGeneratorBalance) {
            return false;
        }
        if (Boolean.TRUE.equals(entry.get("requires-classic-generator-balance")) && sfxGeneratorBalance) {
            return false;
        }
        return true;
    }

    private SfxItemCategory parseCategory(Map<?, ?> entry) {
        String id = string(required(entry, "id"));
        String name = string(required(entry, "name"));
        int order = integer(requiredAny(entry, "priority", "order"));
        boolean hidden = Boolean.TRUE.equals(entry.get("hidden"));
        @SuppressWarnings("unchecked")
        Map<String, Object> icon = (Map<String, Object>) required(entry, "icon");
        Material iconMaterial = parseMaterial(string(required(icon, "material")));
        String iconName = string(required(icon, "name"));
        String headTexture = icon == null ? null : optionalString(icon.get("headTexture"));
        Integer colorRgb = null;
        if (icon != null) {
            if (icon.containsKey("colorRgb")) {
                colorRgb = parseColor(icon.get("colorRgb"));
            } else if (icon.containsKey("color")) {
                colorRgb = parseColor(icon.get("color"));
            }
        }
        Component parsedName = Text.renderFlexible(name);
        return new SfxItemCategory(id, parsedName, LegacySfBootstrapSupport.icon(iconMaterial, Text.renderFlexible(iconName), headTexture, colorRgb), order, hidden);
    }

    private SfxItemDefinition parseItem(Map<?, ?> entry) {
        String id = string(required(entry, "id"));
        Material material = parseMaterial(string(required(entry, "material")));
        String categoryId = string(required(entry, "category"));
        SfxItemDefinition.Builder builder = SfxItemDefinition.builder(id, material, Text.renderFlexible(string(required(entry, "name"))));
        builder.category(categoryId);
        builder.order(integer(requiredAny(entry, "order", "priority", "pos")));
        if (entry.containsKey("version")) {
            builder.version(integer(entry.get("version")));
        }
        if (Boolean.TRUE.equals(entry.get("hidden"))) {
            builder.hidden(true);
        }
        if (Boolean.FALSE.equals(entry.get("giveable"))) {
            builder.giveable(false);
        }
        if (entry.containsKey("variant")) {
            builder.variant(string(entry.get("variant")));
        }
        if (entry.containsKey("headTexture")) {
            builder.headTexture(string(entry.get("headTexture")));
        }
        Integer colorRgb = null;
        if (entry.containsKey("colorRgb")) {
            colorRgb = parseColor(entry.get("colorRgb"));
        } else if (entry.containsKey("color")) {
            colorRgb = parseColor(entry.get("color"));
        }
        if (colorRgb != null) {
            builder.colorRgb(colorRgb);
        }
        if (Boolean.TRUE.equals(entry.get("unbreakable"))) {
            builder.unbreakable(true);
        }
        Object lore = entry.get("lore");
        if (lore instanceof List<?> lines) {
            for (Object line : lines) {
                builder.addLore(Text.renderFlexible(String.valueOf(line)));
            }
        }
        Object flags = entry.get("flags");
        Set<String> normalizedFlags = new HashSet<>();
        if (flags instanceof List<?> values) {
            for (Object flag : values) {
                String normalized = String.valueOf(flag).trim().toLowerCase();
                normalizedFlags.add(normalized);
                builder.flag(normalized);
            }
        }
        if (shouldImplicitlyBePlaceableLegacyBlock(id, categoryId, material, normalizedFlags)) {
            builder.flag("placeable-block");
        }
        Object machine = entry.get("machine");
        if (machine instanceof Map<?, ?> machineMap) {
            String profile = optionalString(machineMap.get("profile"));
            if (profile != null) {
                builder.flag("machine-profile:" + profile.trim().replace(' ', '_').toLowerCase());
            }
        }
        Object itemFlags = entry.get("itemFlags");
        if (itemFlags instanceof List<?> values) {
            for (Object flag : values) {
                builder.itemFlag(String.valueOf(flag));
            }
        }
        Object enchantments = entry.get("enchantments");
        if (enchantments instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> enchantment : values.entrySet()) {
                builder.enchantment(String.valueOf(enchantment.getKey()), integer(enchantment.getValue()));
            }
        }
        Object recipes = entry.get("recipes");
        if (recipes instanceof List<?> list) {
            for (Object raw : list) {
                if (!(raw instanceof Map<?, ?> recipeMap)) {
                    continue;
                }
                builder.addRecipe(parseDisplayRecipe(recipeMap));
            }
        }
        return builder.build();
    }

    private boolean shouldImplicitlyBePlaceableLegacyBlock(String id, String categoryId, Material material, Set<String> flags) {
        if (flags.contains("placeable-block") || !flags.contains("legacy-sf")) {
            return false;
        }
        String normalizedId = SfxItemDefinition.normalizeId(id);
        if (LEGACY_NON_PLACEABLE_BLOCK_IDS.contains(normalizedId)) {
            return false;
        }
        if (material == Material.PLAYER_HEAD) {
            return categoryId != null && LEGACY_PLACEABLE_HEAD_CATEGORIES.contains(SfxItemCategory.normalizeId(categoryId));
        }
        return material.isBlock();
    }

    private SfxRecipe parseDisplayRecipe(Map<?, ?> entry) {
        String type = string(required(entry, "type"));
        String note = string(required(entry, "note"));
        List<SfxRecipeSlot> matrix = parseMatrix(entry.get("matrix"));
        return SfxRecipe.shaped(type, matrix, Text.mm(note));
    }

    private List<SfxRecipeSlot> parseMatrix(Object raw) {
        if (!(raw instanceof List<?> entries) || entries.size() != 9) {
            throw new IllegalArgumentException("recipe matrix must contain exactly 9 entries");
        }
        List<SfxRecipeSlot> matrix = new ArrayList<>();
        for (Object entry : entries) {
            matrix.add(parseSlot(entry));
        }
        return matrix;
    }

    private SfxRecipeSlot parseSlot(Object raw) {
        if (raw == null) {
            return SfxRecipeSlot.empty();
        }
        if (raw instanceof String text) {
            String normalized = text.trim();
            if (normalized.isEmpty() || normalized.equalsIgnoreCase("air") || normalized.equalsIgnoreCase("empty")) {
                return SfxRecipeSlot.empty();
            }
            if (normalized.contains(":")) {
                return SfxRecipeSlot.sfx(normalized);
            }
            return SfxRecipeSlot.vanilla(parseMaterial(normalized));
        }
        if (raw instanceof Map<?, ?> map) {
            String type = string(required(map, "type"));
            int amount = integer(required(map, "amount"));
            if (type.equalsIgnoreCase("sfx")) {
                return SfxRecipeSlot.sfx(string(map.get("id")), amount);
            }
            return SfxRecipeSlot.vanilla(parseMaterial(string(map.get("material"))), amount);
        }
        throw new IllegalArgumentException("unsupported recipe slot: " + raw);
    }

    private static Object required(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("required field missing: " + key);
        }
        return value;
    }

    private static Object requiredAny(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        throw new IllegalArgumentException("required field missing: " + String.join("/", keys));
    }

    private Material parseMaterial(String input) {
        Material material = Material.matchMaterial(input);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + input);
        }
        return material;
    }

    private static int parseColor(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue() & 0xFFFFFF;
        }
        String text = String.valueOf(raw).trim();
        if (text.startsWith("#")) {
            return Integer.parseInt(text.substring(1), 16) & 0xFFFFFF;
        }
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return Integer.parseInt(text.substring(2), 16) & 0xFFFFFF;
        }
        return Integer.parseInt(text) & 0xFFFFFF;
    }

    private static int integer(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }

    private static String string(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("required string value missing");
        }
        return String.valueOf(raw);
    }

    private static String optionalString(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private void saveBundled(String resourcePath, boolean overwrite) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (!overwrite && target.exists()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Failed to create parent directory for " + target.getPath());
            return;
        }
        try {
            plugin.saveResource(resourcePath, overwrite);
        } catch (IllegalArgumentException ignored) {
            try {
                if (!target.exists() && !target.createNewFile()) {
                    logger.warning("Failed to create empty content file: " + target.getPath());
                }
            } catch (IOException ex) {
                logger.warning("Failed to create content file " + target.getPath() + ": " + ex.getMessage());
            }
        }
    }
}
