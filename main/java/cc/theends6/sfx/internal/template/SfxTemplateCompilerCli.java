package cc.theends6.sfx.internal.template;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public final class SfxTemplateCompilerCli {
    private SfxTemplateCompilerCli() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: SfxTemplateCompilerCli <sourceRoot> <outputRoot>");
        }
        Path sourceRoot = Path.of(args[0]);
        Path outputRoot = Path.of(args[1]);
        SfxTemplateCompileReport report = compile(sourceRoot, outputRoot);
        for (String warning : report.warnings()) {
            System.err.println("[template] warning: " + warning);
        }
        if (!report.ok()) {
            for (String error : report.errors()) {
                System.err.println("[template] error: " + error);
            }
            throw new SfxTemplateCompileException("SFX template compile failed with " + report.errors().size() + " error(s).");
        }
        System.out.println("Compiled " + report.sourceFiles() + " template source file(s) into " + report.outputFiles() + " output file(s).");
    }

    private static SfxTemplateCompileReport compile(Path sourceRoot, Path outputRoot) {
        Path contentRoot = sourceRoot.getParent();
        if (contentRoot == null || !Files.isDirectory(contentRoot.resolve("addons"))) {
            return new SfxTemplateCompiler(sourceRoot, outputRoot).compile();
        }
        Path tempContentRoot;
        try {
            tempContentRoot = Files.createTempDirectory("sfx-template-content-");
            copyDirectory(contentRoot, tempContentRoot);
            for (Path addonContentRoot : addonContentRoots(contentRoot.resolve("addons"))) {
                copyDirectory(addonContentRoot, tempContentRoot);
                System.err.println("[template] applied addon content source: " + addonContentRoot);
            }
            return new SfxTemplateCompiler(tempContentRoot.resolve("templates"), outputRoot).compile();
        } catch (IOException ex) {
            throw new SfxTemplateCompileException("Failed to prepare SFX template content source: " + ex.getMessage(), ex);
        }
    }

    private static List<Path> addonContentRoots(Path addonsRoot) throws IOException {
        try (var stream = Files.list(addonsRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve("content"))
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
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
}
