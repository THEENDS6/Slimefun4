package cc.theends6.sfx.internal.machine;

public final class SfxMachineState {
    private int progress;
    private int maxProgress;
    private SfxMachineStatus status = SfxMachineStatus.IDLE;

    public int progress() { return progress; }
    public int maxProgress() { return maxProgress; }
    public SfxMachineStatus status() { return status; }

    public void progress(int progress, int maxProgress) {
        this.progress = Math.max(0, progress);
        this.maxProgress = Math.max(0, maxProgress);
    }

    public void status(SfxMachineStatus status) {
        this.status = status == null ? SfxMachineStatus.ERROR : status;
    }
}
