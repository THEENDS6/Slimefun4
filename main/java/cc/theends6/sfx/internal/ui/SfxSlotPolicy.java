package cc.theends6.sfx.internal.ui;

import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

public record SfxSlotPolicy(SfxSlotRole role, Predicate<ItemStack> itemPredicate) {
    public static SfxSlotPolicy locked() {
        return new SfxSlotPolicy(SfxSlotRole.LOCKED, stack -> false);
    }

    public static SfxSlotPolicy input(Predicate<ItemStack> predicate) {
        Predicate<ItemStack> effective = predicate;
        if (effective == null) {
            effective = stack -> true;
        }
        return new SfxSlotPolicy(SfxSlotRole.INPUT, effective);
    }

    public static SfxSlotPolicy output() {
        return new SfxSlotPolicy(SfxSlotRole.OUTPUT, stack -> false);
    }

    public static SfxSlotPolicy button() {
        return new SfxSlotPolicy(SfxSlotRole.BUTTON, stack -> false);
    }

    public static SfxSlotPolicy status() {
        return new SfxSlotPolicy(SfxSlotRole.STATUS, stack -> false);
    }

    public static SfxSlotPolicy decoration() {
        return new SfxSlotPolicy(SfxSlotRole.DECORATION, stack -> false);
    }

    public static SfxSlotPolicy filterGhost() {
        return new SfxSlotPolicy(SfxSlotRole.FILTER_GHOST, stack -> true);
    }

    public boolean accepts(ItemStack stack) {
        return itemPredicate != null && itemPredicate.test(stack);
    }
}
