package cc.theends6.sfx.internal.machine;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;








public final class SfxMachineSpecialProfiles {
    private SfxMachineSpecialProfiles() {}

    private enum Profile {
        VANILLA_FURNACE, HAND_INPUT_TRANSFORM, AUTO_BREWER, AUTO_CRAFTER, GEO_EXTRACTOR, FLUID_PUMP,
        ITEM_META_TRANSFORM, PROXY_PANEL_REACTOR_ACCESS_PORT, FUEL_GENERATOR, SOLAR_GENERATOR, REACTOR,
        ASSEMBLER, DECORATION, GPS_DEVICE, ANDROID_INTERFACE, ANDROID, ANCIENT_ALTAR, CARGO_NODE, ENERGY_NODE,
        CHARGING_BENCH, XP_COLLECTOR, REINFORCED_SPAWNER, INFUSED_HOPPER, HOLOGRAM_PROJECTOR, BLOCK_PLACER, INDUSTRIAL_MINER
    }

    private static final Map<String, Profile> LEGACY_PROFILES = legacyProfiles();
    private static final Set<String> STRUCTURAL_DECORATIONS = Set.of("sf:hardened_glass", "sf:wither_proof_obsidian", "sf:wither_proof_glass");

    private static Map<String, Profile> legacyProfiles() {
        Map<String, Profile> profiles = new LinkedHashMap<>();
        register(profiles, Profile.VANILLA_FURNACE, "sf:carbonado_edged_furnace", "sf:enhanced_furnace", "sf:enhanced_furnace_10", "sf:enhanced_furnace_11", "sf:enhanced_furnace_2", "sf:enhanced_furnace_3", "sf:enhanced_furnace_4", "sf:enhanced_furnace_5", "sf:enhanced_furnace_6", "sf:enhanced_furnace_7", "sf:enhanced_furnace_8", "sf:enhanced_furnace_9", "sf:reinforced_furnace");
        register(profiles, Profile.HAND_INPUT_TRANSFORM, "sf:composter", "sf:crucible");
        register(profiles, Profile.AUTO_BREWER, "sf:auto_brewer", "sf:auto_brewer_2");
        register(profiles, Profile.AUTO_CRAFTER, "sf:armor_auto_crafter", "sf:enhanced_auto_crafter", "sf:vanilla_auto_crafter");
        register(profiles, Profile.GEO_EXTRACTOR, "sf:geo_miner", "sf:oil_pump");
        register(profiles, Profile.FLUID_PUMP, "sf:fluid_pump");
        register(profiles, Profile.ITEM_META_TRANSFORM, "sf:auto_anvil", "sf:auto_anvil_2", "sf:auto_disenchanter", "sf:auto_disenchanter_2", "sf:auto_enchanter", "sf:auto_enchanter_2", "sf:book_binder");
        register(profiles, Profile.PROXY_PANEL_REACTOR_ACCESS_PORT, "sf:reactor_access_port");
        register(profiles, Profile.FUEL_GENERATOR, "sf:bio_reactor", "sf:bio_reactor_2");
        register(profiles, Profile.REACTOR, "sf:combustion_reactor", "sf:netherstar_reactor", "sf:nuclear_reactor", "sf:reactor_coolant_cell");
        register(profiles, Profile.ASSEMBLER, "sf:iron_golem_assembler", "sf:wither_assembler");
        register(profiles, Profile.DECORATION, "sf:gps_teleporter_pylon", "sf:hardened_glass", "sf:rainbow_boots", "sf:rainbow_chestplate", "sf:rainbow_clay", "sf:rainbow_clay_halloween", "sf:rainbow_clay_valentine", "sf:rainbow_clay_xmas", "sf:rainbow_concrete", "sf:rainbow_concrete_halloween", "sf:rainbow_concrete_valentine", "sf:rainbow_concrete_xmas", "sf:rainbow_glass", "sf:rainbow_glass_halloween", "sf:rainbow_glass_pane", "sf:rainbow_glass_pane_halloween", "sf:rainbow_glass_pane_valentine", "sf:rainbow_glass_pane_xmas", "sf:rainbow_glass_valentine", "sf:rainbow_glass_xmas", "sf:rainbow_glazed_terracotta", "sf:rainbow_glazed_terracotta_halloween", "sf:rainbow_glazed_terracotta_valentine", "sf:rainbow_glazed_terracotta_xmas", "sf:rainbow_helmet", "sf:rainbow_leather", "sf:rainbow_leggings", "sf:rainbow_wool", "sf:rainbow_wool_halloween", "sf:rainbow_wool_valentine", "sf:rainbow_wool_xmas", "sf:wither_proof_glass", "sf:wither_proof_obsidian");
        register(profiles, Profile.GPS_DEVICE, "sf:gps_activation_device_personal", "sf:gps_activation_device_shared", "sf:gps_control_panel", "sf:gps_emergency_transmitter", "sf:gps_geo_scanner", "sf:gps_marker_tool", "sf:gps_teleportation_matrix", "sf:gps_transmitter", "sf:gps_transmitter_2", "sf:gps_transmitter_3", "sf:gps_transmitter_4");
        register(profiles, Profile.ANDROID_INTERFACE, "sf:android_interface_fuel", "sf:android_interface_items");
        register(profiles, Profile.ANDROID, "sf:android_memory_core", "sf:programmable_android", "sf:programmable_android_2", "sf:programmable_android_2_butcher", "sf:programmable_android_2_farmer", "sf:programmable_android_2_fisherman", "sf:programmable_android_3", "sf:programmable_android_3_butcher", "sf:programmable_android_3_fisherman", "sf:programmable_android_butcher", "sf:programmable_android_farmer", "sf:programmable_android_fisherman", "sf:programmable_android_miner", "sf:programmable_android_woodcutter");
        register(profiles, Profile.ANCIENT_ALTAR, "sf:ancient_altar");
        register(profiles, Profile.CARGO_NODE, "sf:cargo_manager", "sf:cargo_motor", "sf:cargo_node", "sf:cargo_node_input", "sf:cargo_node_input_advanced", "sf:cargo_node_output", "sf:cargo_node_output_advanced");
        register(profiles, Profile.ENERGY_NODE, "sf:big_capacitor", "sf:carbonado_edged_capacitor", "sf:energized_capacitor", "sf:energy_connector", "sf:energy_regulator", "sf:large_capacitor", "sf:medium_capacitor", "sf:small_capacitor");
        register(profiles, Profile.SOLAR_GENERATOR, "sf:solar_generator", "sf:solar_generator_2", "sf:solar_generator_3", "sf:solar_generator_4");
        register(profiles, Profile.FUEL_GENERATOR, "sf:coal_generator", "sf:coal_generator_2", "sf:lava_generator", "sf:lava_generator_2", "sf:magnesium_generator");
        register(profiles, Profile.CHARGING_BENCH, "sf:charging_bench");
        register(profiles, Profile.XP_COLLECTOR, "sf:xp_collector");
        register(profiles, Profile.REINFORCED_SPAWNER, "sf:reinforced_spawner");
        register(profiles, Profile.INFUSED_HOPPER, "sf:infused_hopper");
        register(profiles, Profile.HOLOGRAM_PROJECTOR, "sf:hologram_projector");
        register(profiles, Profile.BLOCK_PLACER, "sf:block_placer");
        register(profiles, Profile.INDUSTRIAL_MINER, "sf:advanced_industrial_miner", "sf:industrial_miner");
        return Map.copyOf(profiles);
    }

    private static void register(Map<String, Profile> profiles, Profile profile, String... ids) {
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                profiles.putIfAbsent(id.toLowerCase(Locale.ROOT), profile);
            }
        }
    }

    public static SfxMachineDefinition apply(SfxMachineDefinition definition) {
        return apply(definition, null);
    }

    public static SfxMachineDefinition apply(SfxMachineDefinition definition, String declaredProfile) {
        if (definition == null || definition.id() == null) return definition;
        String id = definition.id().toLowerCase(Locale.ROOT);
        SfxMachineDefinition.Builder builder = definition.toBuilder();
        Profile profile = parseProfile(declaredProfile);
        if (profile == null) {
            profile = LEGACY_PROFILES.get(id);
        }

        if (profile != null) {
            applyProfile(builder, profile, id);
        } else if (definition.category() == SfxMachineCategory.ELECTRIC) {
            recipeMachine(builder);
            builder.capability(SfxMachineCapability.USES_ENERGY);
        } else if (definition.category() == SfxMachineCategory.CONFIGURABLE) {
            commonMachine(builder);
            builder.capability(SfxMachineCapability.HAS_GUI).capability(SfxMachineCapability.USES_ENERGY);
        } else if (definition.category() == SfxMachineCategory.BASIC) {
            commonMachine(builder);
        }
        if (definition.category() == SfxMachineCategory.SPECIAL && !definition.effects().isEmpty()) {
            commonMachine(builder);
        }
        return builder.build();
    }

    private static Profile parseProfile(String declaredProfile) {
        if (declaredProfile == null || declaredProfile.isBlank()) {
            return null;
        }
        String normalized = declaredProfile.trim().replace('-', '_').replace(':', '_').toUpperCase(Locale.ROOT);
        try {
            return Profile.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void applyProfile(SfxMachineDefinition.Builder builder, Profile profile, String id) {
        switch (profile) {
            case VANILLA_FURNACE -> vanillaFurnace(builder);
            case HAND_INPUT_TRANSFORM -> handInputTransform(builder);
            case AUTO_BREWER -> autoBrewer(builder);
            case AUTO_CRAFTER -> autoCrafter(builder);
            case GEO_EXTRACTOR -> geoExtractor(builder);
            case FLUID_PUMP -> fluidPump(builder);
            case ITEM_META_TRANSFORM -> itemMetaTransform(builder);
            case PROXY_PANEL_REACTOR_ACCESS_PORT -> proxyPanel(builder, "reactor-access-port");
            case FUEL_GENERATOR -> generator(builder, false);
            case SOLAR_GENERATOR -> generator(builder, true);
            case REACTOR -> reactor(builder);
            case ASSEMBLER -> assembler(builder);
            case DECORATION -> decoration(builder, id);
            case GPS_DEVICE -> gpsDevice(builder);
            case ANDROID_INTERFACE -> androidInterface(builder);
            case ANDROID -> android(builder);
            case ANCIENT_ALTAR -> ancientAltar(builder);
            case CARGO_NODE -> cargoNode(builder);
            case ENERGY_NODE -> energyNode(builder);
            case CHARGING_BENCH -> chargingBench(builder);
            case XP_COLLECTOR -> xpCollector(builder);
            case REINFORCED_SPAWNER -> reinforcedSpawner(builder);
            case INFUSED_HOPPER -> infusedHopper(builder);
            case HOLOGRAM_PROJECTOR -> hologramProjector(builder);
            case BLOCK_PLACER -> blockPlacer(builder);
            case INDUSTRIAL_MINER -> industrialMiner(builder);
        }
    }

    private static void decoration(SfxMachineDefinition.Builder builder, String id) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .policyRef(SfxMachinePolicyRef.of("block", STRUCTURAL_DECORATIONS.contains(id) ? "structural-decoration-protection" : "decoration-lifecycle"))
                .effect(SfxMachineEffect.marker("decoration:sync-visual", SfxMachinePhase.ON_PLACE))
                .effect(SfxMachineEffect.marker("decoration:animate-state", SfxMachinePhase.BEFORE_PROGRESS))
                .effect(SfxMachineEffect.marker("decoration:sync-visual", SfxMachinePhase.AFTER_TICK))
                .effect(SfxMachineEffect.marker("decoration:drop-plugin-block", SfxMachinePhase.ON_BREAK));
    }

    private static void commonMachine(SfxMachineDefinition.Builder builder) {
        builder.capability(SfxMachineCapability.HAS_CUSTOM_STATUS)
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.ON_PLACE))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.ON_BREAK))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.ON_INTERACT))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.ON_MENU_OPEN))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.ON_MENU_CLICK))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.ON_MENU_CLOSE))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.BEFORE_TRANSFER))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.AFTER_TRANSFER))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.BEFORE_NETWORK_TICK))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.AFTER_NETWORK_TICK))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.BEFORE_WORLD_MUTATION))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.AFTER_WORLD_MUTATION))
                .effect(SfxMachineEffect.marker("framework:audit-tick", SfxMachinePhase.AFTER_TICK));
    }

    private static void recipeMachine(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.HAS_INPUT)
                .capability(SfxMachineCapability.HAS_OUTPUT)
                .capability(SfxMachineCapability.USES_RECIPE)
                .capability(SfxMachineCapability.USES_PROGRESS)
                .policyRef(SfxMachinePolicyRef.of("recipe", "default-recipe-resolver"))
                .policyRef(SfxMachinePolicyRef.of("output", "all-or-nothing-transfer"))
                .effect(SfxMachineEffect.marker("recipe:resolve-operation", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("inventory:reserve-output", SfxMachinePhase.BEFORE_OUTPUT))
                .effect(SfxMachineEffect.marker("inventory:commit-output", SfxMachinePhase.AFTER_OUTPUT));
    }

    private static void vanillaFurnace(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.USES_VANILLA_BLOCK_INVENTORY)
                .capability(SfxMachineCapability.USES_RECIPE)
                .capability(SfxMachineCapability.USES_PROGRESS)
                .inputProvider(SfxMachineInputProvider.vanillaBlockInventory("furnace input/fuel/result"))
                .outputProvider(SfxMachineOutputProvider.vanillaBlockInventory("furnace result slot"))
                .policyRef(SfxMachinePolicyRef.of("fuel", "enhanced-furnace-fuel-multiplier"))
                .policyRef(SfxMachinePolicyRef.of("recipe", "vanilla-furnace-bridge"))
                .effect(SfxMachineEffect.marker("furnace:intercept-burn-smelt", SfxMachinePhase.BEFORE_TICK))
                .effect(SfxMachineEffect.marker("furnace:sync-virtual-state", SfxMachinePhase.AFTER_TICK));
    }

    private static void handInputTransform(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAND_INPUT)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .inputProvider(SfxMachineInputProvider.hand("player hand"))
                .outputProvider(SfxMachineOutputProvider.worldDrop("direct hand transform result"))
                .policyRef(SfxMachinePolicyRef.of("input", "hand-consume"))
                .policyRef(SfxMachinePolicyRef.of("recipe", "material-tag-transform"))
                .effect(SfxMachineEffect.marker("hand:consume-input", SfxMachinePhase.BEFORE_INPUT))
                .effect(SfxMachineEffect.marker("world:drop-result", SfxMachinePhase.AFTER_OUTPUT));
    }

    private static void autoBrewer(SfxMachineDefinition.Builder builder) {
        recipeMachine(builder);
        builder.capability(SfxMachineCapability.USES_FUEL_BUFFER)
                .policyRef(SfxMachinePolicyRef.of("fuel", "blaze-powder-buffer"))
                .policyRef(SfxMachinePolicyRef.of("snapshot", "potion-input-snapshot"))
                .effect(SfxMachineEffect.marker("brew:validate-potions", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("brew:refund-on-interrupt", SfxMachinePhase.ON_ERROR))
                .effect(SfxMachineEffect.marker("brew:commit-multi-bottle-output", SfxMachinePhase.AFTER_OUTPUT));
    }

    private static void autoCrafter(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.USES_ENERGY)
                .capability(SfxMachineCapability.USES_EXTERNAL_CONTAINER)
                .capability(SfxMachineCapability.USES_RECIPE)
                .capability(SfxMachineCapability.HAS_OUTPUT)
                .inputProvider(SfxMachineInputProvider.externalContainer("adjacent/below container"))
                .outputProvider(SfxMachineOutputProvider.externalContainer("same external container"))
                .policyRef(SfxMachinePolicyRef.of("recipe", "vanilla-and-sfx-crafting-resolver"))
                .policyRef(SfxMachinePolicyRef.of("transaction", "external-container-crafting"))
                .effect(SfxMachineEffect.marker("crafting:simulate", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("crafting:commit-transaction", SfxMachinePhase.ON_COMPLETE));
    }

    private static void geoExtractor(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.HAS_OUTPUT)
                .capability(SfxMachineCapability.USES_ENERGY)
                .capability(SfxMachineCapability.USES_GPS)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .policyRef(SfxMachinePolicyRef.of("gps", "require-scanned-chunk"))
                .policyRef(SfxMachinePolicyRef.of("resource", "geo-resource-extraction"))
                .effect(SfxMachineEffect.marker("gps:check-signal-and-scan", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("geo:extract-resource", SfxMachinePhase.ON_COMPLETE))
                .effect(SfxMachineEffect.marker("visual:update-floating-text", SfxMachinePhase.AFTER_TICK));
    }

    private static void fluidPump(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.HAS_OUTPUT)
                .capability(SfxMachineCapability.USES_ENERGY)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .policyRef(SfxMachinePolicyRef.of("world", "fluid-source-extraction"))
                .effect(SfxMachineEffect.marker("fluid:locate-source", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("fluid:remove-source-and-update", SfxMachinePhase.ON_COMPLETE));
    }

    private static void itemMetaTransform(SfxMachineDefinition.Builder builder) {
        recipeMachine(builder);
        builder.capability(SfxMachineCapability.ITEM_META_TRANSFORM)
                .policyRef(SfxMachinePolicyRef.of("item-meta", "safe-copy-transform"))
                .effect(SfxMachineEffect.marker("meta:validate-input", SfxMachinePhase.BEFORE_INPUT))
                .effect(SfxMachineEffect.marker("meta:apply-transform", SfxMachinePhase.ON_COMPLETE));
    }

    private static void reactor(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.PRODUCES_ENERGY)
                .capability(SfxMachineCapability.USES_FUEL_BUFFER)
                .capability(SfxMachineCapability.USES_REACTOR_SAFETY)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .policyRef(SfxMachinePolicyRef.of("reactor", "fuel-cycle"))
                .policyRef(SfxMachinePolicyRef.of("reactor", "coolant-safety"))
                .policyRef(SfxMachinePolicyRef.of("reactor", "meltdown-destruction-transaction"))
                .effect(SfxMachineEffect.marker("reactor:consume-coolant", SfxMachinePhase.AFTER_PROGRESS))
                .effect(SfxMachineEffect.marker("reactor:emit-energy", SfxMachinePhase.AFTER_PROGRESS))
                .effect(SfxMachineEffect.marker("reactor:meltdown-on-error", SfxMachinePhase.ON_ERROR));
    }

    private static void assembler(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.HAS_INPUT)
                .capability(SfxMachineCapability.USES_ENERGY)
                .capability(SfxMachineCapability.SPAWNS_ENTITY)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .policyRef(SfxMachinePolicyRef.of("entity", "assembly-spawn-target"))
                .effect(SfxMachineEffect.marker("assembler:validate-offset", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("assembler:spawn-entity", SfxMachinePhase.ON_COMPLETE));
    }

    private static void gpsDevice(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.USES_GPS)
                .capability(SfxMachineCapability.HAS_CUSTOM_STATUS)
                .policyRef(SfxMachinePolicyRef.of("gps", "waypoint-signal-teleport"))
                .effect(SfxMachineEffect.marker("gps:resolve-signal", SfxMachinePhase.BEFORE_OPERATION_RESOLVE));
    }

    private static void proxyPanel(SfxMachineDefinition.Builder builder, String name) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_PROXY_PANEL)
                .capability(SfxMachineCapability.HAS_GUI)
                .policyRef(SfxMachinePolicyRef.of("proxy", name))
                .effect(SfxMachineEffect.marker("proxy:resolve-host", SfxMachinePhase.BEFORE_OPERATION_RESOLVE));
    }

    private static void androidInterface(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.USES_EXTERNAL_CONTAINER)
                .capability(SfxMachineCapability.STORAGE_ENDPOINT)
                .inputProvider(SfxMachineInputProvider.externalContainer("android interface inventory"))
                .outputProvider(SfxMachineOutputProvider.externalContainer("android interface inventory"))
                .policyRef(SfxMachinePolicyRef.of("android", "interface-storage-endpoint"))
                .effect(SfxMachineEffect.marker("android-interface:sync-storage", SfxMachinePhase.AFTER_TICK));
    }

    private static void android(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.SCRIPTED)
                .capability(SfxMachineCapability.MOVES_BLOCK)
                .capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.USES_FUEL_BUFFER)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.USES_EXTERNAL_CONTAINER)
                .inputProvider(SfxMachineInputProvider.externalContainer("android interface/fuel"))
                .outputProvider(SfxMachineOutputProvider.externalContainer("android interface/items"))
                .policyRef(SfxMachinePolicyRef.of("android", "script-instruction-registry"))
                .policyRef(SfxMachinePolicyRef.of("android", "block-relocation-transaction"))
                .effect(SfxMachineEffect.marker("android:execute-script-step", SfxMachinePhase.AFTER_PROGRESS))
                .effect(SfxMachineEffect.marker("android:relocate-anchor", SfxMachinePhase.ON_COMPLETE));
    }

    private static void ancientAltar(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.USES_MULTIBLOCK)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .inputProvider(SfxMachineInputProvider.pedestals("ancient altar pedestals"))
                .outputProvider(SfxMachineOutputProvider.worldDrop("ritual output"))
                .policyRef(SfxMachinePolicyRef.of("multiblock", "altar-structure"))
                .policyRef(SfxMachinePolicyRef.of("ritual", "animated-transaction"))
                .effect(SfxMachineEffect.marker("altar:validate-structure", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("altar:play-ritual", SfxMachinePhase.ON_COMPLETE));
    }

    private static void cargoNode(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.TOPOLOGY_NODE)
                .capability(SfxMachineCapability.STORAGE_ENDPOINT)
                .capability(SfxMachineCapability.HAS_GUI)
                .policyRef(SfxMachinePolicyRef.of("network", "cargo-topology"))
                .policyRef(SfxMachinePolicyRef.of("inventory", "transfer-transaction"))
                .effect(SfxMachineEffect.marker("cargo:resolve-endpoints", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("cargo:commit-transfer", SfxMachinePhase.ON_COMPLETE));
    }

    private static void energyNode(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.TOPOLOGY_NODE)
                .capability(SfxMachineCapability.HAS_CUSTOM_STATUS)
                .policyRef(SfxMachinePolicyRef.of("network", "energy-topology"))
                .effect(SfxMachineEffect.marker("energy:inspect-grid", SfxMachinePhase.AFTER_TICK));
    }

    private static void generator(SfxMachineDefinition.Builder builder, boolean solar) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.PRODUCES_ENERGY)
                .capability(SfxMachineCapability.TOPOLOGY_NODE)
                .policyRef(SfxMachinePolicyRef.of("generator", solar ? "world-condition-solar" : "fuel-generator"))
                .effect(SfxMachineEffect.marker(solar ? "generator:check-world-condition" : "generator:consume-fuel", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("generator:emit-energy", SfxMachinePhase.AFTER_PROGRESS));
    }

    private static void chargingBench(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.USES_ENERGY)
                .capability(SfxMachineCapability.ITEM_META_TRANSFORM)
                .policyRef(SfxMachinePolicyRef.of("charge", "rechargeable-item-bridge"))
                .effect(SfxMachineEffect.marker("charge:write-item-energy", SfxMachinePhase.AFTER_PROGRESS));
    }

    private static void xpCollector(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.HAS_OUTPUT)
                .capability(SfxMachineCapability.USES_ENERGY)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .policyRef(SfxMachinePolicyRef.of("world", "nearby-experience-collection"))
                .effect(SfxMachineEffect.marker("xp:collect-orbs", SfxMachinePhase.BEFORE_OPERATION_RESOLVE));
    }


    private static void reinforcedSpawner(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_CUSTOM_STATUS)
                .policyRef(SfxMachinePolicyRef.of("spawner", "reinforced-spawner-block-state"))
                .effect(SfxMachineEffect.marker("spawner:restore-entity-type", SfxMachinePhase.ON_PLACE))
                .effect(SfxMachineEffect.marker("spawner:drop-fractured-item", SfxMachinePhase.ON_BREAK))
                .effect(SfxMachineEffect.marker("spawner:repair-to-reinforced", SfxMachinePhase.ON_COMPLETE));
    }

    private static void infusedHopper(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.TOPOLOGY_NODE)
                .capability(SfxMachineCapability.STORAGE_ENDPOINT)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .policyRef(SfxMachinePolicyRef.of("inventory", "nearby-item-pickup-transfer"))
                .effect(SfxMachineEffect.marker("hopper:scan-items", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("hopper:teleport-item", SfxMachinePhase.AFTER_PROGRESS))
                .effect(SfxMachineEffect.marker("hopper:emit-particles", SfxMachinePhase.ON_COMPLETE));
    }

    private static void hologramProjector(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .policyRef(SfxMachinePolicyRef.of("display", "floating-text-projector"))
                .effect(SfxMachineEffect.marker("hologram:open-editor", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("hologram:update-text", SfxMachinePhase.ON_COMPLETE))
                .effect(SfxMachineEffect.marker("hologram:sync-display", SfxMachinePhase.AFTER_TICK));
    }

    private static void blockPlacer(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.USES_VANILLA_BLOCK_INVENTORY)
                .inputProvider(SfxMachineInputProvider.vanillaBlockInventory("dispenser inventory"))
                .outputProvider(SfxMachineOutputProvider.worldDrop("rollback/drop if placement fails"))
                .policyRef(SfxMachinePolicyRef.of("world", "dispenser-placement-transaction"))
                .effect(SfxMachineEffect.marker("placer:resolve-target", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("placer:consume-input", SfxMachinePhase.BEFORE_INPUT))
                .effect(SfxMachineEffect.marker("placer:place-block", SfxMachinePhase.ON_COMPLETE))
                .effect(SfxMachineEffect.marker("placer:rollback-on-fail", SfxMachinePhase.ON_ERROR));
    }

    private static void industrialMiner(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.USES_MULTIBLOCK)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_OUTPUT)
                .capability(SfxMachineCapability.USES_FUEL_BUFFER)
                .policyRef(SfxMachinePolicyRef.of("multiblock", "industrial-miner-structure"))
                .policyRef(SfxMachinePolicyRef.of("inventory", "miner-output-transaction"))
                .effect(SfxMachineEffect.marker("miner:validate-structure", SfxMachinePhase.BEFORE_OPERATION_RESOLVE))
                .effect(SfxMachineEffect.marker("miner:consume-fuel", SfxMachinePhase.BEFORE_INPUT))
                .effect(SfxMachineEffect.marker("miner:animate-piston", SfxMachinePhase.BEFORE_PROGRESS))
                .effect(SfxMachineEffect.marker("miner:extract-ore", SfxMachinePhase.ON_COMPLETE))
                .effect(SfxMachineEffect.marker("miner:commit-output", SfxMachinePhase.AFTER_OUTPUT))
                .effect(SfxMachineEffect.marker("miner:stop-on-error", SfxMachinePhase.ON_ERROR));
    }

    public static Set<SfxMachineCapability> legacyCapabilities(String machineId) {
        SfxMachineDefinition definition = apply(new SfxMachineDefinition(machineId, machineId, List.of(), List.of(), -1, 1));
        return definition.capabilities().isEmpty() ? Set.of() : EnumSet.copyOf(definition.capabilities());
    }
}
