package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.SlimeFunXPlugin;
import cc.theends6.sfx.api.behavior.SfxElectricSpecialProviderKeyContext;
import cc.theends6.sfx.api.behavior.SfxElectricSpecialProviderKeyPolicy;
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
        SfxElectricRecipeProvider electricSmelteryRecipes = new SfxElectricSmelteryRecipeProvider(manualMachines.recipesFor("sf:smeltery"), 6);
        SfxElectricRecipeProvider autoEnchanterRecipes = SfxSpecialElectricRecipeProviders.autoEnchanter(plugin, items);
        SfxElectricRecipeProvider autoDisenchanterRecipes = SfxSpecialElectricRecipeProviders.autoDisenchanter(plugin, items);
        SfxElectricRecipeProvider bookBinderRecipes = SfxSpecialElectricRecipeProviders.bookBinder(plugin);
        SfxElectricRecipeProvider autoAnvilRecipes = SfxSpecialElectricRecipeProviders.autoAnvil(plugin, items, 10);
        SfxElectricRecipeProvider autoAnvil2Recipes = SfxSpecialElectricRecipeProviders.autoAnvil(plugin, items, 25);
        SfxElectricRecipeProvider produceCollectorRecipes = SfxAreaElectricMachineProviders.produceCollector(false);
        SfxElectricRecipeProvider autoBreederRecipes = SfxAreaElectricMachineProviders.autoBreeder(false);
        SfxElectricRecipeProvider animalGrowthRecipes = SfxAreaElectricMachineProviders.animalGrowthAccelerator(2000);
        SfxElectricRecipeProvider cropGrowthRecipes = SfxAreaElectricMachineProviders.cropGrowthAccelerator(blockData, 3, 20, false);
        SfxElectricRecipeProvider cropGrowth2Recipes = SfxAreaElectricMachineProviders.cropGrowthAccelerator(blockData, 4, 30, false);
        SfxElectricRecipeProvider treeGrowthRecipes = SfxAreaElectricMachineProviders.treeGrowthAccelerator(false);
        SfxAreaMachineRules classicAreaRules = SfxAreaMachineRules.classicDefaults();
        SfxElectricRecipeProvider expCollectorRecipes = SfxAreaElectricMachineProviders.expCollector(false, classicAreaRules.xpFlaskEnergyCost());
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
        boolean sfxAutoBrewer = "sfx:advanced_auto_brewer".equals(autoBrewerProviderKey);
        SfxElectricRecipeProvider vanillaAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.vanilla(virtualContainers, items);
        SfxElectricRecipeProvider enhancedAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.enhanced(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:enhanced_crafting_table")));
        SfxElectricRecipeProvider armorAutoCrafterRecipes = SfxAutoCrafterRecipeProvider.armor(virtualContainers, items, List.copyOf(manualMachines.recipesFor("sf:armor_forge")));
        Map<String, SfxElectricRecipeProvider> specialProviders = new LinkedHashMap<>();
        specialProviders.put("sf:vanilla_furnace", furnaceRecipes);
        specialProviders.put("sf:classic_ore_grinder", grinderRecipes);
        specialProviders.put("sf:electric_smeltery", electricSmelteryRecipes);
        specialProviders.put("sf:auto_brewer", classicAutoBrewerRecipes);
        specialProviders.put("sf:auto_brewer_legacy", classicAutoBrewerRecipes);
        specialProviders.put("sf:vanilla_auto_crafter", vanillaAutoCrafterRecipes);
        specialProviders.put("sf:enhanced_auto_crafter", enhancedAutoCrafterRecipes);
        specialProviders.put("sf:armor_auto_crafter", armorAutoCrafterRecipes);
        specialProviders.put("sf:auto_enchanter", autoEnchanterRecipes);
        specialProviders.put("sf:auto_disenchanter", autoDisenchanterRecipes);
        specialProviders.put("sf:book_binder", bookBinderRecipes);
        specialProviders.put("sf:auto_anvil", autoAnvilRecipes);
        specialProviders.put("sf:auto_anvil_2", autoAnvil2Recipes);
        specialProviders.put("sf:produce_collector", produceCollectorRecipes);
        specialProviders.put("sf:auto_breeder", autoBreederRecipes);
        specialProviders.put("sf:animal_growth_accelerator", animalGrowthRecipes);
        specialProviders.put("sf:crop_growth_accelerator", cropGrowthRecipes);
        specialProviders.put("sf:crop_growth_accelerator_2", cropGrowth2Recipes);
        specialProviders.put("sf:tree_growth_accelerator", treeGrowthRecipes);
        specialProviders.put("sf:xp_collector", expCollectorRecipes);
        specialProviders.put("sf:gps_transmitter", gpsTransmitterRecipes);
        specialProviders.put("sf:geo_miner", geoMinerRecipes);
        specialProviders.put("sf:oil_pump", oilPumpRecipes);
        specialProviders.put("sf:fluid_pump", fluidPumpRecipes);
        specialProviders.put("sf:iron_golem_assembler", ironGolemAssemblerRecipes);
        specialProviders.put("sf:wither_assembler", witherAssemblerRecipes);
        if (plugin instanceof SlimeFunXPlugin sfx && sfx.api() != null) {
            for (cc.theends6.sfx.api.behavior.SfxElectricSpecialProviderRegistration registration : sfx.api().behaviors().electricSpecialProviders()) {
                SfxElectricRecipeProvider provider = registration.factory().create(plugin, items, blockData);
                if (provider != null) {
                    specialProviders.put(registration.key(), provider);
                }
            }
        }

        for (String compiledEntryId : config.entryIds()) {
            if (compiledEntryId.contains("#")) {
                continue;
            }
            if (!sfxAutoBrewer && "sf:auto_brewer".equals(compiledEntryId)) {
                continue;
            }
            register(result, config, compiledEntryId, staticRecipes, specialProviders);
        }
        if (!sfxAutoBrewer) {
            register(result, config, "sf:auto_brewer", "sf:auto_brewer#legacy", staticRecipes, specialProviders);
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
            SfxElectricSpecialProviderKeyContext context = new SfxElectricSpecialProviderKeyContext(machineId, compiledEntryId, providerKey);
            for (SfxElectricSpecialProviderKeyPolicy policy : sfx.api().behaviors().electricSpecialProviderKeyPolicies()) {
                String resolved = policy.resolve(context, current);
                if (resolved != null && !resolved.isBlank()) {
                    current = resolved.trim();
                }
            }
        }
        return current;
    }

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, SfxElectricRecipeYamlLoader staticRecipes, Map<String, SfxElectricRecipeProvider> specialProviders) {
        registry.register(config.create(id, id, staticRecipes, specialProviders));
    }

    private static void register(SfxElectricMachineRegistry registry, SfxElectricMachineDefinitionConfig config, String id, String compiledEntryId, SfxElectricRecipeYamlLoader staticRecipes, Map<String, SfxElectricRecipeProvider> specialProviders) {
        registry.register(config.create(id, compiledEntryId, staticRecipes, specialProviders));
    }
}
