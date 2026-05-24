package cc.theends6.sfx.internal.core;

import java.util.logging.Logger;

public interface SfxAuditSink {
    void publish(SfxAuditReport report);

    static SfxAuditSink toLogger(Logger logger) {
        return report -> {
            if (report == null || logger == null) {
                return;
            }
            logger.info("[SFX Audit] " + report.name() + " entries=" + report.entries().size()
                    + " warnings=" + report.warningCount() + " errors=" + report.errorCount());
            for (SfxAuditReport.Entry entry : report.entries()) {
                String line = "[SFX Audit:" + report.name() + "] " + entry.message();
                if (entry.severity() == SfxAuditReport.Severity.ERROR) {
                    logger.severe(line);
                } else if (entry.severity() == SfxAuditReport.Severity.WARNING) {
                    logger.warning(line);
                } else {
                    logger.info(line);
                }
            }
        };
    }
}
