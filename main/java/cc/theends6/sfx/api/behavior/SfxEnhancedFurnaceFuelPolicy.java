package cc.theends6.sfx.api.behavior;

@FunctionalInterface
public interface SfxEnhancedFurnaceFuelPolicy {
    double fuelMultiplier(SfxEnhancedFurnaceFuelContext context, double currentMultiplier);
}
