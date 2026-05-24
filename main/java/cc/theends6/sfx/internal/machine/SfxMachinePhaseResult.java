package cc.theends6.sfx.internal.machine;

/** Result returned by framework phase hooks. Hooks should not throw for expected states. */
public record SfxMachinePhaseResult(Action action, SfxMachineStatus status, String message) {
    public enum Action { CONTINUE, SKIP, BLOCKED, FAILED, ROLLBACK, COMPLETE_NOW }

    public static SfxMachinePhaseResult cont() { return new SfxMachinePhaseResult(Action.CONTINUE, null, null); }
    public static SfxMachinePhaseResult skip(SfxMachineStatus status, String message) { return new SfxMachinePhaseResult(Action.SKIP, status, message); }
    public static SfxMachinePhaseResult blocked(SfxMachineStatus status, String message) { return new SfxMachinePhaseResult(Action.BLOCKED, status == null ? SfxMachineStatus.BLOCKED : status, message); }
    public static SfxMachinePhaseResult failed(String message) { return new SfxMachinePhaseResult(Action.FAILED, SfxMachineStatus.ERROR, message); }
    public static SfxMachinePhaseResult rollback(String message) { return new SfxMachinePhaseResult(Action.ROLLBACK, SfxMachineStatus.ERROR, message); }
    public static SfxMachinePhaseResult complete(SfxMachineStatus status, String message) { return new SfxMachinePhaseResult(Action.COMPLETE_NOW, status == null ? SfxMachineStatus.RUNNING : status, message); }

    public boolean stopsPipeline() {
        return action == Action.SKIP || action == Action.BLOCKED || action == Action.FAILED || action == Action.ROLLBACK || action == Action.COMPLETE_NOW;
    }
}
