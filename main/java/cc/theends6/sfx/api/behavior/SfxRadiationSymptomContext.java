package cc.theends6.sfx.api.behavior;

import org.bukkit.entity.Player;

public final class SfxRadiationSymptomContext {
    private final Player player;
    private final int exposure;
    private final int stageLevel;
    private final int effectDurationTicks;
    private final Runnable radiationDamageMarker;

    public SfxRadiationSymptomContext(Player player, int exposure, int stageLevel, int effectDurationTicks, Runnable radiationDamageMarker) {
        this.player = player;
        this.exposure = exposure;
        this.stageLevel = stageLevel;
        this.effectDurationTicks = effectDurationTicks;
        this.radiationDamageMarker = radiationDamageMarker;
    }

    public Player player() {
        return player;
    }

    public int exposure() {
        return exposure;
    }

    public int stageLevel() {
        return stageLevel;
    }

    public int effectDurationTicks() {
        return effectDurationTicks;
    }

    public void markRadiationDamage() {
        if (radiationDamageMarker != null) {
            radiationDamageMarker.run();
        }
    }
}
