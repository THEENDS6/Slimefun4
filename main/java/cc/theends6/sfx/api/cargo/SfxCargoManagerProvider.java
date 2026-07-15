package cc.theends6.sfx.api.cargo;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxMachineDisplayItem;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.java.JavaPlugin;


public interface SfxCargoManagerProvider {
    default int menuSize() {
        return 27;
    }

    default String titleKey() {
        return "cargo.ui.title.generic";
    }

    default Map<Integer, SfxMachineDisplayItem> displayItems(
            JavaPlugin plugin, SfxItems items, SfxCargoManagerState state) {
        return Map.of();
    }

    default boolean handleMenuClick(
            JavaPlugin plugin,
            SfxItems items,
            SfxCargoManagerState state,
            Player player,
            int rawSlot,
            ClickType clickType,
            SfxCargoManagerAccess access) {
        return false;
    }
}
