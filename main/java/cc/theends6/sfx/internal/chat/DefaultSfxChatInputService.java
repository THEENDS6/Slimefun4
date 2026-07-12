package cc.theends6.sfx.internal.chat;

import cc.theends6.sfx.api.chat.SfxChatInputService;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

public final class DefaultSfxChatInputService implements SfxChatInputService {
    private final SfxRuntime runtime;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public DefaultSfxChatInputService(SfxRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public void await(Player player, String owner, Duration timeout, Consumer<String> onInput, Runnable onTimeout) {
        Objects.requireNonNull(player, "player");
        String normalizedOwner = requireOwner(owner);
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(onInput, "onInput");
        Objects.requireNonNull(onTimeout, "onTimeout");
        long timeoutTicks = Math.max(1L, (timeout.toMillis() + 49L) / 50L);
        Session session = new Session(normalizedOwner, UUID.randomUUID(), onInput, onTimeout);
        sessions.put(player.getUniqueId(), session);
        runtime.executeForPlayerLater(player, timeoutTicks, () -> {
            if (sessions.remove(player.getUniqueId(), session)) {
                session.onTimeout().run();
            }
        });
    }

    @Override
    public boolean cancel(Player player, String owner) {
        Objects.requireNonNull(player, "player");
        String normalizedOwner = requireOwner(owner);
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.owner().equals(normalizedOwner)
                && sessions.remove(player.getUniqueId(), session);
    }

    @Override
    public boolean isAwaiting(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        runtime.executeForPlayer(player, () -> session.onInput().accept(input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private static String requireOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Chat input owner cannot be blank");
        }
        return owner.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private record Session(String owner, UUID token, Consumer<String> onInput, Runnable onTimeout) {
    }
}
