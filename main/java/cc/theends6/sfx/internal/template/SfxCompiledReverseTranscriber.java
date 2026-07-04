package cc.theends6.sfx.internal.template;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SfxCompiledReverseTranscriber {
    private static final Pattern META_KEY = Pattern.compile("^(\\s*)(@[A-Za-z][A-Za-z0-9_-]*)(\\s*):(.*)$");

    private SfxCompiledReverseTranscriber() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        ReverseTranscriber transcriber = new ReverseTranscriber(
                arguments.sourceRoot(),
                arguments.baselineCompiledRoot(),
                arguments.serverCompiledRoot(),
                arguments.reportRoot());
        ReverseReport report = transcriber.run();
        if (!report.unmapped().isEmpty() || !report.conflicts().isEmpty() || !report.roundtripDiffs().isEmpty()) {
            throw new IllegalStateException("SFX reverse transcription finished with "
                    + report.unmapped().size() + " unmapped change(s), "
                    + report.conflicts().size() + " conflict(s), and "
                    + report.roundtripDiffs().size() + " roundtrip diff(s). See " + report.reportDir());
        }
    }

    private record Arguments(Path sourceRoot, Path baselineCompiledRoot, Path serverCompiledRoot, Path reportRoot) {
        private static Arguments parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String arg : args) {
                int split = arg.indexOf('=');
                if (split <= 2 || !arg.startsWith("--")) {
                    throw new IllegalArgumentException("Expected --key=value argument, got: " + arg);
                }
                values.put(arg.substring(2, split), arg.substring(split + 1));
            }
            Path sourceRoot = requiredPath(values, "source-root");
            Path baseline = requiredPath(values, "baseline-compiled");
            Path server = requiredPath(values, "server-compiled");
            Path report = values.containsKey("report-root")
                    ? Path.of(values.get("report-root"))
                    : Path.of("build", "sfx-reverse-transcribe");
            return new Arguments(sourceRoot, baseline, server, report);
        }

        private static Path requiredPath(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing --" + key + "=...");
            }
            return Path.of(value).toAbsolutePath().normalize();
        }
    }

    private static final class ReverseTranscriber {
        private final Path sourceRoot;
        private final Path contentRoot;
        private final Path baselineRoot;
        private final Path serverRoot;
        private final Path reportRoot;
        private final Map<String, OutputMapping> outputs;
        private final Map<String, OriginMapping> origins;
        private final List<AppliedChange> applied = new ArrayList<>();
        private final List<SkippedChange> unmapped = new ArrayList<>();
        private final List<String> conflicts = new ArrayList<>();
        private final Map<String, Object> plannedSourceValues = new LinkedHashMap<>();
        private final Map<Path, YamlConfiguration> sourceYamls = new LinkedHashMap<>();
        private final Map<Path, String> originalSourceTexts = new LinkedHashMap<>();
        private final Set<Path> dirtySources = new LinkedHashSet<>();

        ReverseTranscriber(Path sourceRoot, Path baselineRoot, Path serverRoot, Path reportRoot) {
            this.sourceRoot = requireDirectory(sourceRoot, "source root");
            this.contentRoot = this.sourceRoot.getParent() == null ? this.sourceRoot : this.sourceRoot.getParent();
            this.baselineRoot = requireDirectory(baselineRoot, "baseline compiled root");
            this.serverRoot = requireDirectory(serverRoot, "server compiled root");
            this.reportRoot = reportRoot.toAbsolutePath().normalize();
            YamlConfiguration sourceMap = load(baselineRoot.resolve("_source-map.yml").toFile());
            this.outputs = outputMappings(sourceMap);
            this.origins = originMappings(sourceMap);
        }

        ReverseReport run() throws IOException {
            Path reportDir = reportRoot.resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneId.systemDefault()).format(Instant.now()));
            Files.createDirectories(reportDir);
            compareCompiledTrees();
            for (Path source : dirtySources) {
                saveSource(source, sourceYamls.get(source));
            }
            writePatch(reportDir);
            List<String> roundtripDiffs = roundtrip(reportDir);
            ReverseReport report = new ReverseReport(reportDir, applied, unmapped, conflicts, roundtripDiffs);
            writeReports(report);
            return report;
        }

        private void compareCompiledTrees() {
            for (Path serverFile : yamlFiles(serverRoot)) {
                String relative = slash(serverRoot.relativize(serverFile));
                if (relative.startsWith("_")) {
                    continue;
                }
                Path baselineFile = baselineRoot.resolve(relative);
                if (!Files.isRegularFile(baselineFile)) {
                    unmapped.add(new SkippedChange(relative, "", "baseline compiled file missing"));
                    continue;
                }
                Object baseline = normalize(load(baselineFile.toFile()));
                Object edited = normalize(load(serverFile.toFile()));
                diff(relative, List.of(), baseline, edited);
            }
        }

        @SuppressWarnings("unchecked")
        private void diff(String file, List<PathToken> path, Object baseline, Object edited) {
            if (Objects.equals(baseline, edited)) {
                return;
            }
            if (baseline instanceof Map<?, ?> baseMap && edited instanceof Map<?, ?> editMap) {
                Set<Object> keys = new LinkedHashSet<>();
                keys.addAll(baseMap.keySet());
                keys.addAll(editMap.keySet());
                for (Object key : keys) {
                    List<PathToken> child = new ArrayList<>(path);
                    child.add(PathToken.key(String.valueOf(key)));
                    diff(file, child, baseMap.get(key), editMap.get(key));
                }
                return;
            }
            if (baseline instanceof List<?> baseList && edited instanceof List<?> editList && keyedList(baseList) && keyedList(editList)) {
                Map<String, Object> baseById = byId(baseList);
                Map<String, Object> editById = byId(editList);
                Set<String> keys = new LinkedHashSet<>();
                keys.addAll(baseById.keySet());
                keys.addAll(editById.keySet());
                for (String key : keys) {
                    List<PathToken> child = new ArrayList<>(path);
                    child.add(PathToken.id(key));
                    diff(file, child, baseById.get(key), editById.get(key));
                }
                return;
            }
            applyChange(file, path, edited);
        }

        private void applyChange(String file, List<PathToken> compiledPath, Object value) {
            SourceTarget target = resolveTarget(file, compiledPath);
            if (target == null) {
                unmapped.add(new SkippedChange(file, pathString(compiledPath), "no unique source target"));
                return;
            }
            try {
                YamlConfiguration yaml = sourceYaml(target.sourceFile());
                String planKey = target.sourceFile() + "#" + target.sourcePath();
                Object normalizedValue = normalize(value);
                Object planned = plannedSourceValues.get(planKey);
                if (planned != null && !Objects.equals(planned, normalizedValue)) {
                    conflicts.add(target.sourceFile() + " " + target.sourcePath()
                            + " receives multiple different values");
                    return;
                }
                plannedSourceValues.put(planKey, normalizedValue);
                setYamlValue(yaml, target.sourcePath(), denormalize(value));
                dirtySources.add(target.sourceFile());
                applied.add(new AppliedChange(file, pathString(compiledPath), relativeSource(target.sourceFile()), target.sourcePath(), value));
            } catch (Exception ex) {
                unmapped.add(new SkippedChange(file, pathString(compiledPath), ex.getMessage()));
            }
        }

        @SuppressWarnings("unchecked")
        private void setYamlValue(YamlConfiguration yaml, String path, Object value) {
            Object root = normalize(yaml);
            if (!(root instanceof Map<?, ?>)) {
                root = new LinkedHashMap<String, Object>();
            }
            setIn((Map<String, Object>) root, path.isBlank() ? List.of() : List.of(path.split("\\.")), value);
            for (String key : new ArrayList<>(yaml.getKeys(false))) {
                yaml.set(key, null);
            }
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) root).entrySet()) {
                yaml.set(entry.getKey(), denormalize(entry.getValue()));
            }
        }

        @SuppressWarnings("unchecked")
        private void setIn(Object container, List<String> path, Object value) {
            if (path.isEmpty()) {
                return;
            }
            String key = path.getFirst();
            boolean last = path.size() == 1;
            if (container instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = (Map<String, Object>) rawMap;
                if (last) {
                    if (value == null) {
                        map.remove(key);
                    } else {
                        map.put(key, value);
                    }
                    return;
                }
                Object child = map.get(key);
                if (child == null) {
                    child = isInteger(path.get(1)) ? new ArrayList<>() : new LinkedHashMap<String, Object>();
                    map.put(key, child);
                }
                setIn(child, path.subList(1, path.size()), value);
                return;
            }
            if (container instanceof List<?> rawList && isInteger(key)) {
                List<Object> list = (List<Object>) rawList;
                int index = Integer.parseInt(key);
                while (list.size() <= index) {
                    list.add(null);
                }
                if (last) {
                    list.set(index, value);
                    return;
                }
                Object child = list.get(index);
                if (child == null) {
                    child = isInteger(path.get(1)) ? new ArrayList<>() : new LinkedHashMap<String, Object>();
                    list.set(index, child);
                }
                setIn(child, path.subList(1, path.size()), value);
            }
        }

        private static boolean isInteger(String value) {
            for (int index = 0; index < value.length(); index++) {
                if (!Character.isDigit(value.charAt(index))) {
                    return false;
                }
            }
            return !value.isEmpty();
        }

        private String relativeSource(Path sourceFile) {
            if (sourceFile.startsWith(sourceRoot)) {
                return slash(sourceRoot.relativize(sourceFile));
            }
            if (sourceFile.startsWith(contentRoot)) {
                return slash(contentRoot.relativize(sourceFile));
            }
            return sourceFile.toString();
        }

        private SourceTarget resolveTarget(String file, List<PathToken> compiledPath) {
            OutputMapping output = outputs.get(file);
            if (output == null) {
                return null;
            }
            EntryTarget entry = entryTarget(output, compiledPath);
            if (entry == null) {
                return null;
            }
            String sourcePath = entry.sourcePath();
            int consumed = entry.consumedTokens();
            for (int i = consumed; i < compiledPath.size(); i++) {
                PathToken token = compiledPath.get(i);
                if (!token.key()) {
                    return null;
                }
                if ("id".equals(token.value())) {
                    continue;
                }
                sourcePath = appendPath(sourcePath, token.value());
            }
            String source = entry.sourceFile();
            if (source == null || source.isBlank()) {
                OriginMapping origin = origins.get(entry.sourcePath());
                if (origin != null) {
                    source = origin.source();
                }
            }
            if (source == null || source.isBlank()) {
                return null;
            }
            Path sourceFile = resolveSourceFile(source);
            if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
                return null;
            }
            return new SourceTarget(sourceFile, sourcePath);
        }

        private static String appendPath(String base, String child) {
            return base == null || base.isBlank() ? child : base + "." + child;
        }

        private Path resolveSourceFile(String source) {
            Path direct = sourceRoot.resolve(source).normalize();
            if (direct.startsWith(sourceRoot) && Files.isRegularFile(direct)) {
                return direct;
            }
            Path contentRelative = contentRoot.resolve(source).normalize();
            if (contentRelative.startsWith(contentRoot) && Files.isRegularFile(contentRelative)) {
                return contentRelative;
            }
            return null;
        }

        private EntryTarget entryTarget(OutputMapping output, List<PathToken> compiledPath) {
            String target = output.target();
            String fallbackSource = output.source() == null || output.source().isBlank()
                    ? inferredSource(output.file())
                    : output.source();
            if (target.endsWith("content/items.yml") && compiledPath.size() >= 2 && key(compiledPath, 0, "items") && !compiledPath.get(1).key()) {
                String sourcePath = "items." + compiledPath.get(1).value();
                return new EntryTarget(sourcePath, originSource(sourcePath, fallbackSource), 2);
            }
            if (target.endsWith("content/recipes.yml") && compiledPath.size() >= 2 && key(compiledPath, 0, "recipes")) {
                String recipeId = compiledPath.get(1).value();
                String sourcePath = "recipes." + recipeId;
                return new EntryTarget(sourcePath, originSource(sourcePath, fallbackSource), 2);
            }
            if (target.endsWith("content/machines/machine-catalog.yml") && compiledPath.size() >= 2 && key(compiledPath, 0, "machines")) {
                String sourcePath = "machine-catalog." + compiledPath.get(1).value();
                return new EntryTarget(sourcePath, originSource(sourcePath, fallbackSource), 2);
            }
            if (target.endsWith("content/machines/electric-machines.yml") && compiledPath.size() >= 2 && key(compiledPath, 0, "machines")) {
                String sourcePath = "machines." + compiledPath.get(1).value();
                return new EntryTarget(sourcePath, originSource(sourcePath, fallbackSource), 2);
            }
            if (target.endsWith("content/machines/electric-recipes.yml") && compiledPath.size() >= 2
                    && key(compiledPath, 0, "electric-recipes") && !compiledPath.get(1).key()) {
                String recipePath = electricRecipeSourcePath(compiledPath.get(1).value());
                if (recipePath != null) {
                    return new EntryTarget(recipePath, fallbackSource, 2);
                }
            }
            if (output.sourcePath() != null) {
                String basePath = sourcePathFromOutput(output.sourcePath());
                int consumed = consumedByBasePath(basePath, compiledPath);
                return new EntryTarget(basePath, fallbackSource, consumed);
            }
            return null;
        }

        private String inferredSource(String outputFile) {
            if (outputFile == null) {
                return null;
            }
            return switch (outputFile) {
                case "content/items/items.yml" -> "items.yml";
                case "content/items/10-legacy-categories.yml" -> "items/10-legacy-categories.yml";
                case "content/items/20-legacy-items.yml" -> "items/20-legacy-items.yml";
                case "content/researches/10-legacy-slimefun.yml" -> "researches/10-legacy-slimefun.yml";
                case "content/legacy-item-behavior/legacy-item-behavior.yml" -> "legacy-item-behavior.yml";
                case "content/machines/manual-machines/manual-machines.yml" -> "machines/manual-machines.yml";
                case "content/machines/electric-recipes/electric-recipes.yml" -> "machines/electric-recipes.yml";
                default -> null;
            };
        }

        private String electricRecipeSourcePath(String recipeId) {
            Path sourceFile = contentRoot.resolve("machines").resolve("electric-recipes.yml").normalize();
            if (!Files.isRegularFile(sourceFile)) {
                return null;
            }
            try {
                YamlConfiguration yaml = sourceYaml(sourceFile);
                ConfigurationSection groups = yaml.getConfigurationSection("recipe-groups");
                if (groups == null) {
                    return null;
                }
                for (String groupId : groups.getKeys(false)) {
                    List<Map<?, ?>> recipes = groups.getMapList(groupId + ".recipes");
                    for (int index = 0; index < recipes.size(); index++) {
                        if (recipeId.equals(String.valueOf(recipes.get(index).get("id")))) {
                            return "recipe-groups." + groupId + ".recipes." + index;
                        }
                    }
                }
            } catch (IOException ex) {
                return null;
            }
            return null;
        }

        private static String sourcePathFromOutput(String outputSourcePath) {
            if ("$".equals(outputSourcePath)) {
                return "";
            }
            return outputSourcePath.startsWith("$.") ? outputSourcePath.substring(2) : outputSourcePath;
        }

        private static int consumedByBasePath(String basePath, List<PathToken> compiledPath) {
            if (basePath == null || basePath.isBlank()) {
                return 0;
            }
            String[] parts = basePath.split("\\.");
            for (int index = 0; index < parts.length; index++) {
                if (compiledPath.size() <= index || !compiledPath.get(index).key() || !parts[index].equals(compiledPath.get(index).value())) {
                    return 0;
                }
            }
            return parts.length;
        }

        private String originSource(String sourcePath, String fallback) {
            String source = originSource(sourcePath);
            return source == null ? fallback : source;
        }

        private String originSource(String sourcePath) {
            OriginMapping origin = origins.get(sourcePath);
            return origin == null ? null : origin.source();
        }

        private static boolean key(List<PathToken> path, int index, String key) {
            return path.size() > index && path.get(index).key() && key.equals(path.get(index).value());
        }

        private YamlConfiguration sourceYaml(Path sourceFile) throws IOException {
            YamlConfiguration yaml = sourceYamls.get(sourceFile);
            if (yaml != null) {
                return yaml;
            }
            String text = Files.readString(sourceFile, StandardCharsets.UTF_8);
            originalSourceTexts.put(sourceFile, text);
            YamlConfiguration loaded = new YamlConfiguration();
            try {
                loaded.loadFromString(quoteTemplateMetaKeys(text));
            } catch (org.bukkit.configuration.InvalidConfigurationException ex) {
                throw new IOException("Failed to parse source template " + sourceFile + ": " + ex.getMessage(), ex);
            }
            sourceYamls.put(sourceFile, loaded);
            return loaded;
        }

        private void saveSource(Path sourceFile, YamlConfiguration yaml) throws IOException {
            Files.writeString(sourceFile, yaml.saveToString(), StandardCharsets.UTF_8);
        }

        private void writePatch(Path reportDir) throws IOException {
            if (dirtySources.isEmpty()) {
                Files.writeString(reportDir.resolve("applied-source-patches.patch"), "", StandardCharsets.UTF_8);
                return;
            }
            StringBuilder patch = new StringBuilder();
            for (Path source : dirtySources) {
                String before = originalSourceTexts.getOrDefault(source, "");
                String after = Files.readString(source, StandardCharsets.UTF_8);
                patch.append(simplePatch(relativeSource(source), before, after));
            }
            Files.writeString(reportDir.resolve("applied-source-patches.patch"), patch.toString(), StandardCharsets.UTF_8);
        }

        private static String simplePatch(String file, String before, String after) {
            if (Objects.equals(before, after)) {
                return "";
            }
            List<String> beforeLines = before.lines().toList();
            List<String> afterLines = after.lines().toList();
            int prefix = 0;
            while (prefix < beforeLines.size() && prefix < afterLines.size()
                    && Objects.equals(beforeLines.get(prefix), afterLines.get(prefix))) {
                prefix++;
            }
            int suffix = 0;
            while (suffix < beforeLines.size() - prefix && suffix < afterLines.size() - prefix
                    && Objects.equals(beforeLines.get(beforeLines.size() - 1 - suffix), afterLines.get(afterLines.size() - 1 - suffix))) {
                suffix++;
            }
            int context = 3;
            int beforeStart = Math.max(0, prefix - context);
            int afterStart = Math.max(0, prefix - context);
            int beforeEnd = Math.min(beforeLines.size(), beforeLines.size() - suffix + context);
            int afterEnd = Math.min(afterLines.size(), afterLines.size() - suffix + context);
            StringBuilder patch = new StringBuilder();
            patch.append("diff --git a/").append(file).append(" b/").append(file).append('\n');
            patch.append("--- a/").append(file).append('\n');
            patch.append("+++ b/").append(file).append('\n');
            patch.append("@@ -").append(beforeStart + 1).append(',').append(beforeEnd - beforeStart)
                    .append(" +").append(afterStart + 1).append(',').append(afterEnd - afterStart).append(" @@\n");
            for (int index = beforeStart; index < prefix; index++) {
                patch.append(' ').append(beforeLines.get(index)).append('\n');
            }
            for (int index = prefix; index < beforeLines.size() - suffix; index++) {
                patch.append('-').append(beforeLines.get(index)).append('\n');
            }
            for (int index = prefix; index < afterLines.size() - suffix; index++) {
                patch.append('+').append(afterLines.get(index)).append('\n');
            }
            for (int index = Math.max(prefix, afterLines.size() - suffix); index < afterEnd; index++) {
                patch.append(' ').append(afterLines.get(index)).append('\n');
            }
            return patch.toString();
        }

        private List<String> roundtrip(Path reportDir) throws IOException {
            Path roundtripRoot = reportDir.resolve("compiled-roundtrip");
            SfxTemplateCompileReport compileReport = new SfxTemplateCompiler(sourceRoot, roundtripRoot).compile();
            List<String> log = new ArrayList<>();
            log.add("ok: " + compileReport.ok());
            log.add("source-files: " + compileReport.sourceFiles());
            log.add("output-files: " + compileReport.outputFiles());
            compileReport.warnings().forEach(warning -> log.add("warning: " + warning));
            compileReport.errors().forEach(error -> log.add("error: " + error));
            Files.write(reportDir.resolve("compile.log"), log, StandardCharsets.UTF_8);
            if (!compileReport.ok()) {
                return List.of("roundtrip compile failed");
            }
            List<String> diffs = new ArrayList<>();
            for (Path serverFile : yamlFiles(serverRoot)) {
                String relative = slash(serverRoot.relativize(serverFile));
                if (relative.startsWith("_")) {
                    continue;
                }
                Path roundtripFile = roundtripRoot.resolve(relative);
                if (!Files.isRegularFile(roundtripFile)) {
                    diffs.add(relative + ": roundtrip file missing");
                    continue;
                }
                Object left = normalize(load(roundtripFile.toFile()));
                Object right = normalize(load(serverFile.toFile()));
                if (!Objects.equals(left, right)) {
                    diffs.add(relative);
                }
            }
            Files.write(reportDir.resolve("roundtrip-diff.patch"), diffs, StandardCharsets.UTF_8);
            return diffs;
        }

        private void writeReports(ReverseReport report) throws IOException {
            YamlConfiguration summary = new YamlConfiguration();
            summary.set("input.server-compiled", serverRoot.toString());
            summary.set("input.baseline-compiled", baselineRoot.toString());
            summary.set("input.source-root", sourceRoot.toString());
            summary.set("report-dir", report.reportDir().toString());
            summary.set("applied-count", report.applied().size());
            summary.set("unmapped-count", report.unmapped().size());
            summary.set("conflict-count", report.conflicts().size());
            summary.set("roundtrip-diff-count", report.roundtripDiffs().size());
            summary.set("success", report.unmapped().isEmpty() && report.conflicts().isEmpty() && report.roundtripDiffs().isEmpty());
            Files.writeString(report.reportDir().resolve("summary.yml"), summary.saveToString(), StandardCharsets.UTF_8);

            YamlConfiguration changes = new YamlConfiguration();
            changes.set("changes", report.applied().stream().map(AppliedChange::toMap).toList());
            Files.writeString(report.reportDir().resolve("changes.yml"), changes.saveToString(), StandardCharsets.UTF_8);

            YamlConfiguration unmappedYaml = new YamlConfiguration();
            unmappedYaml.set("unmapped", report.unmapped().stream().map(SkippedChange::toMap).toList());
            Files.writeString(report.reportDir().resolve("unmapped.yml"), unmappedYaml.saveToString(), StandardCharsets.UTF_8);

            YamlConfiguration conflictYaml = new YamlConfiguration();
            conflictYaml.set("conflicts", report.conflicts());
            Files.writeString(report.reportDir().resolve("conflicts.yml"), conflictYaml.saveToString(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, OutputMapping> outputMappings(YamlConfiguration sourceMap) {
        Map<String, OutputMapping> result = new LinkedHashMap<>();
        for (Map<?, ?> entry : sourceMap.getMapList("outputs")) {
            String file = string(entry.get("file"));
            if (file == null) {
                continue;
            }
            result.put(file, new OutputMapping(file, string(entry.get("target")), string(entry.get("source-path")), string(entry.get("source"))));
        }
        return result;
    }

    private static Map<String, OriginMapping> originMappings(YamlConfiguration sourceMap) {
        Map<String, OriginMapping> result = new LinkedHashMap<>();
        ConfigurationSection origins = sourceMap.getConfigurationSection("origins");
        collectOrigins(origins, result);
        return result;
    }

    private static void collectOrigins(ConfigurationSection section, Map<String, OriginMapping> result) {
        if (section == null) {
            return;
        }
        String source = section.getString("source");
        String path = section.getString("path");
        if (source != null && path != null) {
            result.put(path, new OriginMapping(source, path, section.getString("operation")));
        }
        for (String key : section.getKeys(false)) {
            Object child = section.get(key);
            if (child instanceof ConfigurationSection childSection) {
                collectOrigins(childSection, result);
            }
        }
    }

    private static List<Path> yamlFiles(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list YAML files under " + root, ex);
        }
    }

    private static YamlConfiguration load(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    private static Path requireDirectory(Path path, String label) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException(label + " is not a directory: " + normalized);
        }
        return normalized;
    }

    private static String quoteTemplateMetaKeys(String input) {
        StringBuilder result = new StringBuilder(input.length() + 64);
        String[] lines = input.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = META_KEY.matcher(lines[i]);
            if (matcher.matches()) {
                result.append(matcher.group(1))
                        .append('\'').append(matcher.group(2)).append('\'')
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

    private static Object normalize(Object raw) {
        if (raw instanceof YamlConfiguration yaml) {
            return normalize(sectionToMap(yaml));
        }
        if (raw instanceof ConfigurationSection section) {
            return normalize(sectionToMap(section));
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return result;
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(SfxCompiledReverseTranscriber::normalize).toList();
        }
        return raw;
    }

    private static Object denormalize(Object raw) {
        return raw;
    }

    private static Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            result.put(key, section.get(key));
        }
        return result;
    }

    private static boolean keyedList(List<?> list) {
        if (list.isEmpty()) {
            return false;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map) || !map.containsKey("id")) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> byId(List<?> list) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                result.put(String.valueOf(map.get("id")), entry);
            }
        }
        return result;
    }

    private static String pathString(List<PathToken> path) {
        StringBuilder builder = new StringBuilder();
        for (PathToken token : path) {
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(token.key() ? token.value() : "id=" + token.value());
        }
        return builder.toString();
    }

    private static String slash(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String string(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private record PathToken(boolean key, String value) {
        static PathToken key(String value) {
            return new PathToken(true, value);
        }

        static PathToken id(String value) {
            return new PathToken(false, value);
        }
    }

    private record OutputMapping(String file, String target, String sourcePath, String source) {
    }

    private record OriginMapping(String source, String path, String operation) {
    }

    private record EntryTarget(String sourcePath, String sourceFile, int consumedTokens) {
    }

    private record SourceTarget(Path sourceFile, String sourcePath) {
    }

    private record AppliedChange(String compiledFile, String compiledPath, String sourceFile, String sourcePath, Object value) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("compiled-file", compiledFile);
            map.put("compiled-path", compiledPath);
            map.put("source-file", sourceFile);
            map.put("source-path", sourcePath);
            map.put("value", value);
            return map;
        }
    }

    private record SkippedChange(String compiledFile, String compiledPath, String reason) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("compiled-file", compiledFile);
            map.put("compiled-path", compiledPath);
            map.put("reason", reason);
            return map;
        }
    }

    private record ReverseReport(Path reportDir, List<AppliedChange> applied, List<SkippedChange> unmapped,
                                 List<String> conflicts, List<String> roundtripDiffs) {
    }
}
