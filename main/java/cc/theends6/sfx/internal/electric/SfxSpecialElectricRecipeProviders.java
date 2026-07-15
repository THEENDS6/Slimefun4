package cc.theends6.sfx.internal.electric;

import cc.theends6.sfx.api.machine.runtime.*;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.util.SfxEnchantmentRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxSpecialElectricRecipeProviders {
    private SfxSpecialElectricRecipeProviders() {
    }

    static SfxElectricRecipeProvider autoAnvil(JavaPlugin plugin, SfxItems items, int repairFactor) {
        return new DynamicProvider() {
            @Override
            public SfxElectricRecipeMatch findDynamicMatch(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
                Settings settings = settings(plugin);
                for (int first = 0; first < 2; first++) {
                    int second = first == 0 ? 1 : 0;
                    SfxElectricStack itemStack = state.input(first);
                    SfxElectricStack ductTape = state.input(second);
                    if (itemStack == null
                            || (itemStack.isSfxItem() && !settings.allowSfxItemEnchanting())
                            || ductTape == null
                            || !ductTape.matches(SfxRecipeSlot.sfx("sf:duct_tape"))) {
                        continue;
                    }
                    ItemStack item = itemStack.toItemStack(items);
                    if (!isDamaged(item)) {
                        continue;
                    }
                    ItemStack repaired = repair(item, repairFactor);
                    SfxElectricRecipe recipe = SfxElectricRecipe.fixedOutputs(
                            "sf:auto_anvil:" + repairFactor + ":" + first,
                            List.of(slotFor(itemStack, 1), SfxRecipeSlot.sfx("sf:duct_tape")),
                            List.of(SfxElectricStack.fromItemStack(items, repaired)),
                            30);
                    return new SfxElectricRecipeMatch(new int[]{first, second}, recipe);
                }
                return null;
            }
        };
    }

    static SfxElectricRecipeProvider autoEnchanter(JavaPlugin plugin, SfxItems items) {
        return new DynamicProvider() {
            @Override
            public SfxElectricRecipeMatch findDynamicMatch(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
                Settings settings = settings(plugin);
                for (int first = 0; first < 2; first++) {
                    int second = first == 0 ? 1 : 0;
                    SfxElectricStack targetStack = state.input(first);
                    SfxElectricStack bookStack = state.input(second);
                    if (targetStack == null
                            || (targetStack.isSfxItem() && !settings.allowSfxItemEnchanting())
                            || bookStack == null
                            || bookStack.isSfxItem()) {
                        continue;
                    }
                    ItemStack target = targetStack.toItemStack(items);
                    ItemStack enchantedBook = bookStack.toItemStack(items);
                    if (!isEnchantable(target) || enchantedBook.getType() != Material.ENCHANTED_BOOK || !(enchantedBook.getItemMeta() instanceof EnchantmentStorageMeta storageMeta)) {
                        continue;
                    }
                    Map<Enchantment, Integer> applicable = new HashMap<>();
                    for (Map.Entry<Enchantment, Integer> entry : storageMeta.getStoredEnchants().entrySet()) {
                        if (SfxEnchantmentRules.canApplyToTarget(
                                target,
                                entry.getKey(),
                                entry.getValue(),
                                settings.allowConflictingEnchantments(),
                                settings.allowIllegalEnchantments())) {
                            applicable.put(entry.getKey(), entry.getValue());
                        }
                    }
                    if (applicable.isEmpty()) {
                        continue;
                    }
                    ItemStack enchanted = target.clone();
                    enchanted.setAmount(1);
                    enchanted.addUnsafeEnchantments(applicable);
                    SfxElectricRecipe recipe = SfxElectricRecipe.fixedOutputs(
                            "sf:auto_enchanter:" + applicable.size() + ":" + first,
                            List.of(slotFor(targetStack, 1), SfxRecipeSlot.vanilla(Material.ENCHANTED_BOOK)),
                            List.of(SfxElectricStack.fromItemStack(items, enchanted), SfxElectricStack.vanilla(Material.BOOK, 1)),
                            Math.max(1, 75 * applicable.size()));
                    return new SfxElectricRecipeMatch(new int[]{first, second}, recipe);
                }
                return null;
            }
        };
    }

    static SfxElectricRecipeProvider autoDisenchanter(JavaPlugin plugin, SfxItems items) {
        return new DynamicProvider() {
            @Override
            public SfxElectricRecipeMatch findDynamicMatch(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
                Settings settings = settings(plugin);
                for (int first = 0; first < 2; first++) {
                    int second = first == 0 ? 1 : 0;
                    SfxElectricStack itemStack = state.input(first);
                    SfxElectricStack bookStack = state.input(second);
                    if (itemStack == null
                            || (itemStack.isSfxItem() && !settings.allowSfxItemEnchanting())
                            || bookStack == null
                            || bookStack.isSfxItem()
                            || !bookStack.matches(SfxRecipeSlot.vanilla(Material.BOOK))) {
                        continue;
                    }
                    ItemStack item = itemStack.toItemStack(items);
                    if (!isDisenchantable(item)) {
                        continue;
                    }
                    Map<Enchantment, Integer> enchantments = item.getEnchantments();
                    if (enchantments.isEmpty()) {
                        continue;
                    }
                    ItemStack disenchanted = item.clone();
                    disenchanted.setAmount(1);
                    ItemMeta itemMeta = disenchanted.getItemMeta();
                    for (Enchantment enchantment : enchantments.keySet()) {
                        itemMeta.removeEnchant(enchantment);
                    }
                    if (itemMeta instanceof Repairable repairable) {
                        repairable.setRepairCost(0);
                    }
                    disenchanted.setItemMeta(itemMeta);

                    ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
                    EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) enchantedBook.getItemMeta();
                    if (bookMeta instanceof Repairable bookRepairable && item.getItemMeta() instanceof Repairable itemRepairable) {
                        bookRepairable.setRepairCost(itemRepairable.getRepairCost());
                    }
                    for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                        bookMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                    }
                    enchantedBook.setItemMeta(bookMeta);
                    SfxElectricRecipe recipe = SfxElectricRecipe.fixedOutputs(
                            "sf:auto_disenchanter:" + enchantments.size() + ":" + first,
                            List.of(slotFor(itemStack, 1), SfxRecipeSlot.vanilla(Material.BOOK)),
                            List.of(SfxElectricStack.fromItemStack(items, disenchanted), SfxElectricStack.snapshot(enchantedBook)),
                            Math.max(1, 90 * enchantments.size()));
                    return new SfxElectricRecipeMatch(new int[]{first, second}, recipe);
                }
                return null;
            }
        };
    }

    static SfxElectricRecipeProvider bookBinder(JavaPlugin plugin) {
        return new DynamicProvider() {
            @Override
            public SfxElectricRecipeMatch findDynamicMatch(SfxElectricMachineDefinition definition, SfxElectricMachineState state) {
                Settings settings = settings(plugin);
                SfxElectricStack firstStack = state.input(0);
                SfxElectricStack secondStack = state.input(1);
                if (firstStack == null || secondStack == null || firstStack.isSfxItem() || secondStack.isSfxItem()) {
                    return null;
                }
                ItemStack firstBook = firstStack.toItemStack(null);
                ItemStack secondBook = secondStack.toItemStack(null);
                if (!isEnchantedBook(firstBook) || !isEnchantedBook(secondBook)) {
                    return null;
                }
                EnchantmentStorageMeta firstMeta = (EnchantmentStorageMeta) firstBook.getItemMeta();
                EnchantmentStorageMeta secondMeta = (EnchantmentStorageMeta) secondBook.getItemMeta();
                Map<Enchantment, Integer> combined = SfxEnchantmentRules.combineStoredBookEnchantments(
                        firstMeta.getStoredEnchants(),
                        secondMeta.getStoredEnchants(),
                        settings.allowConflictingEnchantments());
                if (combined.isEmpty() || combined.equals(firstMeta.getStoredEnchants()) || combined.equals(secondMeta.getStoredEnchants())) {
                    return null;
                }
                ItemStack result = new ItemStack(Material.ENCHANTED_BOOK);
                EnchantmentStorageMeta resultMeta = (EnchantmentStorageMeta) result.getItemMeta();
                for (Map.Entry<Enchantment, Integer> entry : combined.entrySet()) {
                    resultMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                }
                result.setItemMeta(resultMeta);
                SfxElectricRecipe recipe = SfxElectricRecipe.fixedOutputs(
                        "sf:book_binder:" + combined.size(),
                        List.of(SfxRecipeSlot.vanilla(Material.ENCHANTED_BOOK), SfxRecipeSlot.vanilla(Material.ENCHANTED_BOOK)),
                        List.of(SfxElectricStack.snapshot(result)),
                        Math.max(1, 25 * combined.size()));
                return new SfxElectricRecipeMatch(new int[]{0, 1}, recipe);
            }
        };
    }

    private static Settings settings(JavaPlugin plugin) {
        return new Settings(
                plugin.getConfig().getBoolean("electric-machines.allow-sfx-item-enchanting", true),
                plugin.getConfig().getBoolean("electric-machines.allow-conflicting-enchantments", true),
                plugin.getConfig().getBoolean("electric-machines.allow-illegal-enchantments", false));
    }

    private static boolean isDamaged(ItemStack item) {
        return item != null
                && !item.getType().isAir()
                && item.getType().getMaxDurability() > 0
                && item.getItemMeta() instanceof Damageable damageable
                && damageable.getDamage() > 0;
    }

    private static ItemStack repair(ItemStack item, int repairFactor) {
        ItemStack repaired = item.clone();
        repaired.setAmount(1);
        ItemMeta meta = repaired.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int maxDurability = item.getType().getMaxDurability();
            int repairPercentage = Math.max(1, 100 / Math.max(1, repairFactor));
            damageable.setDamage(Math.max(0, damageable.getDamage() - (maxDurability / repairPercentage)));
            repaired.setItemMeta(meta);
        }
        return repaired;
    }

    private static boolean isEnchantable(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getType() != Material.ENCHANTED_BOOK;
    }

    private static boolean isDisenchantable(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getType() != Material.BOOK && !item.getEnchantments().isEmpty();
    }

    private static boolean isEnchantedBook(ItemStack item) {
        return item != null && item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta meta && !meta.getStoredEnchants().isEmpty();
    }

    private static SfxRecipeSlot slotFor(SfxElectricStack stack, int amount) {
        return stack.isSfxItem() ? SfxRecipeSlot.sfx(stack.itemId(), amount) : SfxRecipeSlot.vanilla(stack.material(), amount);
    }

    private abstract static class DynamicProvider implements SfxElectricRecipeProvider {
        @Override
        public List<SfxElectricRecipe> recipes() {
            return List.of();
        }
    }

    private record Settings(
            boolean allowSfxItemEnchanting,
            boolean allowConflictingEnchantments,
            boolean allowIllegalEnchantments
    ) {
    }
}
