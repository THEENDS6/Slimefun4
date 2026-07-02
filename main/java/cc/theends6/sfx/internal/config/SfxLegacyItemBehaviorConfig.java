package cc.theends6.sfx.internal.config;

import cc.theends6.sfx.internal.template.SfxCompiledYamlResolver;
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
        this.yaml = SfxCompiledYamlResolver.loadMerged(plugin, RESOURCE_PATH);
        this.talismans = new SfxTalismanBehaviorConfig(yaml);
        validateRequiredSchema();
    }


    public SfxTalismanBehaviorConfig talismans() {
        return talismans;
    }

    public int beheadingChance(EntityType type) {
        String key = type.name().toLowerCase(Locale.ROOT).replace('_', '-');
        String path = "beheading-chances." + key;
        if (!yaml.contains(path, true)) {
            path = "beheading-chances.default";
        }
        return requiredPercent(path);
    }

    public boolean smeltersPickaxeAllowFortune() {
        return requiredBoolean("tools.smelters-pickaxe.allow-fortune");
    }

    public Material smeltersPickaxeCustomOutput(Material input) {
        if (input == null) {
            return null;
        }
        ConfigurationSection section = requiredSection("tools.smelters-pickaxe.custom-smelts");
        String raw = section.getString(input.name().toLowerCase(Locale.ROOT));
        if (raw == null || raw.isBlank()) {
            raw = section.getString(input.name());
        }
        return raw == null ? null : Material.matchMaterial(raw);
    }

    public boolean explosivePickaxeAllowFortune() {
        return requiredBoolean("tools.explosive-pickaxe.allow-fortune");
    }

    public boolean explosiveShovelAllowFortune() {
        return requiredBoolean("tools.explosive-shovel.allow-fortune");
    }

    public boolean veinMiningAllowFortune() {
        return requiredBoolean("tools.vein-mining.allow-fortune");
    }

    public int veinMiningMaxBlocks() {
        return requiredPositiveInt("tools.vein-mining.max-blocks");
    }

    public int seekerRange() {
        return requiredPositiveInt("tools.pickaxe-of-the-seeker.range");
    }

    public int seekerDurabilityCost() {
        return requiredNonNegativeInt("tools.pickaxe-of-the-seeker.durability-cost");
    }

    public boolean climbingPickDualWielding() {
        return requiredBoolean("tools.climbing-pick.dual-wielding");
    }

    public int telepositionScrollRadius() {
        return requiredPositiveInt("gadgets.scroll-of-dimensional-teleposition.radius");
    }

    public boolean grapplingHookConsumeOnUse() {
        return requiredBoolean("gadgets.grappling-hook.consume-on-use");
    }

    public int grapplingHookNoFallTicks() {
        return requiredNonNegativeInt("gadgets.grappling-hook.no-fall-ticks");
    }

    public boolean christmasPresentEnabled() {
        return requiredBoolean("seasonal.christmas-present.enabled");
    }

    public int christmasPresentFireworkCount() {
        return requiredNonNegativeInt("seasonal.christmas-present.firework-count");
    }

    public List<GiftEntry> christmasPresentGifts() {
        List<?> raw = requiredList("seasonal.christmas-present.gifts");
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
                throw invalid("seasonal.christmas-present.gifts", "string entries must not be blank");
            }
            String id = trimmed;
            int star = trimmed.lastIndexOf('*');
            if (star < 0) {
                throw invalid("seasonal.christmas-present.gifts", "string entries must use id*amount");
            }
            id = trimmed.substring(0, star).trim();
            int amount;
            try {
                amount = Integer.parseInt(trimmed.substring(star + 1).trim());
            } catch (NumberFormatException ex) {
                throw invalid("seasonal.christmas-present.gifts", "gift amount must be an integer");
            }
            return GiftEntry.of(id, amount);
        }
        if (entry instanceof ConfigurationSection section) {
            String id = section.getString("id");
            if (id == null || id.isBlank()) {
                id = section.getString("material");
            }
            if (id == null || id.isBlank() || !section.isInt("amount")) {
                throw invalid("seasonal.christmas-present.gifts", "map entries must include id/material and amount");
            }
            return GiftEntry.of(id, section.getInt("amount"));
        }
        if (entry instanceof java.util.Map<?, ?> map) {
            Object id = map.get("id");
            if (id == null) {
                id = map.get("material");
            }
            Object amount = map.get("amount");
            if (id == null || id.toString().isBlank() || !(amount instanceof Number number)) {
                throw invalid("seasonal.christmas-present.gifts", "map entries must include id/material and amount");
            }
            int parsedAmount = number.intValue();
            return GiftEntry.of(id == null ? "" : id.toString(), parsedAmount);
        }
        throw invalid("seasonal.christmas-present.gifts", "unsupported gift entry type");
    }

    public record GiftEntry(String id, int amount) {
        static GiftEntry of(String id, int amount) {
            String normalized = id == null ? "" : id.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("Gift id must not be blank");
            }
            if (amount < 1) {
                throw new IllegalArgumentException("Gift amount must be at least 1");
            }
            return new GiftEntry(normalized, amount);
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), RESOURCE_PATH);
    }

    private void validateRequiredSchema() {
        requiredSection("beheading-chances");
        requiredPercent("beheading-chances.default");
        requiredPercent("beheading-chances.ender-dragon");
        requiredPercent("beheading-chances.zombie");
        requiredPercent("beheading-chances.zombie-villager");
        requiredPercent("beheading-chances.skeleton");
        requiredPercent("beheading-chances.creeper");
        requiredPercent("beheading-chances.wither-skeleton");
        requiredPercent("beheading-chances.piglin");
        requiredPercent("beheading-chances.zombified-piglin");
        requiredPercent("beheading-chances.player");

        smeltersPickaxeAllowFortune();
        requiredSection("tools.smelters-pickaxe.custom-smelts");
        explosivePickaxeAllowFortune();
        explosiveShovelAllowFortune();
        veinMiningAllowFortune();
        veinMiningMaxBlocks();
        seekerRange();
        seekerDurabilityCost();
        climbingPickDualWielding();
        telepositionScrollRadius();
        grapplingHookConsumeOnUse();
        grapplingHookNoFallTicks();
        christmasPresentEnabled();
        christmasPresentFireworkCount();
        if (christmasPresentGifts().isEmpty()) {
            throw invalid("seasonal.christmas-present.gifts", "must contain at least one gift");
        }
        talismans.validate();
    }

    private ConfigurationSection requiredSection(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            throw missing(path);
        }
        return section;
    }

    private List<?> requiredList(String path) {
        List<?> list = yaml.getList(path);
        if (list == null) {
            throw missing(path);
        }
        return list;
    }

    private boolean requiredBoolean(String path) {
        if (!yaml.isBoolean(path)) {
            throw missing(path);
        }
        return yaml.getBoolean(path);
    }

    private int requiredPositiveInt(String path) {
        int value = requiredInt(path);
        if (value < 1) {
            throw invalid(path, "must be at least 1");
        }
        return value;
    }

    private int requiredNonNegativeInt(String path) {
        int value = requiredInt(path);
        if (value < 0) {
            throw invalid(path, "must be zero or greater");
        }
        return value;
    }

    private int requiredPercent(String path) {
        int value = requiredInt(path);
        if (value < 0 || value > 100) {
            throw invalid(path, "must be between 0 and 100");
        }
        return value;
    }

    private int requiredInt(String path) {
        if (!yaml.isInt(path)) {
            throw missing(path);
        }
        return yaml.getInt(path);
    }

    private IllegalStateException missing(String path) {
        return invalid(path, "is missing");
    }

    private IllegalStateException invalid(String path, String reason) {
        return new IllegalStateException(RESOURCE_PATH + " requires explicit field " + path + " (" + reason + ")");
    }
}
