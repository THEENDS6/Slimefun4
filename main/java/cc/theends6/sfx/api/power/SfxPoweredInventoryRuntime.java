package cc.theends6.sfx.api.power;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;


public interface SfxPoweredInventoryRuntime {
    SfxPowerInventorySnapshot snapshot(Player player);
    List<SfxPowerRoute> settle(Player player, double transferLimit);
    void invalidate(UUID playerId);
}
