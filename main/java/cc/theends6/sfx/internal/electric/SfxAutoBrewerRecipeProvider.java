package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

final class SfxAutoBrewerRecipeProvider implements SfxElectricRecipeProvider {
    private static final int WORK_SECONDS = 30;

    @Override
    public List<SfxElectricRecipe> recipes() {
        return List.of();
    }

    @Override
    public SfxElectricRecipeMatch findDynamicMatch(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
        for (int potionSlot = 0; potionSlot < state.inputCapacity(); potionSlot++) {
            SfxElectricStack potionStack = state.input(potionSlot);
            if (!isPotionStack(potionStack)) {
                continue;
            }
            ItemStack potion = potionStack.toItemStack(null);
            for (int ingredientSlot = 0; ingredientSlot < state.inputCapacity(); ingredientSlot++) {
                if (ingredientSlot == potionSlot) {
                    continue;
                }
                SfxElectricStack ingredientStack = state.input(ingredientSlot);
                if (!isPlainVanillaIngredient(ingredientStack)) {
                    continue;
                }
                ItemStack result = brew(potion, ingredientStack.material());
                if (result == null || result.getType().isAir()) {
                    continue;
                }
                String key = "auto_brewer:" + potion.getType().name().toLowerCase(Locale.ROOT)
                        + ":" + baseTypeName(potion)
                        + ":" + ingredientStack.material().name().toLowerCase(Locale.ROOT);
                SfxElectricRecipe recipe = SfxElectricRecipe.fixedOutputs(
                        key,
                        List.of(SfxRecipeSlot.vanilla(potionStack.material()), SfxRecipeSlot.vanilla(ingredientStack.material())),
                        List.of(SfxElectricStack.snapshot(result)),
                        WORK_SECONDS);
                return new SfxElectricRecipeMatch(new int[]{potionSlot, ingredientSlot}, recipe);
            }
        }
        return null;
    }

    private boolean isPotionStack(SfxElectricStack stack) {
        if (stack == null || stack.isSfxItem()) {
            return false;
        }
        Material material = stack.material();
        return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION;
    }

    private boolean isPlainVanillaIngredient(SfxElectricStack stack) {
        return stack != null && !stack.isSfxItem() && !stack.hasSnapshot() && stack.amount() > 0;
    }

    private ItemStack brew(ItemStack potion, Material ingredient) {
        if (potion == null || ingredient == null || !(potion.getItemMeta() instanceof PotionMeta meta)) {
            return null;
        }
        PotionType inputType = meta.getBasePotionType();
        if (inputType == null) {
            inputType = type("WATER");
        }
        Material resultMaterial = potion.getType();
        PotionType resultType = null;

        if (ingredient == Material.GUNPOWDER && potion.getType() == Material.POTION) {
            resultMaterial = Material.SPLASH_POTION;
            resultType = inputType;
        } else if (ingredient == Material.DRAGON_BREATH && potion.getType() == Material.SPLASH_POTION) {
            resultMaterial = Material.LINGERING_POTION;
            resultType = inputType;
        } else if (ingredient == Material.REDSTONE) {
            resultType = longType(inputType);
        } else if (ingredient == Material.GLOWSTONE_DUST) {
            resultType = strongType(inputType);
        } else if (ingredient == Material.FERMENTED_SPIDER_EYE) {
            resultType = corruptType(inputType);
        } else if (inputType == type("WATER") && ingredient == Material.NETHER_WART) {
            resultType = type("AWKWARD");
        } else if (inputType == type("WATER") && ingredient == Material.FERMENTED_SPIDER_EYE) {
            resultType = type("WEAKNESS");
        } else if (inputType == type("AWKWARD")) {
            resultType = baseIngredientResult(ingredient);
        }

        if (resultType == null) {
            return null;
        }
        ItemStack result = new ItemStack(resultMaterial, 1);
        PotionMeta resultMeta = (PotionMeta) result.getItemMeta();
        resultMeta.setBasePotionType(resultType);
        result.setItemMeta(resultMeta);
        return result;
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
        if (input == null) {
            return null;
        }
        String name = input.name();
        if (name.startsWith("LONG_") || name.startsWith("STRONG_")) {
            return null;
        }
        return type("LONG_" + name);
    }

    private PotionType strongType(PotionType input) {
        if (input == null) {
            return null;
        }
        String name = input.name();
        if (name.startsWith("LONG_") || name.startsWith("STRONG_")) {
            return null;
        }
        return type("STRONG_" + name);
    }

    private PotionType corruptType(PotionType input) {
        if (input == null) {
            return null;
        }
        String name = input.name();
        return switch (name) {
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

    private PotionType type(String name) {
        if (name == null) {
            return null;
        }
        try {
            return PotionType.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Material optionalMaterial(String name) {
        return Material.matchMaterial(name);
    }

    private String baseTypeName(ItemStack potion) {
        if (potion.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() != null) {
            return meta.getBasePotionType().name().toLowerCase(Locale.ROOT);
        }
        return "water";
    }
}
