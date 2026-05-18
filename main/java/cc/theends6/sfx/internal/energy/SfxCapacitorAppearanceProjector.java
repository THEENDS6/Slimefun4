package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.util.HeadTextures;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

final class SfxCapacitorAppearanceProjector {
    private final SfxRuntime runtime;
    private final SfxBlockDataService blockData;
    private final Map<String, SfxEnergyComponentDefinition> definitions;
    private final Map<UUID, String> lastTextures = new ConcurrentHashMap<>();
    private final Map<UUID, String> requestedTextures = new ConcurrentHashMap<>();

    SfxCapacitorAppearanceProjector(SfxRuntime runtime, SfxBlockDataService blockData, Map<String, SfxEnergyComponentDefinition> definitions) {
        this.runtime = runtime;
        this.blockData = blockData;
        this.definitions = definitions;
    }

    void scheduleUpdate(UUID instanceId, Location location, int stored, int capacity) {
        if (instanceId == null || location == null || location.getWorld() == null || capacity <= 0) {
            return;
        }
        String texture = capacitorTexture(stored, capacity);
        String previous = requestedTextures.put(instanceId, texture);
        if (texture.equals(previous) || texture.equals(lastTextures.get(instanceId))) {
            return;
        }
        runtime.executeAt(location, () -> updateCapacitorAppearance(location, texture));
    }

    void remove(UUID instanceId) {
        if (instanceId != null) {
            requestedTextures.remove(instanceId);
            lastTextures.remove(instanceId);
        }
    }

    private void updateCapacitorAppearance(Location location, String texture) {
        if (location == null || location.getWorld() == null || texture == null) {
            return;
        }
        SfxAnchorRecord anchor = blockData.findAnchorFast(location).orElse(null);
        if (anchor == null) {
            return;
        }
        String previous = lastTextures.get(anchor.instanceId());
        if (texture.equals(previous)) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        SfxEnergyComponentDefinition definition = definitions.get(instance.typeId());
        if (definition == null || definition.componentType() != SfxEnergyComponentType.CAPACITOR) {
            return;
        }
        Block block = location.getBlock();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof Skull skull) {
            HeadTextures.apply(skull, texture);
            lastTextures.put(anchor.instanceId(), texture);
            requestedTextures.put(anchor.instanceId(), texture);
        }
    }

    static String capacitorTexture(int stored, int capacity) {
        double percent = stored <= 0 ? 0D : (stored * 100D) / Math.max(1, capacity);
        if (percent <= 25D) {
            return "91361e576b493cbfdfae328661cedd1add55fab4e5eb418b92cebf6275f8bb4";
        }
        if (percent <= 50D) {
            return "305323394a7d91bfb33df06d92b63cb414ef80f054d04734ea015a23c539";
        }
        if (percent <= 75D) {
            return "5584432af6f382167120258d1eee8c87c6e75d9e479e7b0d4c7b6ad48cfeef";
        }
        return "7a2569415c14e31c98ec993a2f99e6d64846db367a13b199965ad99c438c86c";
    }
}
