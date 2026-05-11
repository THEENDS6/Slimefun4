package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.internal.util.Text;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LegacySfYamlExportTool {
    private static final Pattern CATEGORY_PATTERN = Pattern.compile(
            "registerCategory\\(registry,\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",\\s*Material\\.([A-Z0-9_]+),\\s*\"([^\"]*)\",\\s*(null|\"[^\"]*\"),\\s*(null|0x[0-9A-Fa-f]+|\\d+),\\s*(\\d+)\\);"
    );
    private static final Pattern PUT_PATTERN = Pattern.compile("map\\.put\\(\"([^\"]+)\",\\s*(?:\"([^\"]+)\"|(\\d+))\\);");

    private LegacySfYamlExportTool() {
    }

    public static void main(String[] args) throws IOException {
        ExportRegistry registry = new ExportRegistry();
        LegacySfItemBootstrapPart1.register(registry);
        LegacySfItemBootstrapPart2.register(registry);
        LegacySfItemBootstrapPart3.register(registry);
        LegacySfItemBootstrapPart4.register(registry);

        writeCategories(parseCategories(), new File("main/resources/content/items/10-legacy-categories.yml"));
        writeItems(registry.items(), parsePriorityTable(), new File("main/resources/content/items/20-legacy-items.yml"));
    }

    private static List<Map<String, Object>> parseCategories() throws IOException {
        String source = Files.readString(new File("main/java/cc/theends6/sfx/internal/bootstrap/LegacySfCategoryBootstrap.java").toPath(), StandardCharsets.UTF_8);
        Matcher matcher = CATEGORY_PATTERN.matcher(source);
        List<Map<String, Object>> exported = new ArrayList<>();
        while (matcher.find()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", matcher.group(1));
            entry.put("name", matcher.group(2));
            entry.put("priority", Integer.parseInt(matcher.group(7)));

            Map<String, Object> icon = new LinkedHashMap<>();
            icon.put("material", matcher.group(3));
            icon.put("name", matcher.group(4));
            String texture = stripNullOrQuotes(matcher.group(5));
            if (texture != null) {
                icon.put("headTexture", texture);
            }
            String color = stripNullOrQuotes(matcher.group(6));
            if (color != null) {
                icon.put("colorRgb", parseColorLiteral(color));
            }
            entry.put("icon", icon);
            exported.add(entry);
        }
        return exported;
    }

    private static void writeCategories(List<Map<String, Object>> categories, File target) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("categories", categories);
        yaml.set("items", List.of());
        saveYaml(target, yaml);
    }

    private static void writeItems(Collection<SfxItemDefinition> items, Map<String, Integer> priorityTable, File target) throws IOException {
        List<Map<String, Object>> exported = new ArrayList<>();
        int fallbackPriority = priorityTable.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        for (SfxItemDefinition item : items) {
            if (!item.id().startsWith("sf:")) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", item.id());
            entry.put("category", item.categoryId());
            entry.put("material", item.material().name());
            entry.put("name", Text.toLegacy(item.name()));
            int priority = priorityTable.getOrDefault(item.id(), fallbackPriority++);
            entry.put("priority", priority);
            if (item.hidden()) {
                entry.put("hidden", true);
            }
            if (!item.giveable()) {
                entry.put("giveable", false);
            }
            if (!"default".equals(item.variant())) {
                entry.put("variant", item.variant());
            }
            if (item.headTextureHash() != null) {
                entry.put("headTexture", item.headTextureHash());
            }
            if (item.colorRgb() != null) {
                entry.put("colorRgb", item.colorRgb());
            }
            if (item.unbreakable()) {
                entry.put("unbreakable", true);
            }
            if (!item.lore().isEmpty()) {
                List<String> lore = new ArrayList<>();
                item.lore().forEach(line -> lore.add(Text.toLegacy(line)));
                entry.put("lore", lore);
            }
            if (!item.flags().isEmpty()) {
                entry.put("flags", item.flags());
            }
            if (!item.itemFlags().isEmpty()) {
                entry.put("itemFlags", item.itemFlags());
            }
            if (!item.enchantments().isEmpty()) {
                entry.put("enchantments", item.enchantments());
            }
            exported.add(entry);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("categories", List.of());
        yaml.set("items", exported);
        saveYaml(target, yaml);
    }

    private static Map<String, Integer> parsePriorityTable() throws IOException {
        String source = Files.readString(new File("main/java/cc/theends6/sfx/internal/guide/LegacySfGuideResolver.java").toPath(), StandardCharsets.UTF_8);
        Map<String, Integer> priorities = new LinkedHashMap<>();

        List<String> exactOrder = parseOrderedKeys(extractMethodBody(source, "createExactCategoryByItem"));
        int index = 1;
        for (String id : exactOrder) {
            priorities.putIfAbsent(id, index++);
        }

        mergeIntMap(priorities, parseIntMap(extractMethodBody(source, "createClassicItemOrderOverrides")));
        mergeIntMap(priorities, parseIntMap(extractMethodBody(source, "createClassicBasicMachineOrder")));
        mergeIntMap(priorities, parseIntMap(extractMethodBody(source, "createClassicCargoOrder")));
        mergeIntMap(priorities, parseIntMap(extractMethodBody(source, "createClassicArmorOrder")));
        mergeIntMap(priorities, parseIntMap(extractMethodBody(source, "createClassicElectricityOrder")));
        return priorities;
    }

    private static void mergeIntMap(Map<String, Integer> target, Map<String, Integer> source) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(source.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        for (Map.Entry<String, Integer> entry : entries) {
            target.put(entry.getKey(), entry.getValue());
        }
    }

    private static List<String> parseOrderedKeys(String body) {
        List<String> keys = new ArrayList<>();
        Matcher matcher = PUT_PATTERN.matcher(body);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static Map<String, Integer> parseIntMap(String body) {
        Map<String, Integer> values = new LinkedHashMap<>();
        Matcher matcher = PUT_PATTERN.matcher(body);
        while (matcher.find()) {
            if (matcher.group(3) != null) {
                values.put(matcher.group(1), Integer.parseInt(matcher.group(3)));
            }
        }
        return values;
    }

    private static String extractMethodBody(String source, String methodName) {
        Pattern signature = Pattern.compile("(?:private|public)\\s+static\\s+Map<[^>]+>\\s+" + Pattern.quote(methodName) + "\\s*\\(\\)");
        Matcher matcher = signature.matcher(source);
        if (!matcher.find()) {
            throw new IllegalStateException("Method not found: " + methodName);
        }
        int methodIndex = matcher.start();
        int braceStart = source.indexOf('{', methodIndex);
        int depth = 0;
        for (int i = braceStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(braceStart + 1, i);
                }
            }
        }
        throw new IllegalStateException("Unclosed method body: " + methodName);
    }

    private static void saveYaml(File target, YamlConfiguration yaml) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent);
        }
        yaml.save(target);
    }

    private static String stripNullOrQuotes(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if ("null".equals(text)) {
            return null;
        }
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static int parseColorLiteral(String raw) {
        if (raw.startsWith("0x") || raw.startsWith("0X")) {
            return Integer.parseInt(raw.substring(2), 16) & 0xFFFFFF;
        }
        return Integer.parseInt(raw) & 0xFFFFFF;
    }

    private static final class ExportRegistry implements SfxItemRegistry {
        private final Map<String, SfxItemDefinition> items = new LinkedHashMap<>();

        @Override
        public void registerCategory(SfxItemCategory category) {
        }

        @Override
        public void registerItem(SfxItemDefinition definition) {
            items.put(definition.id(), definition);
        }

        @Override
        public Optional<SfxItemCategory> category(String id) {
            return Optional.empty();
        }

        @Override
        public Optional<SfxItemDefinition> item(String id) {
            return Optional.ofNullable(items.get(SfxItemDefinition.normalizeId(id)));
        }

        @Override
        public Collection<SfxItemCategory> categories() {
            return List.of();
        }

        @Override
        public Collection<SfxItemDefinition> items() {
            return items.values();
        }

        @Override
        public Collection<SfxItemDefinition> visibleItemsInCategory(String categoryId) {
            return items.values();
        }
    }
}
