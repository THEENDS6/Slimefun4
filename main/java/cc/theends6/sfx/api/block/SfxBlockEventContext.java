package cc.theends6.sfx.api.block;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface SfxBlockEventContext<S> {
    UUID instanceId();
    String blockTypeId();
    Location location();
    Player actor();
    S state();
    void state(S state);
}
