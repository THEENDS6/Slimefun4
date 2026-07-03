package cc.theends6.sfx.internal.item;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.api.item.SfxItemKind;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.util.HeadTextures;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class DefaultSfxItems implements SfxItems {
    public static final int PDC_SCHEMA_VERSION = 1;

    private final JavaPlugin plugin;
    private final DefaultSfxItemRegistry registry;
    private final SfxLocalization localization;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey itemVersionKey;
    private final NamespacedKey schemaKey;
    private final NamespacedKey variantKey;
    private final NamespacedKey flagsKey;
    private final NamespacedKey kindKey;
    private final NamespacedKey guideModeKey;

    public DefaultSfxItems(JavaPlugin plugin, DefaultSfxItemRegistry registry, SfxLocalization localization) {
        this.plugin = plugin;
        this.registry = registry;
        this.localization = localization;
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
        this.itemVersionKey = new NamespacedKey(plugin, "item_version");
        this.schemaKey = new NamespacedKey(plugin, "item_schema");
        this.variantKey = new NamespacedKey(plugin, "item_variant");
        this.flagsKey = new NamespacedKey(plugin, "item_flags");
        this.kindKey = new NamespacedKey(plugin, "item_kind");
        this.guideModeKey = new NamespacedKey(plugin, "guide_mode");
    }

    @Override
    public ItemStack create(String id) {
        SfxItemDefinition definition = registry.item(id).orElseThrow(() -> new IllegalArgumentException("Unknown SFX item: " + id));
        return create(definition, 1);
    }

    @Override
    public ItemStack create(String id, int amount) {
        SfxItemDefinition definition = registry.item(id).orElseThrow(() -> new IllegalArgumentException("Unknown SFX item: " + id));
        return create(definition, amount);
    }

    @Override
    public ItemStack create(SfxItemDefinition definition, int amount) {
        ItemStack item = new ItemStack(definition.material());
        int maxStack = definition.material() == Material.PLAYER_HEAD ? 64 : definition.material().getMaxStackSize();
        item.setAmount(Math.max(1, Math.min(maxStack, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (definition.material() == Material.PLAYER_HEAD && definition.headTextureHash() != null) {
                HeadTextures.apply(meta, definition.headTextureHash());
            }
            applyDefinitionMeta(meta, definition);
            meta.displayName(Text.noItalic(localization.component(definition.nameKey())));
            List<net.kyori.adventure.text.Component> lore = definition.loreKey() == null
                    ? List.of()
                    : localization.requiredList(definition.loreKey()).stream()
                    .map(Text::renderFlexible)
                    .toList();
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(Text::noItalic).toList());
            }
            writeMarker(meta, new SfxItemMarker(
                    definition.id(),
                    definition.version(),
                    PDC_SCHEMA_VERSION,
                    definition.variant(),
                    definition.kind(),
                    definition.flags()
            ));
            item.setItemMeta(meta);
        }
        applyDataComponents(item, definition);
        return item;
    }

    @Override
    public ItemStack createGuideBook(GuideMode mode) {
        String itemId = mode == GuideMode.CHEAT ? "sfx:cheat_guide" : "sfx:guide";
        ItemStack item = create(itemId);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(guideModeKey, PersistentDataType.STRING, mode.pdcValue());
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Optional<SfxItemMarker> readMarker(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String itemId = meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        Integer itemVersion = meta.getPersistentDataContainer().get(itemVersionKey, PersistentDataType.INTEGER);
        Integer schemaVersion = meta.getPersistentDataContainer().get(schemaKey, PersistentDataType.INTEGER);
        String variant = meta.getPersistentDataContainer().get(variantKey, PersistentDataType.STRING);
        String kind = meta.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        String flags = meta.getPersistentDataContainer().get(flagsKey, PersistentDataType.STRING);
        if (itemId == null || itemVersion == null || schemaVersion == null) {
            return Optional.empty();
        }
        List<String> flagList = flags == null || flags.isBlank() ? List.of() : List.of(flags.split(","));
        return Optional.of(new SfxItemMarker(itemId, itemVersion, schemaVersion, variant, SfxItemKind.fromPdc(kind), flagList));
    }

    @Override
    public Optional<GuideMode> readGuideMode(ItemStack item) {
        Optional<SfxItemMarker> marker = readMarker(item);
        if (marker.isEmpty() || marker.get().kind() != SfxItemKind.GUIDE) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        return Optional.of(GuideMode.fromPdc(meta.getPersistentDataContainer().get(guideModeKey, PersistentDataType.STRING)));
    }

    @Override
    public boolean isSfxItem(ItemStack item) {
        return readMarker(item).isPresent();
    }

    @Override
    public boolean matches(ItemStack item, SfxRecipeSlot slot) {
        if (slot == null || slot.isEmpty()) {
            return item == null || item.getType().isAir() || item.getAmount() <= 0;
        }
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }
        if (item.getAmount() < slot.amount()) {
            return false;
        }
        if (slot.isSfxItem()) {
            return readMarker(item)
                    .map(marker -> marker.itemId().equals(slot.sfxItemId()))
                    .orElse(false);
        }
        if (isSfxItem(item)) {
            return false;
        }
        return item.getType() == slot.material();
    }

    @Override
    public void give(Player player, ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void applyDefinitionMeta(ItemMeta meta, SfxItemDefinition definition) {
        if (definition.colorRgb() != null) {
            applyColor(meta, definition.colorRgb());
        }
        for (String rawFlag : definition.itemFlags()) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(rawFlag.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale/unknown Bukkit item flags so older server versions can still load the item.
            }
        }
        if (definition.unbreakable()) {
            meta.setUnbreakable(true);
        }
        if (definition.flags().contains("visual-glint")) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        for (Map.Entry<String, Integer> entry : definition.enchantments().entrySet()) {
            Enchantment enchantment = resolveEnchantment(entry.getKey());
            if (enchantment != null) {
                meta.addEnchant(enchantment, entry.getValue(), true);
            }
        }
    }

    private void applyDataComponents(ItemStack item, SfxItemDefinition definition) {
        FoodSpec spec = foodSpec(definition.id());
        if (spec == null) {
            return;
        }

        item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(spec.nutrition())
                .saturation(spec.saturation())
                .canAlwaysEat(spec.alwaysEdible()));

        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(spec.consumeSeconds())
                .animation(spec.animation())
                .sound(Key.key(spec.animation() == ItemUseAnimation.DRINK ? "minecraft:item.honey_bottle.drink" : "minecraft:entity.generic.eat"))
                .hasConsumeParticles(true));
    }

    private FoodSpec foodSpec(String itemId) {
        return switch (itemId) {
            case "sf:fortune_cookie" -> new FoodSpec(2, 0.1f, 1.6f, ItemUseAnimation.EAT, false);
            case "sf:diet_cookie" -> new FoodSpec(1, 0.1f, 1.6f, ItemUseAnimation.EAT, true);
            case "sf:monster_jerky" -> new FoodSpec(4, 0.25f, 1.6f, ItemUseAnimation.EAT, false);
            case "sf:apple_juice", "sf:melon_juice", "sf:carrot_juice", "sf:pumpkin_juice", "sf:sweet_berry_juice", "sf:glow_berry_juice" ->
                    new FoodSpec(3, 0.35f, 1.2f, ItemUseAnimation.DRINK, false);
            case "sf:golden_apple_juice" -> new FoodSpec(4, 0.5f, 1.2f, ItemUseAnimation.DRINK, true);
            case "sf:beef_jerky", "sf:pork_jerky" -> new FoodSpec(8, 1.2f, 1.6f, ItemUseAnimation.EAT, false);
            case "sf:chicken_jerky", "sf:mutton_jerky" -> new FoodSpec(6, 1.0f, 1.6f, ItemUseAnimation.EAT, false);
            case "sf:rabbit_jerky", "sf:fish_jerky" -> new FoodSpec(5, 1.0f, 1.6f, ItemUseAnimation.EAT, false);
            case "sf:kelp_cookie" -> new FoodSpec(2, 0.2f, 1.6f, ItemUseAnimation.EAT, false);
            case "sf:christmas_milk" -> new FoodSpec(3, 0.3f, 1.2f, ItemUseAnimation.DRINK, false);
            case "sf:christmas_chocolate_milk" -> new FoodSpec(6, 0.7f, 1.2f, ItemUseAnimation.DRINK, false);
            case "sf:christmas_egg_nog" -> new FoodSpec(4, 0.45f, 1.2f, ItemUseAnimation.DRINK, false);
            case "sf:christmas_apple_cider", "sf:christmas_hot_chocolate" -> new FoodSpec(7, 0.8f, 1.2f, ItemUseAnimation.DRINK, false);
            case "sf:christmas_cookie" -> new FoodSpec(3, 0.2f, 1.6f, ItemUseAnimation.EAT, false);
            case "sf:christmas_fruit_cake", "sf:christmas_apple_pie", "sf:christmas_cake", "sf:carrot_pie", "sf:easter_apple_pie" ->
                    new FoodSpec(8, 0.9f, 1.6f, ItemUseAnimation.EAT, false);
            case "sf:christmas_caramel_apple", "sf:christmas_chocolate_apple" -> new FoodSpec(6, 0.5f, 1.6f, ItemUseAnimation.EAT, false);
            default -> null;
        };
    }

    @SuppressWarnings("deprecation")
    private Enchantment resolveEnchantment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        NamespacedKey explicitKey = NamespacedKey.fromString(normalized);
        if (explicitKey != null) {
            Enchantment enchantment = Enchantment.getByKey(explicitKey);
            if (enchantment != null) {
                return enchantment;
            }
        }
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(normalized));
        if (enchantment != null) {
            return enchantment;
        }
        String legacy = normalized.toUpperCase(Locale.ROOT);
        enchantment = Enchantment.getByName(legacy);
        if (enchantment != null) {
            return enchantment;
        }
        String alias = legacyEnchantmentAlias(legacy);
        if (alias != null) {
            enchantment = Enchantment.getByName(alias);
            if (enchantment != null) {
                return enchantment;
            }
            return Enchantment.getByKey(NamespacedKey.minecraft(alias.toLowerCase(Locale.ROOT)));
        }
        return null;
    }

    private String legacyEnchantmentAlias(String legacy) {
        return switch (legacy) {
            case "UNBREAKING" -> "DURABILITY";
            case "EFFICIENCY" -> "DIG_SPEED";
            case "FORTUNE" -> "LOOT_BONUS_BLOCKS";
            case "POWER" -> "ARROW_DAMAGE";
            case "PUNCH" -> "ARROW_KNOCKBACK";
            case "FLAME" -> "ARROW_FIRE";
            case "INFINITY" -> "ARROW_INFINITE";
            case "SHARPNESS" -> "DAMAGE_ALL";
            case "SMITE" -> "DAMAGE_UNDEAD";
            case "BANE_OF_ARTHROPODS" -> "DAMAGE_ARTHROPODS";
            case "PROTECTION" -> "PROTECTION_ENVIRONMENTAL";
            case "FIRE_PROTECTION" -> "PROTECTION_FIRE";
            case "BLAST_PROTECTION" -> "PROTECTION_EXPLOSIONS";
            case "PROJECTILE_PROTECTION" -> "PROTECTION_PROJECTILE";
            default -> null;
        };
    }

    private void applyColor(ItemMeta meta, int rgb) {
        Color color = Color.fromRGB(rgb & 0xFFFFFF);
        if (meta instanceof LeatherArmorMeta leatherArmorMeta) {
            leatherArmorMeta.setColor(color);
        } else if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setColor(color);
        } else if (meta instanceof FireworkEffectMeta fireworkEffectMeta) {
            fireworkEffectMeta.setEffect(FireworkEffect.builder().withColor(color).build());
        }
    }

    private void writeMarker(ItemMeta meta, SfxItemMarker marker) {
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, marker.itemId());
        meta.getPersistentDataContainer().set(itemVersionKey, PersistentDataType.INTEGER, marker.itemVersion());
        meta.getPersistentDataContainer().set(schemaKey, PersistentDataType.INTEGER, marker.schemaVersion());
        meta.getPersistentDataContainer().set(variantKey, PersistentDataType.STRING, marker.variant());
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, marker.kind().pdcValue());
        meta.getPersistentDataContainer().set(flagsKey, PersistentDataType.STRING, marker.flagsAsString());
    }

    private record FoodSpec(int nutrition, float saturation, float consumeSeconds, ItemUseAnimation animation, boolean alwaysEdible) {
    }
}
