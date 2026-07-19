package cc.theends6.sfx.api.behavior;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;


public record SfxEntityDropContext(
        String definitionId,
        String outputItemId,
        EntityType entityType,
        SfxEntityDeathSource deathSource,
        Player playerKiller,
        int lootingLevel
) {
    public SfxEntityDropContext {
        if (definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("Entity drop definition id cannot be blank.");
        }
        if (outputItemId == null || outputItemId.isBlank()) {
            throw new IllegalArgumentException("Entity drop output item id cannot be blank.");
        }
        if (entityType == null || deathSource == null) {
            throw new IllegalArgumentException("Entity drop type and death source cannot be null.");
        }
        if (deathSource == SfxEntityDeathSource.PLAYER && playerKiller == null) {
            throw new IllegalArgumentException("Player-attributed entity drops require a player killer.");
        }
        lootingLevel = Math.max(0, lootingLevel);
    }
}
