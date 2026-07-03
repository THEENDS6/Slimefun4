package cc.theends6.sfx.internal.template;

import cc.theends6.sfx.internal.machine.SfxMachineCapability;
import cc.theends6.sfx.internal.machine.SfxMachineCategory;
import cc.theends6.sfx.internal.machine.SfxMachineDefinition;
import cc.theends6.sfx.internal.machine.SfxMachineEffect;
import cc.theends6.sfx.internal.machine.SfxMachineInputProvider;
import cc.theends6.sfx.internal.machine.SfxMachineOutputProvider;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachinePolicyRef;
import cc.theends6.sfx.internal.machine.SfxMachineSpecialProfiles;
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
import org.bukkit.Material;
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
        int sourceFileCount = 0;
        try {
            Files.createDirectories(sourceRoot);
            Files.createDirectories(outputRoot);
            List<Path> sources = listYamlFiles(sourceRoot);
            List<Path> manifestSources = new ArrayList<>(sources);
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
            outputs = groupRecipeOutputs(outputs);
            Path machineCatalogSource = sourceRoot.resolveSibling("machines").resolve("machine-catalog.yml");
            OutputNode machineCatalogOutput = compileMachineCatalogOutput(machineCatalogSource);
            if (machineCatalogOutput != null) {
                outputs.add(machineCatalogOutput);
                manifestSources.add(machineCatalogSource);
            }
            for (ContentPassThroughSource source : contentPassThroughSources()) {
                OutputNode output = compileYamlPassThroughOutput(source);
                if (output != null) {
                    outputs.add(output);
                    manifestSources.add(source.path());
                }
            }
            Path electricRecipeSource = sourceRoot.resolveSibling("machines").resolve("electric-recipes.yml");
            OutputNode electricRecipeOutput = compileElectricRecipeProviderOutput(electricRecipeSource);
            if (electricRecipeOutput != null) {
                outputs.add(electricRecipeOutput);
                manifestSources.add(electricRecipeSource);
            }
            sourceFileCount = manifestSources.size();
            clearOutputDirectory();
            writeOutputs(outputs);
            writeManifest(manifestSources, outputs);
            writeSourceMap();
        } catch (SfxTemplateCompileException ex) {
            errors.add(ex.getMessage());
        } catch (RuntimeException | IOException ex) {
            errors.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        return new SfxTemplateCompileReport(sourceFileCount == 0 ? sourcesByPath.size() : sourceFileCount, outputFileCount(), List.copyOf(warnings), List.copyOf(errors));
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
            case "@d" -> "@define";
            case "@g" -> "@global";
            case "@p" -> "@project";
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
            result.add(new OutputNode(keys, path, activeTarget, deepCopyMap(map), sourceOf(path), null));
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

    @SuppressWarnings("unchecked")
    private List<OutputNode> groupRecipeOutputs(List<OutputNode> outputs) {
        Map<String, Map<String, Object>> groupedRecipes = new LinkedHashMap<>();
        Map<String, OutputNode> groupNodes = new LinkedHashMap<>();
        List<OutputNode> result = new ArrayList<>();
        for (OutputNode output : outputs) {
            if (!isSingleRecipeOutput(output)) {
                result.add(output);
                continue;
            }
            String recipeId = output.keys().get(1);
            Map<String, Object> recipe = deepCopyMap(output.value());
            String groupFile = recipeGroupFile(output, recipe);
            groupedRecipes.computeIfAbsent(groupFile, ignored -> new LinkedHashMap<>()).put(recipeId, recipe);
            groupNodes.putIfAbsent(groupFile, output);
        }
        for (Map.Entry<String, Map<String, Object>> entry : groupedRecipes.entrySet()) {
            OutputNode first = groupNodes.get(entry.getKey());
            result.add(new OutputNode(
                    List.of("recipes"),
                    first == null ? "recipes" : first.path(),
                    first == null ? "content/recipes.yml" : first.targetResource(),
                    entry.getValue(),
                    first == null ? null : first.source(),
                    entry.getKey()));
        }
        return result;
    }

    private boolean isSingleRecipeOutput(OutputNode output) {
        return output != null
                && "content/recipes.yml".equals(output.targetResource())
                && output.keys().size() == 2
                && "recipes".equals(output.keys().get(0));
    }

    private String recipeGroupFile(OutputNode output, Map<String, Object> recipe) {
        String sourceGroup = recipeSourceGroup(output.source());
        String recipeType = stringOrNull(recipe.get("recipe-type"));
        if (recipeType == null) {
            recipeType = stringOrNull(recipe.get("machine"));
        }
        String typeGroup = recipeType == null ? "misc" : recipeType;
        int colon = typeGroup.indexOf(':');
        if (colon >= 0 && colon + 1 < typeGroup.length()) {
            typeGroup = typeGroup.substring(colon + 1);
        }
        return sourceGroup + "/" + slug(typeGroup) + ".yml";
    }

    private String recipeSourceGroup(String source) {
        if (source == null || source.isBlank()) {
            return "misc";
        }
        String normalized = source.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String file = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (file.endsWith(".yml")) {
            file = file.substring(0, file.length() - 4);
        } else if (file.endsWith(".yaml")) {
            file = file.substring(0, file.length() - 5);
        }
        file = file.replaceFirst("^[0-9]+-", "");
        file = file.replaceFirst("^recipes-", "");
        return slug(file);
    }

    private String slug(String raw) {
        String normalized = raw == null ? "misc" : raw.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace('_', '-').replace(':', '-');
        normalized = normalized.replaceAll("[^a-z0-9.-]+", "-").replaceAll("-+", "-");
        normalized = normalized.replaceAll("^-|-$", "");
        return normalized.isBlank() ? "misc" : normalized;
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
        if (machinesRaw instanceof Map<?, ?> rawMachines) {
            for (Object value : rawMachines.values()) {
                if (value instanceof Map<?, ?> rawMachine) {
                    Map<String, Object> machine = (Map<String, Object>) rawMachine;
                    if (isConfigurableUiMachine(machine)) {
                        enrichConfigurableMachineUiPanels(machine);
                    } else {
                        enrichElectricMachineUiSlots(machine);
                    }
                }
            }
        }
        Object componentsRaw = wrapped.get("components");
        if (componentsRaw instanceof Map<?, ?> rawComponents) {
            for (Object value : rawComponents.values()) {
                if (value instanceof Map<?, ?> rawComponent) {
                    enrichElectricMachineUiSlots((Map<String, Object>) rawComponent);
                }
            }
        }
        Object recipesRaw = wrapped.get("recipes");
        if (recipesRaw instanceof Map<?, ?> rawRecipes) {
            for (Map.Entry<?, ?> entry : rawRecipes.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> rawRecipe) {
                    enrichRecipeEntry(String.valueOf(entry.getKey()), (Map<String, Object>) rawRecipe);
                }
            }
        } else if (recipesRaw instanceof List<?> recipes) {
            for (Object entry : recipes) {
                if (entry instanceof Map<?, ?> rawRecipe) {
                    enrichRecipeEntry(null, (Map<String, Object>) rawRecipe);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isConfigurableUiMachine(Map<String, Object> machine) {
        Object uiRaw = machine.get("ui");
        if (!(uiRaw instanceof Map<?, ?> rawUi)) {
            return false;
        }
        return ((Map<String, Object>) rawUi).get("panels") instanceof Map<?, ?>;
    }

    @SuppressWarnings("unchecked")
    private void enrichConfigurableMachineUiPanels(Map<String, Object> machine) {
        Object uiRaw = machine.get("ui");
        if (!(uiRaw instanceof Map<?, ?> rawUi)) {
            return;
        }
        Object panelsRaw = ((Map<String, Object>) rawUi).get("panels");
        if (!(panelsRaw instanceof Map<?, ?> rawPanels)) {
            return;
        }
        for (Object panelRaw : rawPanels.values()) {
            if (panelRaw instanceof Map<?, ?> rawPanel) {
                enrichConfigurableUiPanel((Map<String, Object>) rawPanel);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichConfigurableUiPanel(Map<String, Object> panel) {
        int inventorySize = intValue(panel.get("inventory-size"), 0);
        if (inventorySize <= 0) {
            panel.putIfAbsent("slots", new LinkedHashMap<String, Object>());
            return;
        }
        Map<String, Object> slotDefinitions = new LinkedHashMap<>();
        for (int slot = 0; slot < inventorySize; slot++) {
            slotDefinitions.put(String.valueOf(slot), slot("empty", "locked"));
        }
        Object framesRaw = panel.get("frame");
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
        putConfigurableSlotEntries(slotDefinitions, inventorySize, panel.get("inputs"), "input", "input-filtered");
        putConfigurableSlotEntries(slotDefinitions, inventorySize, panel.get("outputs"), "output", "output-only");
        putConfigurableSlotEntries(slotDefinitions, inventorySize, panel.get("buttons"), "button", "button-click");
        putConfigurableSlotEntries(slotDefinitions, inventorySize, panel.get("status"), "status", "status-display");
        putConfigurableSlotEntries(slotDefinitions, inventorySize, panel.get("display"), "preview", "preview-only");
        panel.remove("frame");
        panel.remove("inputs");
        panel.remove("outputs");
        panel.remove("buttons");
        panel.remove("status");
        panel.remove("display");
        panel.put("slots", slotDefinitions);
        completeElectricUiDefaults(panel);
    }

    @SuppressWarnings("unchecked")
    private void putConfigurableSlotEntries(Map<String, Object> slots, int inventorySize, Object rawEntries, String role, String defaultBehavior) {
        if (!(rawEntries instanceof List<?> entries)) {
            return;
        }
        for (Object rawEntry : entries) {
            if (!(rawEntry instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> entry = (Map<String, Object>) rawMap;
            int rawSlot = intValue(entry.get("slot"), -1);
            if (rawSlot < 0 || rawSlot >= inventorySize) {
                continue;
            }
            Map<String, Object> definition = slot(role, String.valueOf(entry.getOrDefault("behavior", defaultBehavior)));
            copyIfPresent(entry, definition, "accepts");
            copyIfPresent(entry, definition, "action");
            copyIfPresent(entry, definition, "item-source");
            copyIfPresent(entry, definition, "state-index");
            Object itemRaw = entry.get("item");
            if (itemRaw != null) {
                definition.put("item", deepCopyValue(itemRaw));
            }
            slots.put(String.valueOf(rawSlot), definition);
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, deepCopyValue(source.get(key)));
        }
    }

    private void enrichRecipeEntry(String key, Map<String, Object> recipe) {
        if (key != null && !key.isBlank()) {
            recipe.putIfAbsent("id", key);
        }
        recipe.putIfAbsent("runtime", Boolean.FALSE);
        if (recipe.containsKey("matrix")) {
            recipe.put("matrix", explicitRecipeSlots(recipe.get("matrix")));
        }
        if (recipe.containsKey("inputs")) {
            recipe.put("inputs", explicitRecipeSlots(recipe.get("inputs")));
        }
        if (recipe.containsKey("input")) {
            recipe.put("input", explicitRecipeSlot(recipe.get("input")));
        }
        if (recipe.containsKey("outputs")) {
            recipe.put("outputs", explicitRecipeOutputs(recipe.get("outputs")));
        }
        if (recipe.containsKey("random-outputs")) {
            recipe.put("random-outputs", explicitRecipeOutputs(recipe.get("random-outputs")));
        }
    }

    private List<Object> explicitRecipeSlots(Object raw) {
        if (!(raw instanceof List<?> entries)) {
            throw new SfxTemplateCompileException("Recipe slot list must be a list.");
        }
        List<Object> result = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            result.add(explicitRecipeSlot(entry));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> explicitRecipeSlot(Object raw) {
        if (raw == null) {
            return emptyRecipeSlot();
        }
        if (raw instanceof Map<?, ?> map) {
            return explicitRecipeItemMap((Map<String, Object>) map, true);
        }
        if (raw instanceof String text) {
            return explicitRecipeItemToken(text, true);
        }
        throw new SfxTemplateCompileException("Unsupported recipe slot value: " + raw);
    }

    private List<Object> explicitRecipeOutputs(Object raw) {
        if (!(raw instanceof List<?> entries)) {
            throw new SfxTemplateCompileException("Recipe output list must be a list.");
        }
        List<Object> result = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            result.add(explicitRecipeOutput(entry));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> explicitRecipeOutput(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return explicitRecipeItemMap((Map<String, Object>) map, false);
        }
        if (raw instanceof String text) {
            return explicitRecipeItemToken(text, false);
        }
        throw new SfxTemplateCompileException("Unsupported recipe output value: " + raw);
    }

    private Map<String, Object> explicitRecipeItemToken(String raw, boolean allowEmpty) {
        String token = raw == null ? "" : raw.trim();
        if (allowEmpty && (token.isEmpty() || token.equalsIgnoreCase("air") || token.equalsIgnoreCase("empty"))) {
            return emptyRecipeSlot();
        }
        int amount = 1;
        int star = token.lastIndexOf('*');
        if (star > 0 && star + 1 < token.length()) {
            amount = intValue(token.substring(star + 1).trim(), 1);
            token = token.substring(0, star).trim();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (token.contains(":")) {
            result.put("type", "sfx");
            result.put("id", token);
        } else {
            result.put("type", "vanilla");
            result.put("material", parseCatalogMaterial(token).name());
        }
        result.put("amount", amount);
        return result;
    }

    private Map<String, Object> explicitRecipeItemMap(Map<String, Object> raw, boolean allowEmpty) {
        Map<String, Object> result = new LinkedHashMap<>();
        String type = stringOrNull(raw.get("type"));
        if (type == null) {
            if (raw.containsKey("id")) {
                type = "sfx";
            } else if (raw.containsKey("material")) {
                type = "vanilla";
            } else if (allowEmpty) {
                type = "empty";
            } else {
                throw new SfxTemplateCompileException("Recipe output map requires type.");
            }
        }
        type = type.trim().toLowerCase(Locale.ROOT);
        result.put("type", type);
        if ("empty".equals(type)) {
            if (!allowEmpty) {
                throw new SfxTemplateCompileException("Recipe output cannot be empty.");
            }
            result.put("amount", 0);
            return result;
        }
        if ("sfx".equals(type)) {
            result.put("id", requiredCatalogString(raw, "id"));
        } else if ("vanilla".equals(type)) {
            result.put("material", parseCatalogMaterial(requiredCatalogString(raw, "material")).name());
        } else {
            throw new SfxTemplateCompileException("Unsupported recipe item type: " + type);
        }
        result.put("amount", intValue(raw.containsKey("amount") ? raw.get("amount") : 1, 1));
        if (!allowEmpty && raw.containsKey("chance")) {
            result.put("chance", raw.get("chance"));
        }
        return result;
    }

    private Map<String, Object> emptyRecipeSlot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "empty");
        result.put("amount", 0);
        return result;
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
            machine.remove("slots");
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
        machine.remove("slots");
        ui.put("slots", slotDefinitions);
        completeElectricUiDefaults(ui);
    }

    @SuppressWarnings("unchecked")
    private void completeElectricUiDefaults(Map<String, Object> ui) {
        Object framesRaw = ui.get("frame");
        if (framesRaw instanceof List<?> frames) {
            for (Object frameRaw : frames) {
                if (frameRaw instanceof Map<?, ?> rawFrame) {
                    Object itemRaw = ((Map<String, Object>) rawFrame).get("item");
                    if (itemRaw instanceof Map<?, ?> rawItem) {
                        completeUiItem((Map<String, Object>) rawItem);
                    }
                }
            }
        }
        completeNestedUiItems(ui.get("items"));
        Object slotsRaw = ui.get("slots");
        if (slotsRaw instanceof Map<?, ?> slots) {
            for (Object slotRaw : slots.values()) {
                if (slotRaw instanceof Map<?, ?> rawSlot) {
                    Object itemRaw = ((Map<String, Object>) rawSlot).get("item");
                    if (itemRaw instanceof Map<?, ?> rawItem) {
                        completeUiItem((Map<String, Object>) rawItem);
                    }
                }
            }
        }
        Object statusRaw = ui.get("status");
        if (statusRaw instanceof Map<?, ?> status) {
            for (Object templateRaw : status.values()) {
                if (templateRaw instanceof Map<?, ?> rawTemplate) {
                    completeStatusTemplate((Map<String, Object>) rawTemplate);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void completeNestedUiItems(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return;
        }
        Map<String, Object> itemMap = (Map<String, Object>) map;
        if (itemMap.containsKey("material")) {
            completeUiItem(itemMap);
            return;
        }
        for (Object value : itemMap.values()) {
            completeNestedUiItems(value);
        }
    }

    private void completeUiItem(Map<String, Object> item) {
        if (!item.containsKey("name") && !item.containsKey("name-key")) {
            item.put("name", " ");
        }
        if (!item.containsKey("lore") && !item.containsKey("lore-key")) {
            item.put("lore", List.of());
        }
        item.putIfAbsent("glint", Boolean.FALSE);
    }

    private void completeStatusTemplate(Map<String, Object> template) {
        if (!template.containsKey("name") && !template.containsKey("name-key")) {
            template.put("name", " ");
        }
        if (!template.containsKey("lore") && !template.containsKey("lore-key")) {
            template.put("lore", List.of());
        }
        template.putIfAbsent("include-default-lore", Boolean.TRUE);
        template.putIfAbsent("durability-mode", "NONE");
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

    private OutputNode compileMachineCatalogOutput(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(source, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new SfxTemplateCompileException("Cannot load machine catalog source " + relativeSource(source) + ": " + ex.getMessage(), ex);
        }
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if (root == null) {
            throw new SfxTemplateCompileException("Machine catalog source " + relativeSource(source) + " requires machines root section.");
        }
        Map<String, Object> machines = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            SfxMachineDefinition definition = compileMachineCatalogDefinition(id, section);
            machines.put(id, serializeMachineCatalogDefinition(definition));
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("machines", machines);
        return new OutputNode(List.of(), "$.machines", "content/machines/machine-catalog.yml", wrapped, relativeSource(source), "catalog.yml");
    }

    private List<ContentPassThroughSource> contentPassThroughSources() {
        Path machines = sourceRoot.resolveSibling("machines");
        Path contentRoot = sourceRoot.getParent();
        return List.of(
                new ContentPassThroughSource(contentRoot.resolve("items.yml"), "content/items.yml", "items.yml"),
                new ContentPassThroughSource(contentRoot.resolve("items").resolve("10-legacy-categories.yml"), "content/items.yml", "10-legacy-categories.yml"),
                new ContentPassThroughSource(contentRoot.resolve("items").resolve("20-legacy-items.yml"), "content/items.yml", "20-legacy-items.yml"),
                new ContentPassThroughSource(contentRoot.resolve("researches").resolve("10-legacy-slimefun.yml"), "content/researches.yml", "10-legacy-slimefun.yml"),
                new ContentPassThroughSource(contentRoot.resolve("legacy-item-behavior.yml"), "content/legacy-item-behavior.yml", "legacy-item-behavior.yml"),
                new ContentPassThroughSource(machines.resolve("manual-machines.yml"), "content/machines/manual-machines.yml", "manual-machines.yml"),
                new ContentPassThroughSource(machines.resolve("configurable-machines.yml"), "content/machines/configurable-machines.yml", "configurable-machines.yml")
        );
    }

    private OutputNode compileYamlPassThroughOutput(ContentPassThroughSource source) throws IOException {
        if (!Files.isRegularFile(source.path())) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(source.path(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new SfxTemplateCompileException("Cannot load content source " + relativeSource(source.path()) + ": " + ex.getMessage(), ex);
        }
        Map<String, Object> root = plainSectionToMap(yaml);
        if (root.isEmpty()) {
            throw new SfxTemplateCompileException("Content source " + relativeSource(source.path()) + " is empty.");
        }
        return new OutputNode(List.of(), "$", source.targetResource(), root, relativeSource(source.path()), source.fileName());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> plainSectionToMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                map.put(key, plainSectionToMap(child));
            } else if (value instanceof Map<?, ?> childMap) {
                map.put(key, deepCopyMap((Map<String, Object>) childMap));
            } else if (value instanceof List<?> list) {
                map.put(key, deepCopyList(list));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private OutputNode compileElectricRecipeProviderOutput(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(source, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new SfxTemplateCompileException("Cannot load electric recipe source " + relativeSource(source) + ": " + ex.getMessage(), ex);
        }
        ConfigurationSection root = yaml.getConfigurationSection("providers");
        if (root == null) {
            throw new SfxTemplateCompileException("Electric recipe source " + relativeSource(source) + " requires providers root section.");
        }
        Map<String, Object> providers = new LinkedHashMap<>();
        for (String providerId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(providerId);
            if (section == null) {
                continue;
            }
            List<Map<String, Object>> recipes = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("recipes")) {
                recipes.addAll(compileElectricRecipeEntry(raw));
            }
            Map<String, Object> provider = new LinkedHashMap<>();
            provider.put("recipes", recipes);
            providers.put(providerId, provider);
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("providers", providers);
        return new OutputNode(List.of(), "$.providers", "content/machines/electric-recipes.yml", wrapped, relativeSource(source), "electric-recipes.yml");
    }

    private List<Map<String, Object>> compileElectricRecipeEntry(Map<?, ?> entry) {
        String expand = stringOrNull(entry.get("expand"));
        if (expand == null) {
            return List.of(normalizeElectricRecipeEntry(entry));
        }
        if (expand.equalsIgnoreCase("tag")) {
            return compileElectricTagExpansion(entry);
        }
        if (expand.equalsIgnoreCase("materials")) {
            return compileElectricMaterialsExpansion(entry);
        }
        throw new SfxTemplateCompileException("Unsupported electric recipe expansion: " + expand);
    }

    private List<Map<String, Object>> compileElectricTagExpansion(Map<?, ?> entry) {
        String tagName = requiredCatalogString(entry, "tag");
        List<Material> materials = switch (tagName.trim().toUpperCase(Locale.ROOT)) {
            case "SAPLINGS" -> catalogMaterials(
                    "OAK_SAPLING",
                    "SPRUCE_SAPLING",
                    "BIRCH_SAPLING",
                    "JUNGLE_SAPLING",
                    "ACACIA_SAPLING",
                    "DARK_OAK_SAPLING",
                    "MANGROVE_PROPAGULE",
                    "CHERRY_SAPLING",
                    "PALE_OAK_SAPLING");
            case "LEAVES" -> catalogMaterials(
                    "OAK_LEAVES",
                    "SPRUCE_LEAVES",
                    "BIRCH_LEAVES",
                    "JUNGLE_LEAVES",
                    "ACACIA_LEAVES",
                    "DARK_OAK_LEAVES",
                    "MANGROVE_LEAVES",
                    "CHERRY_LEAVES",
                    "PALE_OAK_LEAVES",
                    "AZALEA_LEAVES",
                    "FLOWERING_AZALEA_LEAVES");
            default -> throw new SfxTemplateCompileException("Unsupported electric recipe material tag: " + tagName);
        };
        List<Map<String, Object>> result = new ArrayList<>();
        for (Material material : materials) {
            result.add(compileSingleMaterialExpansion(entry, material));
        }
        return result;
    }

    private List<Map<String, Object>> compileElectricMaterialsExpansion(Map<?, ?> entry) {
        Object raw = entry.get("materials");
        if (!(raw instanceof List<?> materials)) {
            throw new SfxTemplateCompileException("Electric recipe materials expansion requires materials list.");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : materials) {
            Material material = parseCatalogMaterial(String.valueOf(value));
            result.add(compileSingleMaterialExpansion(entry, material));
        }
        return result;
    }

    private Map<String, Object> compileSingleMaterialExpansion(Map<?, ?> entry, Material material) {
        Map<String, Object> recipe = new LinkedHashMap<>();
        recipe.put("id", requiredCatalogString(entry, "id-prefix") + ":" + material.key());
        recipe.put("ticks", requiredCatalogValue(entry, "ticks"));
        List<Object> inputs = new ArrayList<>();
        Object prefixInput = entry.get("input-prefix");
        if (prefixInput instanceof List<?> prefixList) {
            inputs.addAll(deepCopyList(prefixList));
        }
        Map<String, Object> materialInput = new LinkedHashMap<>();
        materialInput.put("material", material.name());
        materialInput.put("amount", intValue(requiredCatalogValue(entry, "input-amount"), 1));
        inputs.add(materialInput);
        recipe.put("inputs", inputs);
        Object outputs = entry.get("outputs");
        if (outputs != null) {
            recipe.put("outputs", deepCopyValue(outputs));
        }
        Object randomOutputs = entry.get("random-outputs");
        if (randomOutputs != null) {
            recipe.put("random-outputs", deepCopyValue(randomOutputs));
        }
        return recipe;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeElectricRecipeEntry(Map<?, ?> entry) {
        if (!entry.containsKey("id")) {
            throw new SfxTemplateCompileException("Electric recipe entry requires id.");
        }
        if (entry.containsKey("expand")) {
            throw new SfxTemplateCompileException("Compiled electric recipe entry must not contain expand.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> raw : entry.entrySet()) {
            if (raw.getKey() == null) {
                continue;
            }
            Object value = raw.getValue();
            if (value instanceof Map<?, ?> map) {
                result.put(String.valueOf(raw.getKey()), deepCopyMap((Map<String, Object>) map));
            } else if (value instanceof List<?> list) {
                result.put(String.valueOf(raw.getKey()), deepCopyList(list));
            } else {
                result.put(String.valueOf(raw.getKey()), value);
            }
        }
        return result;
    }

    private Object requiredCatalogValue(Map<?, ?> map, String path) {
        if (!map.containsKey(path) || map.get(path) == null) {
            throw new SfxTemplateCompileException("machine catalog map entry requires " + path);
        }
        return map.get(path);
    }

    private Material parseCatalogMaterial(String raw) {
        Material material = Material.matchMaterial(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            throw new SfxTemplateCompileException("Unknown material in compiled content source: " + raw);
        }
        return material;
    }

    private List<Material> catalogMaterials(String... names) {
        List<Material> result = new ArrayList<>();
        for (String name : names) {
            result.add(parseCatalogMaterial(name));
        }
        return result;
    }

    private SfxMachineDefinition compileMachineCatalogDefinition(String id, ConfigurationSection section) {
        SfxMachineCategory category = SfxMachineCategory.valueOf(requiredCatalogString(section, "category").trim().replace('-', '_').toUpperCase(Locale.ROOT));
        Set<String> tags = Set.copyOf(strings(section.getList("tags")));
        if (tags.isEmpty()) {
            throw new SfxTemplateCompileException("Machine catalog " + id + " requires at least one tag.");
        }
        SfxMachineDefinition.Builder builder = SfxMachineDefinition.builder(id)
                .displayName(requiredCatalogString(section, "display-name"))
                .category(category)
                .inputSlots(ints(section.getList("input-slots")))
                .outputSlots(ints(section.getList("output-slots")))
                .statusSlot(requiredCatalogInt(section, "status-slot"))
                .tickInterval(Math.max(1, requiredCatalogInt(section, "tick-interval")))
                .tags(tags);
        for (String capability : strings(section.getList("capabilities"))) {
            builder.capability(SfxMachineCapability.valueOf(capability.trim().replace('-', '_').toUpperCase(Locale.ROOT)));
        }
        SfxMachineInputProvider inputProvider = parseCatalogInputProvider(section.getConfigurationSection("input-provider"));
        if (inputProvider != null) {
            builder.inputProvider(inputProvider);
        }
        SfxMachineOutputProvider outputProvider = parseCatalogOutputProvider(section.getConfigurationSection("output-provider"));
        if (outputProvider != null) {
            builder.outputProvider(outputProvider);
        }
        for (Map<?, ?> raw : section.getMapList("policies")) {
            builder.policyRef(SfxMachinePolicyRef.of(requiredCatalogString(raw, "type"), requiredCatalogString(raw, "name")));
        }
        for (Map<?, ?> raw : section.getMapList("effects")) {
            SfxMachinePhase phase = SfxMachinePhase.valueOf(requiredCatalogString(raw, "phase").trim().replace('-', '_').toUpperCase(Locale.ROOT));
            builder.effect(SfxMachineEffect.marker(requiredCatalogString(raw, "name"), phase));
        }
        return SfxMachineSpecialProfiles.apply(builder.build(), section.getString("profile", null));
    }

    private Map<String, Object> serializeMachineCatalogDefinition(SfxMachineDefinition definition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", definition.category().name());
        result.put("display-name", definition.displayName());
        result.put("input-slots", new ArrayList<>(definition.inputSlots()));
        result.put("output-slots", new ArrayList<>(definition.outputSlots()));
        result.put("status-slot", definition.statusSlot());
        result.put("tick-interval", definition.tickInterval());
        result.put("tags", new ArrayList<>(definition.tags().stream().sorted().toList()));
        result.put("capabilities", new ArrayList<>(definition.capabilities().stream().map(Enum::name).sorted().toList()));
        result.put("input-provider", serializeInputProvider(definition.inputProvider()));
        result.put("output-provider", serializeOutputProvider(definition.outputProvider()));
        List<Map<String, Object>> policies = new ArrayList<>();
        for (SfxMachinePolicyRef ref : definition.policyRefs()) {
            Map<String, Object> policy = new LinkedHashMap<>();
            policy.put("type", ref.type());
            policy.put("name", ref.name());
            policies.add(policy);
        }
        result.put("policies", policies);
        List<Map<String, Object>> effects = new ArrayList<>();
        for (SfxMachineEffect effect : definition.effects()) {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("name", effect.name());
            marker.put("phase", effect.phase().name());
            effects.add(marker);
        }
        result.put("effects", effects);
        return result;
    }

    private SfxMachineInputProvider parseCatalogInputProvider(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        SfxMachineInputProvider.Kind kind = SfxMachineInputProvider.Kind.valueOf(requiredCatalogString(section, "kind").trim().replace('-', '_').toUpperCase(Locale.ROOT));
        return new SfxMachineInputProvider(kind, ints(section.getList("slots")), section.getString("description", ""));
    }

    private SfxMachineOutputProvider parseCatalogOutputProvider(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        SfxMachineOutputProvider.Kind kind = SfxMachineOutputProvider.Kind.valueOf(requiredCatalogString(section, "kind").trim().replace('-', '_').toUpperCase(Locale.ROOT));
        return new SfxMachineOutputProvider(kind, ints(section.getList("slots")), section.getString("description", ""));
    }

    private Map<String, Object> serializeInputProvider(SfxMachineInputProvider provider) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", provider.kind().name());
        result.put("slots", new ArrayList<>(provider.slots()));
        result.put("description", provider.description());
        return result;
    }

    private Map<String, Object> serializeOutputProvider(SfxMachineOutputProvider provider) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", provider.kind().name());
        result.put("slots", new ArrayList<>(provider.slots()));
        result.put("description", provider.description());
        return result;
    }

    private String requiredCatalogString(ConfigurationSection section, String path) {
        String value = section.getString(path, null);
        if (value == null || value.isBlank()) {
            throw new SfxTemplateCompileException(section.getCurrentPath() + " requires " + path);
        }
        return value;
    }

    private int requiredCatalogInt(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            throw new SfxTemplateCompileException(section.getCurrentPath() + " requires " + path);
        }
        return section.getInt(path);
    }

    private String requiredCatalogString(Map<?, ?> map, String path) {
        Object value = map.get(path);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new SfxTemplateCompileException("machine catalog map entry requires " + path);
        }
        return String.valueOf(value).trim();
    }

    private String relativeSource(Path source) {
        return sourceRoot.relativize(source).toString().replace('\\', '/');
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
        if (output.relativeOutputFile() != null && !output.relativeOutputFile().isBlank()) {
            return output.relativeOutputFile().replace('\\', '/');
        }
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

    private String sourceOf(String path) {
        Origin origin = origins.get(path);
        return origin == null ? null : origin.source();
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

    private record ContentPassThroughSource(Path path, String targetResource, String fileName) {
    }

    private record OutputNode(List<String> keys, String path, String targetResource, Map<String, Object> value, String source, String relativeOutputFile) {
        private OutputNode(List<String> keys, String path, String targetResource, Map<String, Object> value) {
            this(keys, path, targetResource, value, null, null);
        }
    }
}
