package cc.theends6.sfx.internal.ui;

import java.util.UUID;
import org.bukkit.entity.Player;

public record SfxMenuSession(UUID playerId, SfxMenuScreen screen) {
    public static SfxMenuSession of(Player player, SfxMenuScreen screen) {
        return new SfxMenuSession(player.getUniqueId(), screen);
    }
}
