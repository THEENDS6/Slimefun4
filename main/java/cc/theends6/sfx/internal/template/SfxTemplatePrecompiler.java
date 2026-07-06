package cc.theends6.sfx.internal.template;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.internal.addon.SfxAddonJarResources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxTemplatePrecompiler {
    private static final String SOURCE_DIRECTORY = "content/templates";
    private static final String OUTPUT_DIRECTORY = "content/compiled";
    private static final List<String> BUNDLED_TEMPLATE_RESOURCES = List.of(
            "content/templates/00-electric-machine-templates.yml",
            "content/templates/05-energy-component-templates.yml",
            "content/templates/10-electric-machine-definitions.yml",
            "content/templates/12-electric-machine-ui.yml",
            "content/templates/30-energy-components.yml",
            "content/templates/machines/electric/assemblers.yml",
            "content/templates/machines/electric/auto-breeder.yml",
            "content/templates/machines/electric/auto-brewer.yml",
            "content/templates/machines/electric/auto-crafters.yml",
            "content/templates/machines/electric/auto-drier.yml",
            "content/templates/machines/electric/carbon-press.yml",
            "content/templates/machines/electric/electric-dust-washer.yml",
            "content/templates/machines/electric/electric-furnace.yml",
            "content/templates/machines/electric/electric-gold-pan.yml",
            "content/templates/machines/electric/electric-ingot-factory.yml",
            "content/templates/machines/electric/electric-ingot-pulverizer.yml",
            "content/templates/machines/electric/electric-ore-grinder.yml",
            "content/templates/machines/electric/electric-press.yml",
            "content/templates/machines/electric/electric-smeltery.yml",
            "content/templates/machines/electric/electrified-crucible.yml",
            "content/templates/machines/electric/fluid-pump.yml",
            "content/templates/machines/electric/food-composter.yml",
            "content/templates/machines/electric/food-fabricator.yml",
            "content/templates/machines/electric/freezer.yml",
            "content/templates/machines/electric/geo-extractors.yml",
            "content/templates/machines/electric/gps-transmitters.yml",
            "content/templates/machines/electric/growth-accelerators.yml",
            "content/templates/machines/electric/heated-pressure-chamber.yml",
            "content/templates/machines/electric/item-meta-transform-machines.yml",
            "content/templates/machines/electric/produce-collector.yml",
            "content/templates/machines/electric/refinery.yml",
            "content/templates/machines/electric/xp-collector.yml",
            "content/templates/machines/basic/block-utility-machines.yml",
            "content/templates/machines/basic/enhanced-furnaces.yml",
            "content/templates/machines/basic/manual-machines.yml",
            "content/templates/machines/cargo/nodes.yml",
            "content/templates/machines/configurable/reactors.yml",
            "content/templates/machines/gps/devices.yml",
            "content/templates/machines/android/programmed-androids.yml",
            "content/templates/machines/special/ancient-altar.yml",
            "content/templates/machines/special/block-placer.yml",
            "content/templates/machines/special/hologram-projector.yml",
            "content/templates/machines/special/infused-hopper.yml",
            "content/templates/machines/special/industrial-miners.yml",
            "content/templates/machines/special/rainbow-blocks.yml",
            "content/templates/machines/special/reinforced-spawner.yml",
            "content/templates/machines/special/structural-blocks.yml",
            "content/templates/machines/energy/bio-reactors.yml",
            "content/templates/machines/energy/capacitors.yml",
            "content/templates/machines/energy/charging-bench.yml",
            "content/templates/machines/energy/fluid-fuel-generators.yml",
            "content/templates/machines/energy/network-nodes.yml",
            "content/templates/machines/energy/solar-generators.yml",
            "content/templates/machines/energy/solid-fuel-generators.yml",
            "content/templates/recipes/40-recipe-templates.yml",
            "content/templates/recipes/41-recipes-enhanced.yml",
            "content/templates/recipes/42-recipes-magic.yml",
            "content/templates/recipes/43-recipes-altar.yml",
            "content/templates/recipes/44-recipes-smeltery.yml",
            "content/templates/recipes/45-recipes-armor.yml",
            "content/templates/recipes/46-recipes-electric.yml",
            "content/templates/recipes/47-recipes-special.yml"
    );
    private static final List<String> REMOVED_BUNDLED_TEMPLATE_RESOURCES = List.of(
            "content/templates/10-electric-furnaces.yml",
            "content/templates/11-electric-furnaces-v2.yml",
            "content/templates/12-special-electric-ui.yml",
            "content/templates/20-energy-components.yml",
            "content/templates/20-electric-processing-machines.yml",
            "content/templates/21-electric-crafting-machines.yml",
            "content/templates/22-electric-farming-machines.yml",
            "content/templates/23-electric-utility-machines.yml",
            "content/templates/24-electric-assemblers.yml",
            "content/templates/25-electric-misc-machines.yml"
    );

    private SfxTemplatePrecompiler() {
    }

    public static SfxTemplateCompileReport compile(JavaPlugin plugin, boolean overwriteTemplates) {
        ensureBundledTemplates(plugin, overwriteTemplates);
        ensureBundledAddonContent(plugin, overwriteTemplates);
        Path contentRoot = new File(plugin.getDataFolder(), "content").toPath();
        Path outputRoot = new File(plugin.getDataFolder(), OUTPUT_DIRECTORY).toPath();
        Path tempRoot = outputRoot.resolveSibling(outputRoot.getFileName() + ".tmp-" + System.nanoTime());
        Path tempContentRoot = new File(plugin.getDataFolder(), "content-source.tmp-" + System.nanoTime()).toPath();
        try {
            prepareContentSource(plugin, contentRoot, tempContentRoot);
            Path sourceRoot = tempContentRoot.resolve("templates");
            SfxTemplateCompileReport report = new SfxTemplateCompiler(sourceRoot, tempRoot, outputRoot).compile();
            for (String warning : report.warnings()) {
                plugin.getLogger().warning("[template] " + warning);
            }
            if (!report.ok()) {
                deleteDirectory(tempRoot);
                for (String error : report.errors()) {
                    plugin.getLogger().severe("[template] " + error);
                }
                throw new SfxTemplateCompileException("SFX template precompile failed with " + report.errors().size() + " error(s).");
            }
            publishAtomically(outputRoot, tempRoot);
            plugin.getLogger().info("[template] Compiled " + report.sourceFiles() + " source file(s) into " + report.outputFiles() + " explicit output file(s).");
            return report;
        } finally {
            deleteDirectory(tempRoot);
            deleteDirectory(tempContentRoot);
        }
    }

    private static void prepareContentSource(JavaPlugin plugin, Path contentRoot, Path tempContentRoot) {
        try {
            if (Files.isDirectory(contentRoot)) {
                copyDirectory(contentRoot, tempContentRoot);
            } else {
                Files.createDirectories(tempContentRoot);
            }
            List<Path> addonContentRoots = addonContentRoots(plugin);
            for (Path addonContentRoot : addonContentRoots) {
                copyDirectory(addonContentRoot, tempContentRoot);
                plugin.getLogger().info("[template] Applied SFX addon content source " + addonContentRoot);
            }
            for (File addonJar : externalAddonJars(plugin)) {
                copyAddonContentFromJar(addonJar, tempContentRoot);
                plugin.getLogger().info("[template] Applied SFX addon jar content source " + addonJar.getPath());
            }
        } catch (IOException ex) {
            deleteDirectory(tempContentRoot);
            throw new SfxTemplateCompileException("Failed to prepare SFX template content source: " + ex.getMessage(), ex);
        }
    }

    private static List<Path> addonContentRoots(JavaPlugin plugin) throws IOException {
        Path addonsRoot = new File(plugin.getDataFolder(), "addons").toPath();
        Path bundledAddonsRoot = new File(plugin.getDataFolder(), "content/addons").toPath();
        Set<String> loadedAddonFolders = loadedAddonFolders(plugin);
        List<Path> roots = new java.util.ArrayList<>();
        roots.addAll(contentRootsUnder(bundledAddonsRoot, loadedAddonFolders));
        roots.addAll(contentRootsUnder(addonsRoot, loadedAddonFolders));
        return roots.stream()
                .sorted(Comparator.comparing(Path::toString))
                .toList();
    }

    private static List<Path> contentRootsUnder(Path addonsRoot) throws IOException {
        return contentRootsUnder(addonsRoot, null);
    }

    private static List<Path> contentRootsUnder(Path addonsRoot, Set<String> allowedFolders) throws IOException {
        if (!Files.isDirectory(addonsRoot)) {
            return List.of();
        }
        try (var stream = Files.list(addonsRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> allowedFolders == null || allowedFolders.contains(path.getFileName().toString()))
                    .map(path -> path.resolve("content"))
                    .filter(Files::isDirectory)
                    .toList();
        }
    }

    private static Set<String> loadedAddonFolders(JavaPlugin plugin) {
        if (!(plugin instanceof SlimeFunXPlugin sfx) || sfx.addonManager() == null) {
            return Set.of();
        }
        Set<String> folders = new HashSet<>();
        for (SfxAddon addon : sfx.addonManager().loadedAddons()) {
            String id = addon.id();
            if (id == null || id.isBlank()) {
                continue;
            }
            folders.add(id);
            folders.add(id.replace(':', '_'));
            int namespace = id.indexOf(':');
            if (namespace >= 0 && namespace + 1 < id.length()) {
                folders.add(id.substring(namespace + 1));
            }
        }
        return folders;
    }

    private static List<File> externalAddonJars(JavaPlugin plugin) {
        if (plugin instanceof SlimeFunXPlugin sfx && sfx.addonManager() != null) {
            return sfx.addonManager().externalAddonJars();
        }
        return List.of();
    }

    private static void copyAddonContentFromJar(File jarFile, Path targetRoot) throws IOException {
        if (jarFile == null || !jarFile.isFile()) {
            return;
        }
        try (JarFile jar = new JarFile(jarFile)) {
            for (var entry : jar.stream()
                    .filter(candidate -> !candidate.isDirectory())
                    .sorted(SfxAddonJarResources.byEntryName())
                    .toList()) {
                String relative = SfxAddonJarResources.contentRelativePath(entry.getName());
                if (relative == null || relative.isBlank()) {
                    continue;
                }
                Path target = targetRoot.resolve(relative);
                Files.createDirectories(target.getParent());
                try (var input = jar.getInputStream(entry)) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void publishAtomically(Path outputRoot, Path tempRoot) {
        Path backupRoot = outputRoot.resolveSibling(outputRoot.getFileName() + ".previous");
        try {
            deleteDirectory(backupRoot);
            if (Files.exists(outputRoot)) {
                Files.move(outputRoot, backupRoot, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempRoot, outputRoot, StandardCopyOption.REPLACE_EXISTING);
            deleteDirectory(backupRoot);
        } catch (IOException ex) {
            try {
                if (!Files.exists(outputRoot) && Files.exists(backupRoot)) {
                    Files.move(backupRoot, outputRoot, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException restoreEx) {
                ex.addSuppressed(restoreEx);
            }
            try {
                publishInPlace(outputRoot, tempRoot);
                deleteDirectory(backupRoot);
            } catch (SfxTemplateCompileException fallbackEx) {
                fallbackEx.addSuppressed(ex);
                throw fallbackEx;
            }
        }
    }

    private static void publishInPlace(Path outputRoot, Path tempRoot) {
        try {
            Files.createDirectories(outputRoot);
            deleteDirectoryContents(outputRoot);
            copyDirectory(tempRoot, outputRoot);
            deleteDirectory(tempRoot);
        } catch (IOException ex) {
            deleteDirectory(tempRoot);
            throw new SfxTemplateCompileException("Failed to publish compiled SFX templates in place: " + ex.getMessage(), ex);
        }
    }

    private static void deleteDirectory(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Best effort cleanup. A failed publish still keeps the previous compiled directory intact.
        }
    }

    private static void deleteDirectoryContents(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.list(root)) {
            for (Path child : stream.toList()) {
                deleteDirectory(child);
            }
        }
    }

    private static void copyDirectory(Path sourceRoot, Path targetRoot) throws IOException {
        try (var stream = Files.walk(sourceRoot)) {
            for (Path source : stream.sorted(Comparator.naturalOrder()).toList()) {
                Path target = targetRoot.resolve(sourceRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void ensureBundledTemplates(JavaPlugin plugin, boolean overwriteTemplates) {
        for (String resource : REMOVED_BUNDLED_TEMPLATE_RESOURCES) {
            deleteDirectory(new File(plugin.getDataFolder(), resource).toPath());
        }
        for (String resource : BUNDLED_TEMPLATE_RESOURCES) {
            File target = new File(plugin.getDataFolder(), resource);
            if (target.isFile() && !overwriteTemplates) {
                continue;
            }
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try {
                plugin.saveResource(resource, overwriteTemplates);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Bundled SFX template is missing: " + resource, ex);
            }
        }
    }

    private static void ensureBundledAddonContent(JavaPlugin plugin, boolean overwriteTemplates) {
        for (String resource : bundledAddonContentResources(plugin)) {
            File target = new File(plugin.getDataFolder(), resource);
            if (target.isFile() && !overwriteTemplates) {
                continue;
            }
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try {
                plugin.saveResource(resource, overwriteTemplates);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Bundled SFX addon content is missing: " + resource, ex);
            }
        }
    }

    private static List<String> bundledAddonContentResources(JavaPlugin plugin) {
        try {
            Path location = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(location)) {
                return addonResourcesFromDirectory(location);
            }
            return addonResourcesFromJar(location);
        } catch (IOException | URISyntaxException | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Cannot enumerate bundled SFX addon content resources.", ex);
            return List.of();
        }
    }

    private static List<String> addonResourcesFromDirectory(Path location) throws IOException {
        Path addonRoot = location.resolve("content/addons");
        if (!Files.isDirectory(addonRoot)) {
            return List.of();
        }
        try (var stream = Files.walk(addonRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(location::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> addonResourcesFromJar(Path location) throws IOException {
        if (!Files.isRegularFile(location)) {
            return List.of();
        }
        try (JarFile jar = new JarFile(location.toFile())) {
            return jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> entry.getName().replace('\\', '/'))
                    .filter(name -> name.startsWith("content/addons/"))
                    .sorted()
                    .toList();
        }
    }
}
