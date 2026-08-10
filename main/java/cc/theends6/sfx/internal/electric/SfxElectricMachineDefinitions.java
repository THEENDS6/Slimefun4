package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.behavior.SfxElectricMachineProviderKeyContext;
import cc.theends6.sfx.api.behavior.SfxElectricMachineProviderKeyPolicy;
import cc.theends6.sfx.api.behavior.SfxAreaMachineRules;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxElectricMachineDefinitions {
    private SfxElectricMachineDefinitions() {
    }

    static SfxElectricMachineRegistry create(JavaPlugin plugin, SfxItems items, DefaultManualMachineRegistry manualMachines, SfxBlockDataService blockData, SfxVirtualContainerService virtualContainers) {
        SfxElectricMachineRegistry result = new SfxElectricMachineRegistry();
        SfxElectricMachineDefinitionConfig config = SfxElectricMachineDefinitionConfig.load(plugin);
        SfxElectricRecipeYamlLoader staticRecipes = SfxElectricRecipeYamlLoader.load(plugin);
        SfxElectricRecipeProvider furnaceRecipes = new SfxVanillaFurnaceRecipeProvider(plugin, 4);
        SfxElectricRecipeProvider grinderRecipes = new SfxClassicOreGrinderRecipeProvider(
                manualMachines.recipesFor("sf:grind_stone"),
                manualMachines.recipesFor("sf:ore_crusher"),
                4);
        SfxElectricRecipeProvider ingotPulverizerRecipes = staticRecipes.provider(new SfxElectricMachineRuntimeBinding(
                "sf:electric_processing", null, java.util.Set.of("ingot-pulverizing"),
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of()));
        SfxElectricRecipeProvider electricSmelteryRecipes = new SfxElectricSmelteryRecipeProvider(manualMachines.recipesFor("sf:smeltery"), 6);
        SfxElectricRecipeProvider autoEnchanterRecipes = SfxSpecialElectricRecipeProviders.autoEnchanter(plugin, items);
        SfxElectricRecipeProvider autoDisenchanterRecipes = SfxSpecialElectricRecipeProviders.autoDisenchanter(plugin, items);
        SfxElectricRecipeProvider bookBinderRecipes = SfxSpecialElectricRecipeProviders.bookBinder(plugin);
        SfxElectricRecipeProvider autoAnvilRecipes = SfxSpecialElectricRecipeProviders.autoAnvil(plugin, items, 10);
        SfxElectricRecipeProvider autoAnvil2Recipes = SfxSpecialElectricRecipeProviders.autoAnvil(plugin, items, 25);
        SfxElectricRecipeProvider produceCollectorRecipes = SfxAreaElectricMachineProviders.produceCollector();
        SfxElectricRecipeProvider autoBreederRecipes = SfxAreaElectricMachineProviders.autoBreeder();
        SfxElectricRecipeProvider animalGrowthRecipes = SfxAreaElectricMachineProviders.animalGrowthAccelerator(2000);
        SfxElectricRecipeProvider cropGrowthRecipes = SfxAreaElectricMachineProviders.cropGrowthAccelerator(3);
        SfxElectricRecipeProvider cropGrowth2Recipes = SfxAreaElectricMachineProviders.cropGrowthAccelerator(4);
        SfxElectricRecipeProvider treeGrowthRecipes = SfxAreaElectricMachineProviders.treeGrowthAccelerator();
        SfxAreaMachineRules classicAreaRules = SfxAreaMachineRules.classicDefaults();
        SfxElectricRecipeProvider expCollectorRecipes = SfxAreaElectricMachineProviders.expCollector(classicAreaRules.xpFlaskEnergyCost());
        SfxElectricRecipeProvider fluidPumpRecipes = SfxAreaElectricMachineProviders.fluidPump(classicAreaRules);
        SfxElectricAssemblerSpec ironGolemAssemblerSpec = config.assemblerSpec("sf:iron_golem_assembler");
        SfxElectricAssemblerSpec witherAssemblerSpec = config.assemblerSpec("sf:wither_assembler");
        SfxElectricRecipeProvider ironGolemAssemblerRecipes = assemblerProvider("iron_golem", EntityType.IRON_GOLEM, ironGolemAssemblerSpec);
        SfxElectricRecipeProvider witherAssemblerRecipes = assemblerProvider("wither", EntityType.WITHER, witherAssemblerSpec);
        SfxElectricRecipeProvider gpsTransmitterRecipes = SfxGpsElectricMachineProviders.transmitter();
        SfxElectricRecipeProvider geoMinerRecipes = SfxGpsElectricMachineProviders.geoExtractor(false);
        SfxElectricRecipeProvider oilPumpRecipes = SfxGpsElectricMachineProviders.geoExtractor(true);
        SfxElectricRecipeProvider classicAutoBrewerRecipes = new SfxAutoBrewerRecipeProvider();
        String autoBrewerProviderKey = resolveElectricProviderKey(plugin, "sf:auto_brewer", "sf:auto_brewer", "sf:auto_brewer");
        boolean autoBrewerProviderOverridden = !"sf:auto_brewer".equals(autoBrewerProviderKey);
        SfxElectricRecipeProvider vanillaAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.vanilla(virtualContainers, items);
        SfxElectricRecipeProvider enhancedAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.enhanced(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:enhanced_crafting_table")));
        SfxElectricRecipeProvider armorAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.armor(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:armor_forge")));
        Map<String, SfxElectricRecipeProvider> providers = new LinkedHashMap<>();
        providers.put("sf:vanilla_furnace", furnaceRecipes);
        providers.put("sf:classic_ore_grinder", grinderRecipes);
        providers.put("sf:classic_ingot_pulverizer", ingotPulverizerRecipes);
        providers.put("sf:electric_smeltery", electricSmelteryRecipes);
        providers.put("sf:auto_brewer", classicAutoBrewerRecipes);
        providers.put("sf:auto_brewer_legacy", classicAutoBrewerRecipes);
        providers.put("sf:vanilla_auto_crafter", vanillaAutoCrafterRecipes);
        providers.put("sf:enhanced_auto_crafter", enhancedAutoCrafterRecipes);
        providers.put("sf:armor_auto_crafter", armorAutoCrafterRecipes);
        providers.put("sf:auto_enchanter", autoEnchanterRecipes);
        providers.put("sf:auto_disenchanter", autoDisenchanterRecipes);
        providers.put("sf:book_binder", bookBinderRecipes);
        providers.put("sf:auto_anvil", autoAnvilRecipes);
        providers.put("sf:auto_anvil_2", autoAnvil2Recipes);
        providers.put("sf:produce_collector", produceCollectorRecipes);
        providers.put("sf:auto_breeder", autoBreederRecipes);
        providers.put("sf:animal_growth_accelerator", animalGrowthRecipes);
        providers.put("sf:crop_growth_accelerator", cropGrowthRecipes);
        providers.put("sf:crop_growth_accelerator_2", cropGrowth2Recipes);
        providers.put("sf:tree_growth_accelerator", treeGrowthRecipes);
        providers.put("sf:xp_collector", expCollectorRecipes);
        providers.put("sf:gps_transmitter", gpsTransmitterRecipes);
        providers.put("sf:geo_miner", geoMinerRecipes);
        providers.put("sf:oil_pump", oilPumpRecipes);
        providers.put("sf:fluid_pump", fluidPumpRecipes);
        providers.put("sf:iron_golem_assembler", ironGolemAssemblerRecipes);
        providers.put("sf:wither_assembler", witherAssemblerRecipes);
        if (plugin instanceof SlimeFunXPlugin sfx && sfx.api() != null) {
            for (cc.theends6.sfx.api.behavior.SfxElectricMachineProviderRegistration registration : sfx.api().behaviors().electricMachineProviders()) {
                cc.theends6.sfx.api.behavior.SfxElectricMachineProvider hook = registration.factory().create(
                        new cc.theends6.sfx.api.behavior.SfxElectricMachineProviderContext(
                                plugin, items, (location, machineId, horizontalRadius, verticalRadius) ->
                                hasOverlappingMachine(blockData, location, machineId, horizontalRadius, verticalRadius)));
                if (hook instanceof SfxElectricRecipeProvider provider) {
                    providers.put(registration.key(), provider);
                } else if (hook != null) {
                    throw new IllegalStateException("Invalid electric provider hook for " + registration.key());
                }
            }
        }

        for (String compiledEntryId : config.entryIds()) {
            if (compiledEntryId.contains("#")) {
                continue;
            }
            if (!autoBrewerProviderOverridden && "sf:auto_brewer".equals(compiledEntryId)) {
                continue;
            }
            register(result, config, compiledEntryId, staticRecipes, providers);
        }
        if (!autoBrewerProviderOverridden) {
            register(result, config, "sf:auto_brewer", "sf:auto_brewer#legacy", staticRecipes, providers);
        }
        return result;
    }

    private static SfxElectricRecipeProvider assemblerProvider(String key, EntityType entityType, SfxElectricAssemblerSpec spec) {
        return SfxAreaElectricMachineProviders.assembler(
                key,
                entityType,
                spec.headMaterial(),
                spec.headAmount(),
                spec.bodyMaterials(),
                spec.bodyAmount(),
                30 * 20);
    }

    private static String resolveElectricProviderKey(JavaPlugin plugin, String machineId, String compiledEntryId, String providerKey) {
        String current = providerKey;
        if (plugin instanceof SlimeFunXPlugin sfx && sfx.api() != null) {
            SfxElectricMachineProviderKeyContext context = new SfxElectricMachineProviderKeyContext(machineId, compiledEntryId, providerKey);
            for (SfxElectricMachineProviderKeyPolicy policy : sfx.api().behaviors().electricMachineProviderKeyPolicies()) {
                String resolved = policy.resolve(context, current);
                if (resolved != null && !resolved.isBlank()) {
                    current = resolved.trim();
                }
            }
        }
        return current;
    }

    private static boolean hasOverlappingMachine(
            SfxBlockDataService blockData,
            org.bukkit.Location location,
            String machineId,
            int horizontalRadius,
            int verticalRadius
    ) {
        if (blockData == null || location == null || location.getWorld() == null || machineId == null || machineId.isBlank()) {
            return false;
        }
        java.util.UUID currentId = blockData.findAnchor(location).map(anchor -> anchor.instanceId()).orElse(null);
        cc.theends6.sfx.api.block.SfxBlockAnchorKey current =
                cc.theends6.sfx.api.block.SfxBlockAnchorKey.fromLocation(location);
        for (var anchor : blockData.anchorsNear(current, horizontalRadius, horizontalRadius)) {
            cc.theends6.sfx.api.block.SfxBlockInstanceRecord instance =
                    blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || java.util.Objects.equals(instance.instanceId(), currentId)) {
                continue;
            }
            boolean matchingType = instance.typeId().equals(machineId)
                    || ("sf:crop_growth_accelerator".equals(machineId)
                    && "sf:crop_growth_accelerator_2".equals(instance.typeId()));
            if (!matchingType) {
                continue;
            }
            cc.theends6.sfx.api.block.SfxBlockAnchorKey other = instance.anchorKey();
            if (!other.worldId().equals(current.worldId())) {
                continue;
            }
            if (Math.abs(other.x() - current.x()) <= Math.max(0, horizontalRadius)
                    && Math.abs(other.z() - current.z()) <= Math.max(0, horizontalRadius)
                    && Math.abs(other.y() - current.y()) <= Math.max(0, verticalRadius)) {
                return true;
            }
        }
        return false;
    }

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, SfxElectricRecipeYamlLoader staticRecipes, Map<String, SfxElectricRecipeProvider> providers) {
        registry.register(config.create(id, id, staticRecipes, providers));
    }

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, String compiledEntryId, SfxElectricRecipeYamlLoader staticRecipes, Map<String, SfxElectricRecipeProvider> providers) {
        registry.register(config.create(id, compiledEntryId, staticRecipes, providers));
    }
}
