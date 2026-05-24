package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.internal.core.SfxAuditReport;

public final class SfxContentAudit {
    private final SfxAuditReport.Builder builder;

    public SfxContentAudit(String name) {
        this.builder = SfxAuditReport.builder(name);
    }

    public void loaded(String source) { builder.info("Loaded content source: " + source); }
    public void warning(String message) { builder.warning(message); }
    public void error(String message) { builder.error(message); }
    public SfxAuditReport build() { return builder.build(); }
}
