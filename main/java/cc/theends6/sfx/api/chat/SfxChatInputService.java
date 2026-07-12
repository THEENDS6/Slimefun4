package cc.theends6.sfx.api.chat;

import java.time.Duration;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/** Coordinates one-shot chat input sessions without exposing async chat threads to callers. */
public interface SfxChatInputService extends Listener {
    void await(Player player, String owner, Duration timeout, Consumer<String> onInput, Runnable onTimeout);

    boolean cancel(Player player, String owner);

    boolean isAwaiting(Player player);
}
