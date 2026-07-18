package cc.theends6.sfx.internal.listener;

import cc.theends6.sfx.api.guide.GuideMode;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.text.Text;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxGuideBookGrantListener implements Listener {
    private static final String CONFIG_PATH = "guide.give-book-on-join";
    private static final String BOOK_PERMISSION = "sfx.command.book";

    private final JavaPlugin plugin;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final NamespacedKey grantedKey;

    public SfxGuideBookGrantListener(JavaPlugin plugin, SfxItems items, SfxLocalization localization) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.grantedKey = new NamespacedKey(plugin, "join_guide_book_granted");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean(CONFIG_PATH, true)
                || !player.hasPermission(BOOK_PERMISSION)
                || wasHandled(player)) {
            return;
        }

        if (!hasSurvivalGuide(player)) {
            items.give(player, items.createGuideBook(GuideMode.SURVIVAL));
            player.sendMessage(Text.prefixed(plugin, localization.text("guide.auto-book-received")));
        }
        player.getPersistentDataContainer().set(grantedKey, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean wasHandled(Player player) {
        return player.getPersistentDataContainer().has(grantedKey, PersistentDataType.BYTE);
    }

    private boolean hasSurvivalGuide(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (items.readGuideMode(item).filter(mode -> mode == GuideMode.SURVIVAL).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
