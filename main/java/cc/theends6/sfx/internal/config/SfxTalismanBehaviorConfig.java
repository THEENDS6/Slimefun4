package cc.theends6.sfx.internal.config;

import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SfxTalismanBehaviorConfig {
    private static final Set<String> REQUIRED_TALISMANS = Set.of(
            "anvil",
            "miner",
            "farmer",
            "hunter",
            "lava",
            "water",
            "angel",
            "fire",
            "magician",
            "traveller",
            "warrior",
            "knight",
            "whirlwind",
            "wizard",
            "caveman",
            "wise"
    );
    private static final Set<String> POTION_TALISMANS = Set.of(
            "lava",
            "water",
            "fire",
            "traveller",
            "warrior",
            "knight",
            "caveman"
    );

    private final YamlConfiguration yaml;

    SfxTalismanBehaviorConfig(YamlConfiguration yaml) {
        this.yaml = yaml == null ? new YamlConfiguration() : yaml;
    }

    void validate() {
        requiredSection("talismans");
        for (String talisman : REQUIRED_TALISMANS) {
            requiredSection("talismans." + talisman);
            requiredPercent("talismans." + talisman + ".chance");
            requiredBoolean("talismans." + talisman + ".consume");
        }
        for (String talisman : POTION_TALISMANS) {
            requiredNonNegativeInt("talismans." + talisman + ".duration-ticks");
            requiredNonNegativeInt("talismans." + talisman + ".amplifier");
        }
        wizardFortuneMin();
        wizardFortuneMax();
        wizardDegradeExistingEnchantments();
        wizardDegradeChance();
        hunterCopyEquipmentDrops();
        minerDuplicateBlockDrops();
        farmerDuplicateBlockDrops();
    }

    public int chance(String type) {
        String key = normalize(type);
        return requiredPercent("talismans." + key + ".chance");
    }

    public boolean consume(String type) {
        String key = normalize(type);
        return requiredBoolean("talismans." + key + ".consume");
    }

    public int durationTicks(String type) {
        String key = normalize(type);
        return requiredNonNegativeInt("talismans." + key + ".duration-ticks");
    }

    public int amplifier(String type) {
        String key = normalize(type);
        return requiredNonNegativeInt("talismans." + key + ".amplifier");
    }

    public int wizardFortuneMin() {
        return requiredPositiveInt("talismans.wizard.fortune-min");
    }

    public int wizardFortuneMax() {
        int min = wizardFortuneMin();
        int max = requiredPositiveInt("talismans.wizard.fortune-max");
        if (max < min) {
            throw invalid("talismans.wizard.fortune-max", "must be greater than or equal to fortune-min");
        }
        return max;
    }

    public boolean wizardDegradeExistingEnchantments() {
        return requiredBoolean("talismans.wizard.degrade-existing-enchantments");
    }

    public int wizardDegradeChance() {
        return requiredPercent("talismans.wizard.degrade-chance");
    }

    public boolean hunterCopyEquipmentDrops() {
        return requiredBoolean("talismans.hunter.copy-equipment-drops");
    }

    public boolean minerDuplicateBlockDrops() {
        return requiredBoolean("talismans.miner.duplicate-block-drops");
    }

    public boolean farmerDuplicateBlockDrops() {
        return requiredBoolean("talismans.farmer.duplicate-block-drops");
    }

    private String normalize(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private ConfigurationSection requiredSection(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            throw missing(path);
        }
        return section;
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
        return new IllegalStateException("content/legacy-item-behavior.yml requires explicit talisman field " + path + " (" + reason + ")");
    }
}
