package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.api.item.SfxItems;
import org.bukkit.inventory.ItemStack;

record SfxCargoItemKey(String key) {
    static SfxCargoItemKey of(SfxItems items, ItemStack stack) {
        if (stack == null) {
            return new SfxCargoItemKey("air");
        }
        String sfx = items.readMarker(stack).map(marker -> marker.itemId()).orElse("");
        int metaHash = stack.hasItemMeta() && stack.getItemMeta() != null ? stack.getItemMeta().hashCode() : 0;
        return new SfxCargoItemKey(stack.getType().name() + ":" + sfx + ":" + metaHash);
    }
}
