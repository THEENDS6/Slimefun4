package cc.theends6.sfx.api.randomtick;

import cc.theends6.sfx.api.block.SfxBlockEventContext;
import org.bukkit.World;

public interface SfxRandomTickContext<S> extends SfxBlockEventContext<S> {
    World world();
    int randomTickSpeed();
}
