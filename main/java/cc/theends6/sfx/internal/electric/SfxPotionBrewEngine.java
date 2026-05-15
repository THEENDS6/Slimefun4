package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.item.SfxItems;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

final class SfxPotionBrewEngine {
    static final String POWER_CRYSTAL = "sf:power_crystal";
    static final String MAGIC_SUGAR = "sf:magic_sugar";

    private static final int WATER_BREW_TICKS = 30 * 20;
    private static final int BASE_BREW_TICKS = 40 * 20;
    private static final int FIRST_MODIFIER_TICKS = 60 * 20;
    private static final int SECOND_MODIFIER_TICKS = 80 * 20;
    private static final int SPLASH_TICKS = 30 * 20;
    private static final int LINGERING_TICKS = 60 * 20;
    private static final int DEFAULT_POWER_DURATION_TICKS = 60 * 20;
    private static final int DEFAULT_MAGIC_DURATION_TICKS = 16 * 60 * 20;
    private static final int DEFAULT_COMBINED_DURATION_TICKS = 8 * 60 * 20;

    private static final Map<String, String> EFFECT_TYPES = Map.ofEntries(
            Map.entry("SWIFTNESS", "SPEED"),
            Map.entry("LEAPING", "JUMP"),
            Map.entry("STRENGTH", "INCREASE_DAMAGE"),
            Map.entry("HEALING", "HEAL"),
            Map.entry("POISON", "POISON"),
            Map.entry("REGENERATION", "REGENERATION"),
            Map.entry("FIRE_RESISTANCE", "FIRE_RESISTANCE"),
            Map.entry("WATER_BREATHING", "WATER_BREATHING"),
            Map.entry("NIGHT_VISION", "NIGHT_VISION"),
            Map.entry("INVISIBILITY", "INVISIBILITY"),
            Map.entry("SLOWNESS", "SLOW"),
            Map.entry("HARMING", "HARM"),
            Map.entry("TURTLE_MASTER", "SLOW"),
            Map.entry("SLOW_FALLING", "SLOW_FALLING"),
            Map.entry("WEAKNESS", "WEAKNESS"),
            Map.entry("WIND_CHARGED", "WIND_CHARGED"),
            Map.entry("WEAVING", "WEAVING"),
            Map.entry("OOZING", "OOZING"),
            Map.entry("INFESTED", "INFESTED")
    );

    private static final Map<String, PotionRule> RULES = Map.ofEntries(
            rule("SWIFTNESS", false, true, true, true, true, true, 1, minutes(3), 1, minutes(8), 2, seconds(90), 2, minutes(8), 3, DEFAULT_POWER_DURATION_TICKS, 1, DEFAULT_MAGIC_DURATION_TICKS),
            rule("LEAPING", false, true, true, true, true, true, 1, minutes(3), 1, minutes(8), 2, seconds(90), 2, minutes(8), 3, DEFAULT_POWER_DURATION_TICKS, 1, DEFAULT_MAGIC_DURATION_TICKS),
            rule("STRENGTH", false, true, true, true, true, true, 1, minutes(3), 1, minutes(8), 2, seconds(90), 2, minutes(8), 3, DEFAULT_POWER_DURATION_TICKS, 1, DEFAULT_MAGIC_DURATION_TICKS),
            rule("REGENERATION", false, true, true, true, true, true, 1, seconds(45), 1, minutes(3), 2, seconds(20), 2, seconds(45), 3, seconds(20), 1, minutes(3)),
            rule("POISON", false, true, true, true, true, true, 1, seconds(45), 1, minutes(3), 2, seconds(20), 2, seconds(45), 3, seconds(20), 1, minutes(3)),
            rule("HEALING", true, true, false, true, false, false, 1, 1, 1, 1, 2, 1, 2, 1, 3, 1, 1, 1),
            rule("HARMING", true, true, false, true, false, false, 1, 1, 1, 1, 2, 1, 2, 1, 3, 1, 1, 1),
            rule("FIRE_RESISTANCE", false, false, true, false, true, false, 1, minutes(3), 1, minutes(8), 1, minutes(3), 1, minutes(8), 1, DEFAULT_POWER_DURATION_TICKS, 1, DEFAULT_MAGIC_DURATION_TICKS),
            rule("WATER_BREATHING", false, false, true, false, true, false, 1, minutes(3), 1, minutes(8), 1, minutes(3), 1, minutes(8), 1, DEFAULT_POWER_DURATION_TICKS, 1, DEFAULT_MAGIC_DURATION_TICKS),
            rule("NIGHT_VISION", false, false, true, false, true, false, 1, minutes(3), 1, minutes(8), 1, minutes(3), 1, minutes(8), 1, DEFAULT_POWER_DURATION_TICKS, 1, DEFAULT_MAGIC_DURATION_TICKS),
            rule("INVISIBILITY", false, false, true, false, true, false, 1, minutes(3), 1, minutes(8), 1, minutes(3), 1, minutes(8), 1, DEFAULT_POWER_DURATION_TICKS, 1, DEFAULT_MAGIC_DURATION_TICKS),
            rule("SLOW_FALLING", false, false, true, false, true, false, 1, seconds(90), 1, minutes(4), 1, seconds(90), 1, minutes(4), 1, DEFAULT_POWER_DURATION_TICKS, 1, DEFAULT_MAGIC_DURATION_TICKS),
            rule("WEAKNESS", false, false, true, false, true, false, 1, seconds(90), 1, minutes(4), 1, seconds(90), 1, minutes(4), 1, DEFAULT_POWER_DURATION_TICKS, 1, DEFAULT_MAGIC_DURATION_TICKS),
            rule("SLOWNESS", false, true, true, false, false, false, 1, seconds(90), 1, minutes(4), 4, seconds(20), 4, seconds(20), 4, seconds(20), 1, minutes(4)),
            rule("TURTLE_MASTER", false, true, true, false, false, false, 1, seconds(20), 1, seconds(40), 2, seconds(20), 2, seconds(20), 2, seconds(20), 1, seconds(40)),
            rule("WIND_CHARGED", false, false, false, false, false, false, 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3)),
            rule("WEAVING", false, false, false, false, false, false, 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3)),
            rule("OOZING", false, false, false, false, false, false, 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3)),
            rule("INFESTED", false, false, false, false, false, false, 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3), 1, minutes(3))
    );

    private final NamespacedKey effectKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey durationKey;
    private final NamespacedKey stageKey;

    SfxPotionBrewEngine(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.effectKey = new NamespacedKey(plugin, "sfx_potion_effect");
        this.levelKey = new NamespacedKey(plugin, "sfx_potion_level");
        this.durationKey = new NamespacedKey(plugin, "sfx_potion_duration");
        this.stageKey = new NamespacedKey(plugin, "sfx_potion_stage");
    }

    BrewResult brew(SfxItems items, ItemStack potion, SfxElectricStack ingredient) {
        if (!isValidPotionItem(items, potion) || ingredient == null || !(potion.getItemMeta() instanceof PotionMeta meta)) {
            return null;
        }
        Material potionMaterial = potion.getType();
        if (ingredient.isSfxItem()) {
            if (POWER_CRYSTAL.equals(ingredient.itemId())) {
                return secondModifier(potionMaterial, meta, true);
            }
            if (MAGIC_SUGAR.equals(ingredient.itemId())) {
                return secondModifier(potionMaterial, meta, false);
            }
            return null;
        }
        if (ingredient.hasSnapshot()) {
            return null;
        }
        Material material = ingredient.material();
        if (material == Material.GUNPOWDER && potionMaterial == Material.POTION) {
            ItemStack result = potion.clone();
            result.setType(Material.SPLASH_POTION);
            return new BrewResult(result, workTicks(SPLASH_TICKS, isSpecialPotion(result)));
        }
        if (material == Material.DRAGON_BREATH && potionMaterial == Material.SPLASH_POTION) {
            ItemStack result = potion.clone();
            result.setType(Material.LINGERING_POTION);
            return new BrewResult(result, workTicks(LINGERING_TICKS, isSpecialPotion(result)));
        }

        PotionModel model = modelFrom(meta);
        PotionType inputType = baseType(meta);
        if (material == Material.REDSTONE) {
            if (model != null) {
                return firstModifierFromModel(potionMaterial, model, false);
            }
            if (inputType != null && inputType.name().startsWith("STRONG_")) {
                return combinedFromVanilla(potionMaterial, inputType);
            }
            PotionType longType = longType(inputType);
            return longType == null ? null : vanillaResult(potionMaterial, longType, FIRST_MODIFIER_TICKS);
        }
        if (material == Material.GLOWSTONE_DUST) {
            if (model != null) {
                return firstModifierFromModel(potionMaterial, model, true);
            }
            if (inputType != null && inputType.name().startsWith("LONG_")) {
                return combinedFromVanilla(potionMaterial, inputType);
            }
            PotionType strongType = strongType(inputType);
            return strongType == null ? null : vanillaResult(potionMaterial, strongType, FIRST_MODIFIER_TICKS);
        }
        if (material == Material.FERMENTED_SPIDER_EYE) {
            PotionType corrupt = corruptType(inputType);
            return corrupt == null ? null : vanillaResult(potionMaterial, corrupt, inputType == type("WATER") ? WATER_BREW_TICKS : BASE_BREW_TICKS);
        }
        if (inputType == type("WATER")) {
            if (material == Material.NETHER_WART) {
                return vanillaResult(potionMaterial, type("AWKWARD"), WATER_BREW_TICKS);
            }
            if (material == Material.GLOWSTONE_DUST) {
                return vanillaResult(potionMaterial, type("THICK"), WATER_BREW_TICKS);
            }
            if (material == Material.REDSTONE) {
                return vanillaResult(potionMaterial, type("MUNDANE"), WATER_BREW_TICKS);
            }
        }
        if (inputType == type("AWKWARD") || inputType == type("MUNDANE") || inputType == type("THICK")) {
            PotionType base = baseIngredientResult(material);
            return base == null ? null : vanillaResult(potionMaterial, base, BASE_BREW_TICKS);
        }
        return null;
    }

    boolean isBrewingIngredient(SfxItems items, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        SfxElectricStack electricStack = SfxElectricStack.fromItemStack(items, stack);
        return electricStack != null && isBrewingIngredient(electricStack);
    }

    boolean isBrewingIngredient(SfxElectricStack stack) {
        if (stack == null || stack.amount() <= 0) {
            return false;
        }
        if (stack.isSfxItem()) {
            return POWER_CRYSTAL.equals(stack.itemId()) || MAGIC_SUGAR.equals(stack.itemId());
        }
        if (stack.hasSnapshot()) {
            return false;
        }
        Material m = stack.material();
        return m == Material.NETHER_WART
                || m == Material.REDSTONE
                || m == Material.GLOWSTONE_DUST
                || m == Material.FERMENTED_SPIDER_EYE
                || m == Material.GUNPOWDER
                || m == Material.DRAGON_BREATH
                || m == Material.SUGAR
                || m == Material.RABBIT_FOOT
                || m == Material.BLAZE_POWDER
                || m == Material.GLISTERING_MELON_SLICE
                || m == Material.SPIDER_EYE
                || m == Material.GHAST_TEAR
                || m == Material.MAGMA_CREAM
                || m == Material.PUFFERFISH
                || m == Material.GOLDEN_CARROT
                || m == Material.TURTLE_HELMET
                || m == Material.PHANTOM_MEMBRANE
                || m == optionalMaterial("BREEZE_ROD")
                || m == optionalMaterial("COBWEB")
                || m == optionalMaterial("SLIME_BLOCK")
                || m == Material.STONE;
    }

    boolean isValidPotionItem(SfxItems items, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Material material = stack.getType();
        if (material != Material.POTION && material != Material.SPLASH_POTION && material != Material.LINGERING_POTION) {
            return false;
        }
        SfxElectricStack electricStack = SfxElectricStack.fromItemStack(items, stack);
        return electricStack != null && !electricStack.isSfxItem();
    }

    private BrewResult firstModifierFromModel(Material potionMaterial, PotionModel model, boolean strength) {
        PotionRule rule = rule(model.effectName());
        if (rule == null) {
            return null;
        }
        if (strength) {
            if (model.stage() == PotionStage.FIRST_LONG && rule.allowCombined()) {
                return customResult(potionMaterial, rule, rule.combinedLevel(), rule.combinedDurationTicks(), PotionStage.COMBINED, FIRST_MODIFIER_TICKS);
            }
            if (model.stage() == PotionStage.NORMAL && rule.allowStrength()) {
                return customResult(potionMaterial, rule, rule.strongLevel(), rule.strongDurationTicks(), PotionStage.FIRST_STRONG, FIRST_MODIFIER_TICKS);
            }
            return null;
        }
        if (model.stage() == PotionStage.FIRST_STRONG && rule.allowCombined()) {
            return customResult(potionMaterial, rule, rule.combinedLevel(), rule.combinedDurationTicks(), PotionStage.COMBINED, FIRST_MODIFIER_TICKS);
        }
        if (model.stage() == PotionStage.NORMAL && rule.allowExtension()) {
            return customResult(potionMaterial, rule, rule.longLevel(), rule.longDurationTicks(), PotionStage.FIRST_LONG, FIRST_MODIFIER_TICKS);
        }
        return null;
    }

    private BrewResult secondModifier(Material potionMaterial, PotionMeta meta, boolean strength) {
        PotionModel model = modelFrom(meta);
        PotionType type = baseType(meta);
        if (model == null) {
            String effect = effectName(type);
            if (effect == null) {
                return null;
            }
            model = modelFromVanilla(type, effect);
        }
        PotionRule rule = rule(model.effectName());
        if (rule == null) {
            return null;
        }
        if (strength) {
            if (model.stage() != PotionStage.FIRST_STRONG || !rule.allowSecondStrength()) {
                return null;
            }
            return customResult(potionMaterial, rule, rule.secondStrongLevel(), rule.secondStrongDurationTicks(), PotionStage.SECOND_STRONG, SECOND_MODIFIER_TICKS);
        }
        if (model.stage() != PotionStage.FIRST_LONG || !rule.allowSecondExtension()) {
            return null;
        }
        return customResult(potionMaterial, rule, rule.secondLongLevel(), rule.secondLongDurationTicks(), PotionStage.SECOND_LONG, SECOND_MODIFIER_TICKS);
    }

    private BrewResult combinedFromVanilla(Material potionMaterial, PotionType inputType) {
        String effect = effectName(inputType);
        PotionRule rule = rule(effect);
        if (rule == null || !rule.allowCombined()) {
            return null;
        }
        return customResult(potionMaterial, rule, rule.combinedLevel(), rule.combinedDurationTicks(), PotionStage.COMBINED, FIRST_MODIFIER_TICKS);
    }

    private BrewResult customResult(Material potionMaterial, PotionRule rule, int level, int durationTicks, PotionStage stage, int baseTicks) {
        ItemStack result = customPotion(potionMaterial, rule.effectName(), level, rule.instant() ? 1 : durationTicks, stage);
        return new BrewResult(result, workTicks(baseTicks, isSpecialEffect(rule.effectName())));
    }

    private BrewResult vanillaResult(Material resultMaterial, PotionType resultType, int baseTicks) {
        if (resultType == null) {
            return null;
        }
        ItemStack result = new ItemStack(resultMaterial, 1);
        PotionMeta meta = (PotionMeta) result.getItemMeta();
        meta.setBasePotionType(resultType);
        result.setItemMeta(meta);
        return new BrewResult(result, workTicks(baseTicks, isSpecialType(resultType)));
    }

    private ItemStack customPotion(Material material, String effectName, int level, int durationTicks, PotionStage stage) {
        PotionEffectType effectType = effectType(effectName);
        if (effectType == null) {
            return new ItemStack(Material.AIR);
        }
        ItemStack result = new ItemStack(material, 1);
        PotionMeta meta = (PotionMeta) result.getItemMeta();
        PotionType water = type("WATER");
        if (water != null) {
            meta.setBasePotionType(water);
        }
        meta.clearCustomEffects();
        meta.addCustomEffect(new PotionEffect(effectType, Math.max(1, durationTicks), Math.max(0, level - 1)), true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(effectKey, PersistentDataType.STRING, effectName.toUpperCase(Locale.ROOT));
        pdc.set(levelKey, PersistentDataType.INTEGER, Math.max(1, level));
        pdc.set(durationKey, PersistentDataType.INTEGER, Math.max(1, durationTicks));
        pdc.set(stageKey, PersistentDataType.STRING, stage.name());
        result.setItemMeta(meta);
        return result;
    }

    private PotionModel modelFrom(PotionMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String effect = pdc.get(effectKey, PersistentDataType.STRING);
        Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
        Integer duration = pdc.get(durationKey, PersistentDataType.INTEGER);
        if (effect == null || effect.isBlank() || level == null || duration == null) {
            return null;
        }
        PotionStage stage = PotionStage.NORMAL;
        String rawStage = pdc.get(stageKey, PersistentDataType.STRING);
        if (rawStage != null) {
            try {
                stage = PotionStage.valueOf(rawStage.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                stage = inferStage(effect, level, duration);
            }
        } else {
            stage = inferStage(effect, level, duration);
        }
        return new PotionModel(effect.toUpperCase(Locale.ROOT), Math.max(1, level), Math.max(1, duration), stage);
    }

    private PotionModel modelFromVanilla(PotionType type, String effect) {
        if (type == null || effect == null) {
            return null;
        }
        String name = type.name();
        PotionRule rule = rule(effect);
        if (name.startsWith("STRONG_")) {
            return new PotionModel(effect, rule == null ? 2 : rule.strongLevel(), rule == null ? seconds(90) : rule.strongDurationTicks(), PotionStage.FIRST_STRONG);
        }
        if (name.startsWith("LONG_")) {
            return new PotionModel(effect, rule == null ? 1 : rule.longLevel(), rule == null ? minutes(8) : rule.longDurationTicks(), PotionStage.FIRST_LONG);
        }
        return new PotionModel(effect, rule == null ? 1 : rule.normalLevel(), rule == null ? minutes(3) : rule.normalDurationTicks(), PotionStage.NORMAL);
    }

    private PotionStage inferStage(String effect, int level, int duration) {
        PotionRule rule = rule(effect);
        if (rule == null) {
            return PotionStage.NORMAL;
        }
        if (level >= rule.secondStrongLevel() && rule.allowSecondStrength()) {
            return PotionStage.SECOND_STRONG;
        }
        if (duration >= rule.secondLongDurationTicks() && rule.allowSecondExtension()) {
            return PotionStage.SECOND_LONG;
        }
        if (level >= rule.combinedLevel() && duration >= rule.combinedDurationTicks() && rule.allowCombined()) {
            return PotionStage.COMBINED;
        }
        if (level >= rule.strongLevel() && rule.allowStrength()) {
            return PotionStage.FIRST_STRONG;
        }
        if (duration >= rule.longDurationTicks() && rule.allowExtension()) {
            return PotionStage.FIRST_LONG;
        }
        return PotionStage.NORMAL;
    }

    private PotionType baseType(PotionMeta meta) {
        PotionType type = meta.getBasePotionType();
        return type == null ? type("WATER") : type;
    }

    private PotionType baseIngredientResult(Material ingredient) {
        if (ingredient == Material.SUGAR) return type("SWIFTNESS");
        if (ingredient == Material.RABBIT_FOOT) return type("LEAPING");
        if (ingredient == Material.BLAZE_POWDER) return type("STRENGTH");
        if (ingredient == Material.GLISTERING_MELON_SLICE) return type("HEALING");
        if (ingredient == Material.SPIDER_EYE) return type("POISON");
        if (ingredient == Material.GHAST_TEAR) return type("REGENERATION");
        if (ingredient == Material.MAGMA_CREAM) return type("FIRE_RESISTANCE");
        if (ingredient == Material.PUFFERFISH) return type("WATER_BREATHING");
        if (ingredient == Material.GOLDEN_CARROT) return type("NIGHT_VISION");
        if (ingredient == Material.TURTLE_HELMET) return type("TURTLE_MASTER");
        if (ingredient == Material.PHANTOM_MEMBRANE) return type("SLOW_FALLING");
        if (ingredient == optionalMaterial("BREEZE_ROD")) return type("WIND_CHARGED");
        if (ingredient == optionalMaterial("COBWEB")) return type("WEAVING");
        if (ingredient == optionalMaterial("SLIME_BLOCK")) return type("OOZING");
        if (ingredient == Material.STONE) return type("INFESTED");
        return null;
    }

    private PotionType longType(PotionType input) {
        if (input == null) return null;
        String name = input.name();
        if (name.startsWith("LONG_") || name.startsWith("STRONG_")) return null;
        PotionRule rule = rule(strippedPotionName(name));
        if (rule != null && !rule.allowExtension()) return null;
        return type("LONG_" + name);
    }

    private PotionType strongType(PotionType input) {
        if (input == null) return null;
        String name = input.name();
        if (name.startsWith("LONG_") || name.startsWith("STRONG_")) return null;
        PotionRule rule = rule(strippedPotionName(name));
        if (rule != null && !rule.allowStrength()) return null;
        return type("STRONG_" + name);
    }

    private PotionType corruptType(PotionType input) {
        if (input == null) return null;
        return switch (input.name()) {
            case "WATER" -> type("WEAKNESS");
            case "NIGHT_VISION" -> type("INVISIBILITY");
            case "LONG_NIGHT_VISION" -> type("LONG_INVISIBILITY");
            case "LEAPING", "STRONG_LEAPING", "SWIFTNESS", "STRONG_SWIFTNESS" -> type("SLOWNESS");
            case "LONG_LEAPING", "LONG_SWIFTNESS" -> type("LONG_SLOWNESS");
            case "HEALING", "POISON", "LONG_POISON" -> type("HARMING");
            case "STRONG_HEALING", "STRONG_POISON" -> type("STRONG_HARMING");
            default -> null;
        };
    }

    private int workTicks(int baseTicks, boolean special) {
        return special ? (int) Math.ceil(baseTicks * 1.5D) : baseTicks;
    }

    private boolean isSpecialPotion(ItemStack result) {
        if (!(result.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        PotionModel model = modelFrom(meta);
        if (model != null) {
            return isSpecialEffect(model.effectName());
        }
        return isSpecialType(baseType(meta));
    }

    private boolean isSpecialType(PotionType type) {
        if (type == null) return false;
        return isSpecialEffect(strippedPotionName(type.name()));
    }

    private boolean isSpecialEffect(String effect) {
        if (effect == null) return false;
        String name = strippedPotionName(effect.toUpperCase(Locale.ROOT));
        return name.equals("WIND_CHARGED") || name.equals("WEAVING") || name.equals("OOZING") || name.equals("INFESTED");
    }

    private String effectName(PotionType type) {
        if (type == null) return null;
        String name = strippedPotionName(type.name());
        if (name.equals("AWKWARD") || name.equals("MUNDANE") || name.equals("THICK") || name.equals("WATER")) {
            return null;
        }
        return EFFECT_TYPES.containsKey(name) ? name : null;
    }

    private PotionRule rule(String effect) {
        if (effect == null) return null;
        return RULES.get(strippedPotionName(effect.toUpperCase(Locale.ROOT)));
    }

    private String strippedPotionName(String name) {
        if (name == null) return "";
        String result = name.toUpperCase(Locale.ROOT);
        if (result.startsWith("LONG_")) result = result.substring("LONG_".length());
        if (result.startsWith("STRONG_")) result = result.substring("STRONG_".length());
        return result;
    }

    private PotionEffectType effectType(String effectName) {
        String mapped = EFFECT_TYPES.get(strippedPotionName(effectName));
        if (mapped == null) {
            return null;
        }
        PotionEffectType direct = PotionEffectType.getByName(mapped);
        if (direct != null) {
            return direct;
        }
        String fallback = switch (mapped) {
            case "JUMP" -> "JUMP_BOOST";
            case "INCREASE_DAMAGE" -> "STRENGTH";
            case "HEAL" -> "INSTANT_HEALTH";
            case "HARM" -> "INSTANT_DAMAGE";
            case "SLOW" -> "SLOWNESS";
            default -> mapped;
        };
        return PotionEffectType.getByName(fallback);
    }

    private PotionType type(String name) {
        if (name == null) return null;
        try {
            return PotionType.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Material optionalMaterial(String name) {
        return Material.matchMaterial(name);
    }

    private static Map.Entry<String, PotionRule> rule(String effectName, boolean instant, boolean allowStrength, boolean allowExtension,
                                                      boolean allowSecondStrength, boolean allowSecondExtension, boolean allowCombined,
                                                      int normalLevel, int normalDurationTicks, int longLevel, int longDurationTicks,
                                                      int strongLevel, int strongDurationTicks, int combinedLevel, int combinedDurationTicks,
                                                      int secondStrongLevel, int secondStrongDurationTicks, int secondLongLevel, int secondLongDurationTicks) {
        return Map.entry(effectName, new PotionRule(effectName, instant, allowStrength, allowExtension, allowSecondStrength, allowSecondExtension, allowCombined,
                normalLevel, normalDurationTicks, longLevel, longDurationTicks, strongLevel, strongDurationTicks, combinedLevel, combinedDurationTicks,
                secondStrongLevel, secondStrongDurationTicks, secondLongLevel, secondLongDurationTicks));
    }

    private static int seconds(int seconds) {
        return seconds * 20;
    }

    private static int minutes(int minutes) {
        return seconds(minutes * 60);
    }

    record BrewResult(ItemStack result, int workTicks) {
    }

    private enum PotionStage {
        NORMAL,
        FIRST_STRONG,
        FIRST_LONG,
        COMBINED,
        SECOND_STRONG,
        SECOND_LONG
    }

    private record PotionModel(String effectName, int level, int durationTicks, PotionStage stage) {
    }

    private record PotionRule(String effectName, boolean instant, boolean allowStrength, boolean allowExtension,
                              boolean allowSecondStrength, boolean allowSecondExtension, boolean allowCombined,
                              int normalLevel, int normalDurationTicks, int longLevel, int longDurationTicks,
                              int strongLevel, int strongDurationTicks, int combinedLevel, int combinedDurationTicks,
                              int secondStrongLevel, int secondStrongDurationTicks, int secondLongLevel, int secondLongDurationTicks) {
    }
}
