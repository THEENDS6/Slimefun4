package cc.theends6.sfx.internal.template;

import java.nio.file.Path;

public final class SfxTemplateCompilerCli {
    private SfxTemplateCompilerCli() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: SfxTemplateCompilerCli <sourceRoot> <outputRoot>");
        }
        Path sourceRoot = Path.of(args[0]);
        Path outputRoot = Path.of(args[1]);
        SfxTemplateCompileReport report = new SfxTemplateCompiler(sourceRoot, outputRoot).compile();
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
}
