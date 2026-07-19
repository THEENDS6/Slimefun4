package cc.theends6.sfx.api.power;

import java.util.Collection;
import java.util.List;

public interface SfxInventoryPowerRouter {
    List<SfxPowerRoute> route(Collection<? extends SfxPowerPort> sources,
                              Collection<? extends SfxPowerPort> consumers,
                              double transferLimit);
}
