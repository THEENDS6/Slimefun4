package cc.theends6.sfx.internal.persistence;

import cc.theends6.sfx.internal.core.SfxAuditReport;

public final class SfxDataAudit {
    private final SfxAuditReport.Builder builder;

    public SfxDataAudit(String name) {
        this.builder = SfxAuditReport.builder(name);
    }

    public void badRow(String message) { builder.warning("Bad row: " + message); }
    public void migration(String message) { builder.info("Migration: " + message); }
    public void error(String message) { builder.error(message); }
    public SfxAuditReport build() { return builder.build(); }
}
