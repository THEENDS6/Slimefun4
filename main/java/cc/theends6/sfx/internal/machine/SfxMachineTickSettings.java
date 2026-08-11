package cc.theends6.sfx.internal.machine;

import org.bukkit.configuration.file.FileConfiguration;


public final class SfxMachineTickSettings {
    private static final int DEFAULT_INTERVAL = 5;
    private static final int DEFAULT_VIEWER_INTERVAL = 1;
    private static final int DEFAULT_SLEEPING_PROBE_INTERVAL = 100;
    private static final boolean DEFAULT_FREEZE_UNLOADED_MACHINES = true;

    private final boolean enabled;
    private final int intervalTicks;
    private final int viewerIntervalTicks;
    private final int sleepingProbeIntervalTicks;
    private final boolean freezeUnloadedMachines;

    private SfxMachineTickSettings(boolean enabled, int intervalTicks, int viewerIntervalTicks,
                                   int sleepingProbeIntervalTicks, boolean freezeUnloadedMachines) {
        this.enabled = enabled;
        this.intervalTicks = Math.max(1, intervalTicks);
        this.viewerIntervalTicks = Math.max(1, viewerIntervalTicks);
        this.sleepingProbeIntervalTicks = Math.max(1, sleepingProbeIntervalTicks);
        this.freezeUnloadedMachines = freezeUnloadedMachines;
    }

    public static SfxMachineTickSettings from(FileConfiguration config) {
        boolean enabled = config.getBoolean("machines.lazy-tick.enabled", true);
        int interval = config.getInt("machines.lazy-tick.interval-ticks", DEFAULT_INTERVAL);
        int viewerInterval = config.getInt("machines.lazy-tick.viewer-interval-ticks", DEFAULT_VIEWER_INTERVAL);
        int sleepingProbeInterval = config.getInt("machines.lazy-tick.sleeping-probe-interval-ticks", DEFAULT_SLEEPING_PROBE_INTERVAL);
        boolean freezeUnloadedMachines = config.getBoolean(
                "machines.lazy-tick.freeze-unloaded-machines", DEFAULT_FREEZE_UNLOADED_MACHINES);
        return new SfxMachineTickSettings(enabled, interval, viewerInterval, sleepingProbeInterval, freezeUnloadedMachines);
    }

    public boolean enabled() {
        return enabled;
    }

    public int intervalTicks() {
        return enabled ? intervalTicks : 1;
    }

    public int viewerIntervalTicks() {
        return viewerIntervalTicks;
    }

    public int sleepingProbeIntervalTicks() {
        return sleepingProbeIntervalTicks;
    }

    



    public boolean freezeUnloadedMachines() {
        return freezeUnloadedMachines;
    }

    public int intervalFor(boolean hasViewers) {
        if (!enabled) {
            return 1;
        }
        return hasViewers ? viewerIntervalTicks : intervalTicks;
    }
}
