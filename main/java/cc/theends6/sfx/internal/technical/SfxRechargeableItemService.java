package cc.theends6.sfx.internal.technical;

import cc.theends6.sfx.api.behavior.SfxBehaviorRegistry;
import cc.theends6.sfx.api.behavior.SfxRechargeableItemDefinition;
import cc.theends6.sfx.api.behavior.SfxRechargeableItemKind;
import cc.theends6.sfx.api.behavior.SfxRechargeableItemProvider;
import cc.theends6.sfx.api.item.SfxItemMarker;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.text.Text;
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
    private static final String FUEL_LORE_MARKER = "⛽";

    private final SfxItems items;
    private final NamespacedKey chargeKey;
    private final NamespacedKey fuelKey;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final double rechargeableMultiplier;

    public SfxRechargeableItemService(JavaPlugin plugin, SfxItems items) {
        this(plugin, items, null);
    }

    public SfxRechargeableItemService(JavaPlugin plugin, SfxItems items, SfxBehaviorRegistry behaviors) {
        this.items = items;
        this.chargeKey = new NamespacedKey(plugin, "item_charge");
        this.fuelKey = new NamespacedKey(plugin, "item_fuel");
        this.rechargeableMultiplier = SfxTechnicalGadgetBalance.rules(plugin).rechargeableMultiplier();
        registerDefaults();
        registerAddonDefinitions(behaviors);
    }

    public Optional<Definition> definition(ItemStack stack) {
        SfxItemMarker marker = items.readMarker(stack).orElse(null);
        if (marker == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(marker.itemId()));
    }

    public boolean isRechargeable(ItemStack stack) {
        return definition(stack).filter(Definition::usesElectricCharge).isPresent();
    }

    public double charge(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0.0D;
        }
        Definition definition = definition(stack).orElse(null);
        if (definition == null || !definition.usesElectricCharge()) {
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
        if (definition == null || !definition.usesElectricCharge()) {
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
        if (definition == null || !definition.usesElectricCharge()) {
            return false;
        }
        double current = charge(stack);
        if (current + 0.0000001D < amount) {
            return false;
        }
        updateCharge(stack, definition, Math.max(0.0D, current - amount));
        return true;
    }

    public boolean removeChargeAllowPartial(ItemStack stack, double amount) {
        if (amount <= 0.0D) {
            return false;
        }
        Definition definition = definition(stack).orElse(null);
        if (definition == null || !definition.usesElectricCharge()) {
            return false;
        }
        double current = charge(stack);
        if (current <= 0.0000001D) {
            return false;
        }
        updateCharge(stack, definition, Math.max(0.0D, current - amount));
        return true;
    }

    public double capacity(ItemStack stack) {
        return definition(stack).filter(Definition::usesElectricCharge).map(Definition::capacity).orElse(0.0D);
    }

    public double fuel(ItemStack stack) {
        Definition definition = definition(stack).orElse(null);
        if (stack == null || stack.getType().isAir() || definition == null || !definition.usesFuel()) {
            return 0.0D;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0.0D;
        }
        Double value = meta.getPersistentDataContainer().get(fuelKey, PersistentDataType.DOUBLE);
        return clamp(value == null ? 0.0D : value, 0.0D, definition.fuelCapacity());
    }

    public boolean addFuel(ItemStack stack, double amount) {
        Definition definition = definition(stack).orElse(null);
        if (amount <= 0.0D || definition == null || !definition.usesFuel()) {
            return false;
        }
        double current = fuel(stack);
        if (current >= definition.fuelCapacity()) {
            updateFuel(stack, definition, definition.fuelCapacity());
            return false;
        }
        double updated = Math.min(definition.fuelCapacity(), current + amount);
        updateFuel(stack, definition, updated);
        return updated > current;
    }

    public boolean removeFuel(ItemStack stack, double amount) {
        Definition definition = definition(stack).orElse(null);
        if (amount <= 0.0D || definition == null || !definition.usesFuel()) {
            return false;
        }
        double current = fuel(stack);
        if (current + 0.0000001D < amount) {
            return false;
        }
        updateFuel(stack, definition, Math.max(0.0D, current - amount));
        return true;
    }

    private void updateCharge(ItemStack stack, Definition definition, double charge) {
        if (stack == null || stack.getType().isAir() || definition == null || !definition.usesElectricCharge()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        double value = clamp(charge, 0.0D, definition.capacity());
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.DOUBLE, value);
        Component chargeLine = Text.legacy("&8⇨ &e⚡&7" + CHARGE_FORMAT.format(value) + " / " + CHARGE_FORMAT.format(definition.capacity()) + " J");
        replaceOrAppendLore(meta, chargeLine, CHARGE_LORE_MARKER, " / ", "J");
        stack.setItemMeta(meta);
    }

    private void updateFuel(ItemStack stack, Definition definition, double fuel) {
        if (stack == null || stack.getType().isAir() || definition == null || !definition.usesFuel()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        double value = clamp(fuel, 0.0D, definition.fuelCapacity());
        meta.getPersistentDataContainer().set(fuelKey, PersistentDataType.DOUBLE, value);
        Component fuelLine = Text.legacy("&8⇨ &6⛽&7" + CHARGE_FORMAT.format(value) + " / " + CHARGE_FORMAT.format(definition.fuelCapacity()) + " Fuel");
        replaceOrAppendLore(meta, fuelLine, FUEL_LORE_MARKER);
        stack.setItemMeta(meta);
    }

    private void replaceOrAppendLore(ItemMeta meta, Component replacement, String marker, String... requiredFragments) {
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            meta.lore(List.of(replacement));
            return;
        }
        java.util.ArrayList<Component> updatedLore = new java.util.ArrayList<>();
        boolean replaced = false;
        for (Component line : lore) {
            String legacy = Text.toLegacy(line);
            if (!legacy.contains(marker)) {
                updatedLore.add(line);
                continue;
            }
            boolean allMatch = true;
            for (String fragment : requiredFragments) {
                if (!legacy.contains(fragment)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                if (!replaced) {
                    updatedLore.add(replacement);
                    replaced = true;
                }
                continue;
            }
            updatedLore.add(line);
        }
        if (!replaced) {
            updatedLore.add(replacement);
        }
        meta.lore(updatedLore);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void registerDefaults() {
        registerClassicJetpacksAndBoots();

        generic("sf:duralumin_multi_tool", 20.0D);
        generic("sf:solder_multi_tool", 30.0D);
        generic("sf:billon_multi_tool", 40.0D);
        generic("sf:steel_multi_tool", 50.0D);
        generic("sf:damascus_steel_multi_tool", 60.0D);
        generic("sf:reinforced_alloy_multi_tool", 75.0D);
        generic("sf:carbonado_multi_tool", 100.0D);
    }

    private void registerClassicJetpacksAndBoots() {
        jetpackClassic("sf:duralumin_jetpack", 20.0D, 0.35D);
        jetpackClassic("sf:solder_jetpack", 30.0D, 0.40D);
        jetpackClassic("sf:billon_jetpack", 45.0D, 0.45D);
        jetpackClassic("sf:steel_jetpack", 60.0D, 0.50D);
        jetpackClassic("sf:damascus_steel_jetpack", 75.0D, 0.55D);
        jetpackClassic("sf:reinforced_alloy_jetpack", 100.0D, 0.60D);
        jetpackClassic("sf:carbonado_jetpack", 150.0D, 0.70D);
        jetpackClassic("sf:armored_jetpack", 50.0D, 0.50D);

        jetBootsClassic("sf:duralumin_jetboots", 20.0D, 0.35D);
        jetBootsClassic("sf:solder_jetboots", 30.0D, 0.40D);
        jetBootsClassic("sf:billon_jetboots", 40.0D, 0.45D);
        jetBootsClassic("sf:steel_jetboots", 50.0D, 0.50D);
        jetBootsClassic("sf:damascus_steel_jetboots", 75.0D, 0.55D);
        jetBootsClassic("sf:reinforced_alloy_jetboots", 100.0D, 0.60D);
        jetBootsClassic("sf:carbonado_jetboots", 125.0D, 0.70D);
        jetBootsClassic("sf:armored_jetboots", 50.0D, 0.45D);
    }

    private void jetpackClassic(String id, double classicCapacity, double thrust) {
        definitions.put(id, Definition.electric(id, RechargeableKind.JETPACK, 0, scaled(classicCapacity), thrust, scaled(0.08D), -1, false));
    }

    private void jetBootsClassic(String id, double classicCapacity, double speed) {
        definitions.put(id, Definition.electric(id, RechargeableKind.JETBOOTS, 0, scaled(classicCapacity), speed, scaled(0.075D), -1, false));
    }

    private void generic(String id, double classicCapacity) {
        definitions.put(id, Definition.electric(id, RechargeableKind.GENERIC, 0, scaled(classicCapacity), 0.0D, 0.0D, -1, false));
    }

    private double scaled(double classicValue) {
        return classicValue * rechargeableMultiplier;
    }

    private void registerAddonDefinitions(SfxBehaviorRegistry behaviors) {
        if (behaviors == null) {
            return;
        }
        for (SfxRechargeableItemProvider provider : behaviors.rechargeableItemProviders()) {
            List<SfxRechargeableItemDefinition> addonDefinitions = provider.definitions();
            if (addonDefinitions == null) {
                continue;
            }
            for (SfxRechargeableItemDefinition definition : addonDefinitions) {
                if (definition != null) {
                    definitions.put(definition.itemId(), fromApi(definition));
                }
            }
        }
    }

    private Definition fromApi(SfxRechargeableItemDefinition definition) {
        return new Definition(
                definition.itemId(),
                fromApi(definition.kind()),
                definition.level(),
                definition.capacity(),
                definition.movementValue(),
                definition.useCost(),
                definition.heightLimit(),
                definition.hoverSupported(),
                definition.fuelCapacity());
    }

    private RechargeableKind fromApi(SfxRechargeableItemKind kind) {
        return switch (kind) {
            case GENERIC -> RechargeableKind.GENERIC;
            case JETPACK -> RechargeableKind.JETPACK;
            case JETBOOTS -> RechargeableKind.JETBOOTS;
            case FUEL_JETPACK -> RechargeableKind.FUEL_JETPACK;
        };
    }

    public enum RechargeableKind {
        GENERIC,
        JETPACK,
        JETBOOTS,
        FUEL_JETPACK
    }

    public record Definition(
            String itemId,
            RechargeableKind kind,
            int level,
            double capacity,
            double movementValue,
            double useCost,
            int heightLimit,
            boolean hoverSupported,
            double fuelCapacity
    ) {
        static Definition electric(String itemId, RechargeableKind kind, int level, double capacity, double movementValue, double useCost, int heightLimit, boolean hoverSupported) {
            return new Definition(itemId, kind, level, capacity, movementValue, useCost, heightLimit, hoverSupported, 0.0D);
        }

        static Definition fuelJetpack(String itemId, int level, double thrust, double fuelUseCost, int heightLimit, boolean hoverSupported, double fuelCapacity) {
            return new Definition(itemId, RechargeableKind.FUEL_JETPACK, level, 0.0D, thrust, fuelUseCost, heightLimit, hoverSupported, fuelCapacity);
        }

        boolean usesElectricCharge() {
            return capacity > 0.0D && kind != RechargeableKind.FUEL_JETPACK;
        }

        boolean usesFuel() {
            return fuelCapacity > 0.0D || kind == RechargeableKind.FUEL_JETPACK;
        }
    }
}
