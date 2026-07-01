package cc.theends6.sfx.internal.template;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxTemplatePrecompiler {
    private static final String SOURCE_DIRECTORY = "content/templates";
    private static final String OUTPUT_DIRECTORY = "content/compiled";
    private static final List<String> BUNDLED_TEMPLATE_RESOURCES = List.of(
            "content/templates/00-electric-machine-templates.yml",
            "content/templates/05-energy-component-templates.yml",
            "content/templates/10-electric-furnaces.yml",
            "content/templates/11-electric-furnaces-v2.yml",
            "content/templates/20-energy-components.yml"
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
            deleteDirectory(tempRoot);
            throw new SfxTemplateCompileException("Failed to publish compiled SFX templates atomically: " + ex.getMessage(), ex);
        }
    }

    private static void deleteDirectory(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            
        }
    }

    private static void ensureBundledTemplates(JavaPlugin plugin, boolean overwriteTemplates) {
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
