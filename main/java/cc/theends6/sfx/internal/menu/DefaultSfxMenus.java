package cc.theends6.sfx.internal.menu;

import cc.theends6.sfx.api.menu.SfxMenu;
import cc.theends6.sfx.api.menu.SfxMenuButton;
import cc.theends6.sfx.api.menu.SfxMenuClickContext;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.ui.SfxInventoryPolicy;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class DefaultSfxMenus implements SfxMenus {
    private final SfxRuntime runtime;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> suppressedRestore = ConcurrentHashMap.newKeySet();

    public DefaultSfxMenus(SfxRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void openRoot(Player player, SfxMenu menu) {
        runtime.executeForPlayer(player, () -> openInternal(player, menu, new ArrayDeque<>()));
    }

    @Override
    public void open(Player player, SfxMenu menu) {
        runtime.executeForPlayer(player, () -> {
            Session previous = sessions.get(player.getUniqueId());
            Deque<SfxMenu> history = copyHistory(previous);
            pushCurrent(previous, history);
            openInternal(player, menu, history);
        });
    }

    @Override
    public void replace(Player player, SfxMenu menu) {
        runtime.executeForPlayer(player, () -> {
            Session previous = sessions.get(player.getUniqueId());
            Deque<SfxMenu> history = copyHistory(previous);
            openInternal(player, menu, history);
        });
    }

    private void openInternal(Player player, SfxMenu menu, Deque<SfxMenu> history) {
        SfxMenuHolder holder = new SfxMenuHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, menu.rows() * 9, menu.title());
        holder.bind(inventory);
        for (Map.Entry<Integer, SfxMenuButton> entry : menu.buttons().entrySet()) {
            inventory.setItem(entry.getKey(), dynamicIcon(menu, entry.getKey(), player, entry.getValue().icon()));
        }
        Session session = new Session(menu, inventory, history);
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);
        if (!menu.dynamicIcons().isEmpty()) {
            scheduleDynamicRefresh(player, session);
        }
    }

    private ItemStack dynamicIcon(SfxMenu menu, int slot, Player player, ItemStack fallback) {
        java.util.function.Function<Player, ItemStack> provider = menu.dynamicIcons().get(slot);
        if (provider == null) {
            return fallback;
        }
        ItemStack resolved = provider.apply(player);
        return resolved == null ? fallback : resolved;
    }

    private void scheduleDynamicRefresh(Player player, Session expected) {
        runtime.executeForPlayerLater(player, 20L, () -> {
            Session current = sessions.get(player.getUniqueId());
            if (current != expected || !player.isOnline()) {
                return;
            }
            for (Map.Entry<Integer, java.util.function.Function<Player, ItemStack>> entry : current.menu().dynamicIcons().entrySet()) {
                SfxMenuButton fallback = current.menu().buttons().get(entry.getKey());
                ItemStack icon = dynamicIcon(current.menu(), entry.getKey(), player, fallback == null ? null : fallback.icon());
                current.inventory().setItem(entry.getKey(), icon);
            }
            scheduleDynamicRefresh(player, expected);
        });
    }

    @Override
    public void close(Player player) {
        close(player, false);
    }

    @Override
    public void close(Player player, boolean restoreHistory) {
        runtime.executeForPlayer(player, () -> {
            UUID playerId = player.getUniqueId();
            if (!restoreHistory) {
                suppressedRestore.add(playerId);
                sessions.remove(playerId);
            }
            player.closeInventory();
        });
    }

    @Override
    public boolean hasHistory(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && !session.history().isEmpty();
    }

    @Override
    public void closeAll() {
        
        
        
        
        
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
        if (SfxInventoryPolicy.cancelDangerousClick(event)) {
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
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }
            UUID playerId = menuHolder.viewerId();
            if (suppressedRestore.remove(playerId)) {
                sessions.remove(playerId);
                return;
            }
            Session session = sessions.get(playerId);
            if (session == null || !session.inventory().equals(event.getInventory())) {
                return;
            }
            sessions.remove(playerId);
            if (session.menu().closeHandler() != null) {
                session.menu().closeHandler().accept(player);
            }
            if (!session.menu().restorePreviousOnClose() || session.history().isEmpty()) {
                return;
            }
            SfxMenu previous = session.history().pop();
            Deque<SfxMenu> remaining = new ArrayDeque<>(session.history());
            runtime.executeForPlayer(player, () -> openInternal(player, previous, remaining));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        suppressedRestore.remove(uuid);
    }

    private Deque<SfxMenu> copyHistory(Session previous) {
        return previous == null ? new ArrayDeque<>() : new ArrayDeque<>(previous.history());
    }

    private void pushCurrent(Session previous, Deque<SfxMenu> history) {
        if (previous != null) {
            history.push(previous.menu());
        }
    }

    private record Session(SfxMenu menu, Inventory inventory, Deque<SfxMenu> history) {
    }
}
