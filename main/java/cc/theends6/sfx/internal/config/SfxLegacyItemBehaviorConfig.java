package cc.theends6.sfx.internal.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxLegacyItemBehaviorConfig {
    private static final String RESOURCE_PATH = "content/legacy-item-behavior.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private YamlConfiguration yaml = new YamlConfiguration();
    private SfxTalismanBehaviorConfig talismans = new SfxTalismanBehaviorConfig(yaml);

    public SfxLegacyItemBehaviorConfig(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
    }

    public void ensureDefaultFile() {
        File target = file();
        if (target.exists()) {
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Failed to create parent directory for " + target.getPath());
            return;
        }

        try {
            plugin.saveResource(RESOURCE_PATH, false);
        } catch (IllegalArgumentException ignored) {
            try {
                if (!target.exists() && !target.createNewFile()) {
                    logger.warning("Failed to create content file: " + target.getPath());
                }
            } catch (IOException ex) {
                logger.warning("Failed to create content file " + target.getPath() + ": " + ex.getMessage());
            }
        }
    }

    public void reload() {
        File file = file();
        this.yaml = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        this.talismans = new SfxTalismanBehaviorConfig(yaml);
    }


    public SfxTalismanBehaviorConfig talismans() {
        return talismans;
    }

    public int beheadingChance(EntityType type) {
        String key = type.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return clampPercent(yaml.getInt("beheading-chances." + key, defaultBeheadingChance(type)));
    }

    public boolean smeltersPickaxeAllowFortune() {
        return yaml.getBoolean("tools.smelters-pickaxe.allow-fortune", true);
    }

    public Material smeltersPickaxeCustomOutput(Material input) {
        if (input == null) {
            return null;
        }
        ConfigurationSection section = yaml.getConfigurationSection("tools.smelters-pickaxe.custom-smelts");
        if (section == null) {
            return null;
        }
        String raw = section.getString(input.name().toLowerCase(Locale.ROOT));
        if (raw == null || raw.isBlank()) {
            raw = section.getString(input.name());
        }
        return raw == null ? null : Material.matchMaterial(raw);
    }

    public boolean explosivePickaxeAllowFortune() {
        return yaml.getBoolean("tools.explosive-pickaxe.allow-fortune", false);
    }

    public boolean explosiveShovelAllowFortune() {
        return yaml.getBoolean("tools.explosive-shovel.allow-fortune", false);
    }

    public boolean veinMiningAllowFortune() {
        return yaml.getBoolean("tools.vein-mining.allow-fortune", true);
    }

    public int veinMiningMaxBlocks() {
        return Math.max(1, yaml.getInt("tools.vein-mining.max-blocks", 16));
    }

    public int seekerRange() {
        return Math.max(1, yaml.getInt("tools.pickaxe-of-the-seeker.range", 5));
    }

    public int seekerDurabilityCost() {
        return Math.max(0, yaml.getInt("tools.pickaxe-of-the-seeker.durability-cost", 1));
    }

    public boolean climbingPickDualWielding() {
        return yaml.getBoolean("tools.climbing-pick.dual-wielding", true);
    }

    public int telepositionScrollRadius() {
        return Math.max(1, yaml.getInt("gadgets.scroll-of-dimensional-teleposition.radius", 10));
    }

    public boolean grapplingHookConsumeOnUse() {
        return yaml.getBoolean("gadgets.grappling-hook.consume-on-use", true);
    }

    public int grapplingHookNoFallTicks() {
        return Math.max(0, yaml.getInt("gadgets.grappling-hook.no-fall-ticks", 60));
    }

    public boolean christmasPresentEnabled() {
        return yaml.getBoolean("seasonal.christmas-present.enabled", true);
    }

    public int christmasPresentFireworkCount() {
        return Math.max(0, yaml.getInt("seasonal.christmas-present.firework-count", 3));
    }

    public List<GiftEntry> christmasPresentGifts() {
        List<?> raw = yaml.getList("seasonal.christmas-present.gifts", List.of(
                "sf:christmas_hot_chocolate*1",
                "sf:christmas_chocolate_apple*4",
                "sf:christmas_caramel_apple*4",
                "sf:christmas_cake*4",
                "sf:christmas_cookie*8",
                "sf:christmas_present*1",
                "sf:christmas_egg_nog*1",
                "sf:christmas_milk*1",
                "sf:christmas_apple_cider*1",
                "sf:christmas_fruit_cake*4",
                "sf:christmas_apple_pie*4",
                "EMERALD*1"
        ));
        List<GiftEntry> result = new ArrayList<>();
        for (Object entry : raw) {
            GiftEntry parsed = parseGiftEntry(entry);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private GiftEntry parseGiftEntry(Object entry) {
        if (entry instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            String id = trimmed;
            int amount = 1;
            int star = trimmed.lastIndexOf('*');
            if (star >= 0) {
                id = trimmed.substring(0, star).trim();
                try {
                    amount = Integer.parseInt(trimmed.substring(star + 1).trim());
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            return GiftEntry.of(id, amount);
        }
        if (entry instanceof ConfigurationSection section) {
            String id = section.getString("id", section.getString("material", ""));
            return GiftEntry.of(id, section.getInt("amount", 1));
        }
        if (entry instanceof java.util.Map<?, ?> map) {
            Object id = map.get("id");
            if (id == null) {
                id = map.get("material");
            }
            Object amount = map.get("amount");
            int parsedAmount = amount instanceof Number number ? number.intValue() : 1;
            return GiftEntry.of(id == null ? "" : id.toString(), parsedAmount);
        }
        return null;
    }

    public record GiftEntry(String id, int amount) {
        static GiftEntry of(String id, int amount) {
            String normalized = id == null ? "" : id.trim();
            if (normalized.isBlank()) {
                return null;
            }
            return new GiftEntry(normalized, Math.max(1, amount));
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), RESOURCE_PATH);
    }

    private int defaultBeheadingChance(EntityType type) {
        return switch (type) {
            case ZOMBIE, ZOMBIE_VILLAGER, SKELETON, CREEPER, PIGLIN, ZOMBIFIED_PIGLIN -> 40;
            case WITHER_SKELETON -> 25;
            case ENDER_DRAGON -> 100;
            case PLAYER -> 70;
            default -> 0;
        };
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
