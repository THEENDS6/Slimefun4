package cc.theends6.sfx.internal.machine;

import java.util.Map;

/** Small guard utility for legacy services that are being driven by the shared machine pipeline. */
public final class SfxMachinePipelineGuard {
    private SfxMachinePipelineGuard() {
    }

    public static boolean proceed(SfxMachinePhaseResult result, Map<String, Object> attributes, String phaseName) {
        if (result == null || !result.stopsPipeline()) {
            return true;
        }
        if (result.action() == SfxMachinePhaseResult.Action.COMPLETE_NOW) {
            markCompleted(result, attributes, phaseName);
            return true;
        }
        markStopped(result, attributes, phaseName);
        return false;
    }

    public static void markCompleted(SfxMachinePhaseResult result, Map<String, Object> attributes, String phaseName) {
        if (attributes == null || result == null) {
            return;
        }
        attributes.put("framework.pipeline.completed-now", Boolean.TRUE);
        attributes.put("framework.pipeline.completed-now.phase", phaseName);
        attributes.put("framework.pipeline.completed-now.status", result.status() == null ? null : result.status().name());
        if (result.message() != null && !result.message().isBlank()) {
            attributes.put("framework.pipeline.completed-now.message", result.message());
        }
    }

    public static void markStopped(SfxMachinePhaseResult result, Map<String, Object> attributes, String phaseName) {
        if (attributes == null || result == null) {
            return;
        }
        attributes.put("framework.pipeline.stopped", Boolean.TRUE);
        attributes.put("framework.pipeline.stopped.phase", phaseName);
        attributes.put("framework.pipeline.stopped.action", result.action() == null ? null : result.action().name());
        attributes.put("framework.pipeline.stopped.status", result.status() == null ? null : result.status().name());
        if (result.message() != null && !result.message().isBlank()) {
            attributes.put("framework.pipeline.stopped.message", result.message());
        }
    }
}
