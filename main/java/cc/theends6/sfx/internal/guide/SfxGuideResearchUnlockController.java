package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.internal.playerdata.SfxPlayerProfile;
import cc.theends6.sfx.internal.research.SfxResearchDefinition;
import cc.theends6.sfx.internal.util.Text;
import org.bukkit.entity.Player;

/**
 * Handles research unlock sequencing and guide animation outside the main guide view class.
 */
final class SfxGuideResearchUnlockController {
    private SfxGuideResearchUnlockController() {
    }

    static void begin(DefaultSfxGuide guide, Player player, SfxPlayerProfile profile, SfxItemDefinition definition, SfxResearchDefinition research, Runnable onSuccess, Runnable onFailure) {
        if (profile.hasUnlocked(research.id())) {
            onSuccess.run();
            return;
        }
        if (!guide.canAffordResearch(player, research)) {
            player.sendMessage(Text.prefixed(guide.plugin, guide.tr("messages.not-enough-xp", "<red>You do not have enough levels to unlock this research.</red>")));
            onFailure.run();
            return;
        }
        if (!guide.researchingPlayers.add(player.getUniqueId())) {
            return;
        }

        String researchName = guide.displayResearchName(research, definition);
        guide.consumeResearchCost(player, research);
        GuidePreferences preferences = guide.preferences(player);

        if (!preferences.unlockAnimation()) {
            guide.finishResearchUnlock(player, profile, definition, research, onSuccess);
            return;
        }

        player.sendMessage(Text.prefixed(guide.plugin,
                guide.tr("messages.research.start", "<gray>The Ancient Spirits whisper mysterious words into your ear!</gray>")));

        guide.runtime.executeForPlayerLater(player, 5L, () -> {
            if (!player.isOnline()) {
                guide.finishResearchUnlock(player, profile, definition, research, onSuccess);
                return;
            }
                guide.playResearchSound(player);
                player.sendMessage(Text.prefixed(guide.plugin,
                    guide.tr("messages.research.progress", "<gray>You start to wonder about </gray><aqua>{name}</aqua><gray> ({progress})</gray>")
                            .replace("{name}", researchName)
                            .replace("{progress}", "0%")));
        });

        for (int index = 0; index < guide.RESEARCH_PROGRESS.length; index++) {
            int progress = guide.RESEARCH_PROGRESS[index];
            long delay = (index + 1L) * 20L;
            guide.runtime.executeForPlayerLater(player, delay, () -> {
                if (!player.isOnline()) {
                    return;
                }
                guide.playResearchSound(player);
                player.sendMessage(Text.prefixed(guide.plugin,
                        guide.tr("messages.research.progress", "<gray>You start to wonder about </gray><aqua>{name}</aqua><gray> ({progress})</gray>")
                                .replace("{name}", researchName)
                                .replace("{progress}", progress + "%")));
            });
        }

        guide.runtime.executeForPlayerLater(player, (guide.RESEARCH_PROGRESS.length + 1L) * 20L, () ->
                guide.finishResearchUnlock(player, profile, definition, research, onSuccess));
    
    }
}
