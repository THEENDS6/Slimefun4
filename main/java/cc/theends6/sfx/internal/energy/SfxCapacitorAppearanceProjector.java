package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.util.HeadTextures;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

final class SfxCapacitorAppearanceProjector {
    private final SfxRuntime runtime;

    SfxCapacitorAppearanceProjector(SfxRuntime runtime) {
        this.runtime = runtime;
    }

    void scheduleUpdate(Location location, int stored, int capacity) {
        if (location == null || location.getWorld() == null || capacity <= 0) {
            return;
        }
        String texture = capacitorTexture(stored, capacity);
        runtime.executeAt(location, () -> updateCapacitorAppearance(location, texture));
    }

    private void updateCapacitorAppearance(Location location, String texture) {
        if (location == null || location.getWorld() == null || texture == null) {
            return;
        }
        Block block = location.getBlock();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) {
            block.setType(Material.PLAYER_HEAD, false);
        }
        BlockState state = block.getState();
        if (state instanceof Skull skull) {
            HeadTextures.apply(skull, texture);
        }
    }

    private String capacitorTexture(int stored, int capacity) {
        double ratio = stored <= 0 ? 0D : stored / (double) Math.max(1, capacity);
        if (ratio >= 0.999D) {
            return "7a2569415c14e31c98ec993a2f99e6d64846db367a13b199965ad99c438c86c";
        }
        if (ratio >= 0.75D) {
            return "5584432af6f382167120258d1eee8c87c6e75d9e479e7b0d4c7b6ad48cfeef";
        }
        if (ratio >= 0.50D) {
            return "305323394a7d91bfb33df06d92b63cb414ef80f054d04734ea015a23c539";
        }
        return "91361e576b493cbfdfae328661cedd1add55fab4e5eb418b92cebf6275f8bb4";
    }
}
