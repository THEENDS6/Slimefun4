package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxRecipe;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.Text;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public void ensureDefaultFiles() {
        for (String resource : BUNDLED_ITEM_RESOURCES) {
            saveIfAbsent(resource);
        }
    }

    public void registerAll() {
        loadItems();
    }

    private void loadItems() {
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
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (Map<?, ?> entry : yaml.getMapList("categories")) {
                try {
                    SfxItemCategory category = parseCategory(entry);
                    if (Boolean.TRUE.equals(entry.get("replace"))) {
                        registry.replaceCategory(category);
                    } else {
                        registry.registerCategory(category);
                    }
                } catch (Exception ex) {
                    logger.warning("Failed to load category from YAML " + file.getName() + ": " + ex.getMessage());
                }
            }
            for (Map<?, ?> entry : yaml.getMapList("items")) {
                try {
                    SfxItemDefinition item = parseItem(entry);
                    if (Boolean.TRUE.equals(entry.get("replace"))) {
                        registry.replaceItem(item);
                    } else {
                        registry.registerItem(item);
                    }
                } catch (Exception ex) {
                    logger.warning("Failed to load item from YAML " + file.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    private SfxItemCategory parseCategory(Map<?, ?> entry) {
        String id = string(entry.get("id"));
        String name = string(orDefault(entry, "name", id));
        int order = integer(orDefault(entry, entry.containsKey("priority") ? "priority" : "order", 900000));
        boolean hidden = Boolean.TRUE.equals(entry.get("hidden"));
        @SuppressWarnings("unchecked")
        Map<String, Object> icon = (Map<String, Object>) entry.get("icon");
        Material iconMaterial = parseMaterial(icon == null ? "BOOK" : string(orDefault(icon, "material", "BOOK")));
        String iconName = icon == null ? name : string(orDefault(icon, "name", name));
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
        String id = string(entry.get("id"));
        Material material = parseMaterial(string(entry.get("material")));
        SfxItemDefinition.Builder builder = SfxItemDefinition.builder(id, material, Text.renderFlexible(string(orDefault(entry, "name", id))));
        if (entry.containsKey("category")) {
            builder.category(string(entry.get("category")));
        }
        if (entry.containsKey("order")) {
            builder.order(integer(entry.get("order")));
        } else if (entry.containsKey("priority")) {
            builder.order(integer(entry.get("priority")));
        } else if (entry.containsKey("pos")) {
            builder.order(integer(entry.get("pos")));
        }
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
        if (flags instanceof List<?> values) {
            for (Object flag : values) {
                builder.flag(String.valueOf(flag));
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

    private SfxRecipe parseDisplayRecipe(Map<?, ?> entry) {
        String type = string(orDefault(entry, "type", "multiblock-structure"));
        String note = string(orDefault(entry, "note", "<gray>YAML recipe.</gray>"));
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
            String type = string(orDefault(map, "type", map.containsKey("id") ? "sfx" : "vanilla"));
            int amount = integer(orDefault(map, "amount", 1));
            if (type.equalsIgnoreCase("sfx")) {
                return SfxRecipeSlot.sfx(string(map.get("id")), amount);
            }
            return SfxRecipeSlot.vanilla(parseMaterial(string(map.get("material"))), amount);
        }
        throw new IllegalArgumentException("unsupported recipe slot: " + raw);
    }

    private static Object orDefault(Map<?, ?> map, String key, Object defaultValue) {
        return map.containsKey(key) ? map.get(key) : defaultValue;
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

    private void saveIfAbsent(String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (target.exists()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Failed to create parent directory for " + target.getPath());
            return;
        }
        try {
            plugin.saveResource(resourcePath, false);
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
