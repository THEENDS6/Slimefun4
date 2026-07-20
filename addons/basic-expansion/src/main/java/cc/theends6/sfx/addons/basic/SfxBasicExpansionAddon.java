package cc.theends6.sfx.addons.basic;

import cc.theends6.sfx.api.addon.SfxAddon;
import cc.theends6.sfx.api.addon.SfxAddonContext;
import cc.theends6.sfx.api.behavior.SfxAndroidWoodcutterContext;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRuleContext;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRules;
import cc.theends6.sfx.api.behavior.SfxAutoBrewerBehaviorProvider;
import cc.theends6.sfx.api.behavior.SfxAutoBrewerInputContext;
import cc.theends6.sfx.api.behavior.SfxCargoInputTransferContext;
import cc.theends6.sfx.api.behavior.SfxCargoInputTransferDecision;
import cc.theends6.sfx.api.behavior.SfxCargoFilterRuleContext;
import cc.theends6.sfx.api.behavior.SfxCargoFilterRules;
import cc.theends6.sfx.api.behavior.SfxEnhancedFurnaceFuelContext;
import cc.theends6.sfx.api.behavior.SfxEnergyBalanceRuleContext;
import cc.theends6.sfx.api.behavior.SfxEnergyBalanceRules;
import cc.theends6.sfx.api.behavior.SfxEntityDropContext;
import cc.theends6.sfx.api.behavior.SfxGpsTransmitterInteractionDecision;
import cc.theends6.sfx.api.behavior.SfxGpsTransmitterStatusView;
import cc.theends6.sfx.api.behavior.SfxIndustrialMinerTargetContext;
import cc.theends6.sfx.api.behavior.SfxJetBootsDriveMode;
import cc.theends6.sfx.api.behavior.SfxLocalizedListContext;
import cc.theends6.sfx.api.behavior.SfxRadiationRuleContext;
import cc.theends6.sfx.api.behavior.SfxRadiationRules;
import cc.theends6.sfx.api.behavior.SfxRadiationSymptomContext;
import cc.theends6.sfx.api.behavior.SfxRadiationSymptomProfile;
import cc.theends6.sfx.api.behavior.SfxRechargeableItemDefinition;
import cc.theends6.sfx.api.behavior.SfxRechargeableItemKind;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetBehaviorProvider;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetItem;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetItemKind;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetRuleContext;
import cc.theends6.sfx.api.behavior.SfxTechnicalGadgetRules;
import cc.theends6.sfx.api.behavior.SfxUtilityRuleContext;
import cc.theends6.sfx.api.behavior.SfxUtilityRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class SfxBasicExpansionAddon implements SfxAddon {
    static final String ENHANCED_FURNACE_SPEED_FUEL = "sfx:enhanced_furnace_speed_fuel";
    static final String ANDROID_WOODCUTTER_BATCH_REPLANT = "sfx:android_woodcutter_batch_replant";
    static final String RADIATION_REWORK = "sfx:radiation_rework";
    static final String ADVANCED_INPUT_INTERFACE = "sfx:advanced_input_interface";
    static final String CARGO_GHOST_FILTER_INTERFACE = "sfx:cargo_ghost_filter_interface";
    static final String GPS_TRANSMITTER_STATUS_UI = "sfx:gps_transmitter_status_ui";
    static final String JETPACKS_AND_JETBOOTS_REWORK = "sfx:jetpacks_and_jetboots_rework";
    static final String TECHNICAL_GADGET_BALANCE = "sfx:technical_gadget_balance";
    static final String GENERATOR_BALANCE = "sfx:generator_balance";
    static final String PAUSE_GENERATORS_WHEN_GRID_FULL = "sfx:pause_generators_when_grid_full";
    static final String CROP_GROWTH_ACCELERATOR_BALANCE = "sfx:crop_growth_accelerator_balance";
    static final String TREE_GROWTH_ACCELERATOR_BALANCE = "sfx:tree_growth_accelerator_balance";
    static final String AUTO_BREEDER_BALANCE = "sfx:auto_breeder_balance";
    static final String ANIMAL_GROWTH_ACCELERATOR_BALANCE = "sfx:animal_growth_accelerator_balance";
    static final String PRODUCE_COLLECTOR_BALANCE = "sfx:produce_collector_balance";
    static final String ELECTRIC_ORE_GRINDER_3_BALANCE = "sfx:electric_ore_grinder_3_balance";
    static final String ELECTRIC_INGOT_PULVERIZER_BALANCE = "sfx:electric_ingot_pulverizer_balance";
    static final String XP_COLLECTOR_BALANCE = "sfx:xp_collector_balance";
    static final String FLUID_PUMP_OPTIMIZATION = "sfx:fluid_pump_optimization";
    static final String AUTO_BREWER = "sfx:auto_brewer";
    static final String ENHANCED_MULTIMETER = "sfx:enhanced_multimeter";
    static final String BASIC_CIRCUIT_BOARD_DROP_BALANCE = "sfx:basic_circuit_board_drop_balance";
    static final String INDUSTRIAL_MINER_ACCURACY = "sfx:industrial_miner_accuracy";

    private final List<BasicExpansionModule> modules = List.of(
            new BasicExpansionFeatureModule(),
            new EquipmentModule(),
            new GeneratorModule(),
            new MachineBalanceModule(),
            new CargoModule(),
            new RadiationModule(),
            new IndustrialMinerModule(),
            new EntityDropModule(),
            new BasicExpansionElectricModule());

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
    public void onRegister(SfxAddonContext context) {
        modules.forEach(module -> module.register(context));
    }

    @Override
    public void onDisable() {
        modules.reversed().forEach(BasicExpansionModule::disable);
    }

    static double speedScaledEnhancedFurnaceFuel(SfxAddonContext context, SfxEnhancedFurnaceFuelContext fuelContext, double currentMultiplier) {
        if (!context.api().features().enabled(ENHANCED_FURNACE_SPEED_FUEL) || fuelContext.processingSpeed() <= 0) {
            return currentMultiplier;
        }
        return currentMultiplier / fuelContext.processingSpeed();
    }

    static Block industrialMinerTarget(SfxAddonContext context,
                                               SfxIndustrialMinerTargetContext targetContext,
                                               Block currentTarget) {
        if (!context.api().features().enabled(INDUSTRIAL_MINER_ACCURACY)) {
            return currentTarget;
        }
        String path = targetContext.advanced()
                ? "industrial-miner.accuracy.advanced"
                : "industrial-miner.accuracy.normal";
        double fallback = targetContext.advanced() ? 0.60D : 0.40D;
        double accuracy = Math.max(0.0D, Math.min(1.0D, context.configDouble(path, fallback)));
        if (ThreadLocalRandom.current().nextDouble() < accuracy
                || targetContext.adjacentCandidates().isEmpty()) {
            return currentTarget;
        }
        List<Block> candidates = targetContext.adjacentCandidates();
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    static boolean batchReplantBottomLayer(SfxAddonContext context, SfxAndroidWoodcutterContext woodcutterContext, boolean currentDecision) {
        return currentDecision
                || context.api().features().enabled(ANDROID_WOODCUTTER_BATCH_REPLANT)
                && woodcutterContext.onlyBottomLayerLogsRemain()
                && woodcutterContext.bottomLayerLogCount() > 0;
    }

    static double basicCircuitBoardDropChance(SfxAddonContext context, SfxEntityDropContext dropContext,
                                                       double currentChance) {
        if (!context.api().features().enabled(BASIC_CIRCUIT_BOARD_DROP_BALANCE)
                || !"sf:basic_circuit_board".equals(dropContext.outputItemId())) {
            return currentChance;
        }
        return switch (dropContext.deathSource()) {
            case OTHER -> chance(context, "entity-drops.basic-circuit-board.natural-chance", 0.05D);
            case SFX_ANDROID -> chance(context, "entity-drops.basic-circuit-board.android-chance", 0.50D);
            case PLAYER -> {
                double base = chance(context, "entity-drops.basic-circuit-board.player-base-chance", 0.50D);
                double perLevel = chance(context, "entity-drops.basic-circuit-board.looting-chance-per-level", 0.10D);
                yield Math.min(1.0D, base + perLevel * dropContext.lootingLevel());
            }
        };
    }

    private static double chance(SfxAddonContext context, String path, double fallback) {
        double value = context.configDouble(path, fallback);
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : fallback;
    }

    static SfxRadiationRules sfxRadiationRules(SfxAddonContext context, SfxRadiationRuleContext ruleContext, SfxRadiationRules currentRules) {
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

    static boolean radiationSymptoms(SfxAddonContext context, SfxRadiationSymptomContext symptomContext) {
        if (!context.api().features().enabled(RADIATION_REWORK) || symptomContext.stageLevel() <= 0) {
            return false;
        }
        Player player = symptomContext.player();
        int duration = symptomContext.effectDurationTicks();
        int stage = symptomContext.stageLevel();
        if (stage >= 1) {
            addEffect(player, "WEAKNESS", duration, 0);
            addEffect(player, "HUNGER", duration, 0);
        }
        if (stage >= 2) {
            addEffect(player, "SLOW", duration, 0);
            addEffect(player, "HUNGER", duration, 1);
            addEffect(player, "POISON", duration, 0);
            addEffect(player, "SLOW_DIGGING", duration, 0);
        }
        if (stage >= 3) {
            addEffect(player, "CONFUSION", duration, 0);
            addEffect(player, "WITHER", duration, 0);
        }
        if (stage >= 4) {
            addEffect(player, "WITHER", duration, 1);
            addEffect(player, "SLOW_DIGGING", duration, 1);
            addEffect(player, "CONFUSION", duration, 0);
        }
        if (stage >= 5) {
            addEffect(player, "BLINDNESS", duration, 0);
            symptomContext.markRadiationDamage();
            addEffect(player, "HARM", 1, 0);
        }
        return true;
    }

    private static void addEffect(Player player, String typeName, int durationTicks, int amplifier) {
        PotionEffectType type = potionEffectType(typeName);
        if (type == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, Math.max(1, durationTicks), Math.max(0, amplifier), true, true, true));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PotionEffectType potionEffectType(String name) {
        try {
            return PotionEffectType.getByName(name);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static SfxCargoInputTransferDecision advancedInputTransfer(SfxAddonContext context, SfxCargoInputTransferContext transferContext, SfxCargoInputTransferDecision currentDecision) {
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

    static SfxCargoFilterRules cargoFilterRules(SfxAddonContext context,
                                                 SfxCargoFilterRuleContext ruleContext,
                                                 SfxCargoFilterRules currentRules) {
        return new SfxCargoFilterRules(currentRules.ghostFilterInterfaceEnabled()
                || context.api().features().enabled(CARGO_GHOST_FILTER_INTERFACE));
    }

    static SfxGpsTransmitterInteractionDecision gpsTransmitterInteraction(SfxAddonContext context, SfxGpsTransmitterInteractionDecision currentDecision) {
        if (!context.api().features().enabled(GPS_TRANSMITTER_STATUS_UI)) {
            return currentDecision;
        }
        return SfxGpsTransmitterInteractionDecision.OPEN_STATUS_UI;
    }

    static SfxGpsTransmitterStatusView gpsTransmitterStatusView(
            SfxAddonContext context,
            cc.theends6.sfx.api.behavior.SfxGpsTransmitterInteractionContext transmitterContext,
            SfxGpsTransmitterStatusView currentView
    ) {
        if (!context.api().features().enabled(GPS_TRANSMITTER_STATUS_UI)) {
            return currentView;
        }
        return new SfxGpsTransmitterStatusView(
                "gps.ui.transmitter.title",
                "gps.ui.transmitter.background",
                Material.GRAY_STAINED_GLASS_PANE,
                13,
                Material.LIME_STAINED_GLASS,
                Material.RED_STAINED_GLASS,
                "gps.ui.transmitter.status.name",
                "gps.ui.transmitter.status.online",
                "gps.ui.transmitter.status.offline",
                List.of(
                        new SfxGpsTransmitterStatusView.Line("gps.ui.transmitter.status.energy", Map.of(
                                "stored", transmitterContext.storedEnergy(),
                                "required", transmitterContext.requiredEnergy())),
                        new SfxGpsTransmitterStatusView.Line("gps.ui.transmitter.status.strength", Map.of(
                                "strength", transmitterContext.signalStrength())),
                        new SfxGpsTransmitterStatusView.Line("gps.ui.transmitter.status.network", Map.of(
                                "complexity", transmitterContext.networkComplexity()))
                ),
                15,
                Material.COMPASS,
                "gps.ui.transmitter.info.name",
                List.of(
                        new SfxGpsTransmitterStatusView.Line("gps.ui.transmitter.info.type", Map.of(
                                "type", transmitterContext.transmitterName())),
                        new SfxGpsTransmitterStatusView.Line("gps.ui.transmitter.info.owner", Map.of(
                                "owner", transmitterContext.ownerName())),
                        new SfxGpsTransmitterStatusView.Line("gps.ui.transmitter.info.owner-uuid", Map.of(
                                "uuid", transmitterContext.ownerId() == null ? "-" : transmitterContext.ownerId().toString())),
                        new SfxGpsTransmitterStatusView.Line("gps.ui.transmitter.info.location", Map.of(
                                "world", transmitterContext.worldName(),
                                "x", transmitterContext.blockX(),
                                "y", transmitterContext.blockY(),
                                "z", transmitterContext.blockZ())),
                        new SfxGpsTransmitterStatusView.Line("gps.ui.transmitter.info.transmitters", Map.of(
                                "count", transmitterContext.ownedTransmitterCount()))
                ));
    }

    static SfxTechnicalGadgetRules technicalGadgetRules(SfxAddonContext context, SfxTechnicalGadgetRuleContext ruleContext, SfxTechnicalGadgetRules currentRules) {
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

    static List<SfxRechargeableItemDefinition> rechargeableItems(SfxAddonContext context) {
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

    static SfxEnergyBalanceRules energyBalanceRules(SfxAddonContext context, SfxEnergyBalanceRuleContext ruleContext, SfxEnergyBalanceRules currentRules) {
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

    static SfxAreaMachineRules areaMachineRules(SfxAddonContext context, SfxAreaMachineRuleContext ruleContext, SfxAreaMachineRules currentRules) {
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

    static SfxUtilityRules utilityRules(SfxAddonContext context, SfxUtilityRuleContext ruleContext, SfxUtilityRules currentRules) {
        return new SfxUtilityRules(
                context.api().features().enabled(AUTO_BREWER),
                context.api().features().enabled(ENHANCED_MULTIMETER)
        );
    }

    static String electricMachineProviderKey(SfxAddonContext context, String originalProviderKey, String currentProviderKey) {
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
        if ("sf:classic_ingot_pulverizer".equals(originalProviderKey)
                && context.api().features().enabled(ELECTRIC_INGOT_PULVERIZER_BALANCE)) {
            return "sfx:balanced_ingot_pulverizer";
        }
        return currentProviderKey;
    }

    static List<String> localizedList(SfxAddonContext context, SfxLocalizedListContext listContext, List<String> values) {
        String path = listContext.path();
        if ("cargo.ui.items.lore".equals(path)
                && context.api().features().enabled(CARGO_GHOST_FILTER_INTERFACE)) {
            List<String> ghostLore = listContext.rawList("sfx-basic-expansion.lore.cargo-ghost-filter");
            return ghostLore.isEmpty() ? values : ghostLore;
        }
        if ("items.sf.industrial_miner.lore".equals(path)
                || "items.sf.advanced_industrial_miner.lore".equals(path)) {
            return industrialMinerLore(context, listContext, values,
                    "items.sf.advanced_industrial_miner.lore".equals(path));
        }
        boolean generatorBalance = context.api().features().enabled(GENERATOR_BALANCE);
        if ("items.sf.combustion_reactor.lore".equals(path)) {
            return combustionReactorLore(values, generatorBalance);
        }
        if ("items.sf.netherstar_reactor.lore".equals(path)) {
            return netherStarReactorLore(values, generatorBalance);
        }
        Integer capacitorCapacity = sfxCapacitorCapacity(path);
        if (generatorBalance && capacitorCapacity != null) {
            return replaceEnergyCapacity(values, capacitorCapacity);
        }
        if (generatorBalance && path.startsWith("items.sf.electrified_crucible")) {
            return electrifiedCrucibleLore(context, path, values);
        }
        if ("items.sf.animal_growth_accelerator.lore".equals(path) && context.api().features().enabled(ANIMAL_GROWTH_ACCELERATOR_BALANCE)) {
            return replaceEnergyPerTick(values, 80);
        }
        if ("items.sf.electric_ore_grinder_3.lore".equals(path) && context.api().features().enabled(ELECTRIC_ORE_GRINDER_3_BALANCE)) {
            return replaceEnergyPerTick(values, 75);
        }
        if ("items.sf.electric_ingot_pulverizer.lore".equals(path)
                && context.api().features().enabled(ELECTRIC_INGOT_PULVERIZER_BALANCE)) {
            return ingotPulverizerLore(listContext, replaceEnergyPerTick(values, 18));
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

    private static List<String> industrialMinerLore(SfxAddonContext context,
                                                    SfxLocalizedListContext listContext,
                                                    List<String> values, boolean advanced) {
        if (!context.api().features().enabled(INDUSTRIAL_MINER_ACCURACY)) {
            return values;
        }
        double fallback = advanced ? 0.60D : 0.40D;
        String path = advanced ? "industrial-miner.accuracy.advanced" : "industrial-miner.accuracy.normal";
        double accuracy = Math.max(0.0D, Math.min(1.0D, context.configDouble(path, fallback)));
        String line = listContext.rawText("sfx-basic-expansion.lore.industrial-miner-accuracy");
        if (line == null || line.isBlank()) {
            return values;
        }
        line = listContext.applyPlaceholders(line, Map.of(
                "accuracy", String.valueOf(Math.round(accuracy * 100.0D))
        ));
        if (values.contains(line)) {
            return values;
        }
        List<String> copy = new ArrayList<>(values.size() + 1);
        copy.addAll(values);
        copy.add(line);
        return copy;
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
        return replaceEnergyPerTick(values, Math.max(1, (int) Math.round(classicEnergy * multiplier)));
    }

    private static Integer sfxCapacitorCapacity(String path) {
        return switch (path) {
            case "items.sf.small_capacitor.lore" -> 10240;
            case "items.sf.medium_capacitor.lore" -> 40960;
            case "items.sf.big_capacitor.lore" -> 163840;
            case "items.sf.large_capacitor.lore" -> 655360;
            case "items.sf.carbonado_edged_capacitor.lore" -> 2621440;
            case "items.sf.energized_capacitor.lore" -> 10485760;
            default -> null;
        };
    }

    private static List<String> replaceEnergyPerTick(List<String> values, int energy) {
        String replacement = energy + " J/t";
        List<String> copy = new ArrayList<>(values.size());
        for (String line : values) {
            if (line != null && line.contains("J/t")) {
                copy.add(line.contains("&7")
                        ? line.replaceFirst("(?<=&7)\\d+\\s*J/t", replacement)
                        : line.replaceFirst("\\d+\\s*J/t", replacement));
            } else {
                copy.add(line);
            }
        }
        return copy;
    }

    private static List<String> replaceEnergyCapacity(List<String> values, int capacity) {
        String replacement = formatGroupedEnergy(capacity) + " J";
        List<String> copy = new ArrayList<>(values.size());
        for (String line : values) {
            if (line != null && line.contains(" J") && !line.contains("J/t")) {
                copy.add(line.contains("&7")
                        ? line.replaceFirst("(?<=&7)[\\d']+\\s*J", replacement)
                        : line.replaceFirst("[\\d']+\\s*J", replacement));
            } else {
                copy.add(line);
            }
        }
        return copy;
    }

    private static List<String> ingotPulverizerLore(SfxLocalizedListContext context, List<String> values) {
        String replacement = context.rawText("sfx-basic-expansion.lore.electric-ingot-pulverizer-time");
        if (replacement == null || replacement.isBlank()) {
            return values;
        }
        List<String> copy = new ArrayList<>(values.size());
        for (String line : values) {
            if (line != null && (line.contains("Speed:") || line.contains("速度："))) {
                copy.add(context.applyPlaceholders(replacement, Map.of("seconds", "2")));
            } else {
                copy.add(line);
            }
        }
        return copy;
    }

    private static String formatGroupedEnergy(int value) {
        String digits = Integer.toString(Math.max(0, value));
        StringBuilder builder = new StringBuilder(digits.length() + digits.length() / 3);
        int firstGroup = digits.length() % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }
        builder.append(digits, 0, firstGroup);
        for (int index = firstGroup; index < digits.length(); index += 3) {
            builder.append('\'').append(digits, index, index + 3);
        }
        return builder.toString();
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

    static final class BasicTechnicalGadgetBehavior implements SfxTechnicalGadgetBehaviorProvider {
        private static final double JETPACK_VERTICAL_MULTIPLIER = 2.0D;
        private static final double JETPACK_HORIZONTAL_FACTOR = 0.20D;
        private static final double JETPACK_MAX_HORIZONTAL_SPEED = 0.85D;
        private static final double HOVER_HORIZONTAL_FACTOR = 0.20D;
        private static final double HOVER_HOLD_Y = 0.0D;
        private static final double HOVER_UPWARD_DAMPING_Y = -0.020D;
        private static final double HOVER_DOWNWARD_RECOVERY_Y = 0.010D;
        private static final double HOVER_DESCEND_MULTIPLIER = 0.80D;
        private static final double JETBOOTS_HORIZONTAL_MULTIPLIER = 2.0D;
        private static final double JETBOOTS_ASSIST_HORIZONTAL_FACTOR = 0.20D;
        private static final double JETBOOTS_ASSIST_GROUND_HORIZONTAL_FACTOR = 0.40D;
        private static final double JETBOOTS_ASSIST_GROUND_MIN_ACCELERATION = 0.045D;
        private static final double JETBOOTS_MAX_HORIZONTAL_SPEED = 0.95D;
        private static final float AIR_JUMP_SOUND_VOLUME = 0.38F;
        private static final float AIR_JUMP_SOUND_PITCH = 1.55F;

        @Override
        public Vector jetpackVelocity(Player player, Vector currentVelocity, Vector inputDirection, SfxTechnicalGadgetItem jetpack, boolean aboveHeightLimit) {
            double upward = aboveHeightLimit ? 0.0D : jetpack.movementValue() * JETPACK_VERTICAL_MULTIPLIER;
            Vector horizontal = accelerate(currentVelocity, inputDirection, jetpack.movementValue() * JETPACK_HORIZONTAL_FACTOR);
            return limitHorizontal(new Vector(horizontal.getX(), Math.max(currentVelocity.getY(), upward), horizontal.getZ()), maxJetpackHorizontalSpeed(jetpack));
        }

        @Override
        public double hoverCost(SfxTechnicalGadgetItem jetpack, boolean verticalInput) {
            return jetpack.useCost() * (verticalInput ? 1.0D : 0.5D);
        }

        @Override
        public double hoverYVelocity(double currentY, SfxTechnicalGadgetItem jetpack, boolean jumpDown, boolean shiftDown, boolean aboveHeightLimit) {
            if (jumpDown && !aboveHeightLimit) {
                return jetpack.movementValue() * JETPACK_VERTICAL_MULTIPLIER;
            }
            if (shiftDown) {
                return -Math.max(0.10D, jetpack.movementValue() * JETPACK_VERTICAL_MULTIPLIER * HOVER_DESCEND_MULTIPLIER);
            }
            if (currentY > 0.015D) {
                return HOVER_UPWARD_DAMPING_Y;
            }
            if (currentY < -0.070D) {
                return HOVER_DOWNWARD_RECOVERY_Y;
            }
            if (currentY < -0.025D) {
                return HOVER_DOWNWARD_RECOVERY_Y * 0.5D;
            }
            return HOVER_HOLD_Y;
        }

        @Override
        public double hoverHorizontalAcceleration(SfxTechnicalGadgetItem jetpack) {
            return jetpack.movementValue() * HOVER_HORIZONTAL_FACTOR;
        }

        @Override
        public double maxJetpackHorizontalSpeed(SfxTechnicalGadgetItem jetpack) {
            return JETPACK_MAX_HORIZONTAL_SPEED;
        }

        @Override
        public Vector jetBootsThrustVelocity(Player player, Vector currentVelocity, SfxTechnicalGadgetItem jetBoots) {
            Vector direction = player.getEyeLocation().getDirection();
            Vector vector = new Vector(
                    currentVelocity.getX() * 0.35D + direction.getX() * jetBootsThrustHorizontalAcceleration(jetBoots),
                    Math.max(currentVelocity.getY(), 0.06D),
                    currentVelocity.getZ() * 0.35D + direction.getZ() * jetBootsThrustHorizontalAcceleration(jetBoots)
            );
            return limitHorizontal(vector, maxJetBootsHorizontalSpeed(jetBoots));
        }

        @Override
        public double jetBootsThrustHorizontalAcceleration(SfxTechnicalGadgetItem jetBoots) {
            return jetBoots.movementValue() * JETBOOTS_HORIZONTAL_MULTIPLIER;
        }

        @Override
        public double jetBootsAssistAcceleration(Player player, SfxTechnicalGadgetItem jetBoots) {
            double base = jetBoots.movementValue() * JETBOOTS_ASSIST_HORIZONTAL_FACTOR;
            if (!player.isOnGround()) {
                return base;
            }
            return Math.max(base, Math.max(jetBoots.movementValue() * JETBOOTS_ASSIST_GROUND_HORIZONTAL_FACTOR, JETBOOTS_ASSIST_GROUND_MIN_ACCELERATION));
        }

        @Override
        public double jetBootsUseCost(SfxTechnicalGadgetItem jetBoots, SfxJetBootsDriveMode mode) {
            return jetBoots.useCost() * (mode == SfxJetBootsDriveMode.THRUST ? 2.0D : 1.0D);
        }

        @Override
        public double maxJetBootsHorizontalSpeed(SfxTechnicalGadgetItem jetBoots) {
            return JETBOOTS_MAX_HORIZONTAL_SPEED;
        }

        @Override
        public int maxAirJumps(SfxTechnicalGadgetItem jetBoots) {
            int level = Math.max(1, jetBoots.level());
            if (level >= 7) {
                return 3;
            }
            if (level >= 4) {
                return 2;
            }
            return 1;
        }

        @Override
        public double airJumpVelocity(SfxTechnicalGadgetItem jetBoots) {
            return Math.max(0.38D, 0.42D + jetBoots.movementValue());
        }

        @Override
        public double safeFallBonus(SfxTechnicalGadgetItem jetBoots) {
            return switch (Math.max(1, jetBoots.level())) {
                case 1 -> 3.0D;
                case 2 -> 5.0D;
                case 3 -> 7.0D;
                case 4 -> 10.0D;
                case 5 -> 14.0D;
                case 6 -> 20.0D;
                default -> 32.0D;
            };
        }

        @Override
        public double fallDamageMultiplier(SfxTechnicalGadgetItem jetBoots) {
            return switch (Math.max(1, jetBoots.level())) {
                case 1 -> 0.80D;
                case 2 -> 0.70D;
                case 3 -> 0.60D;
                case 4 -> 0.50D;
                case 5 -> 0.40D;
                case 6 -> 0.30D;
                default -> 0.20D;
            };
        }

        @Override
        public void playJetpackEffects(Player player, SfxTechnicalGadgetItem jetpack) {
            Location location = jetpackParticleLocation(player);
            if (jetpack.kind() == SfxTechnicalGadgetItemKind.FUEL_JETPACK) {
                player.getWorld().spawnParticle(Particle.SMOKE, location, 8, 0.12D, 0.10D, 0.12D, 0.02D);
                player.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.11F, 1.25F);
                return;
            }
            player.getWorld().spawnParticle(Particle.CLOUD, location, 6, 0.11D, 0.08D, 0.11D, 0.015D);
            player.getWorld().playSound(location, Sound.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.22F, 1.25F);
        }

        @Override
        public void playJetBootsEffects(Player player, double extraY) {
            Location location = player.getLocation().clone().add(0.0D, 0.18D + extraY, 0.0D);
            player.getWorld().spawnParticle(Particle.CLOUD, location, 4, 0.18D, 0.04D, 0.18D, 0.012D);
            player.getWorld().playSound(location, Sound.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.22F, 1.25F);
        }

        @Override
        public void playJetBootsAirJumpSound(Player player) {
            Location soundLocation = player.getLocation().clone().add(0.0D, 0.18D, 0.0D);
            player.getWorld().playSound(soundLocation, Sound.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, AIR_JUMP_SOUND_VOLUME, AIR_JUMP_SOUND_PITCH);
        }

        private static Vector accelerate(Vector current, Vector direction, double amount) {
            Vector result = new Vector(current.getX(), 0.0D, current.getZ());
            if (amount <= 0.0D || direction.lengthSquared() <= 0.000001D) {
                return result;
            }
            return result.add(direction.clone().normalize().multiply(amount));
        }

        private static Vector limitHorizontal(Vector vector, double maxHorizontalSpeed) {
            double x = vector.getX();
            double z = vector.getZ();
            double horizontal = Math.sqrt(x * x + z * z);
            if (horizontal <= maxHorizontalSpeed || horizontal <= 0.000001D) {
                return vector;
            }
            double scale = maxHorizontalSpeed / horizontal;
            vector.setX(x * scale);
            vector.setZ(z * scale);
            return vector;
        }

        private static Location jetpackParticleLocation(Player player) {
            Location location = player.getLocation().clone().add(0.0D, 0.85D, 0.0D);
            Vector backward = player.getEyeLocation().getDirection();
            backward.setY(0.0D);
            if (backward.lengthSquared() > 0.000001D) {
                backward.normalize().multiply(-0.36D);
                location.add(backward);
            }
            return location;
        }
    }

    static final class BasicAutoBrewerBehavior implements SfxAutoBrewerBehaviorProvider {
        private static final int BLAZE_SLOT = 10;
        private static final int PROGRESS_SLOT = 13;
        private static final int INGREDIENT_SLOT = 16;
        private static final int FUEL_DISPLAY_SLOT = 22;
        private static final int[] POTION_SLOTS = {37, 39, 41, 43};
        private static final int BLAZE_FUEL_TICKS = 600;
        private static final int MAX_BLAZE_FUEL_TICKS = 3000;
        private static final int AUTO_REFILL_THRESHOLD_TICKS = 2400;
        private static final String POWER_CRYSTAL = "sf:power_crystal";
        private static final String MAGIC_SUGAR = "sf:magic_sugar";

        @Override
        public int blazeSlot() {
            return BLAZE_SLOT;
        }

        @Override
        public int progressSlot() {
            return PROGRESS_SLOT;
        }

        @Override
        public int ingredientSlot() {
            return INGREDIENT_SLOT;
        }

        @Override
        public int fuelDisplaySlot() {
            return FUEL_DISPLAY_SLOT;
        }

        @Override
        public int[] potionSlots() {
            return POTION_SLOTS.clone();
        }

        @Override
        public int blazeFuelTicks() {
            return BLAZE_FUEL_TICKS;
        }

        @Override
        public int maxBlazeFuelTicks() {
            return MAX_BLAZE_FUEL_TICKS;
        }

        @Override
        public int autoRefillThresholdTicks() {
            return AUTO_REFILL_THRESHOLD_TICKS;
        }

        @Override
        public boolean validInput(SfxAutoBrewerInputContext context) {
            if (context.empty()) {
                return true;
            }
            if (context.rawSlot() == BLAZE_SLOT) {
                return context.material() == Material.BLAZE_POWDER && !context.hasItemMeta();
            }
            if (context.rawSlot() == INGREDIENT_SLOT) {
                return context.brewingIngredient()
                        || POWER_CRYSTAL.equals(context.sfxItemId())
                        || MAGIC_SUGAR.equals(context.sfxItemId());
            }
            for (int potionSlot : POTION_SLOTS) {
                if (context.rawSlot() == potionSlot) {
                    return context.validPotion();
                }
            }
            return false;
        }
    }
}
