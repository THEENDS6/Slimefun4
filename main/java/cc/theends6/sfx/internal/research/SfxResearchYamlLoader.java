package cc.theends6.sfx.internal.research;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxResearchYamlLoader {
    private static final List<String> DEFAULT_RESEARCH_FILES = List.of(
            "content/researches/10-legacy-slimefun.yml"
    );

    private final JavaPlugin plugin;
    private final Logger logger;

    public SfxResearchYamlLoader(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
    }

    public void ensureDefaultFiles(boolean overwriteExisting) {
        for (String resourcePath : DEFAULT_RESEARCH_FILES) {
            syncBundledFile(resourcePath, overwriteExisting);
        }
    }

    public void loadInto(SfxResearchRegistry registry) {
        registry.clear();

        File root = new File(plugin.getDataFolder(), "content/researches");
        if (!root.isDirectory()) {
            return;
        }

        List<File> files = new ArrayList<>();
        collectYaml(root, files);
        files.sort(Comparator.comparing(File::getPath));

        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (Map<?, ?> entry : yaml.getMapList("researches")) {
                try {
                    registry.register(parseResearch(entry));
                } catch (Exception ex) {
                    logger.warning("Failed to load research from YAML " + file.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    private void collectYaml(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectYaml(child, files);
            } else if (child.isFile() && child.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                files.add(child);
            }
        }
    }

    private SfxResearchDefinition parseResearch(Map<?, ?> entry) {
        String id = string(entry.get("id"));
        String name = string(entry.get("name"));
        int cost = integer(entry.get("cost"));
        int order = integer(entry.get("order"));
        List<String> items = stringList(entry.get("items"));
        items = filterFeatureItems(items);
        return new SfxResearchDefinition(id, name, cost, order, new LinkedHashSet<>(items));
    }

    private List<String> filterFeatureItems(List<String> items) {
        if (plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true)) {
            return items;
        }
        return items.stream()
                .filter(item -> !"sf:bio_reactor_2".equalsIgnoreCase(item.trim()))
                .toList();
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

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry == null) {
                continue;
            }
            String text = String.valueOf(entry).trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }

    private void syncBundledFile(String resourcePath, boolean overwriteExisting) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (!overwriteExisting && target.exists()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Failed to create parent directory for " + target.getPath());
            return;
        }
        try {
            plugin.saveResource(resourcePath, overwriteExisting);
        } catch (IllegalArgumentException ignored) {
            if (target.exists() && !overwriteExisting) {
                return;
            }
            try (var stream = plugin.getResource(resourcePath)) {
                if (stream == null) {
                    return;
                }
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                    yaml.save(target);
                }
            } catch (IOException ex) {
                logger.warning("Failed to create content file " + target.getPath() + ": " + ex.getMessage());
            }
        }
    }
}
