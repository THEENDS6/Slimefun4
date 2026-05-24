package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.internal.core.SfxAuditReport;
import java.util.ArrayList;
import java.util.List;

public final class SfxContentPipeline {
    private final List<SfxContentSource> sources = new ArrayList<>();
    private final List<SfxContentValidator> validators = new ArrayList<>();
    private final SfxContentLoadMode mode;

    public SfxContentPipeline(SfxContentLoadMode mode) {
        this.mode = mode == null ? SfxContentLoadMode.PRODUCTION_LENIENT : mode;
    }

    public SfxContentPipeline source(SfxContentSource source) {
        if (source != null) { sources.add(source); }
        return this;
    }

    public SfxContentPipeline validator(SfxContentValidator validator) {
        if (validator != null) { validators.add(validator); }
        return this;
    }

    public SfxAuditReport load(SfxContentRegistryRef registry) {
        SfxAuditReport.Builder audit = SfxAuditReport.builder("content-pipeline");
        for (SfxContentSource source : sources) {
            try {
                source.load(registry, audit);
                audit.info("Content source loaded: " + source.name());
            } catch (Exception exception) {
                audit.error("Content source failed: " + source.name() + " - " + exception.getMessage());
                if (mode == SfxContentLoadMode.DEVELOPMENT_STRICT) {
                    throw new IllegalStateException("Strict content load failed for " + source.name(), exception);
                }
            }
        }
        for (SfxContentValidator validator : validators) {
            validator.validate(registry, audit);
        }
        return audit.build();
    }
}
