package cc.theends6.sfx.internal.machine;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;








public final class SfxMachineSpecialProfiles {
    private SfxMachineSpecialProfiles() {}

    public static SfxMachineDefinition apply(SfxMachineDefinition definition) {
        if (definition == null || definition.id() == null) return definition;
        String id = definition.id().toLowerCase(Locale.ROOT);
        SfxMachineDefinition.Builder builder = definition.toBuilder();

        if (id.contains("enhanced_furnace") || id.contains("reinforced_furnace") || id.contains("carbonado_edged_furnace")) {
            vanillaFurnace(builder);
        } else if (id.equals("sf:composter") || id.equals("sf:crucible")) {
            handInputTransform(builder);
        } else if (id.contains("auto_brewer")) {
            autoBrewer(builder);
        } else if (id.contains("auto_crafter")) {
            autoCrafter(builder);
        } else if (id.contains("geo_miner") || id.contains("oil_pump")) {
            geoExtractor(builder);
        } else if (id.contains("fluid_pump")) {
            fluidPump(builder);
        } else if (id.contains("auto_enchanter") || id.contains("auto_disenchanter") || id.contains("auto_anvil") || id.contains("book_binder")) {
            itemMetaTransform(builder);
        } else if (id.contains("reactor_access_port")) {
            proxyPanel(builder, "reactor-access-port");
        } else if (id.contains("bio_reactor")) {
            generator(builder, false);
        } else if (id.contains("reactor")) {
            reactor(builder);
        } else if (id.contains("assembler")) {
            assembler(builder);
        } else if (isDecoration(id)) {
            decoration(builder, id);
        } else if (id.contains("gps_")) {
            gpsDevice(builder);
        } else if (id.contains("android_interface")) {
            androidInterface(builder);
        } else if (id.contains("android")) {
            android(builder);
        } else if (id.contains("ancient_altar")) {
            ancientAltar(builder);
        } else if (id.contains("cargo_")) {
            cargoNode(builder);
        } else if (id.contains("capacitor") || id.contains("energy_regulator") || id.contains("energy_connector")) {
            energyNode(builder);
        } else if (id.contains("generator")) {
            generator(builder, id.contains("solar"));
        } else if (id.contains("charging_bench")) {
            chargingBench(builder);
        } else if (id.contains("reinforced_spawner")) {
            reinforcedSpawner(builder);
        } else if (id.contains("infused_hopper")) {
            infusedHopper(builder);
        } else if (id.contains("hologram_projector")) {
            hologramProjector(builder);
        } else if (id.contains("block_placer")) {
            blockPlacer(builder);
        } else if (id.contains("industrial_miner")) {
            industrialMiner(builder);
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

    private static boolean isDecoration(String id) {
        return id.equals("sf:gps_teleporter_pylon")
                || id.equals("sf:hardened_glass")
                || id.equals("sf:wither_proof_obsidian")
                || id.equals("sf:wither_proof_glass")
                || id.contains("rainbow_");
    }

    private static void decoration(SfxMachineDefinition.Builder builder, String id) {
        commonMachine(builder);
        builder.capability(SfxMachineCapability.MUTATES_WORLD)
                .capability(SfxMachineCapability.HAS_VISUAL_EFFECTS)
                .policyRef(SfxMachinePolicyRef.of("block", id.contains("wither_proof") || id.contains("hardened_glass") ? "structural-decoration-protection" : "decoration-lifecycle"))
                .effect(SfxMachineEffect.marker("decoration:sync-visual", SfxMachinePhase.ON_PLACE))
                .effect(SfxMachineEffect.marker("decoration:animate-state", SfxMachinePhase.BEFORE_PROGRESS))
                .effect(SfxMachineEffect.marker("decoration:sync-visual", SfxMachinePhase.AFTER_TICK))
                .effect(SfxMachineEffect.marker("decoration:drop-plugin-block", SfxMachinePhase.ON_BREAK));
    }

    private static void commonMachine(SfxMachineDefinition.Builder builder) {
        builder.capability(SfxMachineCapability.HAS_CUSTOM_STATUS)
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
