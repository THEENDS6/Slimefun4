package cc.theends6.sfx.internal.machine;


public record SfxMachinePhaseResult(Action action, SfxMachineStatus status, String message) {
    public enum Action { CONTINUE, SKIP, BLOCKED, FAILED, ROLLBACK, COMPLETE_NOW }

    public static SfxMachinePhaseResult cont() { return new SfxMachinePhaseResult(Action.CONTINUE, null, null); }
    public static SfxMachinePhaseResult blocked(SfxMachineStatus status, String message) { return new SfxMachinePhaseResult(Action.BLOCKED, status == null ? SfxMachineStatus.BLOCKED : status, message); }
    public static SfxMachinePhaseResult failed(String message) { return new SfxMachinePhaseResult(Action.FAILED, SfxMachineStatus.ERROR, message); }

    public boolean stopsPipeline() {
        return action == Action.SKIP || action == Action.BLOCKED || action == Action.FAILED || action == Action.ROLLBACK || action == Action.COMPLETE_NOW;
    }
}
