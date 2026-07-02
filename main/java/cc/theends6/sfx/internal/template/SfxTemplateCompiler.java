package cc.theends6.sfx.internal.template;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final Pattern EXACT_PLACEHOLDER = Pattern.compile("^\\$\\{([^{}]+)}$");
    private static final Set<String> META_KEYS = Set.of("@istemplate", "@copyfrom", "@mergeinto", "@isoutput", "@outputtarget", "@define", "@project", "@global");

    private final Path sourceRoot;
    private final Path outputRoot;
    private final Path publishedOutputRoot;
    private final Map<String, Object> globalVariables = new LinkedHashMap<>();
    private final Map<String, Object> projectVariables = new LinkedHashMap<>();
    private final Map<String, SourceNode> sourcesByPath = new LinkedHashMap<>();
    private final Map<String, Origin> origins = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public SfxTemplateCompiler(Path sourceRoot, Path outputRoot) {
        this(sourceRoot, outputRoot, outputRoot);
    }

    public SfxTemplateCompiler(Path sourceRoot, Path outputRoot, Path publishedOutputRoot) {
        this.sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot");
        this.outputRoot = Objects.requireNonNull(outputRoot, "outputRoot");
        this.publishedOutputRoot = Objects.requireNonNull(publishedOutputRoot, "publishedOutputRoot");
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
                outputs.add(new OutputNode(List.of("all"), "$", null, expanded));
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
            case "@ot" -> "@outputtarget";
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
            payload.remove("@outputtarget");
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
            if (truthy(map.get("@istemplate"))) {
                return;
            }
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

    private Object resolveText(String text, String path, ArrayDeque<Map<String, Object>> localScopes) {
        Matcher exact = EXACT_PLACEHOLDER.matcher(text);
        if (exact.matches()) {
            Object value = resolveVariable(exact.group(1), localScopes);
            if (value == null) {
                throw new SfxTemplateCompileException("Unresolved template variable ${" + exact.group(1) + "} at " + path);
            }
            return deepCopyValue(value);
        }
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
        collectOutputs(root, "$", List.of(), null, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectOutputs(Object value, String path, List<String> keys, String inheritedTarget, List<OutputNode> result) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        if (truthy(map.get("@istemplate"))) {
            return;
        }
        String outputTarget = stringOrNull(map.get("@outputtarget"));
        String activeTarget = outputTarget == null ? inheritedTarget : outputTarget;
        if (truthy(map.get("@isoutput"))) {
            result.add(new OutputNode(keys, path, activeTarget, deepCopyMap(map)));
            return;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().startsWith("@")) {
                continue;
            }
            List<String> childKeys = new ArrayList<>(keys);
            childKeys.add(entry.getKey());
            collectOutputs(entry.getValue(), path.equals("$") ? entry.getKey() : path + "." + entry.getKey(), childKeys, activeTarget, result);
        }
    }

    private void writeOutputs(List<OutputNode> outputs) throws IOException {
        Files.createDirectories(outputRoot);
        for (OutputNode output : outputs) {
            Map<String, Object> wrapped = wrapOutput(output.keys(), output.value());
            pruneTemplatesAndMeta(wrapped);
            enrichCompiledOutput(wrapped);
            Path target = outputRoot.resolve(outputDirectory(output.targetResource())).resolve(outputFileName(output));
            Files.createDirectories(target.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<String, Object> entry : wrapped.entrySet()) {
                yaml.set(entry.getKey(), entry.getValue());
            }
            Files.writeString(target, yaml.saveToString(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichCompiledOutput(Map<String, Object> wrapped) {
        Object machinesRaw = wrapped.get("machines");
        if (!(machinesRaw instanceof Map<?, ?> rawMachines)) {
            return;
        }
        for (Object value : rawMachines.values()) {
            if (value instanceof Map<?, ?> rawMachine) {
                enrichElectricMachineUiSlots((Map<String, Object>) rawMachine);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichElectricMachineUiSlots(Map<String, Object> machine) {
        Object uiRaw = machine.get("ui");
        if (!(uiRaw instanceof Map<?, ?> rawUi)) {
            return;
        }
        Map<String, Object> ui = (Map<String, Object>) rawUi;
        int inventorySize = intValue(ui.get("inventory-size"), 0);
        if (inventorySize <= 0) {
            ui.putIfAbsent("slots", new LinkedHashMap<String, Object>());
            return;
        }
        Map<String, Object> slotDefinitions = new LinkedHashMap<>();
        for (int slot = 0; slot < inventorySize; slot++) {
            slotDefinitions.put(String.valueOf(slot), slot("empty", "locked"));
        }
        Object framesRaw = ui.get("frame");
        if (framesRaw instanceof List<?> frames) {
            for (Object frameRaw : frames) {
                if (!(frameRaw instanceof Map<?, ?> rawFrame)) {
                    continue;
                }
                Map<String, Object> frame = (Map<String, Object>) rawFrame;
                Object item = deepCopyValue(frame.get("item"));
                for (Integer slot : ints(frame.get("slots"))) {
                    if (slot >= 0 && slot < inventorySize) {
                        Map<String, Object> definition = slot("decoration", "locked");
                        if (item != null) {
                            definition.put("item", deepCopyValue(item));
                        }
                        slotDefinitions.put(String.valueOf(slot), definition);
                    }
                }
            }
        }
        Object machineSlotsRaw = machine.get("slots");
        if (machineSlotsRaw instanceof Map<?, ?> rawMachineSlots) {
            Map<String, Object> machineSlots = (Map<String, Object>) rawMachineSlots;
            List<Integer> inputSlots = ints(machineSlots.get("input"));
            for (int index = 0; index < inputSlots.size(); index++) {
                Map<String, Object> definition = inputSlot("recipe-input", "any");
                definition.put("state-index", index);
                putSlot(slotDefinitions, inventorySize, inputSlots.get(index), definition);
            }
            List<Integer> outputSlots = ints(machineSlots.get("output"));
            for (int index = 0; index < outputSlots.size(); index++) {
                Map<String, Object> definition = slot("output", "output-only");
                definition.put("state-index", index);
                putSlot(slotDefinitions, inventorySize, outputSlots.get(index), definition);
            }
        }
        int statusSlot = intValue(ui.get("status-slot"), -1);
        if (statusSlot >= 0) {
            Map<String, Object> definition = slot("status", "status-display");
            definition.put("item-source", "machine-status");
            putSlot(slotDefinitions, inventorySize, statusSlot, definition);
        }
        applySpecialElectricUiSlots(machine, slotDefinitions, inventorySize);
        ui.put("slots", slotDefinitions);
    }

    private void applySpecialElectricUiSlots(Map<String, Object> machine, Map<String, Object> slots, int inventorySize) {
        Set<String> functions = Set.copyOf(strings(machine.get("functions")));
        if (functions.contains("auto-crafter")) {
            putSlot(slots, inventorySize, 11, slot("preview", "preview-only"));
            putSlot(slots, inventorySize, 12, slot("preview", "preview-only"));
            putSlot(slots, inventorySize, 13, slot("preview", "preview-only"));
            putSlot(slots, inventorySize, 20, slot("preview", "preview-only"));
            putSlot(slots, inventorySize, 21, slot("preview", "preview-only"));
            putSlot(slots, inventorySize, 22, slot("preview", "preview-only"));
            putSlot(slots, inventorySize, 29, slot("preview", "preview-only"));
            putSlot(slots, inventorySize, 30, slot("preview", "preview-only"));
            putSlot(slots, inventorySize, 31, slot("preview", "preview-only"));
            putSlot(slots, inventorySize, 24, slot("preview", "preview-only"));
            putButton(slots, inventorySize, 45, "auto-crafter-container", "auto-crafter.container");
            putButton(slots, inventorySize, 46, "auto-crafter-previous", "auto-crafter.previous");
            putButton(slots, inventorySize, 49, "auto-crafter-toggle-or-select", "auto-crafter.enabled");
            putButton(slots, inventorySize, 52, "auto-crafter-next", "auto-crafter.next");
        }
        if (functions.contains("auto-brewer")) {
            putSlot(slots, inventorySize, 10, inputSlot("fuel-input", "minecraft:blaze_powder"));
            putSlot(slots, inventorySize, 16, inputSlot("input-filtered", "brewing-ingredient"));
            for (int slot : List.of(37, 39, 41, 43)) {
                putSlot(slots, inventorySize, slot, inputSlot("input-filtered", "potion-bottle"));
            }
            Map<String, Object> fuelDisplay = slot("fuel", "status-display");
            fuelDisplay.put("item-source", "auto-brewer.fuel");
            putSlot(slots, inventorySize, 22, fuelDisplay);
        }
        if (functions.contains("assembler")) {
            putButton(slots, inventorySize, 13, "assembler-toggle", "assembler.enabled");
            putButton(slots, inventorySize, 31, "assembler-offset", "assembler.offset");
            putSlot(slots, inventorySize, 1, previewSlot("assembler.head.display"));
            putSlot(slots, inventorySize, 7, previewSlot("assembler.body.display"));
            putSlot(slots, inventorySize, 19, inputSlot("input-filtered", "assembler-head"));
            putSlot(slots, inventorySize, 28, inputSlot("input-filtered", "assembler-head"));
            putSlot(slots, inventorySize, 25, inputSlot("input-filtered", "assembler-body"));
            putSlot(slots, inventorySize, 34, inputSlot("input-filtered", "assembler-body"));
        }
    }

    private void putButton(Map<String, Object> slots, int inventorySize, int slot, String action, String itemSource) {
        Map<String, Object> definition = slot("button", "button-click");
        definition.put("action", action);
        definition.put("item-source", itemSource);
        putSlot(slots, inventorySize, slot, definition);
    }

    private Map<String, Object> previewSlot(String itemSource) {
        Map<String, Object> definition = slot("preview", "preview-only");
        definition.put("item-source", itemSource);
        return definition;
    }

    private Map<String, Object> inputSlot(String behavior, String accepts) {
        Map<String, Object> definition = slot("input", behavior);
        definition.put("accepts", accepts);
        return definition;
    }

    private void putSlot(Map<String, Object> slots, int inventorySize, int slot, Map<String, Object> definition) {
        if (slot >= 0 && slot < inventorySize) {
            Object existing = slots.get(String.valueOf(slot));
            if (existing instanceof Map<?, ?> existingMap
                    && !definition.containsKey("state-index")
                    && ("input".equals(definition.get("role")) || "output".equals(definition.get("role")))
                    && existingMap.containsKey("state-index")) {
                definition.put("state-index", existingMap.get("state-index"));
            }
            slots.put(String.valueOf(slot), definition);
        }
    }

    private Map<String, Object> slot(String role, String behavior) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("role", role);
        definition.put("behavior", behavior);
        return definition;
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

    private String outputFileName(OutputNode output) {
        return outputFileName(output.keys(), output.targetResource());
    }

    private String outputFileName(List<String> keys, String targetResource) {
        keys = strippedOutputKeys(keys);
        if (keys.isEmpty()) {
            return "all.yml";
        }
        List<String> safe = new ArrayList<>();
        for (String key : keys) {
            safe.add(key.replace(':', '_').replace('#', '_').replaceAll("[^A-Za-z0-9_.-]", "_"));
        }
        return String.join("/", safe) + ".yml";
    }

    private List<String> strippedOutputKeys(List<String> keys) {
        if (keys == null || keys.size() <= 1) {
            return keys == null ? List.of() : keys;
        }
        String first = keys.getFirst();
        if (Set.of("machines", "recipes", "components").contains(first)) {
            return keys.subList(1, keys.size());
        }
        return keys;
    }

    private String outputDirectory(String targetResource) {
        if (targetResource == null || targetResource.isBlank()) {
            return "_global";
        }
        String normalized = targetResource.replace('\\', '/');
        if (normalized.endsWith(".yml")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        } else if (normalized.endsWith(".yaml")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        return normalized;
    }

    private void writeManifest(List<Path> sources, List<OutputNode> outputs) throws IOException {
        YamlConfiguration manifest = new YamlConfiguration();
        manifest.set("compiler", "sfx-template-v1");
        manifest.set("compiler-version", 2);
        manifest.set("compiled-at", Instant.now().toString());
        manifest.set("source-root", sourceRoot.toString());
        manifest.set("output-root", publishedOutputRoot.toString());
        List<String> sourceNames = sources.stream().map(path -> sourceRoot.relativize(path).toString().replace('\\', '/')).toList();
        manifest.set("sources", sourceNames);
        List<Map<String, Object>> sourceEntries = new ArrayList<>();
        List<String> sourceHashParts = new ArrayList<>();
        for (Path source : sources) {
            String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
            String hash = sha256(source);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", relative);
            entry.put("sha256", hash);
            sourceEntries.add(entry);
            sourceHashParts.add(relative + "\n" + hash);
        }
        String templateHash = sha256Strings(sourceHashParts);
        manifest.set("template-hash", templateHash);
        manifest.set("content-version", "template-" + templateHash.substring(0, Math.min(12, templateHash.length())));
        manifest.set("source-files", sourceEntries);
        List<Map<String, Object>> outputEntries = new ArrayList<>();
        for (OutputNode output : outputs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            String file = outputDirectory(output.targetResource()) + "/" + outputFileName(output);
            entry.put("file", file);
            entry.put("target", output.targetResource() == null ? "" : output.targetResource());
            entry.put("source-path", output.path());
            Path outputFile = outputRoot.resolve(file);
            if (Files.isRegularFile(outputFile)) {
                entry.put("sha256", sha256(outputFile));
            }
            outputEntries.add(entry);
        }
        manifest.set("outputs", outputEntries);
        manifest.set("warnings", warnings);
        Files.writeString(outputRoot.resolve("_manifest.yml"), manifest.saveToString(), StandardCharsets.UTF_8);
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String sha256Strings(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
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

    @SuppressWarnings("unchecked")
    private Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return deepCopyList(list);
        }
        return value;
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

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<Integer> ints(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Number number) {
                result.add(number.intValue());
            } else if (entry != null) {
                try {
                    result.add(Integer.parseInt(String.valueOf(entry).trim()));
                } catch (NumberFormatException ignored) {
                    // Non-numeric slot declarations are ignored here and will be caught by schema validation.
                }
            }
        }
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            String text = stringOrNull(entry);
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    private record SourceNode(String relativePath, Map<String, Object> root) {
    }

    private record Origin(String source, String path, String operation) {
    }

    private record Reference(String path) {
    }

    private record MergeDirective(String sourcePath, String targetPath, Map<String, Object> payload) {
    }

    private record OutputNode(List<String> keys, String path, String targetResource, Map<String, Object> value) {
    }
}
