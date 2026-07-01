package cc.theends6.sfx.internal.template;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxTemplatePrecompiler {
    private static final String SOURCE_DIRECTORY = "content/templates";
    private static final String OUTPUT_DIRECTORY = "content/compiled";
    private static final List<String> BUNDLED_TEMPLATE_RESOURCES = List.of(
            "content/templates/00-electric-machine-templates.yml",
            "content/templates/10-electric-furnaces.yml"
    );

    private SfxTemplatePrecompiler() {
    }

    public static SfxTemplateCompileReport compile(JavaPlugin plugin, boolean overwriteTemplates) {
        ensureBundledTemplates(plugin, overwriteTemplates);
        Path sourceRoot = new File(plugin.getDataFolder(), SOURCE_DIRECTORY).toPath();
        Path outputRoot = new File(plugin.getDataFolder(), OUTPUT_DIRECTORY).toPath();
        SfxTemplateCompileReport report = new SfxTemplateCompiler(sourceRoot, outputRoot).compile();
        for (String warning : report.warnings()) {
            plugin.getLogger().warning("[template] " + warning);
        }
        if (!report.ok()) {
            for (String error : report.errors()) {
                plugin.getLogger().severe("[template] " + error);
            }
            throw new SfxTemplateCompileException("SFX template precompile failed with " + report.errors().size() + " error(s).");
        }
        plugin.getLogger().info("[template] Compiled " + report.sourceFiles() + " source file(s) into " + report.outputFiles() + " explicit output file(s).");
        return report;
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
