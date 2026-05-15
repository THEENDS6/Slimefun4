package cc.theends6.sfx.internal.technical;

import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.util.Text;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxRechargeableItemService {
    private static final DecimalFormat CHARGE_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final String CHARGE_LORE_MARKER = "⚡";

    private final SfxItems items;
    private final NamespacedKey chargeKey;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final double rechargeableMultiplier;

    public SfxRechargeableItemService(JavaPlugin plugin, SfxItems items) {
        this.items = items;
        this.chargeKey = new NamespacedKey(plugin, "item_charge");
        this.rechargeableMultiplier = rechargeableMultiplier(plugin);
        registerDefaults();
    }

    public Optional<Definition> definition(ItemStack stack) {
        SfxItemMarker marker = items.readMarker(stack).orElse(null);
        if (marker == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(marker.itemId()));
    }

    public boolean isRechargeable(ItemStack stack) {
        return definition(stack).isPresent();
    }

    public double charge(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0.0D;
        }
        Definition definition = definition(stack).orElse(null);
        if (definition == null) {
            return 0.0D;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0.0D;
        }
        Double value = meta.getPersistentDataContainer().get(chargeKey, PersistentDataType.DOUBLE);
        return clamp(value == null ? 0.0D : value, 0.0D, definition.capacity());
    }

    public boolean addCharge(ItemStack stack, double amount) {
        if (amount <= 0.0D) {
            return false;
        }
        Definition definition = definition(stack).orElse(null);
        if (definition == null) {
            return false;
        }
        double current = charge(stack);
        if (current >= definition.capacity()) {
            updateCharge(stack, definition, definition.capacity());
            return false;
        }
        double updated = Math.min(definition.capacity(), current + amount);
        updateCharge(stack, definition, updated);
        return updated > current;
    }

    public boolean removeCharge(ItemStack stack, double amount) {
        if (amount <= 0.0D) {
            return false;
        }
        Definition definition = definition(stack).orElse(null);
        if (definition == null) {
            return false;
        }
        double current = charge(stack);
        if (current + 0.0000001D < amount) {
            return false;
        }
        updateCharge(stack, definition, Math.max(0.0D, current - amount));
        return true;
    }

    public double capacity(ItemStack stack) {
        return definition(stack).map(Definition::capacity).orElse(0.0D);
    }

    private void updateCharge(ItemStack stack, Definition definition, double charge) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        double value = clamp(charge, 0.0D, definition.capacity());
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.DOUBLE, value);
        List<Component> lore = meta.lore();
        Component chargeLine = Text.legacy("&8⇨ &e⚡&7" + CHARGE_FORMAT.format(value) + " / " + CHARGE_FORMAT.format(definition.capacity()) + " J");
        if (lore == null || lore.isEmpty()) {
            meta.lore(List.of(chargeLine));
        } else {
            java.util.ArrayList<Component> updatedLore = new java.util.ArrayList<>(lore);
            boolean replaced = false;
            for (int i = 0; i < updatedLore.size(); i++) {
                String legacy = Text.toLegacy(updatedLore.get(i));
                if (legacy.contains(CHARGE_LORE_MARKER) && legacy.contains(" / ") && legacy.contains("J")) {
                    updatedLore.set(i, chargeLine);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                updatedLore.add(chargeLine);
            }
            meta.lore(updatedLore);
        }
        stack.setItemMeta(meta);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void registerDefaults() {
        jetpack("sf:duralumin_jetpack", 20.0D, 0.35D);
        jetpack("sf:solder_jetpack", 30.0D, 0.40D);
        jetpack("sf:billon_jetpack", 45.0D, 0.45D);
        jetpack("sf:steel_jetpack", 60.0D, 0.50D);
        jetpack("sf:damascus_steel_jetpack", 75.0D, 0.55D);
        jetpack("sf:reinforced_alloy_jetpack", 100.0D, 0.60D);
        jetpack("sf:carbonado_jetpack", 150.0D, 0.70D);
        jetpack("sf:armored_jetpack", 50.0D, 0.50D);

        jetBoots("sf:duralumin_jetboots", 20.0D, 0.35D);
        jetBoots("sf:solder_jetboots", 30.0D, 0.40D);
        jetBoots("sf:billon_jetboots", 40.0D, 0.45D);
        jetBoots("sf:steel_jetboots", 50.0D, 0.50D);
        jetBoots("sf:damascus_steel_jetboots", 75.0D, 0.55D);
        jetBoots("sf:reinforced_alloy_jetboots", 100.0D, 0.60D);
        jetBoots("sf:carbonado_jetboots", 125.0D, 0.70D);
        jetBoots("sf:armored_jetboots", 50.0D, 0.45D);

        generic("sf:duralumin_multi_tool", 20.0D);
        generic("sf:solder_multi_tool", 30.0D);
        generic("sf:billon_multi_tool", 40.0D);
        generic("sf:steel_multi_tool", 50.0D);
        generic("sf:damascus_steel_multi_tool", 60.0D);
        generic("sf:reinforced_alloy_multi_tool", 75.0D);
        generic("sf:carbonado_multi_tool", 100.0D);
    }

    private void jetpack(String id, double classicCapacity, double thrust) {
        definitions.put(id, new Definition(id, RechargeableKind.JETPACK, scaled(classicCapacity), thrust, scaled(0.08D)));
    }

    private void jetBoots(String id, double classicCapacity, double speed) {
        definitions.put(id, new Definition(id, RechargeableKind.JETBOOTS, scaled(classicCapacity), speed, scaled(0.075D)));
    }

    private void generic(String id, double classicCapacity) {
        definitions.put(id, new Definition(id, RechargeableKind.GENERIC, scaled(classicCapacity), 0.0D, 0.0D));
    }

    private double scaled(double classicValue) {
        return classicValue * rechargeableMultiplier;
    }

    private double rechargeableMultiplier(JavaPlugin plugin) {
        double base = plugin.getConfig().getDouble("technical-gadgets.rechargeable.base-multiplier", 20.0D);
        if (!plugin.getConfig().getBoolean("technical-gadgets.sfx-balance.enabled", true)) {
            return Math.max(1.0D, base);
        }
        double balance = plugin.getConfig().getDouble("technical-gadgets.sfx-balance.rechargeable-multiplier", 5.0D);
        return Math.max(1.0D, base * Math.max(1.0D, balance));
    }

    public enum RechargeableKind {
        GENERIC,
        JETPACK,
        JETBOOTS
    }

    public record Definition(String itemId, RechargeableKind kind, double capacity, double movementValue, double useCost) {
    }
}
