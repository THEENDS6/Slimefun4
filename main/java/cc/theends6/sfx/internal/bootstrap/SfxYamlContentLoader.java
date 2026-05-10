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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
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
    private final JavaPlugin plugin;
    private final DefaultSfxItemRegistry registry;
    private final Logger logger;

    public SfxYamlContentLoader(JavaPlugin plugin, SfxItemRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = (DefaultSfxItemRegistry) Objects.requireNonNull(registry, "registry");
        this.logger = plugin.getLogger();
    }

    public void ensureDefaultFiles() {
        saveIfAbsent(ITEMS_PATH);
    }

    public void registerAll() {
        loadItems();
    }

    private void loadItems() {
        File file = new File(plugin.getDataFolder(), ITEMS_PATH);
        if (!file.isFile()) {
            return;
        }
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
                logger.warning("Failed to load category from YAML: " + ex.getMessage());
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
                logger.warning("Failed to load item from YAML: " + ex.getMessage());
            }
        }
    }

    private SfxItemCategory parseCategory(Map<?, ?> entry) {
        String id = string(entry.get("id"));
        String name = string(orDefault(entry, "name", id));
        int order = integer(orDefault(entry, "order", 900000));
        boolean hidden = Boolean.TRUE.equals(entry.get("hidden"));
        @SuppressWarnings("unchecked")
        Map<String, Object> icon = (Map<String, Object>) entry.get("icon");
        Material iconMaterial = parseMaterial(icon == null ? "BOOK" : string(orDefault(icon, "material", "BOOK")));
        String iconName = icon == null ? name : string(orDefault(icon, "name", name));
        return new SfxItemCategory(id, Text.mm(name), ItemBuilder.of(iconMaterial).name(iconName).build(), order, hidden);
    }

    private SfxItemDefinition parseItem(Map<?, ?> entry) {
        String id = string(entry.get("id"));
        Material material = parseMaterial(string(entry.get("material")));
        SfxItemDefinition.Builder builder = SfxItemDefinition.builder(id, material, Text.mm(string(orDefault(entry, "name", id))));
        if (entry.containsKey("category")) {
            builder.category(string(entry.get("category")));
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
        if (entry.containsKey("colorRgb")) {
            builder.colorRgb(parseColor(entry.get("colorRgb")));
        } else if (entry.containsKey("color")) {
            builder.colorRgb(parseColor(entry.get("color")));
        }
        if (Boolean.TRUE.equals(entry.get("unbreakable"))) {
            builder.unbreakable(true);
        }
        Object lore = entry.get("lore");
        if (lore instanceof List<?> lines) {
            for (Object line : lines) {
                builder.addLore(Text.mm(String.valueOf(line)));
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
