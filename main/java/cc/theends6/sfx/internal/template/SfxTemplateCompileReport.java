package cc.theends6.sfx.internal.template;

import java.util.List;

public record SfxTemplateCompileReport(
        int sourceFiles,
        int outputFiles,
        List<String> warnings,
        List<String> errors
) {
    public boolean ok() {
        return errors.isEmpty();
    }
}
