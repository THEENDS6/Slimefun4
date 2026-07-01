package cc.theends6.sfx.internal.machine;

import java.util.Locale;








public final class SfxMachineSpecialProfiles {
    private SfxMachineSpecialProfiles() {}

    private enum Profile {
        VANILLA_FURNACE, HAND_INPUT_TRANSFORM, AUTO_BREWER, AUTO_CRAFTER, GEO_EXTRACTOR, FLUID_PUMP,
        ITEM_META_TRANSFORM, PROXY_PANEL_REACTOR_ACCESS_PORT, FUEL_GENERATOR, SOLAR_GENERATOR, REACTOR,
        ASSEMBLER, DECORATION, STRUCTURAL_DECORATION, GPS_DEVICE, ANDROID_INTERFACE, ANDROID, ANCIENT_ALTAR, CARGO_NODE, ENERGY_NODE,
        CHARGING_BENCH, XP_COLLECTOR, GPS_TRANSMITTER, ELECTRIC_WORLD_ACTION, REINFORCED_SPAWNER, INFUSED_HOPPER,
        HOLOGRAM_PROJECTOR, BLOCK_PLACER, INDUSTRIAL_MINER
    }

    public static SfxMachineDefinition apply(SfxMachineDefinition definition) {
        return apply(definition, null);
    }

    public static SfxMachineDefinition apply(SfxMachineDefinition definition, String declaredProfile) {
        if (definition == null || definition.id() == null) return definition;
        String id = definition.id().toLowerCase(Locale.ROOT);
        SfxMachineDefinition.Builder builder = definition.toBuilder();
        Profile profile = parseProfile(declaredProfile);

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
            case DECORATION -> decoration(builder, false);
            case STRUCTURAL_DECORATION -> decoration(builder, true);
            case GPS_DEVICE -> gpsDevice(builder);
            case ANDROID_INTERFACE -> androidInterface(builder);
            case ANDROID -> android(builder);
            case ANCIENT_ALTAR -> ancientAltar(builder);
            case CARGO_NODE -> cargoNode(builder);
            case ENERGY_NODE -> energyNode(builder);
            case CHARGING_BENCH -> chargingBench(builder);
            case XP_COLLECTOR -> xpCollector(builder);
            case GPS_TRANSMITTER -> gpsTransmitter(builder);
            case ELECTRIC_WORLD_ACTION -> electricWorldAction(builder);
            case REINFORCED_SPAWNER -> reinforcedSpawner(builder);
            case INFUSED_HOPPER -> infusedHopper(builder);
            case HOLOGRAM_PROJECTOR -> hologramProjector(builder);
            case BLOCK_PLACER -> blockPlacer(builder);
            case INDUSTRIAL_MINER -> industrialMiner(builder);
        }
    }

    private static void decoration(SfxMachineDefinition.Builder builder, boolean structural) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .policyRef(SfxMachinePolicyRef.of("block", structural ? "structural-decoration-protection" : "decoration-lifecycle"))
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
                .effect(SfxMachineEffect.marker("electric:special-tick", SfxMachinePhase.BEFORE_OPERATION_RESOLVE));
    }

    private static void gpsTransmitter(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.USES_ENERGY)
                .capability(SfxMachineCapability.USES_GPS)
                .policyRef(SfxMachinePolicyRef.of("gps", "transmitter-energy-service"))
                .effect(SfxMachineEffect.marker("electric:special-tick", SfxMachinePhase.BEFORE_OPERATION_RESOLVE));
    }

    private static void electricWorldAction(SfxMachineDefinition.Builder builder) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.HAS_GUI)
                .capability(SfxMachineCapability.HAS_INPUT)
                .capability(SfxMachineCapability.USES_ENERGY)
                .capability(SfxMachineCapability.USES_PROGRESS)
                .capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .policyRef(SfxMachinePolicyRef.of("world", "electric-world-action-provider"))
                .effect(SfxMachineEffect.marker("electric:world-action", SfxMachinePhase.BEFORE_OPERATION_RESOLVE));
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

}
