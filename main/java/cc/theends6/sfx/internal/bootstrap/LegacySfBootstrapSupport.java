package cc.theends6.sfx.internal.bootstrap;

import cc.theends6.sfx.api.item.SfxItemCategory;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.Text;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

final class LegacySfBootstrapSupport {
    private static final Map<String, Map<String, Integer>> LEGACY_ENCHANTMENTS = createLegacyEnchantments();

    private LegacySfBootstrapSupport() {
    }

    static void registerCategory(SfxItemRegistry registry, String id, String legacyName, Material material, String iconName, String textureHash, Integer colorRgb, int order) {
        registry.registerCategory(new SfxItemCategory(id, Text.legacy(legacyName), icon(material, Text.legacy(iconName), textureHash, colorRgb), order, false));
    }

    static void registerEnderTalismanVariant(SfxItemRegistry registry, String baseId, String baseName) {
        String strippedName = baseName.replace("&a", "").replace("&6", "").replace("&5", "").replace("&7", "").replace("&f", "");
        registerLegacyItem(registry, "sf:ender_" + baseId, "sf:talisman", Material.EMERALD, "&5Ender " + strippedName, null, null, new String[] {
                "&7&oEnder Infused",
                "",
                "&7Works from your Ender Chest",
                "&7as the Tier II variant of",
                baseName
        });
    }

    static void registerLegacyItem(SfxItemRegistry registry, String id, String categoryId, Material material, String legacyName, String textureHash, Integer colorRgb, String[] legacyLore) {
        registerLegacyItem(registry, id, categoryId, material, legacyName, textureHash, colorRgb, legacyLore, false, true);
    }

    static void registerLegacyItem(SfxItemRegistry registry, String id, String categoryId, Material material, String legacyName, String textureHash, Integer colorRgb, String[] legacyLore, boolean hidden, boolean giveable) {
        SfxItemDefinition.Builder builder = SfxItemDefinition.builder(id, material, Text.legacy(legacyName))
                .category(categoryId)
                .flag("legacy-sf");
        builder.hidden(hidden).giveable(giveable);
        if (textureHash != null) {
            builder.headTexture(textureHash);
        }
        if (colorRgb != null) {
            builder.colorRgb(colorRgb);
        }
        if (legacyLore != null) {
            for (String line : legacyLore) {
                builder.addLore(Text.legacy(line));
            }
        }
        applyLegacyVisualMetadata(id, builder);
        registry.registerItem(builder.build());
    }

    private static void applyLegacyVisualMetadata(String id, SfxItemDefinition.Builder builder) {
        applyLegacyFunctionFlags(id, builder);
        applyLegacyEnchantments(id, builder);
        if (!needsLegacyGlint(id) || LEGACY_ENCHANTMENTS.containsKey(id)) {
            return;
        }
        builder.flag("visual-glint");
    }

    private static void applyLegacyEnchantments(String id, SfxItemDefinition.Builder builder) {
        Map<String, Integer> enchantments = LEGACY_ENCHANTMENTS.get(id);
        if (enchantments == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            builder.enchantment(entry.getKey(), entry.getValue());
        }
    }

    private static void applyLegacyFunctionFlags(String id, SfxItemDefinition.Builder builder) {
        if (id == null) {
            return;
        }
        if (isTalismanId(id)) {
            builder.flag("talisman");
            builder.flag("talisman-" + talismanType(id));
            if (isEnderTalismanId(id)) {
                builder.flag("ender-talisman");
            }
        }
        if (id.equals("sf:glowstone_helmet")
                || id.equals("sf:glowstone_chestplate")
                || id.equals("sf:glowstone_leggings")
                || id.equals("sf:glowstone_boots")
                || id.equals("sf:night_vision_goggles")) {
            builder.flag("armor-night-vision");
        }
        if (id.equals("sf:slime_leggings") || id.equals("sf:slime_steel_leggings")) {
            builder.flag("armor-speed");
        }
        if (id.equals("sf:slime_boots") || id.equals("sf:slime_steel_boots") || id.equals("sf:bee_boots")) {
            builder.flag("armor-jump");
        }
        if (id.equals("sf:slime_boots") || id.equals("sf:slime_steel_boots") || id.equals("sf:boots_of_the_stomper") || id.equals("sf:bee_boots") || id.endsWith("_jetboots")) {
            builder.flag("armor-no-fall");
        }
        if (id.equals("sf:farmer_shoes")) {
            builder.flag("armor-farmland-safe");
        }
        if (id.equals("sf:scuba_helmet")) {
            builder.flag("armor-water-breathing");
        }
        if (id.equals("sf:hazmat_chestplate")) {
            builder.flag("armor-fire-resistance");
        }
        if (isPluginBlockId(id)) {
            builder.flag("plugin-block");
            builder.flag("placeable-block");
            builder.flag("creative-clone");
        }
        if (id.equals("sf:boots_of_the_stomper")) {
            builder.flag("armor-stomper");
        }
        if (id.equals("sf:bee_wings")) {
            builder.flag("armor-bee-wings");
        }
        if (id.equals("sf:elytra_cap")) {
            builder.flag("armor-elytra-impact");
        }
        if (id.equals("sf:ender_boots")) {
            builder.flag("armor-ender-pearl-safe");
        }
        if (id.startsWith("sf:hazmat_") || id.equals("sf:scuba_helmet")) {
            builder.flag("armor-hazmat");
        }
        if (id.startsWith("sf:ender_") && !isEnderTalismanId(id)) {
            builder.flag("armor-ender");
        }
    }

    private static boolean isTalismanId(String id) {
        return id.equals("sf:common_talisman") || id.equals("sf:ender_talisman") || id.endsWith("_talisman");
    }

    private static boolean isEnderTalismanId(String id) {
        return id.equals("sf:ender_talisman") || (id.startsWith("sf:ender_") && id.endsWith("_talisman"));
    }

    private static String talismanType(String id) {
        String normalized = id.substring("sf:".length());
        if (normalized.equals("common_talisman") || normalized.equals("ender_talisman")) {
            return "common";
        }
        if (normalized.startsWith("ender_")) {
            normalized = normalized.substring("ender_".length());
        }
        return normalized.substring(0, normalized.length() - "_talisman".length());
    }

    private static boolean needsLegacyGlint(String id) {
        if (id == null) {
            return false;
        }
        return isTalismanId(id)
                || id.startsWith("sf:staff_")
                || id.contains("soulbound")
                || id.equals("sf:magic_eye_of_ender")
                || id.equals("sf:infused_magnet")
                || id.equals("sf:infused_hopper")
                || id.equals("sf:necrotic_skull")
                || id.equals("sf:infused_elytra");
    }

    private static boolean isPluginBlockId(String id) {
        return id.equals("sf:composter")
                || id.equals("sf:crucible")
                || id.equals("sf:output_chest")
                || id.equals("sf:ignition_chamber")
                || id.equals("sf:block_placer")
                || id.equals("sf:enhanced_furnace")
                || id.startsWith("sf:enhanced_furnace_")
                || id.equals("sf:reinforced_furnace")
                || id.equals("sf:carbonado_edged_furnace")
                || isGpsPlaceableBlockId(id)
                || isDecorativeBlockId(id);
    }

    private static boolean isGpsPlaceableBlockId(String id) {
        return id.equals("sf:gps_transmitter")
                || id.equals("sf:gps_transmitter_2")
                || id.equals("sf:gps_transmitter_3")
                || id.equals("sf:gps_transmitter_4")
                || id.equals("sf:gps_control_panel")
                || id.equals("sf:gps_geo_scanner")
                || id.equals("sf:geo_miner")
                || id.equals("sf:oil_pump")
                || id.equals("sf:gps_teleporter_pylon")
                || id.equals("sf:gps_teleportation_matrix")
                || id.equals("sf:gps_activation_device_shared")
                || id.equals("sf:gps_activation_device_personal")
                || id.equals("sf:elevator_plate");
    }

    private static boolean isDecorativeBlockId(String id) {
        return id.equals("sf:hardened_glass")
                || id.equals("sf:wither_proof_obsidian")
                || id.equals("sf:wither_proof_glass")
                || id.startsWith("sf:rainbow_wool")
                || id.startsWith("sf:rainbow_glass")
                || id.startsWith("sf:rainbow_clay")
                || id.startsWith("sf:rainbow_concrete")
                || id.startsWith("sf:rainbow_glazed_terracotta");
    }

    private static Map<String, Map<String, Integer>> createLegacyEnchantments() {
        Map<String, Map<String, Integer>> map = new LinkedHashMap<>();
        map.put("sf:grandmas_walking_stick", enchantments(entry("knockback", 2)));
        map.put("sf:grandpas_walking_stick", enchantments(entry("knockback", 5)));
        map.put("sf:blade_of_vampires", enchantments(
                entry("fire_aspect", 2),
                entry("unbreaking", 4),
                entry("sharpness", 2)
        ));
        map.put("sf:cobalt_pickaxe", enchantments(
                entry("unbreaking", 10),
                entry("efficiency", 6)
        ));
        map.put("sf:cactus_helmet", enchantments(entry("thorns", 3), entry("unbreaking", 6)));
        map.put("sf:cactus_chestplate", enchantments(entry("thorns", 3), entry("unbreaking", 6)));
        map.put("sf:cactus_leggings", enchantments(entry("thorns", 3), entry("unbreaking", 6)));
        map.put("sf:cactus_boots", enchantments(entry("thorns", 3), entry("unbreaking", 6)));
        map.put("sf:damascus_steel_helmet", enchantments(entry("unbreaking", 5), entry("protection", 5)));
        map.put("sf:damascus_steel_chestplate", enchantments(entry("unbreaking", 5), entry("protection", 5)));
        map.put("sf:damascus_steel_leggings", enchantments(entry("unbreaking", 5), entry("protection", 5)));
        map.put("sf:damascus_steel_boots", enchantments(entry("unbreaking", 5), entry("protection", 5)));
        map.put("sf:reinforced_alloy_helmet", enchantments(entry("unbreaking", 9), entry("protection", 9)));
        map.put("sf:reinforced_alloy_chestplate", enchantments(entry("unbreaking", 9), entry("protection", 9)));
        map.put("sf:reinforced_alloy_leggings", enchantments(entry("unbreaking", 9), entry("protection", 9)));
        map.put("sf:reinforced_alloy_boots", enchantments(entry("unbreaking", 9), entry("protection", 9)));
        map.put("sf:gilded_iron_helmet", enchantments(entry("unbreaking", 6), entry("protection", 8)));
        map.put("sf:gilded_iron_chestplate", enchantments(entry("unbreaking", 6), entry("protection", 8)));
        map.put("sf:gilded_iron_leggings", enchantments(entry("unbreaking", 6), entry("protection", 8)));
        map.put("sf:gilded_iron_boots", enchantments(entry("unbreaking", 6), entry("protection", 8)));
        map.put("sf:gold_12k_helmet", enchantments(entry("unbreaking", 10)));
        map.put("sf:gold_12k_chestplate", enchantments(entry("unbreaking", 10)));
        map.put("sf:gold_12k_leggings", enchantments(entry("unbreaking", 10)));
        map.put("sf:gold_12k_boots", enchantments(entry("unbreaking", 10)));
        map.put("sf:slime_steel_helmet", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:slime_steel_chestplate", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:slime_steel_leggings", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:slime_steel_boots", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:bee_helmet", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:bee_wings", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:bee_leggings", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:bee_boots", enchantments(entry("unbreaking", 4), entry("protection", 2)));
        map.put("sf:staff_elemental_wind", enchantments(entry("luck", 1)));
        map.put("sf:staff_elemental_fire", enchantments(entry("fire_aspect", 5)));
        map.put("sf:staff_elemental_water", enchantments(entry("aqua_affinity", 1)));
        map.put("sf:staff_elemental_storm", enchantments(entry("unbreaking", 1)));
        map.put("sf:infused_elytra", enchantments(entry("mending", 1)));
        return Map.copyOf(map);
    }

    @SafeVarargs
    private static Map<String, Integer> enchantments(Map.Entry<String, Integer>... entries) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            values.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(values);
    }

    private static Map.Entry<String, Integer> entry(String enchantment, int level) {
        return Map.entry(enchantment, level);
    }

    static ItemStack icon(Material material, Component name, String textureHash, Integer colorRgb) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (material == Material.PLAYER_HEAD && textureHash != null) {
                HeadTextures.apply(meta, textureHash);
            }
            if (colorRgb != null) {
                applyColor(meta, colorRgb);
            }
            meta.displayName(Text.noItalic(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void applyColor(ItemMeta meta, int rgb) {
        Color color = Color.fromRGB(rgb);
        if (meta instanceof LeatherArmorMeta leatherArmorMeta) {
            leatherArmorMeta.setColor(color);
        } else if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setColor(color);
        } else if (meta instanceof FireworkEffectMeta fireworkEffectMeta) {
            fireworkEffectMeta.setEffect(FireworkEffect.builder().withColor(color).build());
        }
    }
}
