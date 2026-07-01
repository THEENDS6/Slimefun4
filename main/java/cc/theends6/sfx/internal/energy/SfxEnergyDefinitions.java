package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.electric.SfxElectricStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxEnergyDefinitions {
    private SfxEnergyDefinitions() {
    }

    static Map<String, SfxEnergyComponentDefinition> create(JavaPlugin plugin) {
        Map<String, SfxEnergyComponentDefinition> yamlDefinitions = new SfxEnergyComponentYamlLoader(plugin).load();
        if (!yamlDefinitions.isEmpty()) {
            return yamlDefinitions;
        }
        Map<String, SfxEnergyComponentDefinition> definitions = new LinkedHashMap<>();
        boolean useSfxBalance = plugin.getConfig().getBoolean("energy.generator-balance.use-sfx-balance", true);
        int tier2BurnRate = useSfxBalance ? 15 : 10;
        int lavaSecondsMultiplier = useSfxBalance ? 2 : 1;
        int bioSecondsMultiplier = useSfxBalance ? 4 : 1;
        int combustionOilSeconds = useSfxBalance ? 40 : 30;
        int combustionFuelSeconds = useSfxBalance ? 120 : 90;
        int combustionCapacity = useSfxBalance ? 20480 : 5120;
        int combustionEnergy = useSfxBalance ? 64 : 24;

        define(definitions, new SfxEnergyComponentDefinition("sf:energy_regulator", SfxEnergyComponentType.REGULATOR, 0, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:energy_connector", SfxEnergyComponentType.CONNECTOR, 0, 0, 0, 10, false, Material.REDSTONE, List.of()));
        boolean useTechnicalSfxBalance = plugin.getConfig().getBoolean("technical-gadgets.sfx-balance.enabled", true);
        int chargingBenchEnergy = useTechnicalSfxBalance
                ? plugin.getConfig().getInt("technical-gadgets.sfx-balance.charging-bench.energy-consumption-per-tick", 100)
                : plugin.getConfig().getInt("technical-gadgets.classic.charging-bench.energy-consumption-per-tick", 20);
        define(definitions, new SfxEnergyComponentDefinition("sf:charging_bench", SfxEnergyComponentType.CHARGER, 2560, chargingBenchEnergy, 0, 10, false, Material.GOLDEN_PICKAXE, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:small_capacitor", SfxEnergyComponentType.CAPACITOR, 2560, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:medium_capacitor", SfxEnergyComponentType.CAPACITOR, 10240, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:big_capacitor", SfxEnergyComponentType.CAPACITOR, 20480, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:large_capacitor", SfxEnergyComponentType.CAPACITOR, 163840, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:carbonado_edged_capacitor", SfxEnergyComponentType.CAPACITOR, 1310720, 0, 0, 10, false, Material.REDSTONE, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:energized_capacitor", SfxEnergyComponentType.CAPACITOR, 10485760, 0, 0, 10, false, Material.REDSTONE, List.of()));

        define(definitions, new SfxEnergyComponentDefinition("sf:solar_generator", SfxEnergyComponentType.GENERATOR, 0, 4, 0, 10, false, Material.DAYLIGHT_DETECTOR, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:solar_generator_2", SfxEnergyComponentType.GENERATOR, 0, 16, 0, 10, false, Material.DAYLIGHT_DETECTOR, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:solar_generator_3", SfxEnergyComponentType.GENERATOR, 0, 64, 0, 10, false, Material.DAYLIGHT_DETECTOR, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:solar_generator_4", SfxEnergyComponentType.GENERATOR, 0, 256, 128, 10, false, Material.DAYLIGHT_DETECTOR, List.of()));

        define(definitions, new SfxEnergyComponentDefinition("sf:coal_generator", SfxEnergyComponentType.GENERATOR, 1280, 16, 0, 10, true, Material.FLINT_AND_STEEL, List.of()));
        define(definitions, new SfxEnergyComponentDefinition("sf:coal_generator_2", SfxEnergyComponentType.GENERATOR, 5120, 30, 0, tier2BurnRate, true, Material.FLINT_AND_STEEL, List.of()));

        define(definitions, new SfxEnergyComponentDefinition(
                "sf:lava_generator",
                SfxEnergyComponentType.GENERATOR,
                10240,
                20,
                0,
                10,
                false,
                Material.FLINT_AND_STEEL,
                List.of(new SfxEnergyComponentDefinition.FuelRule("lava", SfxElectricStack.vanilla(Material.LAVA_BUCKET, 1), SfxElectricStack.vanilla(Material.BUCKET, 1), 40 * lavaSecondsMultiplier))));
        define(definitions, new SfxEnergyComponentDefinition(
                "sf:lava_generator_2",
                SfxEnergyComponentType.GENERATOR,
                20480,
                40,
                0,
                tier2BurnRate,
                false,
                Material.FLINT_AND_STEEL,
                List.of(new SfxEnergyComponentDefinition.FuelRule("lava", SfxElectricStack.vanilla(Material.LAVA_BUCKET, 1), SfxElectricStack.vanilla(Material.BUCKET, 1), 40 * lavaSecondsMultiplier))));

        define(definitions, new SfxEnergyComponentDefinition(
                "sf:bio_reactor",
                SfxEnergyComponentType.GENERATOR,
                2560,
                8,
                0,
                10,
                false,
                Material.GOLDEN_HOE,
                bioFuelRules(bioSecondsMultiplier)));

        if (useSfxBalance) {
            define(definitions, new SfxEnergyComponentDefinition(
                    "sf:bio_reactor_2",
                    SfxEnergyComponentType.GENERATOR,
                    5120,
                    20,
                    0,
                    tier2BurnRate,
                    false,
                    Material.GOLDEN_HOE,
                    bioFuelRules(bioSecondsMultiplier)));
        }

        define(definitions, new SfxEnergyComponentDefinition(
                "sf:combustion_reactor",
                SfxEnergyComponentType.GENERATOR,
                combustionCapacity,
                combustionEnergy,
                0,
                10,
                false,
                Material.FLINT_AND_STEEL,
                List.of(
                        new SfxEnergyComponentDefinition.FuelRule("oil", SfxElectricStack.sfx("sf:bucket_of_oil", 1), SfxElectricStack.vanilla(Material.BUCKET, 1), combustionOilSeconds),
                        new SfxEnergyComponentDefinition.FuelRule("fuel", SfxElectricStack.sfx("sf:bucket_of_fuel", 1), SfxElectricStack.vanilla(Material.BUCKET, 1), combustionFuelSeconds))));

        define(definitions, new SfxEnergyComponentDefinition(
                "sf:magnesium_generator",
                SfxEnergyComponentType.GENERATOR,
                2560,
                36,
                0,
                10,
                false,
                Material.FLINT_AND_STEEL,
                List.of(new SfxEnergyComponentDefinition.FuelRule("magnesium", SfxElectricStack.sfx("sf:magnesium_salt", 1), null, 20))));
        return definitions;
    }

    private static void define(Map<String, SfxEnergyComponentDefinition> definitions, SfxEnergyComponentDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    private static List<SfxEnergyComponentDefinition.FuelRule> bioFuelRules(int secondsMultiplier) {
        List<SfxEnergyComponentDefinition.FuelRule> fuels = new ArrayList<>();
        fuel(fuels, "rotten_flesh", Material.ROTTEN_FLESH, 2 * secondsMultiplier);
        fuel(fuels, "spider_eye", Material.SPIDER_EYE, 2 * secondsMultiplier);
        fuel(fuels, "bone", Material.BONE, 2 * secondsMultiplier);
        fuel(fuels, "string", Material.STRING, 2 * secondsMultiplier);
        fuel(fuels, "apple", Material.APPLE, 3 * secondsMultiplier);
        fuel(fuels, "melon_slice", Material.MELON_SLICE, 3 * secondsMultiplier);
        fuel(fuels, "melon", Material.MELON, 27 * secondsMultiplier);
        fuel(fuels, "pumpkin", Material.PUMPKIN, 3 * secondsMultiplier);
        fuel(fuels, "pumpkin_seeds", Material.PUMPKIN_SEEDS, 3 * secondsMultiplier);
        fuel(fuels, "melon_seeds", Material.MELON_SEEDS, 3 * secondsMultiplier);
        fuel(fuels, "wheat", Material.WHEAT, 3 * secondsMultiplier);
        fuel(fuels, "wheat_seeds", Material.WHEAT_SEEDS, 3 * secondsMultiplier);
        fuel(fuels, "carrot", Material.CARROT, 3 * secondsMultiplier);
        fuel(fuels, "potato", Material.POTATO, 3 * secondsMultiplier);
        fuel(fuels, "sugar_cane", Material.SUGAR_CANE, 3 * secondsMultiplier);
        fuel(fuels, "nether_wart", Material.NETHER_WART, 3 * secondsMultiplier);
        fuel(fuels, "red_mushroom", Material.RED_MUSHROOM, 2 * secondsMultiplier);
        fuel(fuels, "brown_mushroom", Material.BROWN_MUSHROOM, 2 * secondsMultiplier);
        fuel(fuels, "kelp", Material.KELP, 3 * secondsMultiplier);
        fuel(fuels, "dried_kelp", Material.DRIED_KELP, 1 * secondsMultiplier);
        fuel(fuels, "cactus", Material.CACTUS, 3 * secondsMultiplier);
        fuel(fuels, "cocoa_beans", Material.COCOA_BEANS, 3 * secondsMultiplier);
        fuel(fuels, "sweet_berries", Material.SWEET_BERRIES, 2 * secondsMultiplier);
        fuel(fuels, "bamboo", Material.BAMBOO, 1 * secondsMultiplier);
        fuel(fuels, "beetroot", Material.BEETROOT, 3 * secondsMultiplier);
        fuel(fuels, "beetroot_seeds", Material.BEETROOT_SEEDS, 3 * secondsMultiplier);
        fuel(fuels, "honeycomb", Material.HONEYCOMB, 4 * secondsMultiplier);
        fuel(fuels, "honeycomb_block", Material.HONEYCOMB_BLOCK, 40 * secondsMultiplier);
        fuel(fuels, "shroomlight", Material.SHROOMLIGHT, 4 * secondsMultiplier);
        fuel(fuels, "crimson_fungus", Material.CRIMSON_FUNGUS, 2 * secondsMultiplier);
        fuel(fuels, "warped_fungus", Material.WARPED_FUNGUS, 2 * secondsMultiplier);
        fuels.add(new SfxEnergyComponentDefinition.FuelRule("strange_nether_goo", SfxElectricStack.sfx("sf:strange_nether_goo", 1), null, 16 * secondsMultiplier));
        optionalFuel(fuels, "glow_berries", Material.GLOW_BERRIES, 2 * secondsMultiplier);
        optionalFuel(fuels, "small_dripleaf", Material.SMALL_DRIPLEAF, 3 * secondsMultiplier);
        optionalFuel(fuels, "big_dripleaf", Material.BIG_DRIPLEAF, 3 * secondsMultiplier);
        optionalFuel(fuels, "glow_lichen", Material.GLOW_LICHEN, 2 * secondsMultiplier);
        optionalFuel(fuels, "spore_blossom", Material.SPORE_BLOSSOM, 20 * secondsMultiplier);
        tagFuels(fuels, "small_flower", Tag.SMALL_FLOWERS, 1 * secondsMultiplier);
        tagFuels(fuels, "leaf", Tag.LEAVES, 1 * secondsMultiplier);
        tagFuels(fuels, "sapling", Tag.SAPLINGS, 1 * secondsMultiplier);
        tagFuels(fuels, "coral", Tag.CORALS, 2 * secondsMultiplier);
        tagFuels(fuels, "coral_block", Tag.CORAL_BLOCKS, 2 * secondsMultiplier);
        return fuels;
    }

    private static void fuel(List<SfxEnergyComponentDefinition.FuelRule> fuels, String key, Material material, int seconds) {
        fuels.add(new SfxEnergyComponentDefinition.FuelRule(key, SfxElectricStack.vanilla(material, 1), null, seconds));
    }

    private static void optionalFuel(List<SfxEnergyComponentDefinition.FuelRule> fuels, String key, Material material, int seconds) {
        if (material != null) {
            fuel(fuels, key, material, seconds);
        }
    }

    private static void tagFuels(List<SfxEnergyComponentDefinition.FuelRule> fuels, String prefix, Tag<Material> tag, int seconds) {
        for (Material material : tag.getValues()) {
            fuels.add(new SfxEnergyComponentDefinition.FuelRule(prefix + ":" + material.key(), SfxElectricStack.vanilla(material, 1), null, seconds));
        }
    }
}
