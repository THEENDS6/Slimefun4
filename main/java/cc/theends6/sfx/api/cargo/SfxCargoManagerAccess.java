package cc.theends6.sfx.api.cargo;

import org.bukkit.entity.Player;


public interface SfxCargoManagerAccess {
    void setEnabled(boolean enabled);

    void setWorkIntervalTicks(double intervalTicks);

    void toggleVisualizer(Player player);

    void markDirty();
}
