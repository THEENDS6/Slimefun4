package cc.theends6.sfx.api.display;

import java.util.UUID;
import org.bukkit.entity.Player;


public interface SfxDisplaySessionService {
    void upsert(SfxDisplayProjection projection);
    void remove(UUID projectionId);
    void refresh(Player player);
    void setCategoryEnabled(UUID playerId, String categoryId, boolean enabled);
    boolean categoryEnabled(UUID playerId, String categoryId);
}
