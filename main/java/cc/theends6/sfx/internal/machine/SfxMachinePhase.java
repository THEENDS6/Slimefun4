package cc.theends6.sfx.internal.machine;

/** Fixed extension points in the common machine pipeline. */
public enum SfxMachinePhase {
    BEFORE_TICK,
    BEFORE_OPERATION_RESOLVE,
    AFTER_OPERATION_RESOLVE,
    BEFORE_INPUT,
    AFTER_INPUT,
    BEFORE_PROGRESS,
    AFTER_PROGRESS,
    BEFORE_COMPLETE,
    ON_COMPLETE,
    BEFORE_OUTPUT,
    AFTER_OUTPUT,
    ON_OUTPUT_BLOCKED,
    ON_IDLE,
    ON_ERROR,
    AFTER_TICK,
    ON_PLACE,
    ON_BREAK,
    ON_UNLOAD
}
