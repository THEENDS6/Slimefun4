package cc.theends6.sfx.api.behavior;

public record SfxEnergyBalanceRules(
        boolean generatorBalanceEnabled,
        boolean pauseGeneratorsWhenGridFull,
        int coalFuelTicksMultiplier,
        int bioFuelSecondsMultiplier,
        int tierTwoBurnRateTenths,
        int lavaFuelSecondsMultiplier,
        int netherStarReactorEnergyPerTick,
        double electrifiedCrucibleConsumptionMultiplier
) {
    public static SfxEnergyBalanceRules classicDefaults() {
        return new SfxEnergyBalanceRules(false, false, 1, 1, 10, 1, 1024, 1.0D);
    }
}
