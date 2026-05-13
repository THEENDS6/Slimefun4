package cc.theends6.sfx.internal.util;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public final class SfxEnchantmentRules {
    private SfxEnchantmentRules() {
    }

    public static boolean canApplyToTarget(
            ItemStack target,
            Enchantment enchantment,
            int level,
            boolean allowConflictingEnchantments,
            boolean allowIllegalEnchantments
    ) {
        if (target == null || target.getType().isAir() || enchantment == null || level <= 0) {
            return false;
        }
        if (!allowIllegalEnchantments && !enchantment.canEnchantItem(target)) {
            return false;
        }
        if (!allowConflictingEnchantments && conflictsWithExisting(target.getEnchantments(), enchantment)) {
            return false;
        }
        return target.getEnchantmentLevel(enchantment) < level;
    }

    public static boolean conflictsWithExisting(Map<Enchantment, Integer> existing, Enchantment candidate) {
        if (existing == null || candidate == null) {
            return false;
        }
        for (Enchantment enchantment : existing.keySet()) {
            if (!candidate.equals(enchantment) && candidate.conflictsWith(enchantment)) {
                return true;
            }
        }
        return false;
    }

    public static Map<Enchantment, Integer> combineStoredBookEnchantments(
            Map<Enchantment, Integer> first,
            Map<Enchantment, Integer> second,
            boolean allowConflictingEnchantments
    ) {
        Map<Enchantment, Integer> combined = new HashMap<>(first == null ? Map.of() : first);
        if (second == null || second.isEmpty()) {
            return combined;
        }
        for (Map.Entry<Enchantment, Integer> entry : second.entrySet()) {
            if (!allowConflictingEnchantments && conflictsWithExisting(combined, entry.getKey())) {
                return Map.of();
            }
            combined.merge(entry.getKey(), entry.getValue(), (a, b) -> combineLevels(entry.getKey().getMaxLevel(), a, b));
        }
        return combined;
    }

    public static int combineLevels(int maxLevel, int first, int second) {
        if (first == second) {
            return Math.min(maxLevel, first + 1);
        }
        return Math.min(maxLevel, Math.max(first, second));
    }
}
