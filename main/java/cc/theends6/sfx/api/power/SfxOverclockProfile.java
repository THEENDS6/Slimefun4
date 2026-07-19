package cc.theends6.sfx.api.power;

public record SfxOverclockProfile(double effectMultiplier, double energyMultiplier) {
    public SfxOverclockProfile {
        if (!Double.isFinite(effectMultiplier) || effectMultiplier < 1.0D) throw new IllegalArgumentException("effectMultiplier must be at least 1");
        if (!Double.isFinite(energyMultiplier) || energyMultiplier < 1.0D) throw new IllegalArgumentException("energyMultiplier must be at least 1");
    }
}
