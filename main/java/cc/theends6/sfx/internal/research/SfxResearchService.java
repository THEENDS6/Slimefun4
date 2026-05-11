package cc.theends6.sfx.internal.research;

import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.playerdata.SfxPlayerProfile;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class SfxResearchService {
    private final SfxResearchRegistry registry;
    private final SfxPlayerDataService profiles;

    public SfxResearchService(SfxResearchRegistry registry, SfxPlayerDataService profiles) {
        this.registry = registry;
        this.profiles = profiles;
    }

    public SfxResearchRegistry registry() {
        return registry;
    }

    public Collection<SfxResearchDefinition> allResearches() {
        return registry.all();
    }

    public Optional<SfxResearchDefinition> researchById(String id) {
        return registry.byId(id);
    }

    public Optional<SfxResearchDefinition> researchForItem(String itemId) {
        return registry.byItemId(itemId);
    }

    public boolean isUnlocked(Player player, String itemId) {
        Optional<SfxResearchDefinition> research = researchForItem(itemId);
        if (research.isEmpty()) {
            return true;
        }
        return findProfile(player.getUniqueId())
                .map(profile -> profile.hasUnlocked(research.get().id()))
                .orElse(false);
    }

    public boolean canUse(Player player, String itemId) {
        return isUnlocked(player, itemId);
    }

    public UnlockResult unlock(Player player, SfxResearchDefinition research) {
        Optional<SfxPlayerProfile> optional = findProfile(player.getUniqueId());
        if (optional.isEmpty()) {
            return UnlockResult.PROFILE_NOT_LOADED;
        }
        SfxPlayerProfile profile = optional.get();
        if (profile.hasUnlocked(research.id())) {
            return UnlockResult.ALREADY_UNLOCKED;
        }
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getLevel() < research.cost()) {
            return UnlockResult.NOT_ENOUGH_LEVELS;
        }
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            player.setLevel(player.getLevel() - research.cost());
        }
        profile.unlock(research.id());
        profiles.saveAsync(profile);
        return UnlockResult.UNLOCKED;
    }

    public boolean grant(SfxPlayerProfile profile, SfxResearchDefinition research) {
        if (profile.hasUnlocked(research.id())) {
            return false;
        }
        profile.unlock(research.id());
        profiles.saveAsync(profile);
        return true;
    }

    public int grantAll(SfxPlayerProfile profile) {
        boolean changed = profile.unlockAll(registry.all().stream().map(SfxResearchDefinition::id).toList());
        if (changed) {
            profiles.saveAsync(profile);
        }
        return profile.unlockedResearchesCopy().size();
    }

    public void resetAll(SfxPlayerProfile profile) {
        profile.clearUnlockedResearches();
        profiles.saveAsync(profile);
    }

    public Optional<SfxPlayerProfile> findProfile(UUID uuid) {
        return profiles.find(uuid);
    }

    public enum UnlockResult {
        PROFILE_NOT_LOADED,
        ALREADY_UNLOCKED,
        NOT_ENOUGH_LEVELS,
        UNLOCKED
    }
}
