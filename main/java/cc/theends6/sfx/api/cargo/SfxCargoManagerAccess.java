package cc.theends6.sfx.api.cargo;

import org.bukkit.entity.Player;


public interface SfxCargoManagerAccess {
    void setEnabled(boolean enabled);

    void setSpeedMultiplier(int multiplier);

    void toggleVisualizer(Player player);

    void markDirty();
}
