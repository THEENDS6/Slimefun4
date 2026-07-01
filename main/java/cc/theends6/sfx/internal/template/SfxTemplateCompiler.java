package cc.theends6.sfx.internal.template;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SfxTemplateCompiler {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");
    private static final Set<String> META_KEYS = Set.of("@istemplate", "@copyfrom", "@mergeinto", "@isoutput", "@define", "@project", "@global");

    private final Path sourceRoot;
    private final Path outputRoot;
    private final Map<String, Object> globalVariables = new LinkedHashMap<>();
    private final Map<String, Object> projectVariables = new LinkedHashMap<>();
    private final Map<String, SourceNode> sourcesByPath = new LinkedHashMap<>();
    private final Map<String, Origin> origins = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public SfxTemplateCompiler(Path sourceRoot, Path outputRoot) {
        this.sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot");
        this.outputRoot = Objects.requireNonNull(outputRoot, "outputRoot");
    }

    public SfxTemplateCompileReport compile() {
        sourcesByPath.clear();
        origins.clear();
        warnings.clear();
        errors.clear();
        globalVariables.clear();
        projectVariables.clear();
        try {
            Files.createDirectories(sourceRoot);
            Files.createDirectories(outputRoot);
            List<Path> sources = listYamlFiles(sourceRoot);
            Map<String, Object> root = new LinkedHashMap<>();
            for (Path source : sources) {
                SourceNode sourceNode = loadSource(source);
                sourcesByPath.put(sourceNode.relativePath(), sourceNode);
                mergeInto(root, sourceNode.root(), sourceNode.relativePath(), "$", false);
            }
            collectVariables(root);
            Map<String, Object> expanded = expandMap(root, "$", new ArrayDeque<>());
            applyMergeInto(expanded);
            resolveVariables(expanded, "$", new ArrayDeque<>());
            List<OutputNode> outputs = collectOutputs(expanded);
            if (outputs.isEmpty()) {
                pruneTemplatesAndMeta(expanded);
                outputs.add(new OutputNode(List.of("all"), "$", expanded));
            }
            clearOutputDirectory();
            writeOutputs(outputs);
            writeManifest(sources, outputs);
            writeSourceMap();
        } catch (SfxTemplateCompileException ex) {
            errors.add(ex.getMessage());
        } catch (RuntimeException | IOException ex) {
            errors.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        return new SfxTemplateCompileReport(sourcesByPath.size(), outputFileCount(), List.copyOf(warnings), List.copyOf(errors));
    }

    private List<Path> listYamlFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
    }

    private SourceNode loadSource(Path source) {
        String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(quoteTemplateMetaKeys(Files.readString(source, StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new SfxTemplateCompileException("Cannot load template source " + relative + ": " + ex.getMessage(), ex);
        }
        Map<String, Object> root = sectionToMap(yaml, relative, "$");
        normalizeMeta(root, relative, "$");
        return new SourceNode(relative, root);
    }

    private String quoteTemplateMetaKeys(String input) {
        StringBuilder result = new StringBuilder(input.length() + 64);
        String[] lines = input.split("\\R", -1);
        Pattern metaKey = Pattern.compile("^(\\s*)(@[A-Za-z][A-Za-z0-9_-]*)(\\s*):(.*)$");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = metaKey.matcher(lines[i]);
            if (matcher.matches()) {
                result.append(matcher.group(1))
                        .append('"').append(matcher.group(2)).append('"')
                        .append(matcher.group(3)).append(':').append(matcher.group(4));
            } else {
                result.append(lines[i]);
            }
            if (i + 1 < lines.length) {
                result.append('\n');
            }
        }
        return result.toString();
    }

    private Map<String, Object> sectionToMap(ConfigurationSection section, String source, String path) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            String childPath = path.equals("$") ? key : path + "." + key;
            origins.put(childPath, new Origin(source, childPath, "load"));
            map.put(key, value instanceof ConfigurationSection child ? sectionToMap(child, source, childPath) : value);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private void normalizeMeta(Object value, String source, String path) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            Map<String, String> normalized = new LinkedHashMap<>();
            for (String key : List.copyOf(map.keySet())) {
                String normalizedKey = normalizeMetaKey(key);
                if (normalizedKey == null || normalizedKey.equals(key)) {
                    continue;
                }
                if (map.containsKey(normalizedKey) && !Objects.equals(map.get(normalizedKey), map.get(key))) {
                    throw new SfxTemplateCompileException("Conflicting template meta keys at " + source + " " + path + ": " + key + " and " + normalizedKey);
                }
                if (normalized.containsKey(normalizedKey) && !Objects.equals(map.get(normalized.get(normalizedKey)), map.get(key))) {
                    throw new SfxTemplateCompileException("Conflicting template meta aliases at " + source + " " + path + ": " + key + " and " + normalized.get(normalizedKey));
                }
                normalized.put(normalizedKey, key);
            }
            for (Map.Entry<String, String> entry : normalized.entrySet()) {
                if (!map.containsKey(entry.getKey())) {
                    map.put(entry.getKey(), map.remove(entry.getValue()));
                } else {
                    map.remove(entry.getValue());
                }
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                normalizeMeta(entry.getValue(), source, path.equals("$") ? entry.getKey() : path + "." + entry.getKey());
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                normalizeMeta(list.get(i), source, path + "[" + i + "]");
            }
        }
    }

    private String normalizeMetaKey(String key) {
        if (key == null || !key.startsWith("@")) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "@it" -> "@istemplate";
            case "@cf" -> "@copyfrom";
            case "@mi" -> "@mergeinto";
            case "@io" -> "@isoutput";
            default -> META_KEYS.contains(normalized) ? normalized : key;
        };
    }

    @SuppressWarnings("unchecked")
    private void collectVariables(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            mergeVariables(globalVariables, map.get("@global"));
            mergeVariables(projectVariables, map.get("@project"));
            for (Object child : map.values()) {
                collectVariables(child);
            }
        } else if (value instanceof List<?> list) {
            for (Object child : list) {
                collectVariables(child);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeVariables(Map<String, Object> target, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            target.put(String.valueOf(entry.getKey()), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> expandMap(Map<String, Object> map, String path, ArrayDeque<String> stack) {
        Object copyFrom = map.get("@copyfrom");
        Map<String, Object> working = new LinkedHashMap<>(map);
        if (copyFrom != null) {
            String reference = String.valueOf(copyFrom).trim();
            String signature = path + " -> " + reference;
            if (stack.contains(signature)) {
                throw new SfxTemplateCompileException("Circular @copyfrom reference: " + String.join(" > ", stack) + " > " + signature);
            }
            stack.addLast(signature);
            Reference resolved = resolveReference(reference, path);
            Object resolvedNode = getPath(resolved.path());
            if (!(resolvedNode instanceof Map<?, ?> resolvedMap)) {
                throw new SfxTemplateCompileException("Invalid @copyfrom target at " + path + ": " + reference + " resolved to " + resolved.path());
            }
            Map<String, Object> base = expandMap(new LinkedHashMap<>((Map<String, Object>) resolvedMap), resolved.path(), stack);
            stack.removeLast();
            base.remove("@istemplate");
            base.remove("@copyfrom");
            mergeInto(base, withoutKey(working, "@copyfrom"), originOf(path), path, false);
            working = base;
        }
        Map<String, Object> expanded = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : working.entrySet()) {
            String childPath = path.equals("$") ? entry.getKey() : path + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> childMap) {
                expanded.put(entry.getKey(), expandMap(new LinkedHashMap<>((Map<String, Object>) childMap), childPath, stack));
            } else if (value instanceof List<?> list) {
                expanded.put(entry.getKey(), expandList(list, childPath, stack));
            } else {
                expanded.put(entry.getKey(), value);
            }
        }
        return expanded;
    }

    @SuppressWarnings("unchecked")
    private List<Object> expandList(List<?> list, String path, ArrayDeque<String> stack) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            if (value instanceof Map<?, ?> map) {
                result.add(expandMap(new LinkedHashMap<>((Map<String, Object>) map), path + "[" + i + "]", stack));
            } else if (value instanceof List<?> child) {
                result.add(expandList(child, path + "[" + i + "]", stack));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private Map<String, Object> withoutKey(Map<String, Object> input, String key) {
        Map<String, Object> result = new LinkedHashMap<>(input);
        result.remove(key);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void applyMergeInto(Map<String, Object> root) {
        List<MergeDirective> directives = new ArrayList<>();
        collectMergeDirectives(root, "$", directives);
        for (MergeDirective directive : directives) {
            Object target = getPath(root, directive.targetPath());
            if (!(target instanceof Map<?, ?> targetMap)) {
                throw new SfxTemplateCompileException("@mergeinto target does not exist or is not a map at " + directive.sourcePath() + ": " + directive.targetPath());
            }
            Map<String, Object> payload = new LinkedHashMap<>(directive.payload());
            payload.remove("@mergeinto");
            payload.remove("@istemplate");
            payload.remove("@isoutput");
            mergeInto((Map<String, Object>) targetMap, payload, originOf(directive.sourcePath()), directive.targetPath(), true);
        }
        removeMergeDirectiveNodes(root);
    }

    @SuppressWarnings("unchecked")
    private void collectMergeDirectives(Object value, String path, List<MergeDirective> result) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            Object target = map.get("@mergeinto");
            if (target != null) {
                result.add(new MergeDirective(path, normalizePath(String.valueOf(target)), map));
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                collectMergeDirectives(entry.getValue(), path.equals("$") ? entry.getKey() : path + "." + entry.getKey(), result);
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                collectMergeDirectives(list.get(i), path + "[" + i + "]", result);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean removeMergeDirectiveNodes(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return false;
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        List<String> remove = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object child = entry.getValue();
            if (child instanceof Map<?, ?> childMap && ((Map<String, Object>) childMap).containsKey("@mergeinto")) {
                remove.add(entry.getKey());
            } else {
                removeMergeDirectiveNodes(child);
            }
        }
        for (String key : remove) {
            map.remove(key);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void resolveVariables(Object value, String path, ArrayDeque<Map<String, Object>> localScopes) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            Map<String, Object> defined = new LinkedHashMap<>();
            mergeVariables(defined, map.get("@define"));
            if (!defined.isEmpty()) {
                localScopes.addLast(defined);
            }
            for (Map.Entry<String, Object> entry : List.copyOf(map.entrySet())) {
                String childPath = path.equals("$") ? entry.getKey() : path + "." + entry.getKey();
                Object child = entry.getValue();
                if (child instanceof String text) {
                    map.put(entry.getKey(), resolveText(text, childPath, localScopes));
                } else {
                    resolveVariables(child, childPath, localScopes);
                }
            }
            if (!defined.isEmpty()) {
                localScopes.removeLast();
            }
        } else if (value instanceof List<?> rawList) {
            List<Object> list = (List<Object>) rawList;
            for (int i = 0; i < list.size(); i++) {
                Object child = list.get(i);
                if (child instanceof String text) {
                    list.set(i, resolveText(text, path + "[" + i + "]", localScopes));
                } else {
                    resolveVariables(child, path + "[" + i + "]", localScopes);
                }
            }
        }
    }

    private String resolveText(String text, String path, ArrayDeque<Map<String, Object>> localScopes) {
        String result = text;
        for (int round = 0; round < 16; round++) {
            Matcher matcher = PLACEHOLDER.matcher(result);
            if (!matcher.find()) {
                return result;
            }
            StringBuffer buffer = new StringBuffer();
            do {
                String key = matcher.group(1);
                Object value = resolveVariable(key, localScopes);
                if (value == null) {
                    throw new SfxTemplateCompileException("Unresolved template variable ${" + key + "} at " + path);
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(value)));
            } while (matcher.find());
            matcher.appendTail(buffer);
            result = buffer.toString();
        }
        throw new SfxTemplateCompileException("Variable resolution exceeded nesting limit at " + path + ": " + text);
    }

    private Object resolveVariable(String key, ArrayDeque<Map<String, Object>> localScopes) {
        var descending = localScopes.descendingIterator();
        while (descending.hasNext()) {
            Map<String, Object> scope = descending.next();
            if (scope.containsKey(key)) {
                return scope.get(key);
            }
        }
        if (projectVariables.containsKey(key)) {
            return projectVariables.get(key);
        }
        return globalVariables.get(key);
    }

    @SuppressWarnings("unchecked")
    private boolean pruneTemplatesAndMeta(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return false;
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        if (truthy(map.get("@istemplate"))) {
            return true;
        }
        List<String> remove = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("@")) {
                remove.add(key);
            } else if (pruneTemplatesAndMeta(entry.getValue())) {
                remove.add(key);
            }
        }
        for (String key : remove) {
            map.remove(key);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<OutputNode> collectOutputs(Map<String, Object> root) {
        List<OutputNode> result = new ArrayList<>();
        collectOutputs(root, "$", List.of(), result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectOutputs(Object value, String path, List<String> keys, List<OutputNode> result) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        if (truthy(map.get("@istemplate"))) {
            return;
        }
        if (truthy(map.get("@isoutput"))) {
            result.add(new OutputNode(keys, path, deepCopyMap(map)));
            return;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().startsWith("@")) {
                continue;
            }
            List<String> childKeys = new ArrayList<>(keys);
            childKeys.add(entry.getKey());
            collectOutputs(entry.getValue(), path.equals("$") ? entry.getKey() : path + "." + entry.getKey(), childKeys, result);
        }
    }

    private void writeOutputs(List<OutputNode> outputs) throws IOException {
        Files.createDirectories(outputRoot);
        for (OutputNode output : outputs) {
            Map<String, Object> wrapped = wrapOutput(output.keys(), output.value());
            pruneTemplatesAndMeta(wrapped);
            Path target = outputRoot.resolve(outputFileName(output.keys()));
            Files.createDirectories(target.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<String, Object> entry : wrapped.entrySet()) {
                yaml.set(entry.getKey(), entry.getValue());
            }
            Files.writeString(target, yaml.saveToString(), StandardCharsets.UTF_8);
        }
    }

    private void clearOutputDirectory() throws IOException {
        if (!Files.isDirectory(outputRoot)) {
            return;
        }
        try (var stream = Files.walk(outputRoot)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                if (!path.equals(outputRoot)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private Map<String, Object> wrapOutput(List<String> keys, Map<String, Object> value) {
        if (keys.isEmpty()) {
            return value;
        }
        Map<String, Object> child = value;
        for (int i = keys.size() - 1; i >= 0; i--) {
            Map<String, Object> parent = new LinkedHashMap<>();
            parent.put(keys.get(i), child);
            child = parent;
        }
        return child;
    }

    private String outputFileName(List<String> keys) {
        if (keys.isEmpty()) {
            return "all.yml";
        }
        List<String> safe = new ArrayList<>();
        for (String key : keys) {
            safe.add(key.replace(':', '_').replace('#', '_').replaceAll("[^A-Za-z0-9_.-]", "_"));
        }
        return String.join("/", safe) + ".yml";
    }

    private void writeManifest(List<Path> sources, List<OutputNode> outputs) throws IOException {
        YamlConfiguration manifest = new YamlConfiguration();
        manifest.set("compiler", "sfx-template-v1");
        manifest.set("compiled-at", Instant.now().toString());
        manifest.set("source-root", sourceRoot.toString());
        manifest.set("output-root", outputRoot.toString());
        List<String> sourceNames = sources.stream().map(path -> sourceRoot.relativize(path).toString().replace('\\', '/')).toList();
        manifest.set("sources", sourceNames);
        manifest.set("outputs", outputs.stream().map(output -> outputFileName(output.keys())).toList());
        manifest.set("warnings", warnings);
        Files.writeString(outputRoot.resolve("_manifest.yml"), manifest.saveToString(), StandardCharsets.UTF_8);
    }

    private void writeSourceMap() throws IOException {
        YamlConfiguration sourceMap = new YamlConfiguration();
        for (Map.Entry<String, Origin> entry : origins.entrySet()) {
            String key = entry.getKey().replace('$', '_').replace(':', '_').replace('#', '_').replace('[', '_').replace(']', '_');
            sourceMap.set("origins." + key + ".source", entry.getValue().source());
            sourceMap.set("origins." + key + ".path", entry.getValue().path());
            sourceMap.set("origins." + key + ".operation", entry.getValue().operation());
        }
        Files.writeString(outputRoot.resolve("_source-map.yml"), sourceMap.saveToString(), StandardCharsets.UTF_8);
    }

    private int outputFileCount() {
        if (!Files.isDirectory(outputRoot)) {
            return 0;
        }
        try (var stream = Files.walk(outputRoot)) {
            return (int) stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return (name.endsWith(".yml") || name.endsWith(".yaml")) && !name.startsWith("_");
                    })
                    .count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeInto(Map<String, Object> target, Map<String, Object> source, String origin, String targetPath, boolean warnOverrides) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object incoming = normalizeValue(entry.getValue());
            Object existing = normalizeValue(target.get(entry.getKey()));
            String childPath = targetPath.equals("$") ? entry.getKey() : targetPath + "." + entry.getKey();
            if (existing instanceof Map<?, ?> existingMap && incoming instanceof Map<?, ?> incomingMap) {
                mergeInto((Map<String, Object>) existingMap, (Map<String, Object>) incomingMap, origin, childPath, warnOverrides);
            } else {
                if (warnOverrides && target.containsKey(entry.getKey()) && !Objects.equals(existing, incoming)) {
                    warnings.add("Template merge override at " + childPath + " from " + originOf(childPath) + " by " + origin);
                }
                target.put(entry.getKey(), incoming);
                origins.put(childPath, new Origin(origin, childPath, "merge"));
            }
        }
    }

    private Object normalizeValue(Object value) {
        return value instanceof ConfigurationSection section ? sectionToPlainMap(section) : value;
    }

    private Map<String, Object> sectionToPlainMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            map.put(key, normalizeValue(value));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                result.put(entry.getKey(), deepCopyMap((Map<String, Object>) map));
            } else if (value instanceof List<?> list) {
                result.put(entry.getKey(), deepCopyList(list));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> deepCopyList(List<?> input) {
        List<Object> result = new ArrayList<>();
        for (Object value : input) {
            if (value instanceof Map<?, ?> map) {
                result.add(deepCopyMap((Map<String, Object>) map));
            } else if (value instanceof List<?> list) {
                result.add(deepCopyList(list));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private Reference resolveReference(String reference, String currentPath) {
        String normalized = normalizePath(reference);
        if (!reference.contains(".") && !currentPath.equals("$")) {
            int lastDot = currentPath.lastIndexOf('.');
            String parent = lastDot < 0 ? "$" : currentPath.substring(0, lastDot);
            String relative = parent.equals("$") ? reference : parent + "." + reference;
            if (getPath(relative) != null) {
                return new Reference(relative);
            }
        }
        return new Reference(normalized);
    }

    private String normalizePath(String path) {
        String trimmed = path.trim();
        return trimmed.startsWith("$.") || trimmed.equals("$") ? trimmed : trimmed;
    }

    private Object getPath(String path) {
        return getPath(path, null);
    }

    @SuppressWarnings("unchecked")
    private Object getPath(Map<String, Object> root, String path) {
        String normalized = normalizePath(path);
        if (normalized.equals("$")) {
            return root;
        }
        String[] parts = normalized.startsWith("$.") ? normalized.substring(2).split("\\.") : normalized.split("\\.");
        Object cursor = root;
        for (String part : parts) {
            if (!(cursor instanceof Map<?, ?> map)) {
                return null;
            }
            cursor = ((Map<String, Object>) map).get(part);
        }
        return cursor;
    }

    @SuppressWarnings("unchecked")
    private Object getPath(String path, Object unused) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (SourceNode source : sourcesByPath.values()) {
            mergeInto(root, source.root(), source.relativePath(), "$", false);
        }
        return getPath(root, path);
    }

    private String originOf(String path) {
        Origin origin = origins.get(path);
        return origin == null ? path : origin.source() + " " + origin.path();
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private record SourceNode(String relativePath, Map<String, Object> root) {
    }

    private record Origin(String source, String path, String operation) {
    }

    private record Reference(String path) {
    }

    private record MergeDirective(String sourcePath, String targetPath, Map<String, Object> payload) {
    }

    private record OutputNode(List<String> keys, String path, Map<String, Object> value) {
    }
}
