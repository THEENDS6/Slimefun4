package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.internal.core.SfxAuditReport;

public interface SfxContentSource {
    String name();

    void load(SfxContentRegistryRef registry, SfxAuditReport.Builder audit) throws Exception;
}
