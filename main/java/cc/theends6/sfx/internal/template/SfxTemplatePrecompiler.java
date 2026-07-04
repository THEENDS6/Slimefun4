package cc.theends6.sfx.internal.template;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
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
        Path sourceRoot = new File(plugin.getDataFolder(), SOURCE_DIRECTORY).toPath();
        Path outputRoot = new File(plugin.getDataFolder(), OUTPUT_DIRECTORY).toPath();
        Path tempRoot = outputRoot.resolveSibling(outputRoot.getFileName() + ".tmp-" + System.nanoTime());
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
}
