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
    private final Map<UUID, Deque<SfxMenu>> suspended = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> suppressedRestore = ConcurrentHashMap.newKeySet();

    public DefaultSfxMenus(SfxRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void openRoot(Player player, SfxMenu menu) {
        runtime.executeForPlayer(player, () -> {
            suspended.remove(player.getUniqueId());
            openInternal(player, menu, new ArrayDeque<>());
        });
    }

    @Override
    public void open(Player player, SfxMenu menu) {
        runtime.executeForPlayer(player, () -> {
            Session previous = sessions.get(player.getUniqueId());
            Deque<SfxMenu> history = previous == null
                    ? suspended.remove(player.getUniqueId())
                    : copyHistory(previous);
            if (history == null) {
                history = new ArrayDeque<>();
            }
            if (returnsToPrevious(menu, history)) {
                history.pop();
                openInternal(player, menu, history);
                return;
            }
            pushCurrent(previous, history);
            openInternal(player, menu, history);
        });
    }

    @Override
    public void replace(Player player, SfxMenu menu) {
        runtime.executeForPlayer(player, () -> {
            Session previous = sessions.get(player.getUniqueId());
            Deque<SfxMenu> history = previous == null
                    ? suspended.remove(player.getUniqueId())
                    : copyHistory(previous);
            if (history == null) {
                history = new ArrayDeque<>();
            }
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
                suspended.remove(playerId);
            }
            player.closeInventory();
        });
    }

    @Override
    public void suspend(Player player) {
        runtime.executeForPlayer(player, () -> {
            UUID playerId = player.getUniqueId();
            Session current = sessions.remove(playerId);
            if (current == null) {
                return;
            }
            Deque<SfxMenu> history = copyHistory(current);
            history.push(current.menu());
            suspended.put(playerId, history);
            suppressedRestore.add(playerId);
            player.closeInventory();
        });
    }

    @Override
    public void resume(Player player) {
        runtime.executeForPlayer(player, () -> {
            Deque<SfxMenu> history = suspended.remove(player.getUniqueId());
            if (history == null || history.isEmpty()) {
                return;
            }
            SfxMenu previous = history.pop();
            openInternal(player, previous, history);
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
        suspended.clear();
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
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }
        boolean topClick = event.getClickedInventory() != null && event.getClickedInventory().equals(top);
        SfxMenuButton button = topClick ? session.menu().buttons().get(event.getRawSlot()) : null;
        if (button != null && event.isShiftClick()) {
            event.setCancelled(true);
            button.handler().accept(new SfxMenuClickContext(player, event.getRawSlot(), event.getClick(), this));
            return;
        }
        if (SfxInventoryPolicy.cancelDangerousClick(event)) {
            return;
        }
        if (session.menu().cancelPlayerClicks()) {
            event.setCancelled(true);
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }
        button = session.menu().buttons().get(event.getRawSlot());
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
        suspended.remove(uuid);
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

    private boolean returnsToPrevious(SfxMenu target, Deque<SfxMenu> history) {
        return target.historyKey() != null
                && !history.isEmpty()
                && target.historyKey().equals(history.peek().historyKey());
    }

    private record Session(SfxMenu menu, Inventory inventory, Deque<SfxMenu> history) {
    }
}
