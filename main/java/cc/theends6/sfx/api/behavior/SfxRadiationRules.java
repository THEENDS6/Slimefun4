package cc.theends6.sfx.api.behavior;

public record SfxRadiationRules(
        int scanIntervalTicks,
        int recoveryPerScan,
        int maxExposure,
        double hazmatReductionPerPiece,
        boolean partialHazmatProtection,
        SfxRadiationSymptomProfile symptomProfile,
        int respawnImmunityTicks
) {
    public static SfxRadiationRules classicDefaults(int respawnImmunityTicks) {
        return new SfxRadiationRules(20, 1, 100, 0.25D, false, SfxRadiationSymptomProfile.CLASSIC, respawnImmunityTicks);
    }
}
