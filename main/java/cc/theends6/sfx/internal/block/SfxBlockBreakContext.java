package cc.theends6.sfx.internal.block;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public record SfxBlockBreakContext(
        Location location,
        Player player,
        SfxBlockDestructionCause cause
) {
}
