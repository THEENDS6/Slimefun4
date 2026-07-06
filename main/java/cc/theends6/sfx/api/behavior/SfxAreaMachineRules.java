package cc.theends6.sfx.api.behavior;

public record SfxAreaMachineRules(
        boolean produceCollectorBalance,
        boolean autoBreederBalance,
        boolean animalGrowthAcceleratorBalance,
        boolean cropGrowthAcceleratorBalance,
        boolean treeGrowthAcceleratorBalance,
        boolean xpCollectorBalance,
        boolean fluidPumpOptimization,
        int animalGrowthAgeIncrement,
        int xpFlaskEnergyCost,
        int fluidPumpProbeIntervalTicks,
        int waterSourceThreshold,
        int lavaSourceThreshold
) {
    public static SfxAreaMachineRules classicDefaults() {
        return new SfxAreaMachineRules(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                2000,
                4096,
                200,
                4,
                300
        );
    }
}
