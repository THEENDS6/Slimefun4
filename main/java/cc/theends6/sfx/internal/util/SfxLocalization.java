package cc.theends6.sfx.internal.util;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxLocalization {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");
    private static final Pattern LEGACY_FORMAT = Pattern.compile("(?i).*[&§][0-9A-FK-OR].*");

    private final JavaPlugin plugin;
    private YamlConfiguration bundled;
    private YamlConfiguration custom;

    public SfxLocalization(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        reload();
    }

    public void reload() {
        String language = language();
        this.bundled = loadBundled(language);
        this.custom = loadCustom(language);
    }

    public String language() {
        return plugin.getConfig().getString("language", "zh-CN");
    }

    public boolean has(String path) {
        return lookup(path) != null;
    }

    public String text(String path, String fallback) {
        String value = lookup(path);
        return value == null ? fallback : value;
    }

    public String text(String path, String fallback, Map<String, ?> placeholders) {
        return applyPlaceholders(text(path, fallback), placeholders);
    }

    public Component component(String path, String fallback) {
        return render(text(path, fallback));
    }

    public Component component(String path, String fallback, Map<String, ?> placeholders) {
        return render(text(path, fallback, placeholders));
    }

    public Component categoryName(String categoryId, Component fallback) {
        String path = "categories." + sanitize(categoryId) + ".name";
        String value = lookup(path);
        return value == null ? fallback : render(value);
    }

    public Component itemName(String itemId, Component fallback) {
        String path = "items." + sanitize(itemId) + ".name";
        String value = lookup(path);
        return value == null ? fallback : render(value);
    }

    public Component researchName(String researchId, Component fallback) {
        String path = "researches." + sanitize(researchId) + ".name";
        String value = lookup(path);
        return value == null ? fallback : render(value);
    }

    public List<Component> itemLore(String itemId, List<Component> fallback) {
        List<String> lines = list("items." + sanitize(itemId) + ".lore");
        if (lines.isEmpty()) {
            return fallback;
        }
        List<Component> localized = new ArrayList<>();
        for (String line : lines) {
            localized.add(render(line));
        }
        return localized;
    }

    public List<Component> recipeNote(String itemId, int index, Component fallback) {
        String path = "recipes." + sanitize(itemId) + "." + index + ".note";
        String value = lookup(path);
        if (value == null) {
            return fallback == null ? List.of() : List.of(fallback);
        }
        return List.of(render(value));
    }

    public List<String> list(String path) {
        List<String> fromCustom = custom == null ? List.of() : custom.getStringList(path);
        if (!fromCustom.isEmpty()) {
            return postProcessList(path, fromCustom);
        }
        List<String> fromBundled = bundled == null ? List.of() : bundled.getStringList(path);
        if (!fromBundled.isEmpty()) {
            return postProcessList(path, fromBundled);
        }
        List<String> indexed = indexedList(custom, path);
        if (!indexed.isEmpty()) {
            return postProcessList(path, indexed);
        }
        return postProcessList(path, indexedList(bundled, path));
    }

    private List<String> postProcessList(String path, List<String> values) {
        boolean sfxGeneratorBalance = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true);
        if ("items.sf.combustion_reactor.lore".equals(path)) {
            return combustionReactorLore(values, sfxGeneratorBalance);
        }
        if ("items.sf.netherstar_reactor.lore".equals(path)) {
            return netherStarReactorLore(values, sfxGeneratorBalance);
        }
        if (sfxGeneratorBalance && path.startsWith("items.sf.electrified_crucible")) {
            return electrifiedCrucibleLore(path, values);
        }
        if (isGrowthAcceleratorLore(path)) {
            return growthAcceleratorLore(path, values);
        }
        if ("items.sf.xp_collector.lore".equals(path)) {
            return xpCollectorLore(values);
        }
        if (!sfxGeneratorBalance) {
            return values;
        }
        if (!"items.sf.coal_generator_2.lore".equals(path)
                && !"items.sf.lava_generator_2.lore".equals(path)
                && !"items.sf.bio_reactor_2.lore".equals(path)) {
            return values;
        }
        String line = text("energy.generator.tier2-fuel-consumption-lore", "&8⇨ &6🔥 &7Fuel consumption: &b1.5x");
        if (line == null || line.isBlank() || values.contains(line)) {
            return values;
        }
        List<String> copy = new ArrayList<>(values);
        copy.add(line);
        return copy;
    }

    private boolean isGrowthAcceleratorLore(String path) {
        return "items.sf.crop_growth_accelerator.lore".equals(path)
                || "items.sf.crop_growth_accelerator_2.lore".equals(path)
                || "items.sf.tree_growth_accelerator.lore".equals(path);
    }

    private List<String> growthAcceleratorLore(String path, List<String> values) {
        boolean crop = "items.sf.crop_growth_accelerator.lore".equals(path)
                || "items.sf.crop_growth_accelerator_2.lore".equals(path);
        boolean tree = "items.sf.tree_growth_accelerator.lore".equals(path);
        boolean enabled = crop
                ? plugin.getConfig().getBoolean("electric-machines.sfx-balance.crop-growth-accelerator", true)
                : plugin.getConfig().getBoolean("electric-machines.sfx-balance.tree-growth-accelerator", true);
        if (!enabled || values.size() < 4) {
            return values;
        }

        boolean zh = language().toLowerCase(Locale.ROOT).startsWith("zh");
        boolean cropOne = "items.sf.crop_growth_accelerator.lore".equals(path);
        int attempts = cropOne ? 20 : 30;
        int energy = cropOne ? 100 : (tree ? 96 : 120);

        List<String> copy = new ArrayList<>();
        copy.add(values.get(0));
        copy.add(values.get(1));
        copy.add(values.get(2));
        copy.add(values.get(3));
        if (zh) {
            copy.add("&8⇨ &7范围： &b19x19");
            copy.add("&8⇨ &7速度： &a" + attempts + "/次");
            copy.add("&8⇨ &7间隔： &b10 秒");
            copy.add("&8⇨ &e⚡ &740960 J 储能");
            copy.add("&8⇨ &e⚡ &7" + energy + " J/t");
        } else {
            copy.add("&8⇨ &7Radius: &b19x19");
            copy.add("&8⇨ &7Speed: &a" + attempts + "/time");
            copy.add("&8⇨ &7Interval: &b10 Seconds");
            copy.add("&8⇨ &e⚡&740960 J Buffer");
            copy.add("&8⇨ &e⚡&7" + energy + " J/t");
        }
        return copy;
    }

    private List<String> xpCollectorLore(List<String> values) {
        String configPath = "electric-machines.sfx-balance.xp-collector";
        boolean enabled = plugin.getConfig().isConfigurationSection(configPath)
                ? plugin.getConfig().getBoolean(configPath + ".enabled", true)
                : plugin.getConfig().getBoolean(configPath, true);
        if (!enabled || values.isEmpty()) {
            return values;
        }
        boolean zh = language().toLowerCase(Locale.ROOT).startsWith("zh");
        int energyCost = Math.max(0, plugin.getConfig().getInt(configPath + ".flask-energy-cost", 4096));
        String perFlask = zh ? "&8⇨ &e⚡ &7" + energyCost + " J/学识之瓶" : "&8⇨ &e⚡&7" + energyCost + " J/Knowledge Flask";
        if (values.contains(perFlask)) {
            return values;
        }
        List<String> copy = new ArrayList<>(values.size() + 1);
        boolean inserted = false;
        for (String value : values) {
            if (!inserted && value != null && value.contains("J/t")) {
                copy.add(perFlask);
                inserted = true;
            }
            copy.add(value);
        }
        if (!inserted) {
            copy.add(perFlask);
        }
        return copy;
    }

    private List<String> electrifiedCrucibleLore(String path, List<String> values) {
        int classicEnergy;
        if ("items.sf.electrified_crucible_3.lore".equals(path)) {
            classicEnergy = 120;
        } else if ("items.sf.electrified_crucible_2.lore".equals(path)) {
            classicEnergy = 80;
        } else if ("items.sf.electrified_crucible.lore".equals(path)) {
            classicEnergy = 48;
        } else {
            return values;
        }
        double multiplier = Math.max(1.0D, plugin.getConfig().getDouble("energy.generator-balance.electrified-crucible-consumption-multiplier", 1.5D));
        String energy = String.valueOf(Math.max(1, (int) Math.round(classicEnergy * multiplier)));
        List<String> copy = new ArrayList<>(values.size());
        for (String line : values) {
            if (line != null && line.contains("J/t")) {
                copy.add(line.replaceFirst("\\d+\\s*J/t", energy + " J/t"));
            } else {
                copy.add(line);
            }
        }
        return copy;
    }

    private List<String> combustionReactorLore(List<String> values, boolean sfxGeneratorBalance) {
        String capacity = sfxGeneratorBalance ? "20480" : "5120";
        String output = sfxGeneratorBalance ? "64" : "24";
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            String line = value
                    .replace("20480 J", capacity + " J")
                    .replace("5120 J", capacity + " J")
                    .replace("64 J/t", output + " J/t")
                    .replace("24 J/t", output + " J/t");
            copy.add(line);
        }
        return copy;
    }

    private List<String> netherStarReactorLore(List<String> values, boolean sfxGeneratorBalance) {
        String output = sfxGeneratorBalance ? "2048" : "1024";
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            copy.add(value
                    .replace("2048 J/t", output + " J/t")
                    .replace("1024 J/t", output + " J/t"));
        }
        return copy;
    }

    public Map<String, String> sectionStrings(String path) {
        Map<String, String> values = new LinkedHashMap<>();
        mergeSection(values, bundled, path);
        mergeSection(values, custom, path);
        return values;
    }

    private void mergeSection(Map<String, String> values, YamlConfiguration configuration, String path) {
        if (configuration == null) {
            return;
        }
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                values.put(key, value);
            }
        }
    }

    private String lookup(String path) {
        if (custom != null) {
            String value = custom.getString(path);
            if (value != null) {
                return value;
            }
        }
        if (bundled != null) {
            String value = bundled.getString(path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static List<String> indexedList(YamlConfiguration configuration, String path) {
        if (configuration == null) {
            return List.of();
        }
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int i = 0;
        while (section.contains(String.valueOf(i))) {
            String value = section.getString(String.valueOf(i));
            if (value != null) {
                values.add(value);
            }
            i++;
        }
        return values;
    }

    private static String applyPlaceholders(String input, Map<String, ?> placeholders) {
        if (input == null || input.isBlank() || placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        Matcher matcher = PLACEHOLDER.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object replacement = placeholders.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement == null ? matcher.group(0) : String.valueOf(replacement)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static Component render(String input) {
        if (input == null) {
            return Component.empty();
        }
        return LEGACY_FORMAT.matcher(input).matches() ? Text.legacy(input) : Text.mm(input);
    }

    private static String sanitize(String input) {
        return Objects.requireNonNull(input, "input").toLowerCase().replace(':', '.').replace('/', '.');
    }

    private YamlConfiguration loadBundled(String language) {
        String resourcePath = "lang/" + language + ".yml";
        var stream = plugin.getResource(resourcePath);
        if (stream == null && !"zh-CN".equals(language)) {
            stream = plugin.getResource("lang/zh-CN.yml");
        }
        if (stream == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private YamlConfiguration loadCustom(String language) {
        File file = new File(new File(plugin.getDataFolder(), "lang"), language + ".yml");
        if (!file.isFile()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
