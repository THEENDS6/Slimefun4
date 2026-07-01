package cc.theends6.sfx.internal.config;

import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SfxTalismanBehaviorConfig {
    private final YamlConfiguration yaml;

    SfxTalismanBehaviorConfig(YamlConfiguration yaml) {
        this.yaml = yaml == null ? new YamlConfiguration() : yaml;
    }

    public int chance(String type) {
        String key = normalize(type);
        return clampPercent(yaml.getInt("talismans." + key + ".chance", defaultChance(key)));
    }

    public boolean consume(String type) {
        String key = normalize(type);
        return yaml.getBoolean("talismans." + key + ".consume", defaultConsume(key));
    }

    public int durationTicks(String type, int defaultValue) {
        String key = normalize(type);
        return Math.max(0, yaml.getInt("talismans." + key + ".duration-ticks", defaultValue));
    }

    public int amplifier(String type, int defaultValue) {
        String key = normalize(type);
        return Math.max(0, yaml.getInt("talismans." + key + ".amplifier", defaultValue));
    }

    public int wizardFortuneMin() {
        return Math.max(1, yaml.getInt("talismans.wizard.fortune-min", 3));
    }

    public int wizardFortuneMax() {
        return Math.max(wizardFortuneMin(), yaml.getInt("talismans.wizard.fortune-max", 5));
    }

    public boolean wizardDegradeExistingEnchantments() {
        return yaml.getBoolean("talismans.wizard.degrade-existing-enchantments", true);
    }

    public int wizardDegradeChance() {
        return clampPercent(yaml.getInt("talismans.wizard.degrade-chance", 40));
    }

    public boolean hunterCopyEquipmentDrops() {
        return yaml.getBoolean("talismans.hunter.copy-equipment-drops", false);
    }

    public boolean minerDuplicateBlockDrops() {
        return yaml.getBoolean("talismans.miner.duplicate-block-drops", false);
    }

    public boolean farmerDuplicateBlockDrops() {
        return yaml.getBoolean("talismans.farmer.duplicate-block-drops", false);
    }

    private String normalize(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private int defaultChance(String type) {
        return switch (type) {
            case "miner", "farmer", "hunter", "wise" -> 20;
            case "knight" -> 30;
            case "caveman" -> 50;
            case "traveller", "whirlwind" -> 60;
            case "angel" -> 75;
            case "magician" -> 80;
            default -> 100;
        };
    }

    private boolean defaultConsume(String type) {
        return switch (type) {
            case "anvil", "lava", "water", "fire", "warrior", "knight" -> true;
            default -> false;
        };
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
