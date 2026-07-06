package cc.theends6.sfx.api.behavior;

public record SfxTechnicalGadgetRuleContext(
        int classicJetpackIntervalTicks,
        int classicJetbootsIntervalTicks,
        double configuredRechargeableBaseMultiplier,
        double configuredClassicChargingBenchEnergyLoss
) {
}
