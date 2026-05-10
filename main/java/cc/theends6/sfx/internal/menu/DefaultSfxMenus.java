package cc.theends6.sfx.internal.menu;

import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.api.menu.SfxMenuClickContext;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class DefaultSfxMenus implements SfxMenus {
    private final SfxRuntime runtime;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public DefaultSfxMenus(SfxRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void open(Player player, SfxMenu menu) {
        runtime.executeForPlayer(player, () -> {
            SfxMenuHolder holder = new SfxMenuHolder(player.getUniqueId());
            Inventory inventory = Bukkit.createInventory(holder, menu.rows() * 9, menu.title());
            holder.bind(inventory);
            for (Map.Entry<Integer, SfxMenuButton> entry : menu.buttons().entrySet()) {
                inventory.setItem(entry.getKey(), entry.getValue().icon());
            }
            sessions.put(player.getUniqueId(), new Session(menu, inventory));
            player.openInventory(inventory);
        });
    }

    @Override
    public void close(Player player) {
        runtime.executeForPlayer(player, player::closeInventory);
    }

    @Override
    public void closeAll() {
        // Folia note: do not directly touch Player inventory from a global/plugin-disable
        // context. Snapshot the viewers, clear SFX-owned menu sessions, then ask each
        // player entity scheduler to close its own inventory. If the scheduler rejects
        // the task during shutdown, the session cleanup has already happened and no
        // world/entity state is modified from the wrong thread.
        List<UUID> viewers = List.copyOf(sessions.keySet());
        sessions.clear();
        for (UUID uuid : viewers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            try {
                runtime.executeForPlayer(player, () -> {
                    InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
                    if (holder instanceof SfxMenuHolder) {
                        player.closeInventory();
                    }
                });
            } catch (IllegalStateException ignored) {
                // Plugin is probably disabling. Session state is already cleared.
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof SfxMenuHolder menuHolder)) {
            return;
        }
        if (!menuHolder.viewerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }
        if (session.menu().cancelPlayerClicks()) {
            event.setCancelled(true);
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }
        SfxMenuButton button = session.menu().buttons().get(event.getRawSlot());
        if (button != null) {
            button.handler().accept(new SfxMenuClickContext(player, event.getRawSlot(), event.getClick(), this));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SfxMenuHolder) {
            int topSize = top.getSize();
            boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
            if (touchesTop) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof SfxMenuHolder menuHolder) {
            Session session = sessions.get(menuHolder.viewerId());
            if (session == null || !session.inventory().equals(event.getInventory())) {
                return;
            }
            sessions.remove(menuHolder.viewerId());
            if (session.menu().closeHandler() != null && event.getPlayer() instanceof Player player) {
                session.menu().closeHandler().accept(player);
            }
        }
    }

    private record Session(SfxMenu menu, Inventory inventory) {
    }
}
