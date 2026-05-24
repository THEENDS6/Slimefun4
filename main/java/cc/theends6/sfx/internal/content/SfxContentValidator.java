package cc.theends6.sfx.internal.content;

import cc.theends6.sfx.internal.core.SfxAuditReport;

public interface SfxContentValidator {
    void validate(SfxContentRegistryRef registry, SfxAuditReport.Builder audit);
}
