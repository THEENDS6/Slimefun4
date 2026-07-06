package cc.theends6.sfx.api.behavior;

public record SfxRadiationRuleContext(
        int configuredScanIntervalTicks,
        int configuredRecoveryPerScan,
        int configuredMaxExposure,
        double configuredHazmatReductionPerPiece,
        int configuredRespawnImmunityTicks
) {
}
