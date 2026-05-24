package cc.theends6.sfx.internal.ui;

import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record SfxDragContext(Player player, Set<Integer> rawSlots, ItemStack oldCursor) {
}
