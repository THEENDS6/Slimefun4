package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.behavior.SfxAndroidWoodcutterContext;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRuleContext;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRules;
import cc.theends6.sfx.api.behavior.SfxCargoInputTransferContext;
import cc.theends6.sfx.api.behavior.SfxCargoInputTransferDecision;
import cc.theends6.sfx.api.behavior.SfxEnhancedFurnaceFuelContext;
import cc.theends6.sfx.api.behavior.SfxEnergyBalanceRuleContext;
import cc.theends6.sfx.api.behavior.SfxEnergyBalanceRules;
import cc.theends6.sfx.api.behavior.SfxGpsTransmitterInteractionDecision;
import cc.theends6.sfx.api.behavior.SfxLocalizedListContext;
import cc.theends6.sfx.api.behavior.SfxRadiationRuleContext;
import cc.theends6.sfx.api.behavior.SfxRadiationRules;
import cc.theends6.sfx.api.behavior.SfxRadiationSymptomProfile;
import cc.theends6.sfx.api.behavior.SfxRechargeableItemDefinition;
import cc.theends6.sfx.api.behavior.SfxRechargeableItemKind;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetRuleContext;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetRules;
import cc.theends6.sfx.api.behavior.SfxUtilityRuleContext;
import cc.theends6.sfx.api.behavior.SfxUtilityRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SfxBasicExpansionAddon implements SfxAddon {
    private static final String ENHANCED_FURNACE_SPEED_FUEL = "sfx:enhanced_furnace_speed_fuel";
    private static final String ANDROID_WOODCUTTER_BATCH_REPLANT = "sfx:android_woodcutter_batch_replant";
    private static final String RADIATION_REWORK = "sfx:radiation_rework";
    private static final String ADVANCED_INPUT_INTERFACE = "sfx:advanced_input_interface";
    private static final String GPS_TRANSMITTER_STATUS_UI = "sfx:gps_transmitter_status_ui";
    private static final String JETPACKS_AND_JETBOOTS_REWORK = "sfx:jetpacks_and_jetboots_rework";
    private static final String TECHNICAL_GADGET_BALANCE = "sfx:technical_gadget_balance";
    private static final String GENERATOR_BALANCE = "sfx:generator_balance";
    private static final String PAUSE_GENERATORS_WHEN_GRID_FULL = "sfx:pause_generators_when_grid_full";
    private static final String CROP_GROWTH_ACCELERATOR_BALANCE = "sfx:crop_growth_accelerator_balance";
    private static final String TREE_GROWTH_ACCELERATOR_BALANCE = "sfx:tree_growth_accelerator_balance";
    private static final String AUTO_BREEDER_BALANCE = "sfx:auto_breeder_balance";
    private static final String ANIMAL_GROWTH_ACCELERATOR_BALANCE = "sfx:animal_growth_accelerator_balance";
    private static final String PRODUCE_COLLECTOR_BALANCE = "sfx:produce_collector_balance";
    private static final String XP_COLLECTOR_BALANCE = "sfx:xp_collector_balance";
    private static final String FLUID_PUMP_OPTIMIZATION = "sfx:fluid_pump_optimization";
    private static final String AUTO_BREWER = "sfx:auto_brewer";
    private static final String ENHANCED_MULTIMETER = "sfx:enhanced_multimeter";

    public static final String ID = "sfx:basic_expansion";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "SFX Basic Expansion";
    }

    @Override
    public void onLoad(SfxAddonContext context) {
        context.features().registerBoolean(GPS_TRANSMITTER_STATUS_UI, "gps.sfx-extensions.transmitter-gui.enabled", true);
        context.features().registerBoolean(ENHANCED_FURNACE_SPEED_FUEL, "plugin-blocks.enhanced-furnace.speed-affects-fuel-consumption", true);
        context.features().registerBoolean(JETPACKS_AND_JETBOOTS_REWORK, "technical-gadgets.sfx-extensions.jetpacks-and-jetboots.enabled", true);
        context.features().registerBoolean(TECHNICAL_GADGET_BALANCE, "technical-gadgets.sfx-balance.enabled", true);
        context.features().registerBoolean(CROP_GROWTH_ACCELERATOR_BALANCE, "electric-machines.sfx-balance.crop-growth-accelerator", true);
        context.features().registerBoolean(TREE_GROWTH_ACCELERATOR_BALANCE, "electric-machines.sfx-balance.tree-growth-accelerator", true);
        context.features().registerBoolean(AUTO_BREEDER_BALANCE, "electric-machines.sfx-balance.auto-breeder", true);
        context.features().registerBoolean(ANIMAL_GROWTH_ACCELERATOR_BALANCE, "electric-machines.sfx-balance.animal-growth-accelerator", true);
        context.features().registerBoolean(PRODUCE_COLLECTOR_BALANCE, "electric-machines.sfx-balance.produce-collector", true);
        context.features().registerBoolean(XP_COLLECTOR_BALANCE, "electric-machines.sfx-balance.xp-collector.enabled", true);
        context.features().registerBoolean(FLUID_PUMP_OPTIMIZATION, "electric-machines.sfx-extensions.fluid-pump-optimization.enabled", true);
        context.features().registerBoolean(AUTO_BREWER, "electric-machines.sfx-extensions.auto-brewer.enabled", true);
        context.features().registerBoolean(GENERATOR_BALANCE, "energy.generator-balance.use-sfx-balance", true);
        context.features().registerBoolean(PAUSE_GENERATORS_WHEN_GRID_FULL, "energy.generator-balance.pause-generators-when-grid-full", true);
        context.features().registerBoolean(ENHANCED_MULTIMETER, "tools.multimeter.use-sfx-enhanced", true);
        context.features().registerBoolean(ADVANCED_INPUT_INTERFACE, "cargo.sfx-extensions.advanced-input-interface.enabled", true);
        context.features().registerBoolean(RADIATION_REWORK, "radiation.sfx-rework.enabled", true);
        context.features().registerBoolean(ANDROID_WOODCUTTER_BATCH_REPLANT, "androids.woodcutter.batch-replant-bottom-layer", true);
        context.behaviors().registerEnhancedFurnaceFuelPolicy((fuelContext, currentMultiplier) ->
                speedScaledEnhancedFurnaceFuel(context, fuelContext, currentMultiplier));
        context.behaviors().registerAndroidWoodcutterPolicy((woodcutterContext, currentDecision) ->
                batchReplantBottomLayer(context, woodcutterContext, currentDecision));
        context.behaviors().registerRadiationRuleProvider((ruleContext, currentRules) ->
                sfxRadiationRules(context, ruleContext, currentRules));
        context.behaviors().registerCargoInputTransferPolicy((transferContext, currentDecision) ->
                advancedInputTransfer(context, transferContext, currentDecision));
        context.behaviors().registerGpsTransmitterInteractionPolicy((transmitterContext, currentDecision) ->
                gpsTransmitterInteraction(context, currentDecision));
        context.behaviors().registerTechnicalGadgetRuleProvider((ruleContext, currentRules) ->
                technicalGadgetRules(context, ruleContext, currentRules));
        context.behaviors().registerRechargeableItemProvider(() ->
                rechargeableItems(context));
        context.behaviors().registerEnergyBalanceRuleProvider((ruleContext, currentRules) ->
                energyBalanceRules(context, ruleContext, currentRules));
        context.behaviors().registerAreaMachineRuleProvider((ruleContext, currentRules) ->
                areaMachineRules(context, ruleContext, currentRules));
        context.behaviors().registerUtilityRuleProvider((ruleContext, currentRules) ->
                utilityRules(context, ruleContext, currentRules));
        context.behaviors().registerElectricSpecialProviderKeyPolicy((providerContext, currentProviderKey) ->
                electricSpecialProviderKey(context, providerContext.providerKey(), currentProviderKey));
        context.behaviors().registerLocalizedListPostProcessor((listContext, currentValues) ->
                localizedList(context, listContext, currentValues));
    }

    private static double speedScaledEnhancedFurnaceFuel(SfxAddonContext context, SfxEnhancedFurnaceFuelContext fuelContext, double currentMultiplier) {
        if (!context.api().features().enabled(ENHANCED_FURNACE_SPEED_FUEL) || fuelContext.processingSpeed() <= 0) {
            return currentMultiplier;
        }
        return currentMultiplier / fuelContext.processingSpeed();
    }

    private static boolean batchReplantBottomLayer(SfxAddonContext context, SfxAndroidWoodcutterContext woodcutterContext, boolean currentDecision) {
        return currentDecision
                || context.api().features().enabled(ANDROID_WOODCUTTER_BATCH_REPLANT)
                && woodcutterContext.onlyBottomLayerLogsRemain()
                && woodcutterContext.bottomLayerLogCount() > 0;
    }

    private static SfxRadiationRules sfxRadiationRules(SfxAddonContext context, SfxRadiationRuleContext ruleContext, SfxRadiationRules currentRules) {
        if (!context.api().features().enabled(RADIATION_REWORK)) {
            return currentRules;
        }
        return new SfxRadiationRules(
                ruleContext.configuredScanIntervalTicks(),
                ruleContext.configuredRecoveryPerScan(),
                ruleContext.configuredMaxExposure(),
                ruleContext.configuredHazmatReductionPerPiece(),
                true,
                SfxRadiationSymptomProfile.SFX_REWORK,
                ruleContext.configuredRespawnImmunityTicks()
        );
    }

    private static SfxCargoInputTransferDecision advancedInputTransfer(SfxAddonContext context, SfxCargoInputTransferContext transferContext, SfxCargoInputTransferDecision currentDecision) {
        if (!context.api().features().enabled(ADVANCED_INPUT_INTERFACE) || !transferContext.advancedInputNode()) {
            return currentDecision;
        }
        return new SfxCargoInputTransferDecision(
                transferContext.batchLimit(),
                transferContext.maxDistinctTypes(),
                transferContext.allowMultipleSlots(),
                transferContext.distribution()
        );
    }

    private static SfxGpsTransmitterInteractionDecision gpsTransmitterInteraction(SfxAddonContext context, SfxGpsTransmitterInteractionDecision currentDecision) {
        if (!context.api().features().enabled(GPS_TRANSMITTER_STATUS_UI)) {
            return currentDecision;
        }
        return SfxGpsTransmitterInteractionDecision.OPEN_STATUS_UI;
    }

    private static SfxTechnicalGadgetRules technicalGadgetRules(SfxAddonContext context, SfxTechnicalGadgetRuleContext ruleContext, SfxTechnicalGadgetRules currentRules) {
        if (!context.api().features().enabled(JETPACKS_AND_JETBOOTS_REWORK)) {
            return currentRules;
        }
        boolean balance = context.api().features().enabled(TECHNICAL_GADGET_BALANCE);
        return new SfxTechnicalGadgetRules(
                true,
                balance
                        ? Math.max(1.0D, ruleContext.configuredRechargeableBaseMultiplier()
                        * Math.max(1.0D, context.configDouble("technical-gadgets.sfx-balance.rechargeable-multiplier", 5.0D)))
                        : currentRules.rechargeableMultiplier(),
                balance
                        ? Math.max(0.0D, Math.min(1.0D, context.configDouble("technical-gadgets.sfx-balance.charging-bench.energy-loss", 0.80D)))
                        : currentRules.chargingBenchEnergyLoss()
        );
    }

    private static List<SfxRechargeableItemDefinition> rechargeableItems(SfxAddonContext context) {
        if (!context.api().features().enabled(JETPACKS_AND_JETBOOTS_REWORK)) {
            return List.of();
        }
        return List.of(
                jetpack("sf:duralumin_jetpack", 1, 2000.0D, 0.10D, 1.0D, 10, false),
                jetBoots("sf:duralumin_jetboots", 1, 2000.0D, 0.10D, 1.0D, 10),
                jetpack("sf:solder_jetpack", 2, 4000.0D, 0.12D, 1.4D, 15, false),
                jetBoots("sf:solder_jetboots", 2, 4000.0D, 0.12D, 1.4D, 15),
                jetpack("sf:billon_jetpack", 3, 6000.0D, 0.14D, 1.6D, 20, false),
                jetBoots("sf:billon_jetboots", 3, 6000.0D, 0.14D, 1.6D, 20),
                jetpack("sf:steel_jetpack", 4, 10000.0D, 0.16D, 2.0D, 25, true),
                jetBoots("sf:steel_jetboots", 4, 10000.0D, 0.16D, 2.0D, 25),
                jetpack("sf:damascus_steel_jetpack", 5, 14000.0D, 0.18D, 2.5D, 30, true),
                jetBoots("sf:damascus_steel_jetboots", 5, 14000.0D, 0.18D, 2.5D, 30),
                jetpack("sf:reinforced_alloy_jetpack", 6, 22000.0D, 0.20D, 3.0D, 50, true),
                jetBoots("sf:reinforced_alloy_jetboots", 6, 22000.0D, 0.20D, 3.0D, 50),
                jetpack("sf:carbonado_jetpack", 7, 30000.0D, 0.25D, 3.0D, 100, true),
                jetBoots("sf:carbonado_jetboots", 7, 30000.0D, 0.25D, 3.0D, 100),
                jetpack("sf:armored_jetpack", 4, 10000.0D, 0.16D, 2.0D, 25, true),
                jetBoots("sf:armored_jetboots", 4, 10000.0D, 0.16D, 2.0D, 25),
                SfxRechargeableItemDefinition.fuelJetpack(
                        "sf:fuel_jetpack",
                        5,
                        0.18D,
                        1.0D,
                        -1,
                        true,
                        10000.0D
                )
        );
    }

    private static SfxRechargeableItemDefinition jetpack(String itemId, int level, double capacity, double thrust, double useCost, int heightLimit, boolean hoverSupported) {
        return SfxRechargeableItemDefinition.electric(itemId, SfxRechargeableItemKind.JETPACK, level, capacity, thrust, useCost, heightLimit, hoverSupported);
    }

    private static SfxRechargeableItemDefinition jetBoots(String itemId, int level, double capacity, double speed, double useCost, int heightLimit) {
        return SfxRechargeableItemDefinition.electric(itemId, SfxRechargeableItemKind.JETBOOTS, level, capacity, speed, useCost, heightLimit, false);
    }

    private static SfxEnergyBalanceRules energyBalanceRules(SfxAddonContext context, SfxEnergyBalanceRuleContext ruleContext, SfxEnergyBalanceRules currentRules) {
        boolean generatorBalance = context.api().features().enabled(GENERATOR_BALANCE);
        boolean pauseFull = context.api().features().enabled(PAUSE_GENERATORS_WHEN_GRID_FULL);
        if (!generatorBalance) {
            return new SfxEnergyBalanceRules(false, pauseFull, 1, 1, 10, 1, 1024, currentRules.electrifiedCrucibleConsumptionMultiplier());
        }
        return new SfxEnergyBalanceRules(
                true,
                pauseFull,
                2,
                4,
                15,
                2,
                2048,
                Math.max(1.0D, context.configDouble("energy.generator-balance.electrified-crucible-consumption-multiplier", 1.5D))
        );
    }

    private static SfxAreaMachineRules areaMachineRules(SfxAddonContext context, SfxAreaMachineRuleContext ruleContext, SfxAreaMachineRules currentRules) {
        return new SfxAreaMachineRules(
                context.api().features().enabled(PRODUCE_COLLECTOR_BALANCE),
                context.api().features().enabled(AUTO_BREEDER_BALANCE),
                context.api().features().enabled(ANIMAL_GROWTH_ACCELERATOR_BALANCE),
                context.api().features().enabled(CROP_GROWTH_ACCELERATOR_BALANCE),
                context.api().features().enabled(TREE_GROWTH_ACCELERATOR_BALANCE),
                context.api().features().enabled(XP_COLLECTOR_BALANCE),
                context.api().features().enabled(FLUID_PUMP_OPTIMIZATION),
                context.api().features().enabled(ANIMAL_GROWTH_ACCELERATOR_BALANCE) ? 4000 : currentRules.animalGrowthAgeIncrement(),
                Math.max(0, context.configInt("electric-machines.sfx-balance.xp-collector.flask-energy-cost", currentRules.xpFlaskEnergyCost())),
                Math.max(1, context.configInt("electric-machines.sfx-extensions.fluid-pump-optimization.check-interval-ticks", currentRules.fluidPumpProbeIntervalTicks())),
                Math.max(1, context.configInt("electric-machines.sfx-extensions.fluid-pump-optimization.water-source-threshold", currentRules.waterSourceThreshold())),
                Math.max(1, context.configInt("electric-machines.sfx-extensions.fluid-pump-optimization.lava-source-threshold", currentRules.lavaSourceThreshold()))
        );
    }

    private static SfxUtilityRules utilityRules(SfxAddonContext context, SfxUtilityRuleContext ruleContext, SfxUtilityRules currentRules) {
        return new SfxUtilityRules(
                context.api().features().enabled(AUTO_BREWER),
                context.api().features().enabled(ENHANCED_MULTIMETER)
        );
    }

    private static String electricSpecialProviderKey(SfxAddonContext context, String originalProviderKey, String currentProviderKey) {
        if ("sf:auto_brewer".equals(originalProviderKey) && context.api().features().enabled(AUTO_BREWER)) {
            return "sfx:advanced_auto_brewer";
        }
        if ("sf:crop_growth_accelerator".equals(originalProviderKey) && context.api().features().enabled(CROP_GROWTH_ACCELERATOR_BALANCE)) {
            return "sfx:crop_growth_accelerator";
        }
        if ("sf:crop_growth_accelerator_2".equals(originalProviderKey) && context.api().features().enabled(CROP_GROWTH_ACCELERATOR_BALANCE)) {
            return "sfx:crop_growth_accelerator_2";
        }
        if ("sf:tree_growth_accelerator".equals(originalProviderKey) && context.api().features().enabled(TREE_GROWTH_ACCELERATOR_BALANCE)) {
            return "sfx:tree_growth_accelerator";
        }
        if ("sf:produce_collector".equals(originalProviderKey) && context.api().features().enabled(PRODUCE_COLLECTOR_BALANCE)) {
            return "sfx:produce_collector";
        }
        if ("sf:auto_breeder".equals(originalProviderKey) && context.api().features().enabled(AUTO_BREEDER_BALANCE)) {
            return "sfx:auto_breeder";
        }
        if ("sf:animal_growth_accelerator".equals(originalProviderKey) && context.api().features().enabled(ANIMAL_GROWTH_ACCELERATOR_BALANCE)) {
            return "sfx:animal_growth_accelerator";
        }
        if ("sf:xp_collector".equals(originalProviderKey) && context.api().features().enabled(XP_COLLECTOR_BALANCE)) {
            return "sfx:xp_collector";
        }
        if ("sf:fluid_pump".equals(originalProviderKey) && context.api().features().enabled(FLUID_PUMP_OPTIMIZATION)) {
            return "sfx:fluid_pump";
        }
        return currentProviderKey;
    }

    private static List<String> localizedList(SfxAddonContext context, SfxLocalizedListContext listContext, List<String> values) {
        String path = listContext.path();
        boolean generatorBalance = context.api().features().enabled(GENERATOR_BALANCE);
        if ("items.sf.combustion_reactor.lore".equals(path)) {
            return combustionReactorLore(values, generatorBalance);
        }
        if ("items.sf.netherstar_reactor.lore".equals(path)) {
            return netherStarReactorLore(values, generatorBalance);
        }
        if (generatorBalance && path.startsWith("items.sf.electrified_crucible")) {
            return electrifiedCrucibleLore(context, path, values);
        }
        if (generatorBalance && ("items.sf.coal_generator_2.lore".equals(path) || "items.sf.lava_generator_2.lore".equals(path))) {
            return tierTwoGeneratorLore(listContext, values);
        }
        if (isGrowthAcceleratorLore(path)) {
            return growthAcceleratorLore(context, listContext, values);
        }
        if ("items.sf.xp_collector.lore".equals(path)) {
            return xpCollectorLore(context, listContext, values);
        }
        if ("items.sf.produce_collector.lore".equals(path)) {
            return produceCollectorLore(context, listContext, values);
        }
        return values;
    }

    private static boolean isGrowthAcceleratorLore(String path) {
        return "items.sf.crop_growth_accelerator.lore".equals(path)
                || "items.sf.crop_growth_accelerator_2.lore".equals(path)
                || "items.sf.tree_growth_accelerator.lore".equals(path);
    }

    private static List<String> combustionReactorLore(List<String> values, boolean generatorBalance) {
        String capacity = generatorBalance ? "20480" : "5120";
        String output = generatorBalance ? "64" : "24";
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            copy.add(value
                    .replace("20480 J", capacity + " J")
                    .replace("5120 J", capacity + " J")
                    .replace("64 J/t", output + " J/t")
                    .replace("24 J/t", output + " J/t"));
        }
        return copy;
    }

    private static List<String> netherStarReactorLore(List<String> values, boolean generatorBalance) {
        String output = generatorBalance ? "2048" : "1024";
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            copy.add(value
                    .replace("2048 J/t", output + " J/t")
                    .replace("1024 J/t", output + " J/t"));
        }
        return copy;
    }

    private static List<String> electrifiedCrucibleLore(SfxAddonContext context, String path, List<String> values) {
        int classicEnergy;
        if ("items.sf.electrified_crucible_3.lore".equals(path)) {
            classicEnergy = 120;
        } else if ("items.sf.electrified_crucible_2.lore".equals(path)) {
            classicEnergy = 80;
        } else if ("items.sf.electrified_crucible.lore".equals(path)) {
            classicEnergy = 48;
        } else {
            return values;
        }
        double multiplier = Math.max(1.0D, context.configDouble("energy.generator-balance.electrified-crucible-consumption-multiplier", 1.5D));
        String energy = String.valueOf(Math.max(1, (int) Math.round(classicEnergy * multiplier)));
        List<String> copy = new ArrayList<>(values.size());
        for (String line : values) {
            if (line != null && line.contains("J/t")) {
                copy.add(line.replaceFirst("\\d+\\s*J/t", energy + " J/t"));
            } else {
                copy.add(line);
            }
        }
        return copy;
    }

    private static List<String> tierTwoGeneratorLore(SfxLocalizedListContext listContext, List<String> values) {
        String line = listContext.rawText("sfx-basic-expansion.lore.tier2-fuel-consumption");
        if (line == null || line.isBlank() || values.contains(line)) {
            return values;
        }
        List<String> copy = new ArrayList<>(values);
        copy.add(line);
        return copy;
    }

    private static List<String> growthAcceleratorLore(SfxAddonContext context, SfxLocalizedListContext listContext, List<String> values) {
        String path = listContext.path();
        boolean crop = "items.sf.crop_growth_accelerator.lore".equals(path)
                || "items.sf.crop_growth_accelerator_2.lore".equals(path);
        boolean tree = "items.sf.tree_growth_accelerator.lore".equals(path);
        boolean enabled = crop
                ? context.api().features().enabled(CROP_GROWTH_ACCELERATOR_BALANCE)
                : context.api().features().enabled(TREE_GROWTH_ACCELERATOR_BALANCE);
        if (!enabled || values.size() < 4) {
            return values;
        }

        boolean cropOne = "items.sf.crop_growth_accelerator.lore".equals(path);
        int attempts = cropOne ? 20 : 30;
        int energy = cropOne ? 100 : (tree ? 96 : 120);
        String addonPath = cropOne
                ? "sfx-basic-expansion.lore.crop-growth-accelerator"
                : tree
                ? "sfx-basic-expansion.lore.tree-growth-accelerator"
                : "sfx-basic-expansion.lore.crop-growth-accelerator-2";
        List<String> addonLines = listContext.rawList(addonPath);
        if (addonLines.isEmpty()) {
            return values;
        }

        List<String> copy = new ArrayList<>();
        copy.add(values.get(0));
        copy.add(values.get(1));
        copy.add(values.get(2));
        copy.add(values.get(3));
        for (String line : addonLines) {
            copy.add(listContext.applyPlaceholders(line, Map.of(
                    "attempts", String.valueOf(attempts),
                    "energy", String.valueOf(energy)
            )));
        }
        return copy;
    }

    private static List<String> xpCollectorLore(SfxAddonContext context, SfxLocalizedListContext listContext, List<String> values) {
        if (!context.api().features().enabled(XP_COLLECTOR_BALANCE) || values.isEmpty()) {
            return values;
        }
        int energyCost = Math.max(0, context.configInt("electric-machines.sfx-balance.xp-collector.flask-energy-cost", 2000));
        String perFlask = listContext.rawText("sfx-basic-expansion.lore.xp-collector-per-flask");
        if (perFlask == null || perFlask.isBlank()) {
            return values;
        }
        perFlask = listContext.applyPlaceholders(perFlask, Map.of("cost", String.valueOf(energyCost)));
        if (values.contains(perFlask)) {
            return values;
        }
        List<String> copy = new ArrayList<>(values.size() + 1);
        boolean inserted = false;
        for (String value : values) {
            if (!inserted && value != null && value.contains("J/t")) {
                copy.add(perFlask);
                inserted = true;
            }
            copy.add(value);
        }
        if (!inserted) {
            copy.add(perFlask);
        }
        return copy;
    }

    private static List<String> produceCollectorLore(SfxAddonContext context, SfxLocalizedListContext listContext, List<String> values) {
        if (!context.api().features().enabled(PRODUCE_COLLECTOR_BALANCE) || values.isEmpty()) {
            return values;
        }
        List<String> addonLines = listContext.rawList("sfx-basic-expansion.lore.produce-collector");
        if (addonLines.isEmpty()) {
            return values;
        }
        List<String> copy = new ArrayList<>(values.size() + addonLines.size());
        boolean inserted = false;
        for (String value : values) {
            if (!inserted && !copy.isEmpty() && value != null && value.isBlank()) {
                copy.addAll(addonLines);
                inserted = true;
            }
            copy.add(value);
        }
        if (!inserted) {
            copy.addAll(addonLines);
        }
        return copy;
    }
}
