package cc.theends6.sfx.api.behavior;

public record SfxTechnicalGadgetRules(
        boolean jetpackReworkEnabled,
        double rechargeableMultiplier,
        double chargingBenchEnergyLoss
) {
    public static SfxTechnicalGadgetRules classicDefaults() {
        return new SfxTechnicalGadgetRules(false, 20.0D, 0.50D);
    }
}
