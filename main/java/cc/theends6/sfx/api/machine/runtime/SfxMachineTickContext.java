package cc.theends6.sfx.api.machine.runtime;

import cc.theends6.sfx.api.machine.runtime.*;





public record SfxMachineTickContext(long currentTick, long elapsedTicks, boolean hasViewers) {
    public SfxMachineTickContext {
        elapsedTicks = Math.max(1L, elapsedTicks);
    }

    public int elapsedTicksInt() {
        return elapsedTicks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsedTicks;
    }
}
