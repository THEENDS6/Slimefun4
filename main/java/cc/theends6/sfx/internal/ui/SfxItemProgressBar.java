package cc.theends6.sfx.internal.ui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;








public final class SfxItemProgressBar {
    private static final int DEFAULT_CUSTOM_MAX_DAMAGE = 100;

    private SfxItemProgressBar() {
    }

    public static void applyToDisplayItem(ItemStack stack, int current, int total) {
        applyToDisplayItem(stack, current, total, SfxDurabilityBarMode.AUTO, DEFAULT_CUSTOM_MAX_DAMAGE);
    }

    public static void applyToDisplayItem(ItemStack stack, int current, int total, SfxDurabilityBarMode mode) {
        applyToDisplayItem(stack, current, total, mode, DEFAULT_CUSTOM_MAX_DAMAGE);
    }

    public static void applyToDisplayItem(ItemStack stack, int current, int total, SfxDurabilityBarMode mode, int customMaxDamage) {
        if (stack == null || stack.getType() == Material.AIR || total <= 0 || mode == null || mode == SfxDurabilityBarMode.NONE) {
            return;
        }
        int safeTotal = Math.max(1, total);
        int safeCurrent = Math.max(0, Math.min(safeTotal, current));
        int nativeMax = stack.getType().getMaxDurability();
        if (nativeMax > 1 && mode != SfxDurabilityBarMode.COMPONENT_ONLY && applyNative(stack, safeCurrent, safeTotal, nativeMax)) {
            return;
        }
        if (mode == SfxDurabilityBarMode.NATIVE_ONLY) {
            return;
        }
        applyComponent(stack, safeCurrent, safeTotal, Math.max(1, customMaxDamage));
    }

    
    @Deprecated(forRemoval = false)
    public static void apply(ItemStack stack, int current, int total) {
        applyToDisplayItem(stack, current, total);
    }

    
    @Deprecated(forRemoval = false)
    public static void apply(ItemStack stack, int current, int total, SfxDurabilityBarMode mode) {
        applyToDisplayItem(stack, current, total, mode);
    }

    
    @Deprecated(forRemoval = false)
    public static void apply(ItemStack stack, int current, int total, SfxDurabilityBarMode mode, int customMaxDamage) {
        applyToDisplayItem(stack, current, total, mode, customMaxDamage);
    }

    private static boolean applyNative(ItemStack stack, int current, int total, int maxDamage) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return false;
        }
        damageable.setDamage(progressToDamage(current, total, maxDamage));
        stack.setItemMeta(meta);
        return true;
    }

    private static void applyComponent(ItemStack stack, int current, int total, int maxDamage) {
        stack.setAmount(1);
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        stack.setData(DataComponentTypes.MAX_DAMAGE, maxDamage);
        stack.setData(DataComponentTypes.DAMAGE, progressToDamage(current, total, maxDamage));
    }

    private static int progressToDamage(int current, int total, int maxDamage) {
        int safeMax = Math.max(1, maxDamage);
        int safeTotal = Math.max(1, total);
        int safeCurrent = Math.max(0, Math.min(safeTotal, current));
        int visible = Math.max(1, (int) Math.round((safeCurrent / (double) safeTotal) * safeMax));
        return Math.max(0, Math.min(safeMax - 1, safeMax - visible));
    }
}
